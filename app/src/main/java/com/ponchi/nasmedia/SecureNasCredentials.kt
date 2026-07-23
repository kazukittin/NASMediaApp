package com.ponchi.nasmedia

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class NasCredentials(
    val server: String,
    val username: String,
    val password: String
)

internal object SecureNasCredentials {
    private const val KEY_ALIAS = "nas_media_login_key"
    private const val PREFERENCES = "nas_secure_credentials"
    private const val ENCRYPTED_VALUE = "encrypted_value"
    private const val INITIALIZATION_VECTOR = "initialization_vector"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun hasSaved(context: Context): Boolean = context
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .contains(ENCRYPTED_VALUE)

    fun save(context: Context, credentials: NasCredentials) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val plainText = JSONObject()
            .put("server", credentials.server)
            .put("username", credentials.username)
            .put("password", credentials.password)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val encrypted = cipher.doFinal(plainText)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(ENCRYPTED_VALUE, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(
                INITIALIZATION_VECTOR,
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            )
            .commit()
    }

    fun load(context: Context): NasCredentials? {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val encryptedText = preferences.getString(ENCRYPTED_VALUE, null) ?: return null
        val initializationVector = preferences.getString(INITIALIZATION_VECTOR, null) ?: return null
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(128, Base64.decode(initializationVector, Base64.NO_WRAP))
            )
        }
        val json = JSONObject(
            cipher.doFinal(Base64.decode(encryptedText, Base64.NO_WRAP))
                .toString(Charsets.UTF_8)
        )
        return NasCredentials(
            server = json.getString("server"),
            username = json.getString("username"),
            password = json.getString("password")
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
            }
            .generateKey()
    }
}
