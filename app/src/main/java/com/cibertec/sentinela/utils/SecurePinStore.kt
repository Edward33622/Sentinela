package com.cibertec.sentinela.utils

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Cifra el caché local de PINs con una clave que permanece en Android Keystore. */
internal class SecurePinStore(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext
        .getSharedPreferences("sentinela_prefs", Context.MODE_PRIVATE)
    private val keyAlias = "sentinela_pin_cache_key"

    fun get(name: String, userId: Int): String {
        if (userId <= 0) return ""
        val storageKey = storageKey(name, userId)
        val encrypted = preferences.getString(storageKey, null)
        if (encrypted != null) return decrypt(encrypted).orEmpty()

        // Migración de la versión anterior: cifra el valor local al primer acceso.
        val legacy = preferences.getString(name, null)
        if (!legacy.isNullOrBlank()) {
            set(name, userId, legacy)
            preferences.edit().remove(name).apply()
            return legacy
        }
        return ""
    }

    fun set(name: String, userId: Int, value: String) {
        if (userId <= 0 || value.isBlank()) return
        preferences.edit().putString(storageKey(name, userId), encrypt(value)).apply()
    }

    private fun storageKey(name: String, userId: Int) = "${name}_$userId"

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = ByteBuffer.allocate(cipher.iv.size + encrypted.size)
            .put(cipher.iv)
            .put(encrypted)
            .array()
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? = try {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        val buffer = ByteBuffer.wrap(payload)
        val iv = ByteArray(12).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }
}
