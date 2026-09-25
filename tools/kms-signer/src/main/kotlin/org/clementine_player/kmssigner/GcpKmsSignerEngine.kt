package org.clementine_player.kmssigner

import com.android.apksig.KeyConfig
import com.android.apksig.SignerEngine
import com.android.apksig.kms.KmsSignerEngineProvider
import com.android.apksig.kms.KmsType
import java.security.MessageDigest
import java.security.spec.AlgorithmParameterSpec

/** The algorithms a Cloud KMS RSA_SIGN_PKCS1_*_SHA256 or EC_SIGN_P256_SHA256 key can produce. */
val SUPPORTED_ALGORITHMS = setOf("SHA256withRSA", "SHA256withECDSA")

/**
 * apksig's [SignerEngine] contract is `sign(data) -> signature`: apksig has already assembled
 * the bytes to sign (for a JAR signature, the .SF file; for APK schemes v2/v3, the signed-data
 * block) and does everything else itself, exactly as for a local key. This hashes [data]
 * locally and has [signDigest] sign the digest, so only the digest crosses the wire.
 */
class GcpKmsSignerEngine(
    algorithm: String,
    private val signDigest: (ByteArray) -> ByteArray,
) : SignerEngine {
    init {
        require(algorithm in SUPPORTED_ALGORITHMS) {
            "Cloud KMS keys sign SHA-256 digests only ($SUPPORTED_ALGORITHMS), but apksig asked " +
                "for $algorithm. RSA keys over 3072 bits make APK signature schemes v2/v3 use " +
                "SHA-512: use a 2048- or 3072-bit key."
        }
    }

    override fun sign(data: ByteArray): ByteArray =
        signDigest(MessageDigest.getInstance("SHA-256").digest(data))
}

/**
 * apksig's extension point for KMS-backed signing, found through ServiceLoader (see
 * META-INF/services). apksig defines [KmsType.GCP] but ships no implementation for it; this is
 * it, and it is what `KeyConfig.Kms(KmsType.GCP, <CryptoKeyVersion name>)` resolves to.
 */
class GcpKmsSignerEngineProvider : KmsSignerEngineProvider {
    override fun getKmsType(): String = KmsType.GCP

    override fun getInstance(
        config: KeyConfig.Kms,
        algorithm: String,
        params: AlgorithmParameterSpec?,
    ): SignerEngine = GcpKmsSignerEngine(algorithm) { KmsSigner.signDigest(config.keyAlias, it) }
}
