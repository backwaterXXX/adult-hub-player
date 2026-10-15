package com.adulthub.tv.data.local

/** Versioned wrapper for sensitive strings kept in Preferences DataStore. */
internal class EncryptedPreferenceValue(
    private val crypto: KeyStoreCrypto,
) {
    companion object {
        private const val PREFIX = "enc:v1:"
    }

    fun isEncrypted(value: String?): Boolean = value?.startsWith(PREFIX) == true

    suspend fun encode(value: String): String =
        if (isEncrypted(value)) value else PREFIX + crypto.encrypt(value)

    suspend fun decode(value: String?): String? = when {
        value == null -> null
        isEncrypted(value) -> crypto.decrypt(value.removePrefix(PREFIX))
        else -> value // Legacy plaintext; migration replaces it after a successful read.
    }
}
