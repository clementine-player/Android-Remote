package org.clementine_player.kmssigner

import com.android.apksig.KeyConfig
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.asn1.x500.X500Name
import java.io.File
import java.math.BigInteger
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SignerTest {

    private val dir = Files.createTempDirectory("kms-signer").toFile()

    private fun rsaKey(bits: Int = 3072): KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }.generateKeyPair()

    private fun ecKey(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    /** A certificate made the way `gencert` makes it, with the key signing in "KMS". */
    private fun certificate(key: KeyPair): X509Certificate {
        val name = X500Name("CN=Test Upload")
        val holder = JcaX509v3CertificateBuilder(name, BigInteger.ONE, Date(),
            Date(System.currentTimeMillis() + 86_400_000), name, key.public)
            .build(KmsContentSigner(key.public.algorithm) { signDigest(key.private, it) })
        return JcaX509CertificateConverter().getCertificate(holder).apply { verify(key.public) }
    }

    /** Stands in for an app bundle: Play only reads its JAR signature. */
    private fun bundle(): File = File(dir, "app.aab").apply {
        ZipOutputStream(outputStream()).use { zip ->
            for ((name, content) in mapOf(
                "BundleConfig.pb" to "config",
                "base/manifest/AndroidManifest.xml" to "manifest",
                "base/dex/classes.dex" to "dex",
                "base/root/META-INF/services/x" to "service",
            )) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }

    private fun kmsKey(alias: String, key: KeyPair): KeyConfig {
        LocalKeySignerEngineProvider.keys[alias] = key.private
        return KeyConfig.Kms(LocalKeySignerEngineProvider.TYPE, alias)
    }

    private fun signBundle(key: KeyPair, alias: String): File {
        val signed = File(dir, "$alias-signed.aab")
        sign(kmsKey(alias, key), certificate(key), bundle(), signed, minSdk = 23)
        return signed
    }

    @Test
    fun signsBundleWithRsaKey() {
        val signed = signBundle(rsaKey(), "rsa")
        assertEquals("verified jar signer=CN=Test Upload", verify(signed))
        JarFile(signed).use { jar ->
            val names = jar.entries().toList().map { it.name }
            assertContains(names, "META-INF/UPLOAD.SF")
            assertContains(names, "META-INF/UPLOAD.RSA")
        }
    }

    @Test
    fun signsBundleWithEcKey() {
        assertEquals("verified jar signer=CN=Test Upload", verify(signBundle(ecKey(), "ec")))
    }

    @Test
    fun jdkJarsignerAcceptsTheBundle() {
        val signed = signBundle(rsaKey(), "jarsigner")
        val jarsigner = File(System.getProperty("java.home"), "bin/jarsigner").path
        val process = ProcessBuilder(jarsigner, "-verify", signed.path).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor(), output)
        assertContains(output, "jar verified.")
    }

    @Test
    fun tamperedBundleFailsVerification() {
        val signed = signBundle(rsaKey(), "tamper")
        val tampered = File(dir, "tampered.aab")
        ZipOutputStream(tampered.outputStream()).use { out ->
            JarFile(signed).use { jar ->
                for (entry in jar.entries()) {
                    out.putNextEntry(ZipEntry(entry.name))
                    val bytes = jar.getInputStream(entry).readAllBytes()
                    out.write(if (entry.name == "base/dex/classes.dex") "evil".toByteArray() else bytes)
                    out.closeEntry()
                }
            }
        }
        assertFailsWith<SecurityException> { verify(tampered) }
    }

    @Test
    fun bundleNeedsMinSdk() {
        val key = rsaKey(2048)
        assertFailsWith<IllegalArgumentException> {
            sign(kmsKey("nominsdk", key), certificate(key), bundle(), File(dir, "out.aab"), minSdk = null)
        }
    }

    @Test
    fun rejectsDigestsKmsCannotSign() {
        assertFailsWith<IllegalArgumentException> { GcpKmsSignerEngine("SHA512withRSA") { it } }
    }
}
