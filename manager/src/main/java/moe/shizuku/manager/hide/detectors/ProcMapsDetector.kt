package moe.shizuku.manager.hide.detectors

import android.util.Log
import java.io.BufferedReader
import java.io.FileReader

/**
 * ProcMapsDetector — يكشف المكتبات والـ paths المشبوهة في /proc/self/maps.
 *
 * معادل userspace لـ SUSFS (SUS File System) المذكور في KernelSU-Next.
 * SUSFS بيخفي هذه الـ entries من /proc/maps للتطبيقات.
 * هنا بنكشفها عشان نعرف إيه اللي ظاهر.
 *
 * بيفحص:
 * - مكتبات Magisk/KernelSU المـloaded في الـ process
 * - Paths مشبوهة في الـ memory maps
 * - Anonymous mappings من مناطق /data/adb
 */
object ProcMapsDetector {

    private const val TAG = "ProcMapsDetector"

    // ── Library paths المشبوهة اللي SUSFS بيحاول يخبيها ──
    private val SUSPICIOUS_MAP_PATTERNS = listOf(
        "/data/adb",
        "/sbin/.magisk",
        "/sbin/.core",
        "magisk",
        "zygisk",
        "libzygisk",
        "libmagisk",
        "libksu",
        "libsuperuser",
        "libadbroot",          // من adb_root.c في KernelSU
        "/debug_ramdisk",
        "ksucompat",
        "ksud",
        "/data/local/tmp"      // common injection path
    )

    /**
     * يقرأ /proc/self/maps ويرجع الـ entries المشبوهة.
     */
    fun scan(): List<String> {
        val leaks = mutableListOf<String>()

        try {
            BufferedReader(FileReader("/proc/self/maps")).use { reader ->
                reader.lineSequence().forEach { line ->
                    SUSPICIOUS_MAP_PATTERNS.forEach { pattern ->
                        if (line.contains(pattern, ignoreCase = true)) {
                            Log.w(TAG, "Suspicious maps entry: $line")
                            leaks += extractPathFromMapsLine(line) ?: line.take(100)
                            return@forEach
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read /proc/self/maps: ${e.message}")
        }

        return leaks.distinct()
    }

    /**
     * يفحص /proc/[pid]/maps لعملية معينة (يحتاج صلاحيات كافية).
     */
    fun scanProcess(pid: Int): List<String> {
        val leaks = mutableListOf<String>()

        try {
            BufferedReader(FileReader("/proc/$pid/maps")).use { reader ->
                reader.lineSequence().forEach { line ->
                    SUSPICIOUS_MAP_PATTERNS.forEach { pattern ->
                        if (line.contains(pattern, ignoreCase = true)) {
                            leaks += extractPathFromMapsLine(line) ?: line.take(100)
                            return@forEach
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // عادي - ممكن ما نقدرش نقرأ maps لـ processes تانية
        }

        return leaks.distinct()
    }

    /**
     * يفحص لو في مكتبة معينة مـloaded في الـ process الحالي.
     * مفيد للفحص السريع.
     *
     * مثال: isMappedInProcess("magisk") → true لو Magisk مـhooking الـ process ده
     */
    fun isMappedInProcess(keyword: String): Boolean {
        return try {
            BufferedReader(FileReader("/proc/self/maps")).use { reader ->
                reader.lineSequence().any { it.contains(keyword, ignoreCase = true) }
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * يرجع كل المكتبات المـloaded حاليًا في الـ process.
     */
    fun getAllMappedLibraries(): List<String> {
        val libs = mutableListOf<String>()
        try {
            BufferedReader(FileReader("/proc/self/maps")).use { reader ->
                reader.lineSequence().forEach { line ->
                    val path = extractPathFromMapsLine(line)
                    if (path != null && (path.endsWith(".so") || path.endsWith(".apk"))) {
                        libs += path
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAllMappedLibraries failed: ${e.message}")
        }
        return libs.distinct()
    }

    /**
     * يستخرج الـ path من سطر /proc/maps.
     * Format: address perms offset dev inode pathname
     * مثال: 7f1234000-7f1238000 r-xp 00000000 fd:01 12345  /system/lib/libc.so
     */
    private fun extractPathFromMapsLine(line: String): String? {
        val parts = line.trim().split("\\s+".toRegex())
        return if (parts.size >= 6) parts.last().takeIf { it.startsWith("/") } else null
    }
}
