package org.clementine_player.kmssigner

import com.google.cloud.kms.v1.AsymmetricSignRequest
import com.google.cloud.kms.v1.Digest
import com.google.cloud.kms.v1.KeyManagementServiceClient
import com.google.protobuf.ByteString
import com.google.protobuf.Int64Value
import java.util.zip.CRC32C

/**
 * The one operation this tool exists for: send a SHA-256 digest to a Cloud KMS signing key and
 * get the signature back, with Cloud KMS's documented integrity checks both ways. The key never
 * leaves KMS. The signature is in the form JCA produces for the key's algorithm (PKCS#1 v1.5
 * for RSA_SIGN_PKCS1_*_SHA256 keys, DER for EC_SIGN_P256_SHA256), which is what apksig and
 * X.509 expect.
 */
object KmsSigner {
    fun signDigest(cryptoKeyVersionName: String, sha256Digest: ByteArray): ByteArray {
        KeyManagementServiceClient.create().use { client ->
            val localCrc = CRC32C().apply { update(sha256Digest) }.value

            val response = client.asymmetricSign(
                AsymmetricSignRequest.newBuilder()
                    .setName(cryptoKeyVersionName)
                    .setDigest(Digest.newBuilder().setSha256(ByteString.copyFrom(sha256Digest)).build())
                    .setDigestCrc32C(Int64Value.of(localCrc))
                    .build(),
            )

            check(response.verifiedDigestCrc32C) { "Cloud KMS did not verify the request digest checksum" }
            val returnedCrc = CRC32C().apply { update(response.signature.toByteArray()) }.value
            check(returnedCrc == response.signatureCrc32C.value) { "Cloud KMS signature response failed checksum verification" }

            return response.signature.toByteArray()
        }
    }
}
