import http from 'node:http';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  CozeAPI,
  COZE_CN_BASE_URL,
  COZE_COM_BASE_URL,
  WorkflowEventType,
  getWebAuthenticationUrl,
  getWebOAuthToken,
  refreshOAuthToken,
} from '@coze/api';

const port = Number.parseInt(process.env.COZE_BRIDGE_PORT ?? '8787', 10);
const workflowId = (process.env.COZE_WORKFLOW_ID ?? '').trim();
const defaultUserId = (process.env.COZE_BRIDGE_USER_ID ?? 'novel-backend').trim();
const baseURL = resolveBaseURL();
const defaultBotId = (process.env.COZE_BOT_ID ?? '').trim();
const defaultAppId = (process.env.COZE_APP_ID ?? '').trim();
const expectedState = (process.env.COZE_OAUTH_EXPECTED_STATE ?? '').trim();

let cachedAccessToken = (process.env.COZE_OAUTH_ACCESS_TOKEN ?? '').trim();
let cachedRefreshToken = (process.env.COZE_OAUTH_REFRESH_TOKEN ?? '').trim();
let cachedTokenExpiresAt = Number.parseInt(process.env.COZE_OAUTH_EXPIRES_AT ?? '0', 10) * 1000;

const client = new CozeAPI({
  baseURL,
  token: async () => {
    return await resolveSdkToken();
  },
});

if (!workflowId) {
  throw new Error('COZE_WORKFLOW_ID 未配置');
}

const server = http.createServer(async (req, res) => {
  try {
    if (req.method === 'GET' && req.url === '/health') {
      writeJson(res, 200, { status: 'ok' });
      return;
    }

    if (req.method === 'GET' && req.url?.startsWith('/oauth/authorize-url')) {
      const url = new URL(req.url, 'http://localhost');
      const stateFromQuery = (url.searchParams.get('state') ?? '').trim();
      const state = stateFromQuery || expectedState || randomState();
      const auth = buildAuthorizationUrl(state);
      writeJson(res, 200, {
        authorize_url: auth,
        state,
        redirect_uri: (process.env.COZE_OAUTH_REDIRECT_URL ?? '').trim(),
      });
      return;
    }

    if (req.method === 'POST' && req.url === '/run') {
      const payload = await readJsonBody(req);
      const request = buildWorkflowRequest(payload);
      const result = await client.workflows.runs.create(request);
      const finalDocument = extractFinalDocument(result.data);
      if (!finalDocument) {
        throw new Error('工作流返回结果缺少 final_document');
      }
      writeJson(res, 200, {
        final_document: finalDocument,
        execute_id: result.execute_id ?? '',
        debug_url: result.debug_url ?? '',
      });
      return;
    }

    if (req.method === 'POST' && req.url === '/stream_run') {
      const payload = await readJsonBody(req);
      const request = buildWorkflowRequest(payload);
      const stream = await client.workflows.runs.stream(request);

      res.statusCode = 200;
      res.setHeader('Content-Type', 'text/event-stream; charset=utf-8');
      res.setHeader('Cache-Control', 'no-cache');
      res.setHeader('Connection', 'keep-alive');

      let finalDocument = '';
      let chunkIndex = 0;
      for await (const event of stream) {
        if (event.event === WorkflowEventType.MESSAGE) {
          const content = extractEventContent(event.data);
          if (!content) {
            continue;
          }
          const parsedFinal = extractFinalDocument(content);
          const nextDocument = parsedFinal || `${finalDocument}${content}`;
          const delta = deriveDeltaContent(finalDocument, nextDocument);
          finalDocument = nextDocument;
          if (!delta) {
            continue;
          }
          writeSseFrame(res, 'content_chunk', {
            chunk: delta,
            chunk_index: chunkIndex,
          });
          chunkIndex += 1;
          continue;
        }

        if (event.event === WorkflowEventType.ERROR) {
          const errorMessage = extractEventError(event.data) ?? 'workflow stream error';
          writeSseFrame(res, 'error', { message: errorMessage });
          throw new Error(errorMessage);
        }
      }

      if (!finalDocument.trim()) {
        throw new Error('工作流流式响应未产出内容');
      }
      writeSseFrame(res, 'done', {
        chunk_count: chunkIndex,
        content_length: finalDocument.length,
      });
      res.end();
      return;
    }

    if (req.method === 'GET' && req.url?.startsWith('/oauth/callback')) {
      const url = new URL(req.url, 'http://localhost');
      const error = (url.searchParams.get('error') ?? '').trim();
      if (error) {
        throw new Error(`OAuth 回调失败: ${error}`);
      }
      const code = (url.searchParams.get('code') ?? '').trim();
      const state = (url.searchParams.get('state') ?? '').trim();
      if (!code) {
        throw new Error('OAuth 回调缺少 code');
      }
      const token = await consumeOAuthCode(code, state);
      writeJson(res, 200, {
        ok: true,
        expires_at: token.expiresAt,
        has_refresh_token: token.hasRefreshToken,
      });
      return;
    }

    if (req.method === 'POST' && req.url === '/oauth/callback') {
      const payload = await readJsonBody(req);
      const code = (payload.code ?? '').toString().trim();
      const state = (payload.state ?? '').toString().trim();
      if (!code) {
        throw new Error('OAuth 回调缺少 code');
      }
      const token = await consumeOAuthCode(code, state);
      writeJson(res, 200, {
        ok: true,
        expires_at: token.expiresAt,
        has_refresh_token: token.hasRefreshToken,
      });
      return;
    }

    writeJson(res, 404, { message: 'Not Found' });
  } catch (error) {
    if (!res.headersSent) {
      writeJson(res, 500, { message: normalizeError(error) });
    } else if (!res.writableEnded) {
      writeSseFrame(res, 'error', { message: normalizeError(error) });
      res.end();
    }
  }
});

