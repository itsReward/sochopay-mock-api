package com.soshopay.mockapi.services

import kotlinx.datetime.Clock
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class OtpSession(
    val otpId: String,
    val mobile: String,
    val otpCode: String,
    val expiresAt: Long,
    var attempts: Int = 0
)

class OtpService {
    private val otpSessions = ConcurrentHashMap<String, OtpSession>()
    private val otpDuration = 5 * 60 * 1000L // 5 minutes

    fun generateOtp(mobile: String): String {
        val otpId = UUID.randomUUID().toString()
        val otpCode = (1000..9999).random().toString()
        val expiresAt = Clock.System.now().toEpochMilliseconds() + otpDuration

        val session = OtpSession(
            otpId = otpId,
            mobile = mobile,
            otpCode = otpCode,
            expiresAt = expiresAt
        )

        otpSessions[otpId] = session

        // Log OTP for testing (in real implementation, send SMS)
        println("📱 OTP for $mobile: $otpCode (ID: $otpId)")

        return otpId
    }

    fun verifyOtp(otpId: String, enteredCode: String): Boolean {
        val session = otpSessions[otpId] ?: return false

        // Check expiration
        if (Clock.System.now().toEpochMilliseconds() > session.expiresAt) {
            otpSessions.remove(otpId)
            return false
        }

        // Check attempts
        if (session.attempts >= 3) {
            otpSessions.remove(otpId)
            return false
        }

        session.attempts++

        return if (session.otpCode == enteredCode) {
            // Don't remove yet - might be used for temp token generation
            true
        } else {
            false
        }
    }

    fun getMobileForOtp(otpId: String): String? {
        return otpSessions[otpId]?.mobile
    }

    fun cleanupExpiredOtps() {
        val now = Clock.System.now().toEpochMilliseconds()
        otpSessions.entries.removeIf { it.value.expiresAt < now }
    }
}