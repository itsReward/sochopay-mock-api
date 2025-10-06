package com.soshopay.mockapi.routes

import com.soshopay.mockapi.models.*
import com.soshopay.mockapi.services.*
import com.soshopay.mockapi.storage.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.MessageDigest

fun Route.authRoutes(
    tokenService: TokenService,
    clientStorage: ClientStorage,
    otpService: OtpService,
    tokenStorage: TokenStorage
) {
    route("/api/mobile/client") {

        // Send OTP
        post("/otp/send") {
            val request = call.receive<SendOtpRequest>()

            // Generate OTP
            val otpId = otpService.generateOtp(request.mobile)

            call.respond(
                HttpStatusCode.OK, mapOf(
                    "otp_id" to otpId,
                    "expires_in" to 300, // 5 minutes
                    "message" to "OTP sent to ${request.mobile}"
                )
            )
        }

        // Verify OTP
        post("/otp/verify") {
            val request = call.receive<VerifyOtpRequest>()

            val isValid = otpService.verifyOtp(request.otpId, request.otpCode)

            if (!isValid) {
                call.respond(
                    HttpStatusCode.BadRequest, mapOf(
                        "message" to "Invalid or expired OTP"
                    )
                )
                return@post
            }

            val mobile = otpService.getMobileForOtp(request.otpId)!!
            val (tempToken, expiresAt) = tokenService.generateTempToken(mobile)

            call.respond(
                HttpStatusCode.OK, mapOf(
                    "token" to tempToken,
                    "expires_at" to expiresAt
                )
            )
        }

        // Set PIN (first time)
        post("/set-pin") {
            val request = call.receive<SetPinRequest>()

            // Validate PIN
            if (request.newPin != request.confirmPin) {
                call.respond(
                    HttpStatusCode.BadRequest, mapOf(
                        "message" to "PINs do not match"
                    )
                )
                return@post
            }

            if (request.newPin.length != 4) {
                call.respond(
                    HttpStatusCode.BadRequest, mapOf(
                        "message" to "PIN must be 4 digits"
                    )
                )
                return@post
            }

            // Check if client exists
            val client = clientStorage.findByMobile(request.mobile)
            if (client == null) {
                call.respond(
                    HttpStatusCode.NotFound, mapOf(
                        "message" to "Client not found"
                    )
                )
                return@post
            }

            // Hash and update PIN
            val pinHash = hashPin(request.newPin)
            val updatedClient = client.copy(pinHash = pinHash)
            clientStorage.update(updatedClient)

            // Generate tokens
            val deviceId = call.request.headers["X-Device-ID"]
            val tokens = tokenService.generateTokenPair(client.id, client.mobile, deviceId)

            call.respond(
                HttpStatusCode.OK, mapOf(
                    "token" to tokens.accessToken,
                    "token_type" to "Bearer",
                    "expires_at" to tokens.accessExpiresAt,
                    "expires_in" to 900, // 15 minutes in seconds
                    "client" to clientStorage.toDto(updatedClient)
                )
            )
        }

        // Login
        post("/login") {
            val request = call.receive<LoginRequest>()

            val client = clientStorage.findByMobile(request.mobile)
            if (client == null) {
                call.respond(
                    HttpStatusCode.Unauthorized, mapOf(
                        "message" to "Invalid credentials"
                    )
                )
                return@post
            }

            // Verify PIN
            val pinHash = hashPin(request.pin)
            if (client.pinHash != pinHash) {
                call.respond(
                    HttpStatusCode.Unauthorized, mapOf(
                        "message" to "Invalid credentials"
                    )
                )
                return@post
            }

            // Generate tokens
            val deviceId = call.request.headers["X-Device-ID"]
            val tokens = tokenService.generateTokenPair(client.id, client.mobile, deviceId)

            call.respond(
                HttpStatusCode.OK, mapOf(
                    "access_token" to tokens.accessToken,
                    "access_token_type" to "Bearer",
                    "access_expires_at" to tokens.accessExpiresAt,
                    "refresh_token" to tokens.refreshToken,
                    "refresh_expires_at" to tokens.refreshExpiresAt,
                    "device_id" to deviceId,
                    "client" to clientStorage.toDto(client)
                )
            )
        }

        // Refresh Token
        post("/refresh-token") {
            val request = call.receive<RefreshTokenRequest>()

            // Verify refresh token
            val claims = tokenService.verifyToken(request.refreshToken)
            if (claims == null) {
                call.respond(
                    HttpStatusCode.Unauthorized, mapOf(
                        "message" to "Invalid refresh token"
                    )
                )
                return@post
            }

            // Check token type
            val tokenType = tokenService.getTokenType(request.refreshToken)
            if (tokenType != "refresh") {
                call.respond(
                    HttpStatusCode.BadRequest, mapOf(
                        "message" to "Invalid token type"
                    )
                )
                return@post
            }

            // Generate new token pair
            val tokens = tokenService.generateTokenPair(
                claims.userId,
                claims.mobile,
                claims.deviceId
            )

            call.respond(
                HttpStatusCode.OK, mapOf(
                    "access_token" to tokens.accessToken,
                    "refresh_token" to tokens.refreshToken,
                    "access_expires_at" to tokens.accessExpiresAt,
                    "refresh_expires_at" to tokens.refreshExpiresAt
                )
            )
        }

        // Logout
        post("/logout") {
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
            if (token != null) {
                val claims = tokenService.verifyToken(token)
                if (claims != null) {
                    // Blacklist the token
                    tokenStorage.blacklistToken(claims.tokenId, claims.deviceId)
                }
            }

            call.respond(
                HttpStatusCode.OK, mapOf(
                    "message" to "Logged out successfully"
                )
            )
        }

        // Create Client (Public Registration)
        post("/create") {
            val request = call.receive<CreateClientRequest>()

            // Validate
            if (request.pin != request.confirmPin) {
                call.respond(
                    HttpStatusCode.BadRequest, mapOf(
                        "message" to "PINs do not match"
                    )
                )
                return@post
            }

            // Check if mobile already exists
            if (clientStorage.findByMobile(request.mobile) != null) {
                call.respond(
                    HttpStatusCode.Conflict, mapOf(
                        "message" to "Mobile number already registered"
                    )
                )
                return@post
            }

            // Create client
            val client = clientStorage.create(
                firstName = request.firstName,
                lastName = request.lastName,
                mobile = request.mobile,
                pin = request.pin
            )

            call.respond(
                HttpStatusCode.Created, mapOf(
                    "client" to clientStorage.toDto(client)
                )
            )
        }
    }
}

// Helper function
private fun hashPin(pin: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
