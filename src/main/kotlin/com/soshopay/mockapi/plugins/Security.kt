package com.soshopay.mockapi.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.soshopay.mockapi.services.TokenService
import com.soshopay.mockapi.storage.TokenStorage
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

fun Application.configureSecurity() {
    val tokenService = TokenService()
    val tokenStorage = TokenStorage()

    authentication {
        jwt("auth-jwt") {
            realm = "SoshoPay Mock API"

            verifier(
                JWT.require(Algorithm.HMAC256("soshopay-mock-secret-key-2025"))
                    .withIssuer("soshopay-mock-api")
                    .build()
            )

            validate { credential ->
                val token = credential.payload.getClaim("token_id").asString()
                val userId = credential.subject

                // Check if token is blacklisted (logged out)
                if (tokenStorage.isTokenBlacklisted(token)) {
                    return@validate null
                }

                // Check token type (must be access token)
                val tokenType = credential.payload.getClaim("token_type").asString()
                if (tokenType != "access") {
                    return@validate null
                }

                if (userId != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf(
                        "message" to "Invalid or expired token",
                        "code" to "TOKEN_INVALID"
                    )
                )
            }
        }
    }
}