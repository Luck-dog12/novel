package com.novel.writing.assistant

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object WorkflowMockServer {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val port = 18000
    @Volatile
    private var engine: ApplicationEngine? = null

    fun ensureStarted(): Int {
        if (engine != null) return port
        synchronized(this) {
            if (engine != null) return port
            engine = embeddedServer(Netty, port = port, host = "127.0.0.1") {
                install(ContentNegotiation) { json() }
                routing {
                    get("/health") {
                        call.respondText("""{"status":"healthy"}""", ContentType.Application.Json)
                    }
                    get("/graph_parameter") {
                        call.respondText("""{"input_schema":{"type":"object"}}""", ContentType.Application.Json)
                    }
                    post("/run") {
                        val raw = call.receiveText()
                        val root = json.parseToJsonElement(raw).jsonObject
                        val referenceDoc = root["reference_doc"]?.jsonPrimitive?.content.orEmpty()
                        val writingDirection = root["writing_direction"]?.jsonPrimitive?.content.orEmpty()
                        val finalDoc = "# 最终文档\n\n$writingDirection\n\n${referenceDoc.take(120)}"
                        call.respond(
                            HttpStatusCode.OK,
                            TextContent(
                                json.encodeToString(mapOf("final_document" to finalDoc)),
                                ContentType.Application.Json
                            )
                        )
                    }
                    post("/stream_run") {
                        val raw = call.receiveText()
                        val root = json.parseToJsonElement(raw).jsonObject
                        val referenceDoc = root["reference_doc"]?.jsonPrimitive?.content.orEmpty()
                        val writingDirection = root["writing_direction"]?.jsonPrimitive?.content.orEmpty()
                        val finalDoc = "# 最终文档\n\n$writingDirection\n\n${referenceDoc.take(120)}"
                        val sse = buildString {
                            append("event: message\n")
                            append("data: ")
                            append(json.encodeToString(mapOf("final_document" to finalDoc)))
                            append("\n\n")
                            append("event: message\n")
                            append("data: ")
                            append(json.encodeToString(mapOf("message" to "success")))
                            append("\n\n")
                        }
                        call.respondText(sse, ContentType.Text.EventStream, HttpStatusCode.OK)
                    }
                }
            }.start(wait = false)
            return port
        }
    }

    fun configureClientProperties() {
        val p = ensureStarted()
        System.setProperty("COZE_API_BASE_URL", "http://127.0.0.1:$p")
        System.setProperty("COZE_API_RUN_PATH", "/run")
        System.setProperty("COZE_API_STREAM_PATH", "/stream_run")
        System.setProperty("COZE_FORCE_SYNC_FOR_STREAM", "false")
        System.setProperty("COZE_STREAM_FALLBACK_TO_RUN", "false")
        System.setProperty("ENABLE_MOCK_FALLBACK", "false")
        System.setProperty("COZE_API_TOKEN", "")
        System.setProperty("COZE_API_KEY", "")
        System.setProperty("REQUIRE_DB", "false")
    }
}

