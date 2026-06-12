package moe.shizuku.manager.hide.detectors

import android.util.Log
import java.io.File

/**
 * SuCompatDetector — يكشف تفعيل sucompat في KernelSU-Next.
 *
 * sucompat.c بيعمل:
 *   • ksu_handle_faccessat:       /system/bin/su → /system/bin/sh
 *   • ksu_handle_stat:            /system/bin/su → /system/bin/sh
 *   • ksu_handle_execve_sucompat: execve(su)     → execve(ksud)
 *
 * طريقة الكشف (userspace):
 *   1. نقارن inode بتاع /system/bin/su مع /system/bin/sh
 *      → لو sucompat شغال stat(su) بيرجع بيانات sh
 *   2. نفحص لو /data/adb/ksu/bin/ksud موجود
 *      → وجوده بيأكد KernelSU مثبت
 */
object SuCompatDetector {

    private const val TAG = "SuCompatDetector"

    private const val SU_PATH  = "/system/bin/su"
    private const val SH_PATH  = "/system/bin/sh"

    // KSUD_PATH الحقيقي المعرّف في kernel/include/ksud.h واللي
    // ksu_handle_execve_sucompat بيعمله execve عليه بدل su
    private const val KSUD_PATH = "/data/adb/ksud"

    // symlink بيتعمله ksud نفسه في BINARY_DIR (defs.rs: DAEMON_LINK_PATH)
    private const val KSUD_BIN_LINK = "/data/adb/ksu/bin/ksud"

    data class SuCompatStatus(
        val suRedirectedToSh: Boolean,   // su stat == sh stat
        val ksudPresent: Boolean,        // ksud binary موجود
        val suFileExists: Boolean        // /system/bin/su موجود فعلاً
    ) {
        val isActive: Boolean
            get() = suRedirectedToSh || ksudPresent
    }

    /**
     * يشغل فحص شامل لـ sucompat.
     */
    fun scan(): SuCompatStatus {
        val suFile = File(SU_PATH)
        val shFile = File(SH_PATH)

        val suExists = suFile.exists()
        val ksudPresent = File(KSUD_PATH).exists() || File(KSUD_BIN_LINK).exists()

        // لو /system/bin/su مش موجود أصلاً → مفيش su binary
        // لو sucompat شغال → stat(su) بيرجع بيانات sh
        var suRedirectedToSh = false
        if (suExists && shFile.exists()) {
            try {
                val suLen  = suFile.length()
                val shLen  = shFile.length()
                val suLastMod = suFile.lastModified()
                val shLastMod = shFile.lastModified()

                // sucompat بيخلي stat(su) يرجع نفس stats بتاع sh
                if (suLen > 0 && suLen == shLen && suLastMod == shLastMod) {
                    Log.w(TAG, "su stats == sh stats → sucompat active (su→sh redirect)")
                    suRedirectedToSh = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "SuCompatDetector stat check failed: ${e.message}")
            }
        }

        if (ksudPresent) {
            Log.i(TAG, "ksud binary found at $KSUD_PATH → KernelSU installed")
        }

        return SuCompatStatus(
            suRedirectedToSh = suRedirectedToSh,
            ksudPresent = ksudPresent,
            suFileExists = suExists
        )
    }

    /**
     * يرجع قائمة بالـ leaks المكتشفة (format موحد مع باقي الـ detectors).
     */
    fun scanLeaks(): List<String> {
        val status = scan()
        val leaks = mutableListOf<String>()
        if (status.suRedirectedToSh) {
            leaks += "/system/bin/su → redirected to sh [sucompat active]"
        }
        if (status.ksudPresent) {
            leaks += "$KSUD_PATH [KernelSU ksud binary present]"
        }
        return leaks
    }
}
