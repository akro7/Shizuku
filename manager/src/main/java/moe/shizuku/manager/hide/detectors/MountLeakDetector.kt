package moe.shizuku.manager.hide.detectors

import android.util.Log
import java.io.BufferedReader
import java.io.FileReader

/**
 * MountLeakDetector — يكشف الـ bind mounts المرتبطة بالـ root/modules.
 *
 * معادل userspace لـ kernel/feature/kernel_umount.c في KernelSU-Next.
 * بيقرأ /proc/self/mounts ويدور على أي paths مشبوهة ممكن تكشف
 * وجود KernelSU / Magisk / modules مثبّتة.
 */
object MountLeakDetector {

    private const val TAG = "MountLeakDetector"

    // ── Paths المشبوهة اللي KernelSU بيعملها mount (من kernel_umount.c) ──
    private val SUSPICIOUS_MOUNT_PREFIXES = listOf(
        "/data/adb",
        "/data/adb/ksu",
        "/data/adb/ksu/bin",       // BINARY_DIR (symlink ksud) — defs.rs DAEMON_LINK_PATH
        "/data/adb/ksu/lib",       // libadbroot.so — adb_root.c
        "/data/adb/ksud",          // KSUD_PATH الحقيقي (kernel/include/ksud.h) — sucompat execve target
        "/data/adb/magisk",
        "/data/adb/modules",
        "/data/adb/ap",            // APatch path
        "/debug_ramdisk",
        "/debug_ramdisk/ksud",     // KernelSU early ksud
        "/sbin/.magisk",
        "/sbin/.core",
        "/system/bin/su",
        "/system/xbin/su",
        "/vendor/bin/su",
        "/system/lib/libsuperuser",
        "/data/data/eu.chainfire.supersu",
        "/data/user/0/eu.chainfire.supersu",
        "/system/bin/ksud",
        // adbd path — adb_root.c بيـhook execve عليه
        "/apex/com.android.adbd"
    )

    // ── خصائص الـ mount المشبوهة (overlay على /system أو /vendor) ──
    private val SUSPICIOUS_MOUNT_TYPES = listOf(
        "overlay",
        "tmpfs"
    )

    private val SUSPICIOUS_OVERLAY_TARGETS = listOf(
        "/system",
        "/vendor",
        "/product",
        "/odm"
    )

    /**
     * يقرأ /proc/self/mounts ويرجع قائمة بالـ mount points المشبوهة.
     */
    fun scan(): List<String> {
        val leaks = mutableListOf<String>()

        try {
            BufferedReader(FileReader("/proc/self/mounts")).use { reader ->
                reader.lineSequence().forEach { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size < 4) return@forEach

                    val device = parts[0]
                    val mountPoint = parts[1]
                    val fsType = parts[2]
                    val options = parts[3]

                    // فحص الـ paths المشبوهة مباشرة
                    SUSPICIOUS_MOUNT_PREFIXES.forEach { prefix ->
                        if (mountPoint.startsWith(prefix) || device.startsWith(prefix)) {
                            Log.w(TAG, "Suspicious mount: $line")
                            leaks += mountPoint
                            return@forEach
                        }
                    }

                    // فحص الـ overlay mounts على /system و /vendor
                    // selinux_hide.c + Zygisk بيعملوا overlay على /system
                    // device ممكن يكون "overlay" أو "none" حسب الـ kernel
                    if (fsType == "overlay") {
                        SUSPICIOUS_OVERLAY_TARGETS.forEach { target ->
                            if (mountPoint == target || mountPoint.startsWith("$target/")) {
                                Log.w(TAG, "Overlay on system partition: $line")
                                leaks += "$mountPoint [overlay]"
                            }
                        }
                    }
                    // tmpfs على partitions النظام ممكن يكون Magisk/KSU hiding
                    if (fsType == "tmpfs") {
                        SUSPICIOUS_OVERLAY_TARGETS.forEach { target ->
                            if (mountPoint == target || mountPoint.startsWith("$target/")) {
                                Log.w(TAG, "tmpfs on system partition: $line")
                                leaks += "$mountPoint [tmpfs]"
                            }
                        }
                    }

                    // tmpfs على /sbin بيكون دايمًا Magisk
                    if (fsType == "tmpfs" && mountPoint == "/sbin") {
                        leaks += "/sbin [tmpfs - possible Magisk]"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read /proc/self/mounts: ${e.message}")
        }

        return leaks.distinct()
    }

    /**
     * يرجع true لو في أي mount leak.
     */
    fun hasLeaks(): Boolean = scan().isNotEmpty()
}
