package moe.shizuku.manager.magisk

import com.topjohnwu.superuser.Shell

/**
 * libsu Initializer — يضبط BOOTMODE=true للـ Shell session.
 *
 * ملاحظة: util_functions.sh و app_functions.sh مش محتاجين يتحملوا هنا
 * لأن MagiskInstallViewModel بتبني الـ logic نفسها جوها مباشرة.
 * لو عايز تستخدم shell functions زي find_boot_image() مباشرة،
 * اعمل source هنا وبعدين استدعيها من sh() في الـ ViewModels.
 */
class RootShellInit : Shell.Initializer() {
    override fun onInit(context: android.content.Context, shell: Shell): Boolean {
        Shell.cmd(
            "export BOOTMODE=true",
            // uncomment لو هتستخدم Magisk shell functions مباشرة:
            // "[ -f /data/adb/magisk/util_functions.sh ] && . /data/adb/magisk/util_functions.sh 2>/dev/null || true",
            // "[ -f /data/adb/magisk/app_functions.sh ]  && . /data/adb/magisk/app_functions.sh  2>/dev/null || true",
        ).exec()
        return true
    }
}
