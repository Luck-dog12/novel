package com.novel.writing.assistant.service

import android.content.Context
import com.novel.writing.assistant.network.GenerationResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object GenerationResultStore {
    private const val RESULT_DIR = "generation-results"
    private val json = Json { ignoreUnknownKeys = true }

    fun save(context: Context, response: GenerationResponse): String {
        val relativePath = relativePath(response.id)
        val file = File(context.filesDir, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(response), Charsets.UTF_8)
        return relativePath
    }

    internal fun writeResult(directory: File, response: GenerationResponse) {
        directory.mkdirs()
        File(directory, "${response.id}.json").writeText(json.encodeToString(response), Charsets.UTF_8)
    }

    internal fun readResult(directory: File, generationId: String): GenerationResponse? {
        val file = File(directory, "$generationId.json")
        if (!file.exists()) {
            return null
        }
        return runCatching {
            json.decodeFromString<GenerationResponse>(file.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    private fun relativePath(generationId: String): String = "$RESULT_DIR/$generationId.json"
}
