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

    /** 是否存有密文；不触发解密，也不访问 Keystore。 */
    val hasValue: Boolean
        get() = preferences.contains(KEY_ENCRYPTED_API_KEY) && preferences.contains(KEY_IV)

    var value: String?
        get() {
            val encrypted = preferences.getString(KEY_ENCRYPTED_API_KEY, null) ?: return null
            val iv = preferences.getString(KEY_IV, null) ?: return null
            return try {
                // 密文尚在而密钥已不存在，永远解不开：清掉密文而不是每次都失败。
                val key = loadKey() ?: return clearAndReturnNull()
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(iv, Base64.NO_WRAP)))
                cipher.updateAAD(ASSOCIATED_DATA)
                String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), StandardCharsets.UTF_8)
                    .trim()
                    .takeIf { it.isNotEmpty() }
            } catch (error: GeneralSecurityException) {
                onReadFailure(error)
            } catch (error: IllegalArgumentException) {
                onReadFailure(error)
            } catch (error: IOException) {
                onReadFailure(error)
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

    /**
     * 只有永久性失败（标签不符、密钥失效、密文损坏）才清除；Keystore 瞬时故障保留密文，
     * 本次返回 null，下次读取再试。不记录异常内容。
     */
    private fun onReadFailure(error: Throwable): String? =
        if (ApiKeyDecryptFailure.isPermanent(error)) clearAndReturnNull() else null

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
        /** 早期版本的 gateway 凭据；由 [LegacyMigrations] 一次性清理，不再在构造时执行。 */
        internal fun clearLegacyGatewayCredential(context: Context) {
            context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit {
                remove(KEY_LEGACY_GATEWAY_IV)
                remove(KEY_LEGACY_GATEWAY_TOKEN)
            }
            try {
                KeyStore.getInstance(ANDROID_KEYSTORE).run {
                    load(null)
                    if (containsAlias(KEY_LEGACY_GATEWAY_ALIAS)) deleteEntry(KEY_LEGACY_GATEWAY_ALIAS)
                }
            } catch (_: GeneralSecurityException) {
                // 密文已删除，残留的旧密钥无法泄露任何信息。
            } catch (_: IOException) {
                // 同上。
            }
        }

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
