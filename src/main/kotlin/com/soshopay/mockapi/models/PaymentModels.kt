package com.soshopay.mockapi.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PaymentRequest(
    @SerialName("loan_id") val loanId: String,
    val amount: Double,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("customer_reference") val customerReference: String? = null
)
