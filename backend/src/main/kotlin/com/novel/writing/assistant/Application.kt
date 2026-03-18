package com.novel.writing.assistant

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.serialization.kotlinx.json.*
import com.novel.writing.assistant.api.configureRouting
import com.novel.writing.assistant.service.DatabaseService
import com.novel.writing.assistant.service.ErrorHandlingService

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        DatabaseService.init()
        install(ContentNegotiation) {
            json()
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                val exception = cause as? Exception ?: Exception(cause)
                ErrorHandlingService.handleException(
                    e = exception,
                    context = mapOf(
                        "path" to call.request.uri,
                        "method" to call.request.httpMethod.value
                    )
                )
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to (cause.message ?: "internal server error"))
                )
            }
        }
        configureRouting()
    }.start(wait = true)
}
