package moe.shizuku.manager.hide.detectors

import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * PropsLeakDetector — يكشف system properties اللي تكشف وجود الـ root.
 *
 * معادل userspace لـ extras.c (AVC Spoof) في KernelSU-Next.
 * بيفحص properties المرتبطة بـ:
 * - KernelSU / Magisk / SuperSU
 * - ro.debuggable / ro.secure
 * - test-keys build signatures
 * - ro.build.tags (userdebug)
 */
object PropsLeakDetector {

    private const val TAG = "PropsLeakDetector"

    data class PropLeak(
        val key: String,
        val value: String,
        val severity: Severity,
        val reason: String
    ) {
        enum class Severity { HIGH, MEDIUM, LOW }
    }

    // ── Props اللي وجودها = root مكشوف مباشرة ──
    private val HIGH_RISK_PROPS = mapOf(
        "ro.magisk.version" to "Magisk version exposed",
        "ro.kernelsu.version" to "KernelSU version exposed",
        "ro.kernelsu" to "KernelSU flag exposed",
        "persist.magisk.hide" to "Magisk hide config exposed",
        "magisk.version" to "Magisk version exposed",
        "ro.build.selinux" to "SELinux build info exposed"
    )

    // ── Props اللي قيمتها المشبوهة تكشف root ──
    private val SUSPICIOUS_PROP_VALUES = mapOf(
        "ro.debuggable" to listOf("1"),
        "ro.secure" to listOf("0"),
        "ro.build.tags" to listOf("test-keys", "dev-keys"),
        "ro.build.type" to listOf("userdebug", "eng"),
        "ro.adb.secure" to listOf("0"),
        "service.adb.root" to listOf("1"),
        "ro.boot.verifiedbootstate" to listOf("orange", "red")
    )

    /**
     * يشغل فحص كامل ويرجع قائمة بـ prop leaks المكتشفة.
     */
    fun scan(): List<String> {
        val leaks = mutableListOf<String>()
        leaks += scanHighRiskProps()
        leaks += scanSuspiciousValues()
        leaks += checkBuildFingerprint()
        return leaks.distinct()
    }

    /**
     * يرجع تفاصيل كاملة عن كل leak.
     */
    fun scanDetailed(): List<PropLeak> {
        val leaks = mutableListOf<PropLeak>()

        // High risk props
        HIGH_RISK_PROPS.forEach { (key, reason) ->
            val value = getProperty(key)
            if (!value.isNullOrEmpty() && value != "unknown") {
                leaks += PropLeak(key, value, PropLeak.Severity.HIGH, reason)
                Log.w(TAG, "HIGH: $key=$value — $reason")
            }
        }

        // Suspicious values
        SUSPICIOUS_PROP_VALUES.forEach { (key, suspiciousValues) ->
            val value = getProperty(key)
            if (!value.isNullOrEmpty() && value in suspiciousValues) {
                leaks += PropLeak(
                    key, value, PropLeak.Severity.MEDIUM,
                    "Value '$value' indicates modified/rooted system"
                )
                Log.w(TAG, "MEDIUM: $key=$value")
            }
        }

        return leaks
    }

    private fun scanHighRiskProps(): List<String> {
        return HIGH_RISK_PROPS.mapNotNull { (key, reason) ->
            val value = getProperty(key)
            if (!value.isNullOrEmpty() && value != "unknown") "$key=$value" else null
        }
    }

    private fun scanSuspiciousValues(): List<String> {
        return SUSPICIOUS_PROP_VALUES.mapNotNull { (key, suspiciousValues) ->
            val value = getProperty(key)
            if (!value.isNullOrEmpty() && value in suspiciousValues) "$key=$value" else null
        }
    }

    /**
     * يفحص الـ build fingerprint لو فيه test-keys أو userdebug.
     */
    private fun checkBuildFingerprint(): List<String> {
        val leaks = mutableListOf<String>()
        val fingerprint = Build.FINGERPRINT
        if (fingerprint.contains("test-keys")) {
            leaks += "ro.build.fingerprint contains test-keys"
            Log.w(TAG, "Build fingerprint has test-keys: $fingerprint")
        }
        if (fingerprint.contains("userdebug")) {
            leaks += "ro.build.fingerprint contains userdebug"
        }
        return leaks
    }

    /**
     * يقرأ system property عبر الـ shell — أكثر شمولًا من SystemProperties API.
     */
    private fun getProperty(key: String): String? {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("getprop", key))
            val result = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            if (result.isEmpty()) null else result
        } catch (e: Exception) {
            try {
                // fallback إلى SystemProperties reflection
                val clazz = Class.forName("android.os.SystemProperties")
                val method = clazz.getMethod("get", String::class.java, String::class.java)
                method.invoke(null, key, null) as? String
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * يرجع summary سريع.
     */
    fun getSummary(): String {
        val leaks = scanDetailed()
        if (leaks.isEmpty()) return "✅ No prop leaks"
        val high = leaks.count { it.severity == PropLeak.Severity.HIGH }
        val medium = leaks.count { it.severity == PropLeak.Severity.MEDIUM }
        return "🔴 High: $high  🟡 Medium: $medium"
    }
}
