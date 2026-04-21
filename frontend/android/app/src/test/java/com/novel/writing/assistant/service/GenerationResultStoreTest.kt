package com.novel.writing.assistant.service

import com.novel.writing.assistant.network.GenerationResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.file.Files

class GenerationResultStoreTest {
    @Test
    fun resultStoreRoundTripsLargeOutput() {
        val directory = Files.createTempDirectory("generation-result-store").toFile()
        val response = GenerationResponse(
            id = "generation-1",
            projectId = "project-1",
            sessionId = "session-1",
            generationType = "initial",
            outputContent = "a".repeat(220_000),
            generationDate = "2026-04-20T12:00:00Z",
            duration = 1234L
        )

        GenerationResultStore.writeResult(directory, response)

        val loaded = GenerationResultStore.readResult(directory, response.id)
        assertNotNull(loaded)
        assertEquals(response, loaded)
    }
}
