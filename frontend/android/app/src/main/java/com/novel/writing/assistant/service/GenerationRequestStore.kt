package com.novel.writing.assistant.service

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class PendingGenerationRequest(
    val projectId: String,
    val isContinueWriting: Boolean,
    val referenceDoc: String? = null,
    val genreType: String? = null,
    val writingDirection: String? = null,
    val sessionId: String? = null
)

object GenerationRequestStore {
    private const val REQUEST_DIR = "generation-requests"
    private val json = Json { ignoreUnknownKeys = true }

    fun stage(context: Context, requestId: String, request: PendingGenerationRequest) {
        writeRequest(requestDirectory(context), requestId, request)
    }

    fun load(context: Context, requestId: String): PendingGenerationRequest? {
        return readRequest(requestDirectory(context), requestId)
    }

    fun delete(context: Context, requestId: String) {
        deleteRequest(requestDirectory(context), requestId)
    }

    internal fun writeRequest(directory: File, requestId: String, request: PendingGenerationRequest) {
        directory.mkdirs()
        requestFile(directory, requestId).writeText(json.encodeToString(request), Charsets.UTF_8)
    }

    internal fun readRequest(directory: File, requestId: String): PendingGenerationRequest? {
        val file = requestFile(directory, requestId)
        if (!file.exists()) {
            return null
        }
        return runCatching {
            json.decodeFromString<PendingGenerationRequest>(file.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    internal fun deleteRequest(directory: File, requestId: String) {
        requestFile(directory, requestId).delete()
    }

    private fun requestDirectory(context: Context): File {
        return File(context.cacheDir, REQUEST_DIR)
    }

    private fun requestFile(directory: File, requestId: String): File {
        return File(directory, "$requestId.json")
    }
}
