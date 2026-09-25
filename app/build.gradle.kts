import org.gradle.api.tasks.testing.logging.TestExceptionFormat
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
    compileSdk = 36

    defaultConfig {
        applicationId = "de.qspool.clementineremote"
        minSdk = 23
        targetSdk = 36
        // Bump both on every release; F-Droid reads them from here.
        versionCode = 800
        versionName = "13-dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The builds differ only in their application ID. Google Play reserves
    // de.qspool.clementineremote for the original author's account, so the Play build has its
    // own; F-Droid and GitHub Releases keep the original, so existing installs upgrade.
    flavorDimensions += "store"
    productFlavors {
        create("fdroid") {
            dimension = "store"
            isDefault = true
        }
        create("play") {
            dimension = "store"
            applicationId = "org.clementine_player.remote"
        }
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
            // Robolectric's Android 36 runtime reaches into JDK internals.
            test.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED")
            test.testLogging {
                events("failed")
                exceptionFormat = TestExceptionFormat.FULL
            }
            // Integration tests against a real Clementine (see clementine-it/) run only
            // when a host is given: ./gradlew testFdroidDebugUnitTest -Pclementine.host=localhost
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

// Every build uploaded to Google Play needs a higher version code than the last, so the Play
// workflow passes one in (see .github/workflows/play.yml). The versions above stay the ones
// F-Droid reads.
androidComponents {
    onVariants(selector().withFlavor("store" to "play")) { variant ->
        val code = project.findProperty("playVersionCode")?.toString()?.toInt()
        val name = project.findProperty("playVersionName")?.toString()
        variant.outputs.forEach { output ->
            code?.let { output.versionCode.set(it) }
            name?.let { output.versionName.set(it) }
        }
    }
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
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.viewpager)
    implementation(libs.androidx.legacy.support.v13)
    implementation(libs.media3.session)
    implementation(libs.protobuf.javalite)
    implementation(libs.jmdns)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.uiautomator)
}
