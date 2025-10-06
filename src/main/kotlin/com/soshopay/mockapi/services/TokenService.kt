package com.soshopay.mockapi.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import kotlinx.datetime.*
import java.util.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: String,
    val refreshExpiresAt: String,
    val deviceId: String?
)

data class TokenClaims(
    val userId: String,
    val mobile: String,
    val tokenId: String,
    val deviceId: String?
)

class TokenService {
    private val secret = "soshopay-mock-secret-key-2025"
    private val algorithm = Algorithm.HMAC256(secret)
    private val issuer = "soshopay-mock-api"

    // Token expiration times
    private val accessTokenDuration = 15.minutes  // 15 minutes (realistic)
    private val refreshTokenDuration = 30.days     // 30 days
    private val tempTokenDuration = 10.minutes     // For OTP flow

    /**
     * Generate access and refresh token pair
     */
    fun generateTokenPair(
        userId: String,
        mobile: String,
        deviceId: String? = null
    ): TokenPair {
        val now = Clock.System.now()
        val tokenId = UUID.randomUUID().toString()

        val accessExpiresAt = now + accessTokenDuration
        val refreshExpiresAt = now + refreshTokenDuration

        val accessToken = JWT.create()
            .withIssuer(issuer)
            .withSubject(userId)
            .withClaim("mobile", mobile)
            .withClaim("token_id", tokenId)
            .withClaim("token_type", "access")
            .withClaim("device_id", deviceId)
            .withExpiresAt(Date.from(accessExpiresAt.toJavaInstant()))
            .sign(algorithm)

        val refreshToken = JWT.create()
            .withIssuer(issuer)
            .withSubject(userId)
            .withClaim("mobile", mobile)
            .withClaim("token_id", tokenId)
            .withClaim("token_type", "refresh")
            .withClaim("device_id", deviceId)
            .withExpiresAt(Date.from(refreshExpiresAt.toJavaInstant()))
            .sign(algorithm)

        return TokenPair(
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessExpiresAt = accessExpiresAt.toString(),
            refreshExpiresAt = refreshExpiresAt.toString(),
            deviceId = deviceId
        )
    }

    /**
     * Generate temporary token (for OTP verification flow)
     */
    fun generateTempToken(mobile: String): Pair<String, String> {
        val now = Clock.System.now()
        val expiresAt = now + tempTokenDuration

        val token = JWT.create()
            .withIssuer(issuer)
            .withClaim("mobile", mobile)
            .withClaim("token_type", "temp")
            .withExpiresAt(Date.from(expiresAt.toJavaInstant()))
            .sign(algorithm)

        return Pair(token, expiresAt.toString())
    }

    /**
     * Verify and decode token
     */
    fun verifyToken(token: String): TokenClaims? {
        return try {
            val verifier = JWT.require(algorithm)
                .withIssuer(issuer)
                .build()

            val decodedJWT = verifier.verify(token)

            TokenClaims(
                userId = decodedJWT.subject,
                mobile = decodedJWT.getClaim("mobile").asString(),
                tokenId = decodedJWT.getClaim("token_id").asString(),
                deviceId = decodedJWT.getClaim("device_id").asString()
            )
        } catch (e: JWTVerificationException) {
            null
        }
    }

    /**
     * Check if token is expired
     */
    fun isTokenExpired(token: String): Boolean {
        return try {
            val decodedJWT = JWT.decode(token)
            decodedJWT.expiresAt.before(Date())
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Extract token type
     */
    fun getTokenType(token: String): String? {
        return try {
            JWT.decode(token).getClaim("token_type").asString()
        } catch (e: Exception) {
            null
        }
    }
}