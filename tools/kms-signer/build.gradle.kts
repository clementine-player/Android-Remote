plugins {
    kotlin("jvm") version "2.3.20"
    application
}

dependencies {
    // Google's own signing implementation, the same code apksigner and the Android Gradle
    // plugin use. This tool only supplies the signatures, from Cloud KMS.
    implementation("com.android.tools.build:apksig:8.13.2")

    // Cloud KMS client: pure Java/gRPC, authenticated with Application Default Credentials.
    implementation("com.google.cloud:google-cloud-kms:2.96.0")

    // Builds the one-time self-signed certificate around the KMS key's public half.
    implementation("org.bouncycastle:bcpkix-jdk18on:1.80")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("org.clementine_player.kmssigner.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
