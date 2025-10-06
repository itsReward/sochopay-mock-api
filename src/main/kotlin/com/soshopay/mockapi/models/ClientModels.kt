package com.soshopay.mockapi.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClientStore(
    val clients: MutableMap<String, Client> = mutableMapOf(),
    val nextId: Int = 1
)

@Serializable
data class Client(
    val id: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    val mobile: String,
    @SerialName("pin_hash") val pinHash: String,
    @SerialName("profile_picture") val profilePicture: String? = null,
    @SerialName("personal_details") val personalDetails: PersonalDetails? = null,
    val address: Address? = null,
    val documents: Documents? = null,
    @SerialName("next_of_kin") val nextOfKin: NextOfKin? = null,
    @SerialName("client_type") val clientType: String = "PRIVATE_SECTOR_EMPLOYEE",
    @SerialName("verification_status") val verificationStatus: String = "UNVERIFIED",
    @SerialName("can_apply_for_loan") val canApplyForLoan: Boolean = false,
    @SerialName("account_status") val accountStatus: String = "INCOMPLETE",
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class PersonalDetails(
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("date_of_birth") val dateOfBirth: String,
    val gender: String,
    val nationality: String,
    val occupation: String,
    @SerialName("monthly_income") val monthlyIncome: Double,
    @SerialName("last_updated") val lastUpdated: String
)

@Serializable
data class Address(
    @SerialName("street_address") val streetAddress: String,
    val suburb: String,
    val city: String,
    val province: String,
    @SerialName("postal_code") val postalCode: String,
    @SerialName("residence_type") val residenceType: String,
    @SerialName("last_updated") val lastUpdated: String
)

@Serializable
data class Documents(
    @SerialName("proof_of_residence") val proofOfResidence: Document? = null,
    @SerialName("national_id") val nationalId: Document? = null
)

@Serializable
data class Document(
    val id: String,
    val url: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("file_size") val fileSize: String,
    @SerialName("upload_date") val uploadDate: String,
    @SerialName("last_updated") val lastUpdated: String,
    @SerialName("verification_status") val verificationStatus: String,
    @SerialName("verification_date") val verificationDate: String? = null,
    @SerialName("verification_notes") val verificationNotes: String? = null,
    @SerialName("document_type") val documentType: String
)

@Serializable
data class NextOfKin(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String,
    val relationship: String,
    @SerialName("phone_number") val phoneNumber: String,
    val address: Address,
    val documents: Documents? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)