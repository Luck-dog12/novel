package com.novel.writing.assistant.network

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiServiceStreamParsingTest {
    @Test
    fun parseEventStreamSupportsLargeSingleLinePayload() = runBlocking {
        val payload = """{"final_document":"${"a".repeat(70_000)}"}"""
        val channel = ByteReadChannel(
            buildString {
                appendLine("event: message")
                append("data: ")
                append(payload)
                appendLine()
                appendLine()
            }
        )
        val apiService = ApiService()

        var eventName: String? = null
        var eventPayload: String? = null

        apiService.parseEventStream(channel) { event, data ->
            eventName = event
            eventPayload = data
        }

        assertEquals("message", eventName)
        assertNotNull(eventPayload)
        assertEquals(payload, eventPayload)
    }

    @Test
    fun parseEventStreamSupportsChunkedPayloadSequence() = runBlocking {
        val channel = ByteReadChannel(
            buildString {
                appendLine("event: result_meta")
                appendLine("""data: {"id":"g1","projectId":"p1","sessionId":"s1","generationType":"initial","generationDate":"now","duration":1}""")
                appendLine()
                appendLine("event: content_chunk")
                appendLine("""data: {"chunk":"hello ","chunkIndex":0,"chunkCount":2}""")
                appendLine()
                appendLine("event: content_chunk")
                appendLine("""data: {"chunk":"world","chunkIndex":1,"chunkCount":2}""")
                appendLine()
                appendLine("event: done")
                appendLine("""data: {"id":"g1","projectId":"p1","sessionId":"s1","generationType":"initial","generationDate":"now","duration":1}""")
                appendLine()
            }
        )
        val apiService = ApiService()
        val events = mutableListOf<String>()

        apiService.parseEventStream(channel) { event, data ->
            events += "$event::$data"
        }

        assertEquals(4, events.size)
        assertTrue(events[1].startsWith("content_chunk::"))
        assertTrue(events[2].contains("world"))
    }
}
