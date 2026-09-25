package org.clementine_player.kmssigner

import com.google.cloud.kms.v1.CryptoKeyVersionName
import com.google.cloud.kms.v1.KeyManagementServiceClient
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.ContentSigner
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Date

private const val VALIDITY_YEARS = 30L

/**
 * One-time bootstrap: builds a long-lived self-signed certificate around the Cloud KMS key's
 * public half, signed by the key itself in KMS. Every release of an app must be signed with the
 * same certificate, so the output is committed and reused, never regenerated per build.
 */
fun generateCert(cryptoKeyVersionName: String, subjectDn: String, outPath: String) {
    val publicKey = fetchKmsPublicKey(cryptoKeyVersionName)
    val subject = X500Name(subjectDn)
    val now = Instant.now()

    val holder = JcaX509v3CertificateBuilder(
        /* issuer = */ subject,
        /* serial = */ BigInteger.valueOf(now.toEpochMilli()),
        /* notBefore = */ Date.from(now),
        /* notAfter = */ Date.from(now.plus(VALIDITY_YEARS * 365, ChronoUnit.DAYS)),
        /* subject = */ subject,
        /* publicKey = */ publicKey,
    ).build(KmsContentSigner(publicKey.algorithm) { KmsSigner.signDigest(cryptoKeyVersionName, it) })

    File(outPath).writeText(buildString {
        append("-----BEGIN CERTIFICATE-----\n")
        append(Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(holder.encoded))
        append("\n-----END CERTIFICATE-----\n")
    })
    println("Wrote self-signed cert for $cryptoKeyVersionName to $outPath (valid $VALIDITY_YEARS years from now)")
}

/**
 * BouncyCastle's extension point for signing that happens elsewhere, used only by
 * [generateCert] to sign the certificate in KMS.
 */
class KmsContentSigner(
    keyAlgorithm: String,
    private val signDigest: (ByteArray) -> ByteArray,
) : ContentSigner {
    private val identifier = when (keyAlgorithm) {
        "RSA" -> AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption)
        "EC" -> AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA256)
        else -> error("Unsupported key algorithm $keyAlgorithm")
    }

    private val buffer = ByteArrayOutputStream()

    override fun getAlgorithmIdentifier() = identifier

    override fun getOutputStream() = buffer

    override fun getSignature(): ByteArray =
        signDigest(MessageDigest.getInstance("SHA-256").digest(buffer.toByteArray()))
}

private fun fetchKmsPublicKey(cryptoKeyVersionName: String): PublicKey {
    KeyManagementServiceClient.create().use { client ->
        val response = client.getPublicKey(CryptoKeyVersionName.parse(cryptoKeyVersionName))
        val encoded = Base64.getMimeDecoder().decode(response.pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", ""))
        val algorithm = if (response.algorithm.name.startsWith("EC_")) "EC" else "RSA"
        return KeyFactory.getInstance(algorithm).generatePublic(X509EncodedKeySpec(encoded))
    }
}
