package com.soshopay.mockapi.workflows

import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import java.util.*

enum class PaymentStatus {
    PENDING,
    PROCESSING,
    SUCCESSFUL,
    FAILED,
    CANCELLED
}

data class PaymentState(
    val paymentId: String,
    val status: PaymentStatus,
    val transactionReference: String? = null,
    val receiptNumber: String? = null,
    val failureReason: String? = null,
    val processedAt: Long? = null,
    val metadata: Map<String, Any> = emptyMap()
)

class PaymentProcessingWorkflow {

    /**
     * Simulate realistic payment processing with mobile money providers
     */
    suspend fun processPayment(
        paymentId: String,
        amount: Double,
        phoneNumber: String,
        method: String
    ): PaymentState {
        // Initial pending state
        var state = PaymentState(
            paymentId = paymentId,
            status = PaymentStatus.PENDING
        )

        delay(500)

        // Move to processing
        state = state.copy(status = PaymentStatus.PROCESSING)

        // Simulate payment gateway processing (3-10 seconds)
        delay((3000..10000).random().toLong())

        // Simulate success/failure (90% success rate)
        val isSuccessful = (0..100).random() > 10

        state = if (isSuccessful) {
            state.copy(
                status = PaymentStatus.SUCCESSFUL,
                transactionReference = "TXN${UUID.randomUUID().toString().take(8).uppercase()}",
                receiptNumber = "RCP${Clock.System.now().toEpochMilliseconds()}",
                processedAt = Clock.System.now().toEpochMilliseconds()
            )
        } else {
            state.copy(
                status = PaymentStatus.FAILED,
                failureReason = generateFailureReason(method),
                processedAt = Clock.System.now().toEpochMilliseconds()
            )
        }

        return state
    }

    private fun generateFailureReason(method: String): String {
        val reasons = listOf(
            "Insufficient funds in account",
            "Payment timeout - please try again",
            "Transaction declined by provider",
            "Network error occurred",
            "Invalid phone number format"
        )
        return reasons.random()
    }
}