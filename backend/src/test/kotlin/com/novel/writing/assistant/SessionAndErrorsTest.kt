package com.novel.writing.assistant

import com.novel.writing.assistant.api.configureRouting
import com.novel.writing.assistant.model.GenerationReceiptRequest
import com.novel.writing.assistant.model.GenerationRequest
import com.novel.writing.assistant.model.GenerationResponse
import com.novel.writing.assistant.model.SessionInfo
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionAndErrorsTest {
    @Test
    fun `continuation without sessionId returns 400`() = testApplication {
        application app@{
            WorkflowMockServer.configureClientProperties()
            this@app.install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            configureRouting()
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val response = client.post("/api/v1/generation") {
            contentType(ContentType.Application.Json)
            setBody(
                GenerationRequest(
                    projectId = "p1",
                    isContinueWriting = true
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `continuation with sessionId but no context returns 409`() = testApplication {
        application app@{
            WorkflowMockServer.configureClientProperties()
            this@app.install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            configureRouting()
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val response = client.post("/api/v1/generation") {
            contentType(ContentType.Application.Json)
            setBody(
                GenerationRequest(
                    projectId = "p2",
                    isContinueWriting = true,
                    sessionId = "s1"
                )
            )
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `latest session endpoint returns most recent sessionId`() = testApplication {
        application app@{
            WorkflowMockServer.configureClientProperties()
            this@app.install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            configureRouting()
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val projectId = "p3"
        val generationResponse = client.post("/api/v1/generation") {
            contentType(ContentType.Application.Json)
            setBody(
                GenerationRequest(
                    projectId = projectId,
                    isContinueWriting = false,
                    genreType = "小说",
                    writingDirection = "测试会话"
                )
            )
        }.body<GenerationResponse>()

        client.post("/api/v1/generation/${generationResponse.id}/receipt") {
            contentType(ContentType.Application.Json)
            setBody(
                GenerationReceiptRequest(
                    projectId = generationResponse.projectId,
                    sessionId = generationResponse.sessionId,
                    contentLength = generationResponse.outputContent.length,
                    storageRef = "tests/${generationResponse.id}.json"
                )
            )
        }

        val sessionInfo = client.get("/api/v1/sessions/latest") {
            parameter("projectId", projectId)
        }.body<SessionInfo>()

        assertEquals(generationResponse.sessionId, sessionInfo.sessionId)
    }
}
