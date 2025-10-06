package com.soshopay.mockapi.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@kotlinx.serialization.Serializable
data class SendOtpRequest(val mobile: String)
@kotlinx.serialization.Serializable
data class VerifyOtpRequest(
    @SerialName("otp_id") val otpId: String,
    @SerialName("otp_code") val otpCode: String
)
@kotlinx.serialization.Serializable
data class SetPinRequest(
    val mobile: String,
    @SerialName("new_pin") val newPin: String,
    @SerialName("confirm_pin") val confirmPin: String
)
@kotlinx.serialization.Serializable
data class LoginRequest(
    val mobile: String,
    val pin: String
)
@kotlinx.serialization.Serializable
data class RefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String
)
@Serializable
data class CreateClientRequest(
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    val mobile: String,
    val pin: String,
    @SerialName("confirm_pin") val confirmPin: String
)
