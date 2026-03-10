package com.novel.writing.assistant

import com.novel.writing.assistant.api.configureRouting
import com.novel.writing.assistant.model.ContextInfo
import com.novel.writing.assistant.model.GenerationRequest
import com.novel.writing.assistant.model.GenerationResponse
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertTrue

class ContextFlowTest {
    @Test
    fun `generation saves chapter context and is readable`() = testApplication {
        application app@{
            this@app.install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            configureRouting()
        }

        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val projectId = "p1"

        val generationResponse = client.post("/api/v1/generation") {
            contentType(ContentType.Application.Json)
            setBody(
                GenerationRequest(
                    projectId = projectId,
                    isContinueWriting = false,
                    genreType = "小说",
                    writingDirection = "开篇引入主角与冲突"
                )
            )
        }.body<GenerationResponse>()

        val contextsAfterInitial = client.get("/api/v1/context") {
            parameter("projectId", projectId)
            parameter("sessionId", generationResponse.sessionId)
        }.body<List<ContextInfo>>()

        assertTrue(contextsAfterInitial.any { it.contextType == "chapter" && it.contextContent == generationResponse.outputContent })
        assertTrue(contextsAfterInitial.any { it.contextType == "chapter_title" })
        assertTrue(contextsAfterInitial.any { it.contextType == "chapter_summary" })
        assertTrue(contextsAfterInitial.any { it.contextType == "genre" })
        assertTrue(contextsAfterInitial.any { it.contextType == "writing_direction" })
        assertTrue(contextsAfterInitial.any { it.contextType == "outline" })
        assertTrue(contextsAfterInitial.any { it.contextType == "characters" })
        assertTrue(contextsAfterInitial.any { it.contextType == "world" })
        assertTrue(contextsAfterInitial.any { it.contextType == "plot_outline" })

        client.post("/api/v1/generation") {
            contentType(ContentType.Application.Json)
            setBody(
                GenerationRequest(
                    projectId = projectId,
                    isContinueWriting = true,
                    sessionId = generationResponse.sessionId,
                    referenceDoc = null,
                    referenceFileId = null
                )
            )
        }.body<GenerationResponse>()

        val contextsAfterContinuation = client.get("/api/v1/context") {
            parameter("projectId", projectId)
            parameter("sessionId", generationResponse.sessionId)
        }.body<List<ContextInfo>>()

        assertTrue(contextsAfterContinuation.size >= contextsAfterInitial.size + 1)
    }
}
