package moe.shizuku.manager.update

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val RELEASES_URL =
        "https://api.github.com/repos/akro7/Shizuku/releases/latest"

    suspend fun checkForUpdate(context: Context): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val currentVersionCode = context.packageManager
                .getPackageInfo(context.packageName, 0)
                .longVersionCode.toInt()

            val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext UpdateResult.Error("HTTP $responseCode")
            }

            val json = JSONObject(connection.inputStream.bufferedReader().readText())

            // Parse tag: e.g. "v13.7" → versionName, versionCode from assets name or tag
            val tagName = json.getString("tag_name").removePrefix("v")
            val body = json.optString("body", "")
            val publishedAt = json.optString("published_at", "")

            // Try to extract versionCode from tag (e.g. "13.7" → 13007) or assets
            val remoteVersionCode = parseVersionCode(tagName)

            val downloadUrl = json.getJSONArray("assets")
                .let { arr ->
                    (0 until arr.length()).map { arr.getJSONObject(it) }
                        .firstOrNull { it.getString("name").endsWith(".apk") }
                        ?.getString("browser_download_url")
                } ?: json.optString("html_url", "")

            val info = UpdateInfo(
                versionName = tagName,
                versionCode = remoteVersionCode,
                releaseNotes = body,
                downloadUrl = downloadUrl,
                publishedAt = publishedAt
            )

            if (remoteVersionCode > currentVersionCode) {
                UpdateResult.UpdateAvailable(info)
            } else {
                UpdateResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Converts "13.7" → 13007, "14.0.1" → 14000001, etc.
     * Falls back to 0 if parsing fails.
     */
    private fun parseVersionCode(versionName: String): Int {
        return try {
            val parts = versionName.split(".").map { it.toIntOrNull() ?: 0 }
            when (parts.size) {
                1 -> parts[0] * 1_000_000
                2 -> parts[0] * 1_000_000 + parts[1] * 1_000
                else -> parts[0] * 1_000_000 + parts[1] * 1_000 + parts[2]
            }
        } catch (e: Exception) {
            0
        }
    }
}

sealed class UpdateResult {
    data class UpdateAvailable(val info: UpdateInfo) : UpdateResult()
    object UpToDate : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}
