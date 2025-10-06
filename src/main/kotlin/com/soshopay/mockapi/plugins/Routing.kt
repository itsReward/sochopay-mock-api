package com.soshopay.mockapi.plugins

import com.soshopay.mockapi.routes.*
import com.soshopay.mockapi.services.*
import com.soshopay.mockapi.storage.*
import com.soshopay.mockapi.workflows.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    // Initialize services
    val tokenService = TokenService()
    val otpService = OtpService()

    // Initialize storage
    val clientStorage = ClientStorage()
    val loanStorage = LoanStorage()
    val paymentStorage = PaymentStorage()
    val tokenStorage = TokenStorage()

    // Initialize workflows
    val loanWorkflow = LoanApplicationWorkflow()
    val paymentWorkflow = PaymentProcessingWorkflow()

    routing {
        // Health check
        get("/health") {
            call.respond(mapOf(
                "status" to "healthy",
                "service" to "SoshoPay Mock API",
                "version" to "1.0.0"
            ))
        }

        // Auth routes
        authRoutes(tokenService, clientStorage, otpService, tokenStorage)

        // Profile routes
        profileRoutes(clientStorage)

        // Loan routes
        loanRoutes(loanStorage, clientStorage, loanWorkflow)

        // Payment routes
        paymentRoutes(paymentStorage, loanStorage, paymentWorkflow)
    }
}