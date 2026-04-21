package com.novel.writing.assistant.service

import android.content.Context
import com.novel.writing.assistant.network.GenerationReceiptRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class PendingGenerationReceipt(
    val generationId: String,
    val receipt: GenerationReceiptRequest
)

object GenerationReceiptStore {
    private const val RECEIPT_DIR = "generation-receipts"
    private val json = Json { ignoreUnknownKeys = true }

    fun stage(context: Context, pendingReceipt: PendingGenerationReceipt) {
        writeReceipt(receiptDirectory(context), pendingReceipt)
    }

    fun loadAll(context: Context): List<PendingGenerationReceipt> {
        return readReceipts(receiptDirectory(context))
    }

    fun delete(context: Context, generationId: String) {
        deleteReceipt(receiptDirectory(context), generationId)
    }

    internal fun writeReceipt(directory: File, pendingReceipt: PendingGenerationReceipt) {
        directory.mkdirs()
        receiptFile(directory, pendingReceipt.generationId).writeText(
            json.encodeToString(pendingReceipt),
            Charsets.UTF_8
        )
    }

    internal fun readReceipts(directory: File): List<PendingGenerationReceipt> {
        if (!directory.exists()) {
            return emptyList()
        }
        return directory.listFiles()
            ?.sortedBy { it.name }
            ?.mapNotNull { file ->
                runCatching {
                    json.decodeFromString<PendingGenerationReceipt>(file.readText(Charsets.UTF_8))
                }.getOrNull()
            }
            ?: emptyList()
    }

    internal fun deleteReceipt(directory: File, generationId: String) {
        receiptFile(directory, generationId).delete()
    }

    private fun receiptDirectory(context: Context): File = File(context.filesDir, RECEIPT_DIR)

    private fun receiptFile(directory: File, generationId: String): File {
        return File(directory, "$generationId.json")
    }
}
