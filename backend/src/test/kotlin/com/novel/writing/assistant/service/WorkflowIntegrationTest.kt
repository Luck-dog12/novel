package com.novel.writing.assistant.service

import com.novel.writing.assistant.WorkflowMockServer
import com.novel.writing.assistant.model.GenerationRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains

class WorkflowIntegrationTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun generateInitialStreamSendsReferenceDocAndReceivesFinalDocument(): Unit = runBlocking {
        WorkflowMockServer.configureClientProperties()
        val request = GenerationRequest(
            projectId = "p1",
            isContinueWriting = false,
            sessionId = null,
            referenceFileId = null,
            referenceDoc = java.io.File("d:/项目/novel/testtxt.txt").readText(Charsets.UTF_8),
            genreType = "小说",
            writingDirection = "写一本驭鬼者故事"
        )
        val response = GenerationService.generateContentStream(request) { }
        assertContains(response.outputContent, "教室门外漆黑一片")
        assertContains(response.outputContent, "写一本驭鬼者故事")
    }
}

