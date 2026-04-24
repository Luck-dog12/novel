package com.novel.writing.assistant.service

import com.novel.writing.assistant.WorkflowMockServer
import com.novel.writing.assistant.model.GenerationRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class WorkflowIntegrationTest {
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
        val frames = mutableListOf<String>()
        val response = GenerationService.generateContentStream(request) { frame ->
            frames += frame
        }
        assertContains(response.outputContent, "教室门外漆黑一片")
        assertContains(response.outputContent, "写一本驭鬼者故事")
        assertTrue(frames.any { it.contains("event: result_meta") })
        assertTrue(frames.any { it.contains("event: content_chunk") })
        assertTrue(frames.count { it.contains("event: content_chunk") } >= 2)
    }
}

