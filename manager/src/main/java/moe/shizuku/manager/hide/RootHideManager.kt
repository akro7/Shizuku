package moe.shizuku.manager.hide

import android.content.Context
import android.util.Log
import moe.shizuku.manager.hide.detectors.MountLeakDetector
import moe.shizuku.manager.hide.detectors.PropsLeakDetector
import moe.shizuku.manager.hide.detectors.SelinuxLeakDetector
import moe.shizuku.manager.hide.detectors.ProcMapsDetector
import moe.shizuku.manager.hide.detectors.SuCompatDetector

/**
 * RootHideManager — مسؤول عن تنسيق جميع آليات إخفاء الـ root.
 *
 * الآليات المطبقة (userspace equivalent لـ KernelSU-Next hiding):
 * 1. Mount leak detection   → kernel_umount.c  — يكشف mounted paths المشبوهة
 * 2. SELinux enforce spoof  → selinux_hide.c   — يقارن enforce file vs status page
 * 3. Props spoof detection  → extras.c         — يفحص sys props
 * 4. /proc/maps analysis    → adb_root.c       — يكشف libadbroot.so
 * 5. SuCompat detection     → sucompat.c       — يكشف su→sh redirect وksud
 */
object RootHideManager {

    private const val TAG = "RootHideManager"

    data class HideStatus(
        val mountLeaks: List<String> = emptyList(),
        val selinuxLeaked: Boolean = false,
        val suspiciousProps: List<String> = emptyList(),
        val mapsLeaks: List<String> = emptyList(),
        val suCompatLeaks: List<String> = emptyList()
    ) {
        val isClean: Boolean
            get() = mountLeaks.isEmpty()
                    && !selinuxLeaked
                    && suspiciousProps.isEmpty()
                    && mapsLeaks.isEmpty()
                    && suCompatLeaks.isEmpty()

        val summary: String
            get() {
                if (isClean) return "✅ No leaks detected"
                val issues = mutableListOf<String>()
                if (mountLeaks.isNotEmpty())
                    issues += "🔴 Mount leaks: ${mountLeaks.size}"
                if (selinuxLeaked)
                    issues += "🔴 SELinux enforce spoofed (KSU fake_status)"
                if (suspiciousProps.isNotEmpty())
                    issues += "🟡 Suspicious props: ${suspiciousProps.size}"
                if (mapsLeaks.isNotEmpty())
                    issues += "🟡 Maps leaks: ${mapsLeaks.size}"
                if (suCompatLeaks.isNotEmpty())
                    issues += "🟡 SuCompat active: ${suCompatLeaks.size}"
                return issues.joinToString("\n")
            }
    }

    /**
     * يشغل فحص شامل لجميع آليات الإخفاء.
     * يُستدعى من background thread.
     */
    fun runFullScan(context: Context): HideStatus {
        Log.i(TAG, "Starting full hide scan…")

        val mountLeaks = try {
            MountLeakDetector.scan()
        } catch (e: Exception) {
            Log.w(TAG, "MountLeakDetector failed: ${e.message}")
            emptyList()
        }

        val selinuxLeaked = try {
            SelinuxLeakDetector.isLeaked()
        } catch (e: Exception) {
            Log.w(TAG, "SelinuxLeakDetector failed: ${e.message}")
            false
        }

        val suspiciousProps = try {
            PropsLeakDetector.scan()
        } catch (e: Exception) {
            Log.w(TAG, "PropsLeakDetector failed: ${e.message}")
            emptyList()
        }

        val mapsLeaks = try {
            ProcMapsDetector.scan()
        } catch (e: Exception) {
            Log.w(TAG, "ProcMapsDetector failed: ${e.message}")
            emptyList()
        }

        val suCompatLeaks = try {
            SuCompatDetector.scanLeaks()
        } catch (e: Exception) {
            Log.w(TAG, "SuCompatDetector failed: ${e.message}")
            emptyList()
        }

        return HideStatus(
            mountLeaks = mountLeaks,
            selinuxLeaked = selinuxLeaked,
            suspiciousProps = suspiciousProps,
            mapsLeaks = mapsLeaks,
            suCompatLeaks = suCompatLeaks
        ).also {
            Log.i(TAG, "Scan complete — clean=${it.isClean}")
        }
    }
}
