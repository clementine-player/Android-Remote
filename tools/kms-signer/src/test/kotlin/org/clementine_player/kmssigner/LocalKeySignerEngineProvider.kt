package org.clementine_player.kmssigner

import com.android.apksig.KeyConfig
import com.android.apksig.SignerEngine
import com.android.apksig.kms.KmsSignerEngineProvider
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher

/**
 * Stands in for Cloud KMS in tests: signs digests with a local key, the way KMS signs them, so
 * apksig drives the real [GcpKmsSignerEngine] through its KMS extension point.
 */
class LocalKeySignerEngineProvider : KmsSignerEngineProvider {
    companion object {
        const val TYPE = "local-test"
        val keys = mutableMapOf<String, PrivateKey>()
    }

    override fun getKmsType() = TYPE

    override fun getInstance(
        config: KeyConfig.Kms,
        algorithm: String,
        params: AlgorithmParameterSpec?,
    ): SignerEngine = GcpKmsSignerEngine(algorithm) { signDigest(keys.getValue(config.keyAlias), it) }
}

/** Signs a SHA-256 digest as Cloud KMS does: PKCS#1 v1.5 for RSA, DER-encoded ECDSA for EC. */
fun signDigest(key: PrivateKey, digest: ByteArray): ByteArray = when (key.algorithm) {
    "RSA" -> Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
        init(Cipher.ENCRYPT_MODE, key)
        doFinal(SHA256_DIGEST_INFO_PREFIX + digest)
    }
    "EC" -> Signature.getInstance("NONEwithECDSA").run {
        initSign(key)
        update(digest)
        sign()
    }
    else -> error(key.algorithm)
}

/** DER DigestInfo header for SHA-256 (RFC 8017 section 9.2). */
private val SHA256_DIGEST_INFO_PREFIX = byteArrayOf(
    0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86.toByte(), 0x48, 0x01, 0x65, 0x03, 0x04, 0x02,
    0x01, 0x05, 0x00, 0x04, 0x20,
)
