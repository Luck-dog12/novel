package com.novel.writing.assistant

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import com.novel.writing.assistant.api.configureRouting
import com.novel.writing.assistant.service.DatabaseService

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        DatabaseService.init()
        install(ContentNegotiation) {
            json()
        }
        configureRouting()
    }.start(wait = true)
}
