// signing.gradle.kts
// Uses debug keystore by default. Override by creating config.prop:
// keyStoreFile=...
// keyStorePassword=...
// keyAlias=...
// keyPassword=...

import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.dsl.SigningConfig

val props = java.util.Properties()
val configFile = rootProject.file("config.prop")
if (configFile.exists()) {
    configFile.inputStream().use { props.load(it) }
}

val androidExt = project.extensions.getByType<AppExtension>()

(androidExt.signingConfigs.getByName("sign") as SigningConfig).apply {
    val ksFile = props.getProperty("keyStoreFile")
    if (ksFile != null) {
        storeFile = file(ksFile)
        storePassword = props.getProperty("keyStorePassword")
        keyAlias = props.getProperty("keyAlias")
        keyPassword = props.getProperty("keyPassword")
    } else {
        // Use debug keystore
        val debugKs = File(System.getProperty("user.home"), ".android/debug.keystore")
        storeFile = debugKs
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
    }
}
