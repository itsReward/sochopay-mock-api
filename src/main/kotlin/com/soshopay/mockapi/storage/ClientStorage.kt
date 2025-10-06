package com.soshopay.mockapi.storage

import com.soshopay.mockapi.models.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.util.*

class ClientStorage {
    private val storage = JsonFileStorage<ClientStore>(
        fileName = "clients.json",
        defaultValue = ClientStore()
    )

    suspend fun create(
        firstName: String,
        lastName: String,
        mobile: String,
        pin: String
    ): Client {
        val store = storage.read<ClientStore>()
        val id = store.nextId.toString()
        val now = Clock.System.now().toString()

        val client = Client(
            id = id,
            firstName = firstName,
            lastName = lastName,
            mobile = mobile,
            pinHash = hashPin(pin),
            createdAt = now,
            updatedAt = now
        )

        storage.write(store.copy(
            clients = store.clients.apply { put(id, client) },
            nextId = store.nextId + 1
        ))

        return client
    }

    suspend fun findById(id: String): Client? {
        val store = storage.read<ClientStore>()
        return store.clients[id]
    }

    suspend fun findByMobile(mobile: String): Client? {
        val store = storage.read<ClientStore>()
        return store.clients.values.find { it.mobile == mobile }
    }

    suspend fun update(client: Client) {
        storage.update<ClientStore> { store ->
            store.copy(
                clients = store.clients.apply {
                    put(client.id, client.copy(
                        updatedAt = Clock.System.now().toString()
                    ))
                }
            )
        }
    }

    suspend fun updateProfile(
        clientId: String,
        personalDetails: PersonalDetails? = null,
        address: Address? = null,
        profilePicture: String? = null,
        documents: Documents? = null,
        nextOfKin: NextOfKin? = null
    ): Client? {
        val client = findById(clientId) ?: return null

        val updated = client.copy(
            personalDetails = personalDetails ?: client.personalDetails,
            address = address ?: client.address,
            profilePicture = profilePicture ?: client.profilePicture,
            documents = documents ?: client.documents,
            nextOfKin = nextOfKin ?: client.nextOfKin,
            accountStatus = calculateAccountStatus(
                personalDetails ?: client.personalDetails,
                address ?: client.address,
                documents ?: client.documents,
                nextOfKin ?: client.nextOfKin
            ),
            canApplyForLoan = canApplyForLoan(
                personalDetails ?: client.personalDetails,
                address ?: client.address,
                documents ?: client.documents,
                client.verificationStatus
            )
        )

        update(updated)
        return updated
    }

    private fun calculateAccountStatus(
        personalDetails: PersonalDetails?,
        address: Address?,
        documents: Documents?,
        nextOfKin: NextOfKin?
    ): String {
        return when {
            personalDetails != null && address != null &&
                    documents?.nationalId != null && documents.proofOfResidence != null &&
                    nextOfKin != null -> "COMPLETE"
            personalDetails != null || address != null -> "INCOMPLETE"
            else -> "INCOMPLETE"
        }
    }

    private fun canApplyForLoan(
        personalDetails: PersonalDetails?,
        address: Address?,
        documents: Documents?,
        verificationStatus: String
    ): Boolean {
        return personalDetails != null &&
                address != null &&
                documents?.nationalId != null &&
                documents.proofOfResidence != null &&
                verificationStatus == "VERIFIED"
    }

    fun toDto(client: Client): Map<String, Any?> {
        return mapOf(
            "id" to client.id,
            "first_name" to client.firstName,
            "last_name" to client.lastName,
            "mobile" to client.mobile,
            "profile_picture" to client.profilePicture,
            "personal_details" to client.personalDetails,
            "address" to client.address,
            "documents" to client.documents,
            "next_of_kin" to client.nextOfKin,
            "client_type" to client.clientType,
            "verification_status" to client.verificationStatus,
            "can_apply_for_loan" to client.canApplyForLoan,
            "account_status" to client.accountStatus,
            "created_at" to client.createdAt,
            "updated_at" to client.updatedAt
        )
    }

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}