server.listen(port, '0.0.0.0', () => {
  process.stdout.write(`coze-sdk-bridge listening on :${port}\n`);
});

function resolveBaseURL() {
  const raw = (process.env.COZE_API_BASE ?? process.env.COZE_BASE_URL ?? '').trim();
  if (!raw) {
    return COZE_CN_BASE_URL;
  }
  if (raw === 'coze.com') {
    return COZE_COM_BASE_URL;
  }
  if (raw === 'coze.cn') {
    return COZE_CN_BASE_URL;
  }
  return raw;
}

function resolveWebBaseURL() {
  const api = resolveBaseURL();
  return api.replace('https://api.', 'https://www.');
}

async function resolveSdkToken() {
  const now = Date.now();
  if (cachedAccessToken && cachedTokenExpiresAt - now > 15_000) {
    return cachedAccessToken;
  }

  const clientId = (process.env.COZE_OAUTH_CLIENT_ID ?? '').trim();
  const clientSecret = (process.env.COZE_OAUTH_CLIENT_SECRET ?? '').trim();
  const redirectUrl = (process.env.COZE_OAUTH_REDIRECT_URL ?? '').trim();
  const authCode = (process.env.COZE_OAUTH_CODE ?? '').trim();

  if (cachedRefreshToken && clientId && clientSecret) {
    const token = await refreshOAuthToken({
      baseURL,
      clientId,
      clientSecret,
      refreshToken: cachedRefreshToken,
    });
    cachedAccessToken = token.access_token;
    cachedRefreshToken = token.refresh_token;
    cachedTokenExpiresAt = Date.now() + Math.max(30, token.expires_in - 30) * 1000;
    await persistOAuthTokens();
    return cachedAccessToken;
  }

  if (authCode && clientId && clientSecret && redirectUrl) {
    const token = await getWebOAuthToken({
      baseURL,
      code: authCode,
      clientId,
      clientSecret,
      redirectUrl,
    });
    cachedAccessToken = token.access_token;
    cachedRefreshToken = token.refresh_token;
    cachedTokenExpiresAt = Date.now() + Math.max(30, token.expires_in - 30) * 1000;
    await persistOAuthTokens();
    return cachedAccessToken;
  }

  if (cachedAccessToken) {
    return cachedAccessToken;
  }
  throw new Error('OAuth 授权码鉴权未配置完整，请设置 COZE_OAUTH_CLIENT_ID / COZE_OAUTH_CLIENT_SECRET / COZE_OAUTH_REDIRECT_URL / COZE_OAUTH_CODE');
}

