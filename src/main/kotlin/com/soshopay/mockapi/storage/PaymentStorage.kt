package com.soshopay.mockapi.storage

import com.soshopay.mockapi.models.*
import com.soshopay.mockapi.workflows.PaymentStatus
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class PaymentStore(
    val payments: MutableMap<String, Payment> = mutableMapOf(),
    val nextPaymentId: Int = 1
)

@Serializable
data class Payment(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("loan_id") val loanId: String,
    @SerialName("payment_id") val paymentId: String,
    val amount: Double,
    val method: String,
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("receipt_number") val receiptNumber: String?,
    val status: String,
    @SerialName("transaction_reference") val transactionReference: String?,
    @SerialName("processed_at") val processedAt: Long?,
    @SerialName("failure_reason") val failureReason: String?,
    val principal: Double?,
    val interest: Double?,
    val penalties: Double?,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

class PaymentStorage {
    private val storage = JsonFileStorage<PaymentStore>(
        fileName = "payments.json",
        defaultValue = PaymentStore()
    )

    suspend fun createPayment(
        userId: String,
        loanId: String,
        amount: Double,
        method: String,
        phoneNumber: String
    ): Payment {
        val store = storage.read<PaymentStore>()
        val id = "PAY${store.nextPaymentId}"
        val now = Clock.System.now()

        val payment = Payment(
            id = id,
            userId = userId,
            loanId = loanId,
            paymentId = "TXN${UUID.randomUUID().toString().take(8).uppercase()}",
            amount = amount,
            method = method,
            phoneNumber = phoneNumber,
            receiptNumber = null,
            status = PaymentStatus.PENDING.name,
            transactionReference = null,
            processedAt = null,
            failureReason = null,
            principal = amount * 0.85, // 85% principal
            interest = amount * 0.15,  // 15% interest
            penalties = 0.0,
            createdAt = now.toString(),
            updatedAt = now.toString()
        )

        storage.write(store.copy(
            payments = store.payments.apply { put(id, payment) },
            nextPaymentId = store.nextPaymentId + 1
        ))

        return payment
    }

    suspend fun updatePaymentStatus(
        paymentId: String,
        status: PaymentStatus,
        transactionReference: String? = null,
        receiptNumber: String? = null,
        failureReason: String? = null
    ) {
        storage.update<PaymentStore> { store ->
            val payment = store.payments[paymentId]
            if (payment != null) {
                val updated = payment.copy(
                    status = status.name,
                    transactionReference = transactionReference ?: payment.transactionReference,
                    receiptNumber = receiptNumber ?: payment.receiptNumber,
                    failureReason = failureReason,
                    processedAt = if (status == PaymentStatus.SUCCESSFUL || status == PaymentStatus.FAILED)
                        Clock.System.now().toEpochMilliseconds() else null,
                    updatedAt = Clock.System.now().toString()
                )
                store.payments[paymentId] = updated
            }
            store
        }
    }

    suspend fun findPaymentById(paymentId: String): Payment? {
        val store = storage.read<PaymentStore>()
        return store.payments[paymentId]
    }

    suspend fun findPaymentsByUserId(userId: String): List<Payment> {
        val store = storage.read<PaymentStore>()
        return store.payments.values.filter { it.userId == userId }
    }

    suspend fun findPaymentsByLoanId(loanId: String): List<Payment> {
        val store = storage.read<PaymentStore>()
        return store.payments.values.filter { it.loanId == loanId }
    }
}