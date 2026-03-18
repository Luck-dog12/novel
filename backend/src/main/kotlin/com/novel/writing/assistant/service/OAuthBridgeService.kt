package com.novel.writing.assistant.service

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Serializable
data class BridgeOAuthCallbackRequest(
    val code: String,
    val state: String? = null
)

@Serializable
data class BridgeOAuthCallbackResponse(
    val ok: Boolean = false,
    val expires_at: Long = 0,
    val has_refresh_token: Boolean = false
)

@Serializable
data class BridgeOAuthAuthorizeUrlResponse(
    val authorize_url: String = "",
    val state: String = "",
    val redirect_uri: String = ""
)

object OAuthBridgeService {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = HttpClient(OkHttp)

    suspend fun submitOAuthCode(code: String, state: String?): BridgeOAuthCallbackResponse {
        val token = bridgeToken()
        val response = client.post("${bridgeBaseUrl()}/oauth/callback") {
            if (token.isNotBlank()) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    BridgeOAuthCallbackRequest(
                        code = code.trim(),
                        state = state?.trim()?.takeIf { it.isNotBlank() }
                    )
                )
            )
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw IllegalStateException("OAuth 回调转发失败(${response.status.value}): ${body.take(300)}")
        }
        return runCatching { json.decodeFromString<BridgeOAuthCallbackResponse>(body) }
            .getOrElse { throw IllegalStateException("OAuth 回调解析失败: ${it.message}") }
    }

    suspend fun getAuthorizeUrl(state: String?): BridgeOAuthAuthorizeUrlResponse {
        val token = bridgeToken()
        val query = state?.trim()?.takeIf { it.isNotBlank() }?.let {
            "?state=${URLEncoder.encode(it, StandardCharsets.UTF_8.toString())}"
        } ?: ""
        val response = client.get("${bridgeBaseUrl()}/oauth/authorize-url$query") {
            if (token.isNotBlank()) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw IllegalStateException("OAuth 授权链接获取失败(${response.status.value}): ${body.take(300)}")
        }
        return runCatching { json.decodeFromString<BridgeOAuthAuthorizeUrlResponse>(body) }
            .getOrElse { throw IllegalStateException("OAuth 授权链接解析失败: ${it.message}") }
    }

    private fun bridgeBaseUrl(): String {
        return (
            System.getProperty("COZE_BRIDGE_BASE_URL")
                ?: System.getenv("COZE_BRIDGE_BASE_URL")
                ?: "http://127.0.0.1:8787"
            ).trim().trimEnd('/')
    }

    private fun bridgeToken(): String {
        return (
            System.getProperty("COZE_BRIDGE_TOKEN")
                ?: System.getenv("COZE_BRIDGE_TOKEN")
                ?: ""
            ).trim()
    }
}
