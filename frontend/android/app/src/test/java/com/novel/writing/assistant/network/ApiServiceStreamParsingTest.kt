package com.novel.writing.assistant.network

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
}