async function consumeOAuthCode(code, state) {
  if (expectedState && state !== expectedState) {
    throw new Error('OAuth state 校验失败');
  }
  const clientId = (process.env.COZE_OAUTH_CLIENT_ID ?? '').trim();
  const clientSecret = (process.env.COZE_OAUTH_CLIENT_SECRET ?? '').trim();
  const redirectUrl = (process.env.COZE_OAUTH_REDIRECT_URL ?? '').trim();
  if (!clientId || !clientSecret || !redirectUrl) {
    throw new Error('OAuth 回调处理缺少 COZE_OAUTH_CLIENT_ID / COZE_OAUTH_CLIENT_SECRET / COZE_OAUTH_REDIRECT_URL');
  }
  const token = await getWebOAuthToken({
    baseURL,
    code,
    clientId,
    clientSecret,
    redirectUrl,
  });
  cachedAccessToken = token.access_token;
  cachedRefreshToken = token.refresh_token;
  cachedTokenExpiresAt = Date.now() + Math.max(30, token.expires_in - 30) * 1000;
  await persistOAuthTokens();
  return {
    expiresAt: Math.floor(cachedTokenExpiresAt / 1000),
    hasRefreshToken: Boolean(cachedRefreshToken),
  };
}

function buildAuthorizationUrl(state) {
  const clientId = (process.env.COZE_OAUTH_CLIENT_ID ?? '').trim();
  const redirectUrl = (process.env.COZE_OAUTH_REDIRECT_URL ?? '').trim();
  if (!clientId || !redirectUrl) {
    throw new Error('OAuth 授权链接生成缺少 COZE_OAUTH_CLIENT_ID / COZE_OAUTH_REDIRECT_URL');
  }
  if (!clientId.endsWith('.app.coze')) {
    throw new Error('COZE_OAUTH_CLIENT_ID 格式错误，应为 xxx.app.coze');
  }
  return getWebAuthenticationUrl({
    baseURL: resolveWebBaseURL(),
    clientId,
    redirectUrl,
    state,
  });
}

