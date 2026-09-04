package com.bradj.airshift.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.InvalidKeyException
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException

class ApiKeyDecryptFailureTest {
    @Test
    fun `a bad authentication tag means the ciphertext can never be read again`() {
        assertTrue(ApiKeyDecryptFailure.isPermanent(AEADBadTagException("tag mismatch")))
    }

    @Test
    fun `an unusable or unrecoverable key is permanent`() {
        assertTrue(ApiKeyDecryptFailure.isPermanent(InvalidKeyException("key permanently invalidated")))
        assertTrue(ApiKeyDecryptFailure.isPermanent(UnrecoverableKeyException("gone")))
    }

    @Test
    fun `corrupt stored ciphertext is permanent`() {
        assertTrue(ApiKeyDecryptFailure.isPermanent(IllegalArgumentException("bad base64")))
    }

    @Test
    fun `a keystore hiccup is transient and must keep the ciphertext`() {
        // 系统升级后首次访问、刚解锁、StrongBox 暂不可用都会抛这种异常，稍后重试即可恢复。
        assertFalse(ApiKeyDecryptFailure.isPermanent(KeyStoreException("Keystore operation failed")))
    }

    @Test
    fun `provider and io errors are transient`() {
        assertFalse(ApiKeyDecryptFailure.isPermanent(ProviderException("keystore busy")))
        assertFalse(ApiKeyDecryptFailure.isPermanent(IOException("read failed")))
    }
}
