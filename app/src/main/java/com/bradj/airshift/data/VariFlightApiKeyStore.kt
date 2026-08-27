package com.bradj.airshift.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class VariFlightApiKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    init {
        clearLegacyGatewayCredential()
    }

    var value: String?
        get() {
            val encrypted = preferences.getString(KEY_ENCRYPTED_API_KEY, null) ?: return null
            val iv = preferences.getString(KEY_IV, null) ?: return null
            return try {
                val key = loadKey() ?: return clearAndReturnNull()
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(iv, Base64.NO_WRAP)))
                cipher.updateAAD(ASSOCIATED_DATA)
                String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), StandardCharsets.UTF_8)
                    .trim()
                    .takeIf { it.isNotEmpty() }
            } catch (_: GeneralSecurityException) {
                clearAndReturnNull()
            } catch (_: IllegalArgumentException) {
                clearAndReturnNull()
            } catch (_: IOException) {
                clearAndReturnNull()
            }
        }
        set(apiKey) {
            val normalized = apiKey?.trim()?.takeIf { it.isNotEmpty() }
            if (normalized == null) {
                clear()
                return
            }
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
                cipher.updateAAD(ASSOCIATED_DATA)
                val encrypted = cipher.doFinal(normalized.toByteArray(StandardCharsets.UTF_8))
                preferences.edit {
                    putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                    putString(KEY_ENCRYPTED_API_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                }
            } catch (_: GeneralSecurityException) {
                clear()
                throw IllegalStateException("无法安全保存飞常准 API Key")
            } catch (_: IOException) {
                clear()
                throw IllegalStateException("无法安全保存飞常准 API Key")
            }
        }

    fun clear() {
        preferences.edit {
            remove(KEY_IV)
            remove(KEY_ENCRYPTED_API_KEY)
        }
        deleteKey(KEY_ALIAS)
    }

    private fun getOrCreateKey(): SecretKey = loadKey() ?: KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        .apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
        }
        .generateKey()

    private fun loadKey(): SecretKey? = KeyStore.getInstance(ANDROID_KEYSTORE).run {
        load(null)
        getKey(KEY_ALIAS, null) as? SecretKey
    }

    private fun clearAndReturnNull(): String? {
        clear()
        return null
    }

    private fun clearLegacyGatewayCredential() {
        preferences.edit {
            remove(KEY_LEGACY_GATEWAY_IV)
            remove(KEY_LEGACY_GATEWAY_TOKEN)
        }
        deleteKey(KEY_LEGACY_GATEWAY_ALIAS)
    }

    private fun deleteKey(alias: String) {
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE).run {
                load(null)
                if (containsAlias(alias)) deleteEntry(alias)
            }
        } catch (_: GeneralSecurityException) {
            // The encrypted value is already removed; a stale key cannot expose the API key.
        } catch (_: IOException) {
            // The encrypted value is already removed; a stale key cannot expose the API key.
        }
    }

    companion object {
        private const val FILE_NAME = "air_shift_secrets"
        private const val KEY_IV = "variflight_api_key_iv"
        private const val KEY_ENCRYPTED_API_KEY = "variflight_api_key_ciphertext"
        private const val KEY_ALIAS = "air_shift_variflight_api_key"
        private const val KEY_LEGACY_GATEWAY_IV = "gateway_token_iv"
        private const val KEY_LEGACY_GATEWAY_TOKEN = "gateway_token_ciphertext"
        private const val KEY_LEGACY_GATEWAY_ALIAS = "air_shift_gateway_token"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private val ASSOCIATED_DATA = "air-shift-variflight-api-key-v1".toByteArray(StandardCharsets.UTF_8)
    }
}
