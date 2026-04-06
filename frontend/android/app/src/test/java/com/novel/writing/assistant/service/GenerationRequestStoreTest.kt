package com.novel.writing.assistant.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class GenerationRequestStoreTest {
    @Test
    fun requestStoreRoundTripsLargeReferenceDoc() {
        val directory = Files.createTempDirectory("generation-request-store").toFile()
        val requestId = "request-1"
        val request = PendingGenerationRequest(
            projectId = "project-1",
            isContinueWriting = false,
            referenceDoc = "a".repeat(120_000),
            genreType = "novel",
            writingDirection = "test-long-input",
            sessionId = null
        )

        GenerationRequestStore.writeRequest(directory, requestId, request)

        assertEquals(request, GenerationRequestStore.readRequest(directory, requestId))

        GenerationRequestStore.deleteRequest(directory, requestId)

        assertNull(GenerationRequestStore.readRequest(directory, requestId))
    }
}
