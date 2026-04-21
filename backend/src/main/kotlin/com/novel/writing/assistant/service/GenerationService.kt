package com.novel.writing.assistant.service

import com.novel.writing.assistant.model.GenerationRequest
import com.novel.writing.assistant.model.GenerationResponse
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import java.io.File
import java.util.*

@Serializable
data class WorkflowRunRequest(
    @SerialName("reference_file")
    val referenceFile: String = "",
    @SerialName("reference_doc")
    val referenceDoc: String = "",
    @SerialName("genre_type")
    val genreType: String = "小说",
    @SerialName("writing_direction")
    val writingDirection: String = "",
    @SerialName("is_continue_writing")
    val isContinueWriting: Boolean = false
)

@Serializable
data class WorkflowRunResponse(
    @SerialName("final_document")
    val finalDocument: String? = null,
    @SerialName("style_match_report")
    val styleMatchReport: String? = null,
    @SerialName("content_consistency_report")
    val contentConsistencyReport: String? = null,
    @SerialName("quality_report")
    val qualityReport: String? = null,
    @SerialName("final_document_file")
    val finalDocumentFile: String? = null,
    @SerialName("run_id")
    val runId: String? = null
)

@Serializable
data class WorkflowErrorDetail(
    @SerialName("error_code")
    val errorCode: String? = null,
    @SerialName("error_message")
    val errorMessage: String? = null,
    @SerialName("stack_trace")
    val stackTrace: String? = null
)

@Serializable
data class WorkflowErrorResponse(
    val code: Int? = null,
    val msg: String? = null,
    val message: String? = null,
    val detail: WorkflowErrorDetail? = null
)

