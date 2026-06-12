pluginManagement {
    repositories {
        google()
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.10.1"
        id("com.android.library") version "8.10.1"
        id("org.jetbrains.kotlin.android") version "2.1.21"
        id("dev.rikka.tools.autoresconfig") version "1.2.2"
        id("dev.rikka.tools.materialthemebuilder") version "1.5.1"
        // dev.rikka.tools.refine plugin removed - incompatible with AGP 8.8+
        // libs.refine.* runtime/annotation dependencies are still kept in build.gradle.kts
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenLocal()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.topjohnwu.libsu")
            }
        }
    }
    versionCatalogs {
        create("libs") {
            version("hidden-api", "4.4.0")
            version("refine", "4.4.0")
            library("refine-runtime", "dev.rikka.tools.refine", "runtime").versionRef("refine")
            library("refine-annotation", "dev.rikka.tools.refine", "annotation").versionRef("refine")
            library("refine-annotation-processor", "dev.rikka.tools.refine", "annotation-processor").versionRef("refine")
            library("hidden-compat", "dev.rikka.hidden", "compat").versionRef("hidden-api")
            library("hidden-stub", "dev.rikka.hidden", "stub").versionRef("hidden-api")
        }
    }
}

rootProject.name = "ShizukuRoot"

// ── Main app ──────────────────────────────────────────────────────────────────
include(":manager")

// ── Shizuku core modules ──────────────────────────────────────────────────────
include(":server", ":starter", ":shell", ":common")

// ── Shizuku API submodule (cloned fresh by CI from RikkaApps/Shizuku-API) ────
include(":aidl")
project(":aidl").projectDir = file("api/aidl")

include(":rish")
project(":rish").projectDir = file("api/rish")

include(":shared")
project(":shared").projectDir = file("api/shared")

include(":api")
project(":api").projectDir = file("api/api")

include(":provider")
project(":provider").projectDir = file("api/provider")

include(":server-shared")
project(":server-shared").projectDir = file("api/server-shared")

// NOTE: hidden-api-stub is a Maven dependency (dev.rikka.hidden:stub), NOT a local project
// It is declared in versionCatalogs above as libs.hidden.stub

// ── Magisk stub module ────────────────────────────────────────────────────────
include(":stub")

// ── Magisk app modules ─────────────────────────────────────────────────────────
include(":app")
include(":app:shared")

// ── Magisk native module ─────────────────────────────────────────────────────
include(":native")
