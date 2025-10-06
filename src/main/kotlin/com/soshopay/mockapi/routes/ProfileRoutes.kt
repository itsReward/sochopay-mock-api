package com.soshopay.mockapi.routes

import com.soshopay.mockapi.models.*
import com.soshopay.mockapi.storage.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.util.*

fun Route.profileRoutes(clientStorage: ClientStorage) {
    route("/api/mobile/client") {

        authenticate("auth-jwt") {

            // Get User Profile
            get("/me") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!

                val client = clientStorage.findById(userId)

                if (client == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "message" to "Client not found"
                    ))
                    return@get
                }

                call.respond(HttpStatusCode.OK, mapOf(
                    "client" to clientStorage.toDto(client)
                ))
            }

            // Update Personal Details
            put("/personal-details") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!
                val request = call.receive<PersonalDetailsRequest>()

                val client = clientStorage.findById(userId)
                if (client == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "message" to "Client not found"
                    ))
                    return@put
                }

                val personalDetails = PersonalDetails(
                    firstName = request.firstName,
                    lastName = request.lastName,
                    dateOfBirth = request.dateOfBirth,
                    gender = request.gender,
                    nationality = request.nationality,
                    occupation = request.occupation,
                    monthlyIncome = request.monthlyIncome,
                    lastUpdated = Clock.System.now().toString()
                )

                clientStorage.updateProfile(
                    clientId = userId,
                    personalDetails = personalDetails
                )

                call.respond(HttpStatusCode.OK, mapOf(
                    "message" to "Personal details updated successfully"
                ))
            }

            // Update Address
            put("/address") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!
                val request = call.receive<AddressRequest>()

                val address = Address(
                    streetAddress = request.streetAddress,
                    suburb = request.suburb,
                    city = request.city,
                    province = request.province,
                    postalCode = request.postalCode,
                    residenceType = request.residenceType,
                    lastUpdated = Clock.System.now().toString()
                )

                clientStorage.updateProfile(
                    clientId = userId,
                    address = address
                )

                call.respond(HttpStatusCode.OK, mapOf(
                    "message" to "Address updated successfully"
                ))
            }

            // Upload Profile Picture
            post("/upload-picture") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!

                val multipart = call.receiveMultipart()
                var fileUrl: String? = null

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            val fileName = part.originalFileName ?: "profile_${UUID.randomUUID()}.jpg"
                            val fileBytes = part.streamProvider().readBytes()

                            // Save file
                            val uploadsDir = File("uploads/profiles").apply { mkdirs() }
                            val file = File(uploadsDir, "${userId}_$fileName")
                            file.writeBytes(fileBytes)

                            fileUrl = "/uploads/profiles/${userId}_$fileName"
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                if (fileUrl == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "message" to "No file uploaded"
                    ))
                    return@post
                }

                clientStorage.updateProfile(
                    clientId = userId,
                    profilePicture = fileUrl
                )

                call.respond(HttpStatusCode.OK, mapOf(
                    "url" to fileUrl,
                    "message" to "Profile picture uploaded successfully"
                ))
            }

            // Upload Documents
            post("/upload-documents") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!

                val multipart = call.receiveMultipart()
                val uploadedDocs = mutableMapOf<String, Document>()

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            val fieldName = part.name ?: ""
                            val fileName = part.originalFileName ?: "document.pdf"
                            val fileBytes = part.streamProvider().readBytes()

                            // Save file
                            val uploadsDir = File("uploads/documents").apply { mkdirs() }
                            val savedFileName = "${userId}_${fieldName}_$fileName"
                            val file = File(uploadsDir, savedFileName)
                            file.writeBytes(fileBytes)

                            val documentType = when (fieldName) {
                                "proof_of_residence" -> "PROOF_OF_RESIDENCE"
                                "national_id" -> "NATIONAL_ID"
                                else -> "OTHER"
                            }

                            val document = Document(
                                id = UUID.randomUUID().toString(),
                                url = "/uploads/documents/$savedFileName",
                                fileName = fileName,
                                fileSize = "${fileBytes.size / 1024}KB",
                                uploadDate = Clock.System.now().toString(),
                                lastUpdated = Clock.System.now().toString(),
                                verificationStatus = "PENDING",
                                verificationDate = null,
                                verificationNotes = null,
                                documentType = documentType
                            )

                            uploadedDocs[fieldName] = document
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                // Get current client
                val client = clientStorage.findById(userId)
                if (client == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "message" to "Client not found"
                    ))
                    return@post
                }

                // Update documents
                val currentDocs = client.documents
                val updatedDocs = Documents(
                    proofOfResidence = uploadedDocs["proof_of_residence"] ?: currentDocs?.proofOfResidence,
                    nationalId = uploadedDocs["national_id"] ?: currentDocs?.nationalId
                )

                clientStorage.updateProfile(
                    clientId = userId,
                    documents = updatedDocs
                )

                call.respond(HttpStatusCode.OK, mapOf(
                    "message" to "Documents uploaded successfully",
                    "documents" to uploadedDocs.values.map { it.documentType }
                ))
            }

            // Get Documents
            get("/documents") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!

                val client = clientStorage.findById(userId)
                if (client == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "message" to "Client not found"
                    ))
                    return@get
                }

                call.respond(HttpStatusCode.OK, mapOf(
                    "documents" to client.documents
                ))
            }

            // Update Next of Kin
            put("/next-of-kin") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!
                val request = call.receive<NextOfKinRequest>()

                val nextOfKin = NextOfKin(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    fullName = request.fullName,
                    relationship = request.relationship,
                    phoneNumber = request.phoneNumber,
                    address = Address(
                        streetAddress = request.address.streetAddress,
                        suburb = request.address.suburb,
                        city = request.address.city,
                        province = request.address.province,
                        postalCode = request.address.postalCode,
                        residenceType = request.address.residenceType,
                        lastUpdated = Clock.System.now().toString()
                    ),
                    documents = null,
                    createdAt = Clock.System.now().toString(),
                    updatedAt = Clock.System.now().toString()
                )

                clientStorage.updateProfile(
                    clientId = userId,
                    nextOfKin = nextOfKin
                )

                call.respond(HttpStatusCode.OK, mapOf(
                    "message" to "Next of kin updated successfully"
                ))
            }

            // Get Next of Kin
            get("/next-of-kin") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!

                val client = clientStorage.findById(userId)
                if (client == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "message" to "Client not found"
                    ))
                    return@get
                }

                if (client.nextOfKin == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "message" to "Next of kin not found"
                    ))
                    return@get
                }

                call.respond(HttpStatusCode.OK, client.nextOfKin)
            }

            // Get Client Types
            get("/client-types") {
                call.respond(HttpStatusCode.OK, mapOf(
                    "client_types" to listOf(
                        "PRIVATE_SECTOR_EMPLOYEE",
                        "PUBLIC_SECTOR_EMPLOYEE",
                        "SELF_EMPLOYED",
                        "BUSINESS_OWNER",
                        "STUDENT",
                        "PENSIONER"
                    )
                ))
            }

            // Update Client Type
            put("/client-type") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!
                val request = call.receive<ClientTypeRequest>()

                val client = clientStorage.findById(userId)
                if (client == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "message" to "Client not found"
                    ))
                    return@put
                }

                val updatedClient = client.copy(
                    clientType = request.clientType,
                    updatedAt = Clock.System.now().toString()
                )

                clientStorage.update(updatedClient)

                call.respond(HttpStatusCode.OK, mapOf(
                    "message" to "Client type updated successfully"
                ))
            }

            // Update PIN
            post("/pin") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!
                val request = call.receive<UpdatePinRequest>()

                if (request.newPin != request.confirmPin) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "message" to "PINs do not match"
                    ))
                    return@post
                }

                val client = clientStorage.findById(userId)
                if (client == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "message" to "Client not found"
                    ))
                    return@post
                }

                // Verify current PIN
                val currentPinHash = hashPin(request.currentPin)
                if (client.pinHash != currentPinHash) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf(
                        "message" to "Current PIN is incorrect"
                    ))
                    return@post
                }

                // Update PIN
                val newPinHash = hashPin(request.newPin)
                val updatedClient = client.copy(
                    pinHash = newPinHash,
                    updatedAt = Clock.System.now().toString()
                )

                clientStorage.update(updatedClient)

                call.respond(HttpStatusCode.OK, mapOf(
                    "message" to "PIN updated successfully"
                ))
            }
        }
    }
}

// Helper function
private fun hashPin(pin: String): String {
    val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

// Request DTOs
@Serializable
data class PersonalDetailsRequest(
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("date_of_birth") val dateOfBirth: String,
    val gender: String,
    val nationality: String,
    val occupation: String,
    @SerialName("monthly_income") val monthlyIncome: Double
)

@Serializable
data class AddressRequest(
    @SerialName("street_address") val streetAddress: String,
    val suburb: String,
    val city: String,
    val province: String,
    @SerialName("postal_code") val postalCode: String,
    @SerialName("residence_type") val residenceType: String
)

@Serializable
data class NextOfKinRequest(
    @SerialName("full_name") val fullName: String,
    val relationship: String,
    @SerialName("phone_number") val phoneNumber: String,
    val address: AddressRequest
)

@Serializable
data class ClientTypeRequest(
    @SerialName("client_type") val clientType: String
)

@Serializable
data class UpdatePinRequest(
    @SerialName("current_pin") val currentPin: String,
    @SerialName("new_pin") val newPin: String,
    @SerialName("confirm_pin") val confirmPin: String
)