function randomState() {
  return `${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
}

async function persistOAuthTokens() {
  const envFilePath = resolveEnvFilePath();
  let content = '';
  try {
    content = await fs.readFile(envFilePath, 'utf-8');
  } catch {
    return;
  }
  let next = content;
  next = upsertEnvValue(next, 'COZE_OAUTH_ACCESS_TOKEN', cachedAccessToken);
  next = upsertEnvValue(next, 'COZE_OAUTH_REFRESH_TOKEN', cachedRefreshToken);
  next = upsertEnvValue(next, 'COZE_OAUTH_EXPIRES_AT', String(Math.floor(cachedTokenExpiresAt / 1000)));
  next = upsertEnvValue(next, 'COZE_OAUTH_CODE', '');
  if (next !== content) {
    await fs.writeFile(envFilePath, next, 'utf-8');
  }
}

function upsertEnvValue(content, key, value) {
  const safeValue = String(value ?? '').replace(/\r?\n/g, '');
  const line = `${key}=${safeValue}`;
  const pattern = new RegExp(`^${key}=.*$`, 'm');
  if (pattern.test(content)) {
    return content.replace(pattern, line);
  }
  const suffix = content.endsWith('\n') ? '' : '\n';
  return `${content}${suffix}${line}\n`;
}

function resolveEnvFilePath() {
  const custom = (process.env.COZE_ENV_FILE ?? '').trim();
  if (custom) {
    return path.resolve(custom);
  }
  const currentDir = path.dirname(fileURLToPath(import.meta.url));
  return path.resolve(currentDir, '..', '.env');
}

function buildWorkflowRequest(payload) {
  const parameters = normalizeParameters(payload);
  const userId = (payload.user_id ?? payload.userId ?? defaultUserId).toString();
  const appId = (payload.app_id ?? payload.appId ?? defaultAppId).toString().trim();
  const botId = (payload.bot_id ?? payload.botId ?? defaultBotId).toString().trim();
  const request = {
    workflow_id: workflowId,
    parameters,
    ext: { user_id: userId },
  };
  if (appId) {
    request.app_id = appId;
  }
  if (botId) {
    request.bot_id = botId;
  }
  return request;
}

function normalizeParameters(payload) {
  const input = isRecord(payload) ? payload : {};
  const nested = isRecord(input.parameters) ? input.parameters : {};
  const reference = firstNonBlank(
    nested.reference_file,
    nested.reference_doc,
    nested.referenceFile,
    nested.referenceDoc,
    input.reference_file,
    input.reference_doc,
    input.referenceFile,
    input.referenceDoc
  );
  const genre = firstNonBlank(
    nested.genre_type,
    nested.genreType,
    input.genre_type,
    input.genreType
  ) ?? '小说';
  const direction = firstNonBlank(
    nested.writing_direction,
    nested.writingDirection,
    input.writing_direction,
    input.writingDirection
  ) ?? '';
  const isContinue = firstBoolean(
    nested.is_continue_writing,
    nested.isContinueWriting,
    input.is_continue_writing,
    input.isContinueWriting
  ) ?? false;
  return {
    ...nested,
    reference_file: reference,
    reference_doc: reference,
    genre_type: genre,
    writing_direction: direction,
    is_continue_writing: isContinue,
  };
}

async function readJsonBody(req) {
  const chunks = [];
  for await (const chunk of req) {
    chunks.push(chunk);
  }
  const raw = Buffer.concat(chunks).toString('utf-8').trim();
  if (!raw) {
    return {};
  }
  return JSON.parse(raw);
}

function writeJson(res, statusCode, payload) {
  const raw = JSON.stringify(payload);
  res.statusCode = statusCode;
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.end(raw);
}

function writeSseFrame(res, event, payload) {
  const data = JSON.stringify(payload);
  res.write(`event: ${event}\n`);
  res.write(`data: ${data}\n\n`);
}

function deriveDeltaContent(previous, next) {
  if (typeof next !== 'string' || !next) {
    return '';
  }
  if (!previous) {
    return next;
  }
  if (next === previous) {
    return '';
  }
  if (next.startsWith(previous)) {
    return next.slice(previous.length);
  }
  return '';
}

function extractFinalDocument(raw) {
  if (typeof raw !== 'string' || !raw.trim()) {
    return '';
  }
  const parsed = safeJsonParse(raw);
  if (typeof parsed === 'string') {
    return parsed;
  }
  if (isRecord(parsed)) {
    const finalDocument = findStringByKey(parsed, 'final_document');
    if (finalDocument) {
      return finalDocument;
    }
  }
  return raw;
}

function extractEventContent(data) {
  if (!isRecord(data)) {
    return '';
  }
  const raw = data.content;
  if (typeof raw === 'string') {
    return raw;
  }
  return '';
}

function extractEventError(data) {
  if (!isRecord(data)) {
    return null;
  }
  const message = data.error_message;
  if (typeof message === 'string' && message.trim()) {
    return message.trim();
  }
  return null;
}

function safeJsonParse(raw) {
  try {
    return JSON.parse(raw);
  } catch {
    return raw;
  }
}

function findStringByKey(node, key) {
  if (Array.isArray(node)) {
    for (const item of node) {
      const found = findStringByKey(item, key);
      if (found) {
        return found;
      }
    }
    return null;
  }

  if (!isRecord(node)) {
    return null;
  }

  const value = node[key];
  if (typeof value === 'string' && value.trim()) {
    return value;
  }

  for (const nested of Object.values(node)) {
    const found = findStringByKey(nested, key);
    if (found) {
      return found;
    }
  }
  return null;
}

function isRecord(value) {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function firstNonBlank(...values) {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) {
      return value.trim();
    }
  }
  return '';
}

function firstBoolean(...values) {
  for (const value of values) {
    if (typeof value === 'boolean') {
      return value;
    }
    if (typeof value === 'string') {
      const normalized = value.trim().toLowerCase();
      if (normalized === 'true') {
        return true;
      }
      if (normalized === 'false') {
        return false;
      }
    }
  }
  return null;
}

function normalizeError(error) {
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return String(error ?? 'unknown error');
}
