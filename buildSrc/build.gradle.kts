plugins {
    `kotlin-dsl`
}
repositories {
    google()
    mavenCentral()
}

gradlePlugin {
    plugins {
        register("MagiskPlugin") {
            id = "MagiskPlugin"
            implementationClass = "MagiskPlugin"
        }
    }
}

dependencies {
    implementation(kotlin("gradle-plugin", "2.1.21"))
    implementation("com.android.tools.build:gradle:8.10.1")
    implementation("androidx.navigation:navigation-safe-args-gradle-plugin:2.7.7")
    // io.michaelrocks:paranoid-gradle-plugin removed:
    // it calls the now-removed android.registerTransform API and fails to apply
    // under AGP 8.10. paranoid-core (annotation runtime, used via @Obfuscate) is
    // still kept as a normal dependency in app/shared/build.gradle.kts.
    // jgit removed - using git CLI command instead
}
