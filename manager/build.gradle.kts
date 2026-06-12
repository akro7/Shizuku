import com.android.build.gradle.internal.tasks.CompileArtProfileTask
import com.android.build.api.artifact.SingleArtifact

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("dev.rikka.tools.autoresconfig")
    id("dev.rikka.tools.materialthemebuilder")
}

android {
    namespace = "moe.shizuku.manager"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "moe.shizuku.privileged.api"
        versionCode = rootProject.extra["versionCode"] as Int
        versionName = rootProject.extra["versionName"] as String
        minSdk = 26
        targetSdk = 36
        externalNativeBuild {
            cmake {
                arguments("-DANDROID_STL=none")
            }
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        prefab = true
    }

    signingConfigs {
        create("sign") {
            // Will use debug keystore by default; override via signing.gradle
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("sign")
        }
        release {
            signingConfig = signingConfigs.getByName("sign")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.31.0+"
        }
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf("**")
        }
    }

    dependenciesInfo {
        includeInApk = false
    }

    lint {
        checkReleaseBuilds = false
    }
}

autoResConfig {
    generatedClassFullName = "rikka.shizuku.manager.ShizukuLocales"
    generateRes = false
    generatedArrayFirstItem = "SYSTEM"
    generateLocaleConfig = true
}

materialThemeBuilder {
    themes {
        create("shizuku") {
            primaryColor = "#3F51B5"
            lightThemeFormat = "Theme.Material3.Light.%s"
            lightThemeParent = "Theme.Material3.Light.Rikka"
            darkThemeFormat = "Theme.Material3.Dark.%s"
            darkThemeParent = "Theme.Material3.Dark.Rikka"
        }
    }
    generatePalette = true
    generateTextColors = true
}

// Use lazy matching instead of getByName/named to avoid "task not found"
// errors caused by task-registration ordering with newer AGP versions.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(":shell:assembleRelease")
}
tasks.matching { it.name == "preDebugBuild" }.configureEach {
    dependsOn(":shell:assembleDebug")
}

// New Variant API: copy (and rename) the produced APK into out/apk,
// and for minified build types also copy the mapping file into out/mapping.
androidComponents {
    onVariants(selector().all()) { variant ->
        val versionNameStr = rootProject.extra["versionName"] as String
        val variantNameCapped = variant.name.replaceFirstChar { it.uppercase() }
        val finalApkName = "shizuku-v$versionNameStr-${variant.name}.apk"

        val apkDirProvider = variant.artifacts.get(SingleArtifact.APK)

        val isMinifyEnabled = variant.buildType?.let {
            android.buildTypes.getByName(it).isMinifyEnabled
        } ?: false

        val copyOutputsTask = tasks.register<Copy>("copyOutputs$variantNameCapped") {
            from(apkDirProvider) {
                include("*.apk")
                rename { finalApkName }
                into("apk")
            }

            if (isMinifyEnabled) {
                val mappingFileProvider = variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE)
                from(mappingFileProvider) {
                    rename { "mapping-$versionNameStr.txt" }
                    into("mapping")
                }
            }

            into(File(rootDir, "out"))
        }

        tasks.matching { it.name == "assemble$variantNameCapped" }.configureEach {
            finalizedBy(copyOutputsTask)
        }
    }
}

tasks.withType<CompileArtProfileTask>().configureEach {
    enabled = false
}

configurations.configureEach {
    exclude(group = "androidx.appcompat", module = "appcompat")
    exclude(group = "androidx.profileinstaller", module = "profileinstaller")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation(project(":server"))
    implementation(project(":rish"))
    implementation(project(":starter"))
    implementation(project(":api"))
    implementation(project(":provider"))

    implementation(libs.hidden.compat)
    compileOnly(libs.hidden.stub)

    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.fragment:fragment-ktx:1.8.7")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.0")
    implementation("androidx.preference:preference-ktx:1.2.1")

    implementation("com.google.android.material:material:1.12.0")

    implementation("com.github.topjohnwu.libsu:core:6.0.0")

    implementation("dev.rikka.rikkax.appcompat:appcompat:1.6.1")
    implementation("dev.rikka.rikkax.compatibility:compatibility:2.0.0")
    implementation("dev.rikka.rikkax.core:core-ktx:1.4.1")
    implementation("dev.rikka.rikkax.material:material:2.7.2")
    implementation("dev.rikka.rikkax.material:material-preference:2.0.0")
    implementation("dev.rikka.rikkax.html:html-ktx:1.1.2")
    implementation("dev.rikka.rikkax.recyclerview:recyclerview-adapter:1.3.0")
    implementation("dev.rikka.rikkax.recyclerview:recyclerview-ktx:1.3.2")
    implementation("dev.rikka.rikkax.insets:insets:1.3.0")
    implementation("dev.rikka.rikkax.layoutinflater:layoutinflater:1.3.0")
    implementation("dev.rikka.rikkax.widget:borderview:1.1.0")
    implementation("dev.rikka.rikkax.preference:simplemenu-preference:1.0.3")
    implementation("dev.rikka.rikkax.lifecycle:lifecycle-resource-livedata:1.0.1")
    implementation("dev.rikka.rikkax.lifecycle:lifecycle-shared-viewmodel:1.0.1")
    implementation("dev.rikka.rikkax.lifecycle:lifecycle-viewmodel-lazy:2.0.0")

    implementation("io.github.vvb2060.ndk:boringssl:20250114")
    implementation("org.lsposed.libcxx:libcxx:27.0.12077973")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.80")
    implementation("me.zhanghai.android.appiconloader:appiconloader:1.5.0")
}

// Signing config
apply(from = rootProject.file("signing.gradle.kts"))
