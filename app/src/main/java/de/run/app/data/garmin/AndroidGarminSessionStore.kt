package de.run.app.data.garmin

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class GarminSession(
    val accessToken: String,
    val refreshToken: String,
    val clientId: String,
    val expiresAtMillis: Long
)

internal class AndroidGarminSessionStore(context: Context) {
    private val file = File(context.filesDir, SESSION_FILE_NAME)

    @Synchronized
    fun load(): GarminSession? = runCatching {
        val encrypted = file.readBytes()
        require(encrypted.size > IV_BYTES)
        val iv = encrypted.copyOfRange(0, IV_BYTES)
        val payload = encrypted.copyOfRange(IV_BYTES, encrypted.size)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val cleartext = cipher.doFinal(payload)
        try {
            JSONObject(String(cleartext, Charsets.UTF_8)).let { value ->
                GarminSession(
                    value.getString("accessToken"),
                    value.getString("refreshToken"),
                    value.getString("clientId"),
                    value.getLong("expiresAtMillis")
                )
            }
        } finally {
            cleartext.fill(0)
            encrypted.fill(0)
        }
    }.getOrNull()

    @Synchronized
    fun save(session: GarminSession) {
        val cleartext = JSONObject()
            .put("accessToken", session.accessToken)
            .put("refreshToken", session.refreshToken)
            .put("clientId", session.clientId)
            .put("expiresAtMillis", session.expiresAtMillis)
            .toString()
            .toByteArray(Charsets.UTF_8)
        try {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encrypted = cipher.iv + cipher.doFinal(cleartext)
            file.outputStream().use { it.write(encrypted) }
            encrypted.fill(0)
        } finally {
            cleartext.fill(0)
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "lap.garmin.connect.session.v1"
        const val SESSION_FILE_NAME = "garmin-connect-session.enc"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val IV_BYTES = 12
    }
}
