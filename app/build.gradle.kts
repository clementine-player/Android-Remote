import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.protobuf)
}

// Release signing: CI passes the keystore via environment variables; locally a
// key.properties file (see key.properties.sample) can be used instead.
val keyProperties = Properties().apply {
    val file = file("key.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? =
    System.getenv(env) ?: keyProperties.getProperty(prop)

android {
    namespace = "de.qspool.clementineremote"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.qspool.clementineremote"
        minSdk = 23
        targetSdk = 30
        // Bump both on every release; F-Droid reads them from here.
        versionCode = 800
        versionName = "13-dev"
    }

    signingConfigs {
        create("release") {
            signingValue("SIGNING_KEYSTORE", "keystore")?.let { storeFile = file(it) }
            storePassword = signingValue("SIGNING_KEYSTORE_PASSWORD", "keystore.password")
            keyAlias = signingValue("SIGNING_KEY_ALIAS", "key.alias")
            keyPassword = signingValue("SIGNING_KEY_PASSWORD", "key.password")
        }
    }

    buildTypes {
        release {
            val release = signingConfigs.getByName("release")
            if (release.storeFile != null) signingConfig = release
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // Integration tests against a real Clementine (see clementine-it/) run only
            // when a host is given: ./gradlew testDebugUnitTest -Pclementine.host=localhost
            val host = project.findProperty("clementine.host") as String?
            if (host == null) {
                test.exclude("**/integration/**")
            } else {
                test.systemProperty("clementine.host", host)
                listOf("clementine.port", "clementine.authCode").forEach { name ->
                    project.findProperty(name)?.let { test.systemProperty(name, it) }
                }
                test.outputs.upToDateWhen { false }
            }
        }
    }

    lint {
        disable += "MissingQuantity"
        // Existing issues are recorded in the baseline; CI fails on new ones.
        baseline = file("lint-baseline.xml")
    }
}

base {
    archivesName.set("ClementineRemote")
}

protobuf {
    protoc {
        artifact = libs.protoc.get().toString()
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") { option("lite") }
            }
        }
    }
}

dependencies {
    implementation(libs.support.v13)
    implementation(libs.support.appcompat)
    implementation(libs.support.recyclerview)
    implementation(libs.protobuf.javalite)
    implementation(libs.jmdns)
    implementation(libs.material.dialogs.core)
    implementation(libs.material.dialogs.commons)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