object GenerationService {
    private val COZE_CONNECT_TIMEOUT_MS = parseEnvLong("COZE_CONNECT_TIMEOUT_MS", defaultValue = 15_000)
    private val COZE_REQUEST_TIMEOUT_MS = parseEnvLong("COZE_REQUEST_TIMEOUT_MS", defaultValue = 300_000)
    private val COZE_SOCKET_TIMEOUT_MS = parseEnvLong("COZE_SOCKET_TIMEOUT_MS", defaultValue = 300_000)
    private val STREAM_HEARTBEAT_INTERVAL_MS = parseEnvLong("STREAM_HEARTBEAT_INTERVAL_MS", defaultValue = 10_000)
    private val httpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = COZE_REQUEST_TIMEOUT_MS
            socketTimeoutMillis = COZE_SOCKET_TIMEOUT_MS
            connectTimeoutMillis = COZE_CONNECT_TIMEOUT_MS
        }
    }
    private val workflowJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private fun workflowApiBaseUrl(): String {
        return (
            System.getProperty("COZE_WORKFLOW_BASE_URL")
                ?: System.getenv("COZE_WORKFLOW_BASE_URL")
                ?: System.getProperty("COZE_BRIDGE_BASE_URL")
                ?: System.getenv("COZE_BRIDGE_BASE_URL")
                ?: System.getProperty("COZE_API_BASE_URL")
                ?: System.getenv("COZE_API_BASE_URL")
                ?: System.getProperty("COZE_API_URL")
                ?: System.getenv("COZE_API_URL")
                ?: "http://127.0.0.1:8787"
            ).trim().trimEnd('/')
    }

    private fun workflowApiToken(): String {
        return (
            System.getProperty("COZE_WORKFLOW_TOKEN")
                ?: System.getenv("COZE_WORKFLOW_TOKEN")
                ?: System.getProperty("COZE_BRIDGE_TOKEN")
                ?: System.getenv("COZE_BRIDGE_TOKEN")
                ?: System.getProperty("COZE_API_TOKEN")
                ?: System.getenv("COZE_API_TOKEN")
                ?: System.getProperty("COZE_API_KEY")
                ?: System.getenv("COZE_API_KEY")
                ?: ""
            ).trim()
    }

    private fun workflowRunPath(): String {
        return (
            System.getProperty("COZE_WORKFLOW_RUN_PATH")
                ?: System.getenv("COZE_WORKFLOW_RUN_PATH")
                ?: System.getProperty("COZE_BRIDGE_RUN_PATH")
                ?: System.getenv("COZE_BRIDGE_RUN_PATH")
                ?: System.getProperty("COZE_API_RUN_PATH")
                ?: System.getenv("COZE_API_RUN_PATH")
                ?: "/run"
            ).trim().ifBlank { "/run" }
    }

    private fun workflowRunUrl(): String {
        val base = workflowApiBaseUrl()
        val path = workflowRunPath()
        return "$base${if (path.startsWith("/")) path else "/$path"}"
    }

    private fun enableMockFallback(): Boolean = parseEnvBoolean("ENABLE_MOCK_FALLBACK", defaultValue = false)
    private fun workflowForceSyncForStream(): Boolean = parseEnvBoolean("COZE_FORCE_SYNC_FOR_STREAM", defaultValue = false)
    private fun workflowStreamFallbackToRun(): Boolean = parseEnvBoolean("COZE_STREAM_FALLBACK_TO_RUN", defaultValue = true)
    
    suspend fun generateContent(request: GenerationRequest): GenerationResponse {
        val startTime = System.currentTimeMillis()

        val sessionId = if (request.isContinueWriting) {
            request.sessionId?.takeIf { it.isNotBlank() }
                ?: throw MissingSessionIdException()
        } else {
            UUID.randomUUID().toString()
        }
        
        LogService.info("Generating content for project ${request.projectId}, type: ${if (request.isContinueWriting) "continuation" else "initial"}")
        
        val outputContent = try {
            if (request.isContinueWriting) {
                generateContinuation(request, sessionId)
            } else {
                generateInitial(request)
            }
        } catch (e: Exception) {
            if (e is MissingSessionIdException || e is NoContextException) {
                throw e
            }
            // Handle exception with error reporting
            ErrorHandlingService.handleException(
                e = e,
                context = mapOf(
                    "projectId" to request.projectId,
                    "generationType" to if (request.isContinueWriting) "continuation" else "initial"
                )
            )
            if (enableMockFallback()) mockGenerationContent(request.isContinueWriting) else throw e
        }
        
        val duration = System.currentTimeMillis() - startTime
        val generationType = if (request.isContinueWriting) "continuation" else "initial"
        
        val response = GenerationResponse(
            id = UUID.randomUUID().toString(),
            projectId = request.projectId,
            sessionId = sessionId,
            generationType = generationType,
            outputContent = outputContent,
            generationDate = Date().toString(),
            duration = duration
        )
        
        GenerationReceiptService.stagePending(
            request = request,
            response = response
        )

        return response
    }

    suspend fun generateContentStream(
        request: GenerationRequest,
        emitSseFrame: suspend (String) -> Unit
    ): GenerationResponse {
        val startTime = System.currentTimeMillis()
        val sessionId = if (request.isContinueWriting) {
            request.sessionId?.takeIf { it.isNotBlank() }
                ?: throw MissingSessionIdException()
        } else {
            UUID.randomUUID().toString()
        }
        val generationType = if (request.isContinueWriting) "continuation" else "initial"
        val outputContent = try {
            if (request.isContinueWriting) {
                generateContinuationStream(request, sessionId, emitSseFrame)
            } else {
                generateInitialStream(request, emitSseFrame)
            }
        } catch (e: Exception) {
            if (e is MissingSessionIdException || e is NoContextException) {
                throw e
            }
            ErrorHandlingService.handleException(
                e = e,
                context = mapOf(
                    "projectId" to request.projectId,
                    "generationType" to generationType,
                    "mode" to "stream"
                )
            )
            if (enableMockFallback()) {
                emitSseFrame("event: error\ndata: ${workflowJson.encodeToString(mapOf("message" to (e.message ?: "stream generation failed")))}\n\n")
                mockGenerationContent(request.isContinueWriting)
            } else {
                throw e
            }
        }
        val duration = System.currentTimeMillis() - startTime
        val response = GenerationResponse(
            id = UUID.randomUUID().toString(),
            projectId = request.projectId,
            sessionId = sessionId,
            generationType = generationType,
            outputContent = outputContent,
            generationDate = Date().toString(),
            duration = duration
        )
        GenerationReceiptService.stagePending(
            request = request,
            response = response
        )
        return response
    }
    
    private suspend fun generateInitial(request: GenerationRequest): String {
        val referenceDoc = buildInitialReferenceDoc(request)
        val runRequest = WorkflowRunRequest(
            referenceFile = referenceDoc,
            referenceDoc = referenceDoc,
            genreType = request.genreType ?: "小说",
            writingDirection = request.writingDirection ?: "",
            isContinueWriting = false
        )
        return callWorkflowRunApi(runRequest)
    }

    private suspend fun generateInitialStream(
        request: GenerationRequest,
        emitSseFrame: suspend (String) -> Unit
    ): String {
        val referenceDoc = buildInitialReferenceDoc(request)
        val runRequest = WorkflowRunRequest(
            referenceFile = referenceDoc,
            referenceDoc = referenceDoc,
            genreType = request.genreType ?: "小说",
            writingDirection = request.writingDirection ?: "",
            isContinueWriting = false
        )
        return callWorkflowStreamRunApi(runRequest, emitSseFrame)
    }
    
    private suspend fun generateContinuation(request: GenerationRequest, sessionId: String): String {
        val lastChapterContent = ContextService.getLatestContextContent(request.projectId, sessionId, "chapter")
        val genreType = ContextService.getLatestContextContent(request.projectId, sessionId, "genre")
        val writingDirection = ContextService.getLatestContextContent(request.projectId, sessionId, "writing_direction")
        val chapterTitle = ContextService.getLatestContextContent(request.projectId, sessionId, "chapter_title")
        val outline = ContextService.getLatestContextContent(request.projectId, sessionId, "outline")
        val characters = ContextService.getLatestContextContent(request.projectId, sessionId, "characters")
        val world = ContextService.getLatestContextContent(request.projectId, sessionId, "world")
        val plotOutline = ContextService.getLatestContextContent(request.projectId, sessionId, "plot_outline")
        val chapterSummary = ContextService.getLatestContextContent(request.projectId, sessionId, "chapter_summary")

        if (lastChapterContent.isNullOrBlank() && outline.isNullOrBlank() && characters.isNullOrBlank() &&
            world.isNullOrBlank() && plotOutline.isNullOrBlank() && chapterSummary.isNullOrBlank()
        ) {
            throw NoContextException()
        }

        val contextDoc = buildString {
            if (!genreType.isNullOrBlank()) append("题材：$genreType\n")
            if (!writingDirection.isNullOrBlank()) append("写作方向：$writingDirection\n")
            if (!chapterTitle.isNullOrBlank()) append("章节名称：$chapterTitle\n")
            if (!outline.isNullOrBlank()) append("大纲：$outline\n")
            if (!characters.isNullOrBlank()) append("人物设定：$characters\n")
            if (!world.isNullOrBlank()) append("世界观：$world\n")
            if (!plotOutline.isNullOrBlank()) append("剧情大纲：$plotOutline\n")
            if (!chapterSummary.isNullOrBlank()) append("章节梗概：$chapterSummary\n")
            if (!lastChapterContent.isNullOrBlank()) append("上一章节内容：$lastChapterContent\n")
        }
        val mergedReferenceDoc = listOfNotNull(
            request.referenceDoc?.takeIf { it.isNotBlank() },
            contextDoc.takeIf { it.isNotBlank() }
        ).joinToString("\n\n").ifBlank { "" }
        val runRequest = WorkflowRunRequest(
            referenceFile = mergedReferenceDoc,
            referenceDoc = mergedReferenceDoc,
            genreType = genreType ?: request.genreType ?: "小说",
            writingDirection = writingDirection ?: request.writingDirection ?: "",
            isContinueWriting = true
        )
        return callWorkflowRunApi(runRequest)
    }

    private suspend fun generateContinuationStream(
        request: GenerationRequest,
        sessionId: String,
        emitSseFrame: suspend (String) -> Unit
    ): String {
        val lastChapterContent = ContextService.getLatestContextContent(request.projectId, sessionId, "chapter")
        val genreType = ContextService.getLatestContextContent(request.projectId, sessionId, "genre")
        val writingDirection = ContextService.getLatestContextContent(request.projectId, sessionId, "writing_direction")
        val chapterTitle = ContextService.getLatestContextContent(request.projectId, sessionId, "chapter_title")
        val outline = ContextService.getLatestContextContent(request.projectId, sessionId, "outline")
        val characters = ContextService.getLatestContextContent(request.projectId, sessionId, "characters")
        val world = ContextService.getLatestContextContent(request.projectId, sessionId, "world")
        val plotOutline = ContextService.getLatestContextContent(request.projectId, sessionId, "plot_outline")
        val chapterSummary = ContextService.getLatestContextContent(request.projectId, sessionId, "chapter_summary")
        if (lastChapterContent.isNullOrBlank() && outline.isNullOrBlank() && characters.isNullOrBlank() &&
            world.isNullOrBlank() && plotOutline.isNullOrBlank() && chapterSummary.isNullOrBlank()
        ) {
            throw NoContextException()
        }
        val contextDoc = buildString {
            if (!genreType.isNullOrBlank()) append("题材：$genreType\n")
            if (!writingDirection.isNullOrBlank()) append("写作方向：$writingDirection\n")
            if (!chapterTitle.isNullOrBlank()) append("章节名称：$chapterTitle\n")
            if (!outline.isNullOrBlank()) append("大纲：$outline\n")
            if (!characters.isNullOrBlank()) append("人物设定：$characters\n")
            if (!world.isNullOrBlank()) append("世界观：$world\n")
            if (!plotOutline.isNullOrBlank()) append("剧情大纲：$plotOutline\n")
            if (!chapterSummary.isNullOrBlank()) append("章节梗概：$chapterSummary\n")
            if (!lastChapterContent.isNullOrBlank()) append("上一章节内容：$lastChapterContent\n")
        }
        val mergedReferenceDoc = listOfNotNull(
            request.referenceDoc?.takeIf { it.isNotBlank() },
            contextDoc.takeIf { it.isNotBlank() }
        ).joinToString("\n\n").ifBlank { "" }
        val runRequest = WorkflowRunRequest(
            referenceFile = mergedReferenceDoc,
            referenceDoc = mergedReferenceDoc,
            genreType = genreType ?: request.genreType ?: "小说",
            writingDirection = writingDirection ?: request.writingDirection ?: "",
            isContinueWriting = true
        )
        return callWorkflowStreamRunApi(runRequest, emitSseFrame)
    }
    
    private fun resolveReferenceDoc(request: GenerationRequest): String {
        val inlineDoc = request.referenceDoc?.trim().orEmpty()
        if (inlineDoc.isNotBlank()) return inlineDoc
        val document = resolveDocumentById(request.referenceFileId) ?: return ""
        return runCatching { File(document.filePath).readText(Charsets.UTF_8) }
            .getOrElse {
                LogService.warn("Failed to read reference document ${document.id}: ${it.message}")
                ""
            }
            .trim()
    }

    private fun buildInitialReferenceDoc(request: GenerationRequest): String {
        val raw = resolveReferenceDoc(request).trim()
        val genre = request.genreType?.trim().orEmpty()
        val direction = request.writingDirection?.trim().orEmpty()
        return buildString {
            if (genre.isNotBlank()) append("题材：$genre\n")
            if (direction.isNotBlank()) append("写作方向：$direction\n")
            if (raw.isNotBlank()) append("\n$raw")
        }.trim()
    }

    private fun resolveDocumentById(referenceFileId: String?): Document? {
        if (referenceFileId.isNullOrBlank()) return null
        return runCatching { DocumentService.getDocumentById(referenceFileId) }
            .onFailure {
                LogService.warn("Failed to query document $referenceFileId: ${it.message}")
            }
            .getOrNull()
    }

    private suspend fun callWorkflowRunApi(runRequest: WorkflowRunRequest): String {
        val runResponse = requestWorkflowRun(runRequest)
        val finalDoc = runResponse.finalDocument?.trim().orEmpty()
        if (finalDoc.isBlank()) {
            throw IllegalStateException("Workflow API returned empty final_document")
        }
        if (looksLikeEmptyTemplate(finalDoc)) {
            throw IllegalStateException("Workflow API returned empty template")
        }
        return finalDoc
    }

    private suspend fun callWorkflowStreamRunApi(
        runRequest: WorkflowRunRequest,
        emitSseFrame: suspend (String) -> Unit
    ): String {
        if (workflowForceSyncForStream()) {
            emitSseFrame("event: message\ndata: ${workflowJson.encodeToString(mapOf("type" to "fallback", "reason" to "use_run_endpoint"))}\n\n")
        }
        val runResponse = withHeartbeat(emitSseFrame) { requestWorkflowRun(runRequest) }
        val finalDocument = runResponse.finalDocument?.trim().orEmpty()
        if (finalDocument.isBlank()) {
            if (workflowStreamFallbackToRun()) {
                return callWorkflowRunApi(runRequest)
            }
            throw IllegalStateException("Workflow run completed without final_document")
        }
        if (looksLikeEmptyTemplate(finalDocument)) {
            throw IllegalStateException("Workflow run returned empty template")
        }
        val payload = workflowJson.encodeToString(
            mapOf(
                "final_document" to finalDocument,
                "style_match_report" to runResponse.styleMatchReport,
                "content_consistency_report" to runResponse.contentConsistencyReport,
                "quality_report" to runResponse.qualityReport,
                "run_id" to runResponse.runId
            )
        )
        emitSseFrame("event: message\ndata: $payload\n\n")
        return finalDocument
    }

    private fun looksLikeEmptyTemplate(text: String): Boolean {
        val t = text.trim()
        if (t.isBlank()) return true
        return t.contains("# 最终文档") &&
            t.contains("总字数：0") &&
            t.contains("风格匹配度：0.0/100")
    }

    private fun encodeWorkflowRequestCompat(runRequest: WorkflowRunRequest): String {
        return workflowJson.encodeToString(runRequest)
    }

    private suspend fun requestWorkflowRun(runRequest: WorkflowRunRequest): WorkflowRunResponse {
        val token = workflowApiToken()
        val requestBody = encodeWorkflowRequestCompat(runRequest)
        val response = httpClient.post(workflowRunUrl()) {
            if (token.isNotBlank()) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(TextContent(requestBody, ContentType.Application.Json))
        }
        val responseBody = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Workflow API request failed (${response.status.value}): ${extractWorkflowError(responseBody)}")
        }
        return runCatching {
            workflowJson.decodeFromString<WorkflowRunResponse>(responseBody)
        }.getOrElse {
            val root = runCatching { workflowJson.parseToJsonElement(responseBody) }.getOrNull()
            val finalDocument = root?.let { findStringByKey(it, "final_document") }
            if (!finalDocument.isNullOrBlank()) {
                WorkflowRunResponse(finalDocument = finalDocument)
            } else {
                throw IllegalStateException("Workflow API response decode failed: ${it.message}; body=${responseBody.take(500)}")
            }
        }
    }

    private fun parseEnvBoolean(name: String, defaultValue: Boolean): Boolean {
        val value = (System.getProperty(name) ?: System.getenv(name))?.trim()?.lowercase() ?: return defaultValue
        return when (value) {
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> defaultValue
        }
    }

    private fun parseEnvLong(name: String, defaultValue: Long): Long {
        val raw = (System.getProperty(name) ?: System.getenv(name))?.trim().orEmpty()
        val value = raw.toLongOrNull()
        return if (value != null && value > 0) value else defaultValue
    }

    private suspend fun <T> withHeartbeat(emitSseFrame: suspend (String) -> Unit, block: suspend () -> T): T {
        if (STREAM_HEARTBEAT_INTERVAL_MS <= 0) return block()
        return coroutineScope {
            val job = launch {
                while (isActive) {
                    emitSseFrame("event: message\ndata: ${workflowJson.encodeToString(mapOf("type" to "heartbeat"))}\n\n")
                    delay(STREAM_HEARTBEAT_INTERVAL_MS)
                }
            }
            try {
                block()
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    private fun mockGenerationContent(isContinueWriting: Boolean): String {
        return if (isContinueWriting) {
            "This is a mock continuation of your writing. In production, this would be generated by Coze API based on your saved context."
        } else {
            "This is a mock initial generation. In production, this would be generated by Coze API based on your genre type and writing direction."
        }
    }

    private fun extractWorkflowError(responseBody: String): String {
        return runCatching {
            val parsed = workflowJson.decodeFromString<WorkflowErrorResponse>(responseBody)
            when {
                !parsed.detail?.errorMessage.isNullOrBlank() ->
                    "${parsed.detail?.errorCode ?: "WORKFLOW_ERROR"}: ${parsed.detail?.errorMessage}"
                !parsed.msg.isNullOrBlank() ->
                    "${parsed.code ?: "WORKFLOW_ERROR"}: ${parsed.msg}"
                !parsed.message.isNullOrBlank() ->
                    parsed.message
                else -> responseBody.take(300)
            }
        }.getOrDefault(responseBody.take(300))
    }

    private fun extractFinalDocument(payload: String): String? {
        val root = runCatching { workflowJson.parseToJsonElement(payload) }.getOrNull() ?: return null
        return findStringByKey(root, "final_document")
    }

    private fun extractStreamMessage(payload: String): String? {
        val root = runCatching { workflowJson.parseToJsonElement(payload) }.getOrNull() ?: return null
        return findStringByKey(root, "message")
    }

    private fun findStringByKey(element: JsonElement, key: String): String? {
        when (element) {
            is JsonObject -> {
                element[key]?.let { value ->
                    if (value is JsonPrimitive && value.isString) {
                        return value.content
                    }
                }
                element.values.forEach { value ->
                    val nested = findStringByKey(value, key)
                    if (!nested.isNullOrBlank()) {
                        return nested
                    }
                }
            }
            is kotlinx.serialization.json.JsonArray -> {
                element.forEach { value ->
                    val nested = findStringByKey(value, key)
                    if (!nested.isNullOrBlank()) {
                        return nested
                    }
                }
            }
            else -> {}
        }
        return null
    }

    internal fun persistGenerationData(
        request: GenerationRequest,
        sessionId: String,
        generationType: String,
        outputContent: String,
        duration: Long
    ) {
        try {
            HistoryService.saveHistory(
                projectId = request.projectId,
                generationType = generationType,
                inputParams = request.toString(),
                outputContent = outputContent,
                duration = duration
            )
            if (!request.genreType.isNullOrBlank()) {
                ContextService.saveContext(
                    projectId = request.projectId,
                    sessionId = sessionId,
                    contextType = "genre",
                    contextContent = request.genreType
                )
            }
            if (!request.writingDirection.isNullOrBlank()) {
                ContextService.saveContext(
                    projectId = request.projectId,
                    sessionId = sessionId,
                    contextType = "writing_direction",
                    contextContent = request.writingDirection
                )
            }
            val chapterTitle = buildChapterTitle(request.projectId, sessionId)
            ContextService.saveContext(
                projectId = request.projectId,
                sessionId = sessionId,
                contextType = "chapter_title",
                contextContent = chapterTitle
            )
            val chapterSummary = buildChapterSummaryFallback(outputContent)
            ContextService.saveContext(
                projectId = request.projectId,
                sessionId = sessionId,
                contextType = "chapter_summary",
                contextContent = chapterSummary
            )
            if (generationType == "initial") {
                ContextService.saveContext(
                    projectId = request.projectId,
                    sessionId = sessionId,
                    contextType = "outline",
                    contextContent = buildOutlineFallback(request)
                )
                ContextService.saveContext(
                    projectId = request.projectId,
                    sessionId = sessionId,
                    contextType = "characters",
                    contextContent = buildCharactersFallback(request)
                )
                ContextService.saveContext(
                    projectId = request.projectId,
                    sessionId = sessionId,
                    contextType = "world",
                    contextContent = buildWorldFallback()
                )
                ContextService.saveContext(
                    projectId = request.projectId,
                    sessionId = sessionId,
                    contextType = "plot_outline",
                    contextContent = buildPlotOutlineFallback(request)
                )
            }
            ContextService.saveContext(
                projectId = request.projectId,
                sessionId = sessionId,
                contextType = "chapter",
                contextContent = outputContent
            )
            LogService.info("Content generated successfully for project ${request.projectId}, duration: ${duration}ms")
        } catch (e: Exception) {
            ErrorHandlingService.handleException(
                e = e,
                context = mapOf(
                    "projectId" to request.projectId,
                    "operation" to "saveHistoryAndContext"
                )
            )
            throw IllegalStateException("Failed to persist generation result for project ${request.projectId}", e)
        }
    }

    private fun buildChapterTitle(projectId: String, sessionId: String): String {
        val nextChapterNumber = ContextService.getNextChapterNumber(projectId, sessionId)
        return "第${nextChapterNumber}章"
    }

    private fun buildChapterSummary(content: String, maxLength: Int = 200): String {
        val normalized = content.replace("\r\n", "\n").trim()
        if (normalized.length <= maxLength) return normalized
        return normalized.take(maxLength)
    }

    private fun buildOutlineFallback(request: GenerationRequest): String {
        val genre = request.genreType ?: "小说"
        val direction = request.writingDirection ?: ""
        return if (direction.isBlank()) {
            "题材：$genre\n主线：从开篇到高潮再到结尾的完整故事线"
        } else {
            "题材：$genre\n主线：$direction"
        }
    }

    private fun buildCharactersFallback(request: GenerationRequest): String {
        val direction = request.writingDirection ?: ""
        return if (direction.isBlank()) {
            "主角：未命名（可在后续补充）\n配角：未命名（可在后续补充）\n动机：待补充"
        } else {
            "主角：围绕“$direction”的核心人物\n配角：推动冲突与转折的关键人物\n动机：与写作方向一致"
        }
    }

    private fun buildWorldFallback(): String {
        return "世界观：现代都市（可在后续改为架空/历史/科幻等）"
    }

    private fun buildPlotOutlineFallback(request: GenerationRequest): String {
        val direction = request.writingDirection ?: ""
        return if (direction.isBlank()) {
            "剧情大纲：引子—冲突—升级—高潮—收束"
        } else {
            "剧情大纲：围绕“$direction”推进（引子—冲突—升级—高潮—收束）"
        }
    }

    private fun buildChapterSummaryFallback(content: String): String {
        return buildChapterSummary(content)
    }
}
