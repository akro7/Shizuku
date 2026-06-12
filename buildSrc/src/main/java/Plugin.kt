import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.provideDelegate
import java.io.File
import java.util.*

private val props = Properties()
private var commitHash = ""

object Config {
    operator fun get(key: String): String? {
        val v = props[key] as? String ?: return null
        return if (v.isBlank()) null else v
    }

    fun contains(key: String) = get(key) != null

    val version: String get() = get("version") ?: commitHash
    val versionCode: Int get() = get("magisk.versionCode")?.toIntOrNull() ?: 1
    val stubVersion: String get() = get("magisk.stubVersion") ?: "1"
}

class MagiskPlugin : Plugin<Project> {
    override fun apply(project: Project) = project.applyPlugin()

    private fun Project.applyPlugin() {
        initRandom(rootProject.file("dict.txt"))
        props.clear()
        rootProject.file("gradle.properties").inputStream().use { props.load(it) }
        val configPath: String? by this
        val config = configPath?.let { File(it) } ?: rootProject.file("config.prop")
        if (config.exists())
            config.inputStream().use { props.load(it) }

        // Get commit hash via git command (works in GitHub Actions with fetch-depth=0)
        commitHash = try {
            val result = Runtime.getRuntime()
                .exec(arrayOf("git", "rev-parse", "--short", "HEAD"), null, rootDir)
                .inputStream.bufferedReader().readLine()?.trim() ?: "unknown"
            if (result.isBlank() || result.startsWith("fatal")) "unknown" else result
        } catch (e: Exception) {
            "unknown"
        }
    }
}
