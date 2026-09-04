package com.bradj.airshift.data

import java.security.InvalidKeyException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException

/**
 * 判定一次 API Key 解密失败是否永久性。
 *
 * 永久失败（密文再也读不出来）才允许清除密文和密钥：GCM 认证标签不符、密钥已失效或不可恢复
 * （含 Android 的 `KeyPermanentlyInvalidatedException`，它继承自 [InvalidKeyException]）、
 * 存储的 Base64 已损坏。其余异常（Keystore 服务暂不可用、Provider 或 I/O 错误）视为瞬时，
 * 必须保留密文，稍后重试。
 */
internal object ApiKeyDecryptFailure {
    fun isPermanent(error: Throwable): Boolean = when (error) {
        is AEADBadTagException -> true
        is InvalidKeyException -> true
        is UnrecoverableKeyException -> true
        is IllegalArgumentException -> true
        else -> false
    }
}
