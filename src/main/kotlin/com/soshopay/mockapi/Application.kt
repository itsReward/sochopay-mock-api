package com.soshopay.mockapi

import com.soshopay.mockapi.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(
        Netty,
        port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    configureSerialization()
    configureSecurity()
    configureStatusPages()
    configureRouting()

    log.info("🚀 SoshoPay Mock API started successfully!")
    log.info("📂 Data directory: ${System.getProperty("user.dir")}/data")
    log.info("📤 Upload directory: ${System.getProperty("user.dir")}/uploads")
}