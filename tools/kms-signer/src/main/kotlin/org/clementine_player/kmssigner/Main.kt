package org.clementine_player.kmssigner

import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.android.apksig.KeyConfig
import com.android.apksig.kms.KmsType
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.jar.JarFile

/**
 * Signs Android release builds with a Cloud KMS key, without the private key ever existing
 * outside Cloud KMS. See README.md.
 *
 *   kms-signer gencert --key <CryptoKeyVersion name> --subject "CN=..." --out <cert.pem>
 *     One-time bootstrap of the signing certificate, committed and reused for every build.
 *
 *   kms-signer sign --key <CryptoKeyVersion name> --cert <cert.pem> --in <in> --out <out>
 *                   [--min-sdk <n>]
 *     Signs an APK (JAR signing and APK signature schemes v2 and v3) or an app bundle (.aab,
 *     JAR signing only, which is all Google Play reads). A bundle has no top-level manifest to
 *     read the minimum SDK from, so --min-sdk is required for one.
 *
 *   kms-signer verify --in <apk or aab>
 *
 * Authentication is Application Default Credentials: Workload Identity Federation in CI,
 * `gcloud auth application-default login` locally.
 */
fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "Usage: kms-signer <sign|gencert|verify> --flag value ..." }
    val opts = parseArgs(args.drop(1).toTypedArray())

    when (args[0]) {
        "sign" -> {
            sign(
                keyConfig = KeyConfig.Kms(KmsType.GCP, opts.require("key")),
                cert = loadCertificate(File(opts.require("cert"))),
                input = File(opts.require("in")),
                output = File(opts.require("out")),
                minSdk = opts.optional("min-sdk")?.toInt(),
            )
            println("Signed ${opts.require("in")} -> ${opts.require("out")}")
        }
        "gencert" -> generateCert(
            cryptoKeyVersionName = opts.require("key"),
            subjectDn = opts.require("subject"),
            outPath = opts.require("out"),
        )
        "verify" -> println(verify(File(opts.require("in"))))
        else -> error("Unknown subcommand '${args[0]}' (expected 'sign', 'gencert' or 'verify')")
    }
}

private fun isBundle(file: File) = file.name.endsWith(".aab")

fun sign(keyConfig: KeyConfig, cert: X509Certificate, input: File, output: File, minSdk: Int?) {
    val signerConfig = ApkSigner.SignerConfig.Builder("upload", keyConfig, listOf(cert)).build()
    val builder = ApkSigner.Builder(listOf(signerConfig))
        .setInputApk(input)
        .setOutputApk(output)

    if (isBundle(input)) {
        requireNotNull(minSdk) { "--min-sdk is required to sign an app bundle" }
        builder.setMinSdkVersion(minSdk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(false)
            .setV3SigningEnabled(false)
            .setV4SigningEnabled(false)
    } else {
        minSdk?.let { builder.setMinSdkVersion(it) }
        builder.setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
    }
    builder.build().sign()
}

/**
 * Checks the signature and returns its certificate's subject. An APK goes through apksig's own
 * verifier; a bundle carries a plain JAR signature, which the JDK checks on every entry.
 */
fun verify(file: File): String {
    if (!isBundle(file)) {
        val result = ApkVerifier.Builder(file).build().verify()
        result.errors.forEach { System.err.println("ERROR: $it") }
        check(result.isVerified) { "$file failed verification" }
        return "verified v1=${result.isVerifiedUsingV1Scheme} v2=${result.isVerifiedUsingV2Scheme} " +
            "v3=${result.isVerifiedUsingV3Scheme} signer=${result.signerCertificates.single().subjectX500Principal}"
    }

    val signers = mutableSetOf<String>()
    JarFile(file, true).use { jar ->
        for (entry in jar.entries()) {
            if (entry.isDirectory || SIGNATURE_FILE.matches(entry.name)) continue
            // Reading an entry to the end checks its digest against the signature.
            jar.getInputStream(entry).use { it.readAllBytes() }
            val certs = checkNotNull(entry.codeSigners) { "${entry.name} is not signed" }
            certs.forEach { signers += (it.signerCertPath.certificates[0] as X509Certificate).subjectX500Principal.name }
        }
    }
    check(signers.size == 1) { "Expected one signer, found $signers" }
    return "verified jar signer=${signers.single()}"
}

/** The JAR signature's own files, which are not themselves signed. */
private val SIGNATURE_FILE = Regex("META-INF/(MANIFEST\\.MF|[^/]+\\.(SF|RSA|DSA|EC))", RegexOption.IGNORE_CASE)

private class Options(private val map: Map<String, String>) {
    fun require(flag: String): String = map[flag] ?: error("--$flag is required")
    fun optional(flag: String): String? = map[flag]
}

private fun parseArgs(args: Array<String>): Options {
    val map = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val flag = args[i].removePrefix("--")
        require(i + 1 < args.size) { "Missing value for --$flag" }
        map[flag] = args[i + 1]
        i += 2
    }
    return Options(map)
}

fun loadCertificate(file: File): X509Certificate = file.inputStream().use {
    CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
}
