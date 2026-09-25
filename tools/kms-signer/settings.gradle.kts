// A standalone build, separate from the app's: it is a CI tool that signs the release bundle
// after the app build has produced it unsigned. Run it with the root wrapper:
//   ./gradlew -p tools/kms-signer installDist
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kms-signer"
