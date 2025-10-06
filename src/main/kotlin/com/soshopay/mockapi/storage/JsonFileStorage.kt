package com.soshopay.mockapi.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class JsonFileStorage<T>(
    private val fileName: String,
    private val defaultValue: T,
    val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) {
    val mutex = Mutex()
    private val dataDir = File("data").apply { mkdirs() }
    val file = File(dataDir, fileName)

    init {
        if (!file.exists()) {
            file.writeText(json.encodeToString(defaultValue as Any))
        }
    }

    suspend inline fun <reified T> read(): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            json.decodeFromString<T>(file.readText())
        }
    }

    suspend inline fun <reified T> write(data: T) = withContext(Dispatchers.IO) {
        mutex.withLock {
            file.writeText(json.encodeToString(data))
        }
    }

    suspend inline fun <reified T> update(block: (T) -> T) {
        val current = read<T>()
        val updated = block(current)
        write(updated)
    }
}