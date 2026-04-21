package com.novel.writing.assistant.service

import com.novel.writing.assistant.network.GenerationReceiptRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class GenerationReceiptStoreTest {
    @Test
    fun receiptStoreStagesAndDeletesReceipts() {
        val directory = Files.createTempDirectory("generation-receipt-store").toFile()
        val pendingReceipt = PendingGenerationReceipt(
            generationId = "generation-1",
            receipt = GenerationReceiptRequest(
                projectId = "project-1",
                sessionId = "session-1",
                contentLength = 4096,
                storageRef = "generation-results/generation-1.json"
            )
        )

        GenerationReceiptStore.writeReceipt(directory, pendingReceipt)

        val loaded = GenerationReceiptStore.readReceipts(directory)
        assertEquals(listOf(pendingReceipt), loaded)

        GenerationReceiptStore.deleteReceipt(directory, pendingReceipt.generationId)
        assertTrue(GenerationReceiptStore.readReceipts(directory).isEmpty())
    }
}
