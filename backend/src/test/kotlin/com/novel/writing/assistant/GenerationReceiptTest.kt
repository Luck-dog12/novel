package com.novel.writing.assistant

import com.novel.writing.assistant.api.configureRouting
import com.novel.writing.assistant.model.GenerationReceiptRequest
import com.novel.writing.assistant.model.GenerationReceiptResponse
import com.novel.writing.assistant.model.GenerationRequest
import com.novel.writing.assistant.model.GenerationResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerationReceiptTest {
    @Test
    fun `receipt endpoint acknowledges persisted generation`() = testApplication {
        application {
            WorkflowMockServer.configureClientProperties()
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            configureRouting()
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val generationResponse = client.post("/api/v1/generation") {
            contentType(ContentType.Application.Json)
            setBody(
                GenerationRequest(
                    projectId = "receipt-project",
                    isContinueWriting = false,
                    genreType = "novel",
                    writingDirection = "receipt verification"
                )
            )
        }.body<GenerationResponse>()

        val receiptResponse = client.post("/api/v1/generation/${generationResponse.id}/receipt") {
            contentType(ContentType.Application.Json)
            setBody(
                GenerationReceiptRequest(
                    projectId = generationResponse.projectId,
                    sessionId = generationResponse.sessionId,
                    contentLength = generationResponse.outputContent.length,
                    storageRef = "generation-results/${generationResponse.id}.json"
                )
            )
        }

        assertEquals(HttpStatusCode.OK, receiptResponse.status)
        val body = receiptResponse.body<GenerationReceiptResponse>()
        assertEquals(generationResponse.id, body.generationId)
        assertTrue(body.acknowledged)
        assertEquals(generationResponse.outputContent.length, body.contentLength)
    }
}
