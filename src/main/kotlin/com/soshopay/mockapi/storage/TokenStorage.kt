package com.soshopay.mockapi.storage

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

@Serializable
data class TokenStore(
    val blacklistedTokens: MutableSet<String> = mutableSetOf(),
    val deviceTokens: MutableMap<String, MutableSet<String>> = mutableMapOf() // deviceId -> tokenIds
)

class TokenStorage {
    private val storage = JsonFileStorage<TokenStore>(
        fileName = "tokens.json",
        defaultValue = TokenStore()
    )

    suspend fun blacklistToken(tokenId: String, deviceId: String?) {
        storage.update<TokenStore> { store ->
            store.blacklistedTokens.add(tokenId)

            if (deviceId != null) {
                // Remove from device tokens
                store.deviceTokens[deviceId]?.remove(tokenId)
            }

            store
        }
    }

    suspend fun blacklistDeviceTokens(deviceId: String) {
        storage.update<TokenStore> { store ->
            val tokens = store.deviceTokens[deviceId] ?: emptySet()
            store.blacklistedTokens.addAll(tokens)
            store.deviceTokens.remove(deviceId)
            store
        }
    }

    suspend fun isTokenBlacklisted(tokenId: String): Boolean {
        val store = storage.read<TokenStore>()
        return store.blacklistedTokens.contains(tokenId)
    }

    suspend fun registerDeviceToken(deviceId: String, tokenId: String) {
        storage.update<TokenStore> { store ->
            if (!store.deviceTokens.containsKey(deviceId)) {
                store.deviceTokens[deviceId] = mutableSetOf()
            }
            store.deviceTokens[deviceId]?.add(tokenId)
            store
        }
    }
}