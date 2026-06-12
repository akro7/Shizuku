plugins {
    id("idea")
}

idea.module {
    excludeDirs.add(file("out"))
}

subprojects {
    plugins.withId("com.android.base") {
        extensions.configure<com.android.build.gradle.BaseExtension> {
            compileSdkVersion(36)
            buildToolsVersion = "36.0.0"
            ndkVersion = "29.0.13113456"
            defaultConfig {
                minSdk = 24
                targetSdk = 36
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }
        }
    }
}

apply(from = "api/manifest.gradle")

val gitCommitId = "git rev-parse --short HEAD".let {
    Runtime.getRuntime().exec(it.split(" ").toTypedArray(), null, rootDir).inputStream
        .bufferedReader().readLine()?.trim() ?: "unknown"
}
val gitCommitCount = "git rev-list --count HEAD".let {
    Runtime.getRuntime().exec(it.split(" ").toTypedArray(), null, rootDir).inputStream
        .bufferedReader().readLine()?.trim()?.toIntOrNull() ?: 1
}
val baseVersionName = "14.0"

extra["versionCode"] = gitCommitCount
extra["versionName"] = baseVersionName

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
