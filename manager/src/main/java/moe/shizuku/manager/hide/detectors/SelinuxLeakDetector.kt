package moe.shizuku.manager.hide.detectors

import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * SelinuxLeakDetector — يكشف تسريبات الـ SELinux context اللي تكشف الـ root.
 *
 * معادل userspace لـ kernel/feature/selinux_hide.c في KernelSU-Next.
 * بيفحص:
 * 1. /proc/self/attr/current → الـ context الحالي للعملية
 * 2. /sys/fs/selinux/enforce → هل SELinux enforced؟
 * 3. Context strings المشبوهة (u:r:su:s0 وغيرها)
 */
object SelinuxLeakDetector {

    private const val TAG = "SelinuxLeakDetector"

    // ── Contexts المشبوهة اللي KernelSU وMagisk بيشتغلوا بيها ──
    // هي نفس اللي selinux_hide.c بيحاول يخبيها
    private val SUSPICIOUS_CONTEXTS = listOf(
        "u:r:su:s0",
        "u:r:magisk:s0",
        "u:r:kernelsu:s0",
        "u:r:init:s0",
        "u:r:kernel:s0",
        "u:r:magisk_client:s0",
    )

    // ── Files اللي من خلالها بنفحص الـ SELinux state ──
    private val SELINUX_STATUS_FILE = "/sys/fs/selinux/enforce"
    private val PROC_ATTR_FILE = "/proc/self/attr/current"

    /**
     * يرجع true لو في تسريب في الـ SELinux context.
     * - العملية شغالة بـ su/magisk/kernelsu context
     * - أو الـ SELinux مـdisabled بطريقة غريبة
     */
    fun isLeaked(): Boolean {
        return isCurrentContextLeaked() || isEnforceStatusSuspicious()
    }

    /**
     * يقرأ الـ context الحالي من /proc/self/attr/current
     */
    fun getCurrentContext(): String? {
        return try {
            File(PROC_ATTR_FILE).readText().trim().trimEnd('\u0000')
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read current context: ${e.message}")
            null
        }
    }

    /**
     * يفحص لو الـ context الحالي مشبوه.
     * لو العملية شغالة بـ u:r:su:s0 مثلًا ده يكشف الـ root.
     */
    private fun isCurrentContextLeaked(): Boolean {
        val context = getCurrentContext() ?: return false
        SUSPICIOUS_CONTEXTS.forEach { suspicious ->
            if (context.contains(suspicious)) {
                Log.w(TAG, "Suspicious SELinux context: $context")
                return true
            }
        }
        return false
    }

    /**
     * يفحص حالة الـ enforce.
     *
     * selinux_hide.c (KernelSU-Next) بيعمل fake_status page عبر
     * sel_open_handle_status → my_sel_open_handle_status
     * ده بيخلي apps تشوف enforce=1 حتى لو السيستم permissive.
     *
     * طريقة الكشف:
     * نقارن enforce من /sys/fs/selinux/enforce (direct read)
     * مع enforce من /sys/fs/selinux/status (mmap'd — ده اللي KSU بيـspoof)
     * لو مختلفين → في spoofing شغال
     */
    private fun isEnforceStatusSuspicious(): Boolean {
        return try {
            // enforce الحقيقي من الـ file
            val realEnforce = File(SELINUX_STATUS_FILE)
                .readText().trim().toIntOrNull() ?: return false

            // بنية struct selinux_kernel_status (security/selinux/include/security.h):
            //   u32 version;       // offset 0
            //   u32 sequence;      // offset 4
            //   u32 enforcing;     // offset 8   ← ده اللي KSU بيـspoof في fake_status
            //   u32 policyload;    // offset 12
            //   u32 deny_unknown;  // offset 16
            // /sys/fs/selinux/status هو اللي KSU بيـspoof بالـ fake_status page
            val statusFile = File("/sys/fs/selinux/status")
            if (!statusFile.exists()) return false

            val bytes = statusFile.readBytes()
            if (bytes.size < 16) return false

            // enforcing field في offset 8 (4 bytes, little-endian)
            val statusEnforce = (bytes[8].toInt() and 0xFF) or
                    ((bytes[9].toInt() and 0xFF) shl 8) or
                    ((bytes[10].toInt() and 0xFF) shl 16) or
                    ((bytes[11].toInt() and 0xFF) shl 24)

            if (realEnforce != statusEnforce) {
                Log.w(TAG, "SELinux enforce mismatch! enforce=$realEnforce status.enforcing=$statusEnforce → KSU fake_status detected")
                return true
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "isEnforceStatusSuspicious failed: ${e.message}")
            false
        }
    }

    /**
     * يرجع الـ SELinux enforce status.
     * 1 = enforcing, 0 = permissive
     */
    fun getEnforceStatus(): Int {
        return try {
            File(SELINUX_STATUS_FILE).readText().trim().toIntOrNull() ?: -1
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * يفحص contexts لعمليات معينة في /proc/[pid]/attr/current
     * مفيد لفحص الـ zygote و system_server
     */
    fun getProcessContext(pid: Int): String? {
        return try {
            File("/proc/$pid/attr/current").readText().trim().trimEnd('\u0000')
        } catch (e: Exception) {
            null
        }
    }

    /**
     * يفحص لو في عمليات شغالة بـ su context في الـ /proc
     * هذا ما يحاول selinux_hide.c إخفاؤه من التطبيقات
     */
    fun findSuContextProcesses(): List<Pair<Int, String>> {
        val result = mutableListOf<Pair<Int, String>>()
        try {
            File("/proc").listFiles()?.forEach { pidDir ->
                val pid = pidDir.name.toIntOrNull() ?: return@forEach
                val context = getProcessContext(pid) ?: return@forEach
                if (SUSPICIOUS_CONTEXTS.any { context.contains(it) }) {
                    result += pid to context
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "findSuContextProcesses failed: ${e.message}")
        }
        return result
    }
}
