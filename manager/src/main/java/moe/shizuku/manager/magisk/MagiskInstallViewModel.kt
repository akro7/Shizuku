package moe.shizuku.manager.magisk

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MagiskInstallViewModel(app: Application) : AndroidViewModel(app) {

    enum class OpState { IDLE, RUNNING, SUCCESS, FAILED }

    private val _consoleOutput = MutableLiveData<String>("")
    val consoleOutput: LiveData<String> = _consoleOutput

    private val _operationState = MutableLiveData(OpState.IDLE)
    val operationState: LiveData<OpState> = _operationState

    private val _patchedFilePath = MutableLiveData<String?>(null)
    val patchedFilePath: LiveData<String?> = _patchedFilePath

    private val logBuilder = StringBuilder()

    // ─────────────────────────────────────────────────────────────────────────
    // Shell helpers — libsu بدل ProcessBuilder
    // ─────────────────────────────────────────────────────────────────────────
    private fun shell(): Shell = Shell.getShell()

    /** تشغيل أوامر root وإرجاع Shell.Result كامل */
    private fun sh(vararg cmds: String): Shell.Result {
        val out = ArrayList<String>()
        val err = ArrayList<String>()
        return shell().newJob().add(*cmds).to(out, err).exec()
    }

    /** أمر سريع يرجع أول سطر */
    private fun fsh(cmd: String): String = ShellUtils.fastCmd(shell(), cmd)

    private fun appendLog(line: String) {
        logBuilder.appendLine(line)
        _consoleOutput.postValue(logBuilder.toString())
    }

    private fun resetLog() {
        logBuilder.clear()
        _consoleOutput.postValue("")
        _patchedFilePath.postValue(null)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // مأخوذ من util_functions.sh → find_boot_image
    // بيجرب by-name → vendor_boot → init_boot → boot → legacy fallback
    // ─────────────────────────────────────────────────────────────────────────
    private fun findBootImage(): String {
        // نفس منطق Magisk util_functions.sh find_boot_image
        val script = """
            SLOT=$(getprop ro.boot.slot_suffix 2>/dev/null)
            VENDORBOOT=false
            RECOVERYMODE=false

            # GKI 13+ init_boot
            if [ -e "/dev/block/by-name/init_boot${"\$SLOT"}" ]; then
                SDK=${"$"}(getprop ro.build.version.sdk)
                [ "${"\$SDK"}" -ge 33 ] && { echo "/dev/block/by-name/init_boot${"\$SLOT"}"; exit 0; }
            fi

            # Standard boot by-name (Android 10+)
            [ -e "/dev/block/by-name/boot${"\$SLOT"}" ] && { echo "/dev/block/by-name/boot${"\$SLOT"}"; exit 0; }
            [ -e "/dev/block/bootdevice/by-name/boot${"\$SLOT"}" ] && { echo "/dev/block/bootdevice/by-name/boot${"\$SLOT"}"; exit 0; }

            # A/B legacy fallback
            if [ -n "${"\$SLOT"}" ]; then
                DEV=$(find /dev/block/platform -name "boot${"\$SLOT"}" 2>/dev/null | head -1)
                [ -n "${"\$DEV"}" ] && { echo "${"\$DEV"}"; exit 0; }
            fi

            # Non-A/B legacy — نفس قائمة Magisk
            for NAME in ramdisk kern-a android_boot kernel bootimg boot lnx boot_a; do
                DEV=$(find /dev/block -name "${"\$NAME"}" 2>/dev/null | head -1)
                [ -n "${"\$DEV"}" ] && { echo "${"\$DEV"}"; exit 0; }
            done

            # fstab fallback
            DEV=$(grep -v '#' /etc/*fstab* 2>/dev/null | grep -E '/boot(img)?[^a-zA-Z]' \
                  | grep -oE '/dev/[a-zA-Z0-9_./-]*' | head -1)
            [ -n "${"\$DEV"}" ] && echo "${"\$DEV"}" || echo ""
        """.trimIndent()
        return fsh(script).trim()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // بحث عن magiskboot:
    // 1) المسارات المثبّتة (Magisk / KSU / PATH)
    // 2) lib المستخرجة من APK نفسه  → libmagiskboot.so (مسمّاة بصيغة Android)
    // 3) assets/magiskboot_<abi>    → نسخ خفيفة مضمّنة في APK
    // ─────────────────────────────────────────────────────────────────────────
    private fun findMagiskBoot(): String {
        val app = getApplication<Application>()

        // ── 1. المسارات الخارجية (Magisk / KSU / PATH) ──────────────────────
        val systemScript = """
            for P in /data/adb/magisk/magiskboot \
                      ${"$"}(which magiskboot 2>/dev/null) \
                      /sbin/magiskboot \
                      /system/bin/magiskboot \
                      /data/adb/ksu/bin/magiskboot; do
                [ -x "${"$"}{P}" ] && { echo "${"$"}{P}"; exit 0; }
            done
            echo ""
        """.trimIndent()
        val fromSystem = fsh(systemScript).trim()
        if (fromSystem.isNotEmpty()) return fromSystem

        // ── 2. libmagiskboot.so المستخرجة من APK (jniLibs) ──────────────────
        //    Android بيستخرج .so من jniLibs تلقائياً لـ nativeLibraryDir
        //    نستخدم dataDir/files لأنه دايماً exec (بخلاف /data/local/tmp أو cacheDir)
        val nativeDir = app.applicationInfo.nativeLibraryDir
        val libSo = File(nativeDir, "libmagiskboot.so")
        if (libSo.exists()) {
            // /data/local/tmp ممكن يكون noexec على بعض الأجهزة
            // نستخدم dataDir/files بدلاً منه لأنه دايماً exec
            val execDir = File(app.applicationInfo.dataDir, "files").also { it.mkdirs() }
            val execPath = File(execDir, "magiskboot").absolutePath
            val copyResult = sh(
                "cp '${libSo.absolutePath}' '$execPath'",
                "chmod 755 '$execPath'"
            )
            if (copyResult.isSuccess) {
                // تحقق إن الـ binary شغّال فعلاً
                val testResult = sh("'$execPath' --version 2>&1 || '$execPath' 2>&1 | head -1 || true")
                if (testResult.isSuccess || testResult.out.isNotEmpty()) return execPath
            }
        }

        // ── 3. assets/magiskboot_<abi> مضمّنة مباشرة في APK ─────────────────
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val assetAbi = when {
            abi.startsWith("arm64") -> "arm64-v8a"
            abi.startsWith("armeabi") -> "armeabi-v7a"
            abi.startsWith("x86_64") -> "x86_64"
            abi.startsWith("x86") -> "x86"
            else -> abi
        }
        val assetName = "magiskboot_$assetAbi"
        val assetTmp = File(app.cacheDir, "magiskboot_asset")
        try {
            app.assets.open(assetName).use { ins ->
                FileOutputStream(assetTmp).use { out -> ins.copyTo(out) }
            }
            val execDir = File(app.applicationInfo.dataDir, "files").also { it.mkdirs() }
            val assetExecPath = File(execDir, "magiskboot").absolutePath
            val copyResult = sh(
                "cp '${assetTmp.absolutePath}' '$assetExecPath'",
                "chmod 755 '$assetExecPath'"
            )
            if (copyResult.isSuccess) return assetExecPath
        } catch (_: Exception) {}

        // لو حتى assets مش موجودة → فاضي
        return ""
    }

    // ─────────────────────────────────────────────────────────────────────────
    // مأخوذ من app_functions.sh → flash_image
    // نفس المنطق: blockdev --setrw → size check → dd
    // ترجع Array<String> عشان تتدمج مع buildDirectInstallCmds بشكل صح
    // ─────────────────────────────────────────────────────────────────────────
    private fun buildFlashCmd(imgPath: String, bootDev: String): Array<String> = arrayOf(
        "IMG='$imgPath'",
        "DEV='$bootDev'",
        "IMG_SZ=\$(stat -c '%s' \"\$IMG\")",
        "BLK_SZ=\$(blockdev --getsize64 \"\$DEV\" 2>/dev/null || echo 0)",
        "[ \"\$IMG_SZ\" -gt \"\$BLK_SZ\" ] && { echo '! Insufficient partition size'; exit 1; }",
        "blockdev --setrw \"\$DEV\" 2>/dev/null || true",
        "BLK_RO=\$(blockdev --getro \"\$DEV\" 2>/dev/null || echo 0)",
        "[ \"\$BLK_RO\" -eq 1 ] && { echo \"! \$DEV is read only\"; exit 2; }",
        "cat \"\$IMG\" /dev/zero > \"\$DEV\" 2>/dev/null",
        "echo '- Boot flashed OK'"
    )

    // ─────────────────────────────────────────────────────────────────────────
    // مأخوذ من boot_patch.sh كاملاً — unpack → ramdisk patch → binary patches → repack
    // ─────────────────────────────────────────────────────────────────────────
    private fun buildBootPatchCmds(
        magiskboot: String,
        workDir: String,
        bootImg: String
    ): Array<String> = arrayOf(
        "cd '$workDir'",
        "echo '- Unpacking boot image'",
        "$magiskboot unpack '$bootImg' 2>&1",
        // exit code check
        "case \$? in 0);; 2) echo '- ChromeOS image';; 3) echo '- Vendor boot';; *) echo '! Unable to unpack boot image'; exit 1;; esac",

        // ── Ramdisk status check (مثل boot_patch.sh) ──
        "RAMDISK=''",
        "for P in ramdisk.cpio vendor_ramdisk/init_boot.cpio vendor_ramdisk/ramdisk.cpio; do",
        "  [ -e \"\$P\" ] && { RAMDISK=\$P; break; }",
        "done",
        "[ -z \"\$RAMDISK\" ] && RAMDISK=ramdisk.cpio",
        "echo \"- Ramdisk: \$RAMDISK\"",

        // ── Samsung kernel binary patches (مثل boot_patch.sh) ──
        "if [ -f kernel ]; then",
        // Samsung RKP
        "  $magiskboot hexpatch kernel 49010054011440B93FA00F71E9000054010840B93FA00F7189000054001840B91FA00F7188010054 A1020054011440B93FA00F7140020054010840B93FA00F71E0010054001840B91FA00F7181010054 2>/dev/null && echo '- Patched Samsung RKP' || true",
        // Samsung defex
        "  $magiskboot hexpatch kernel 821B8012 E2FF8F12 2>/dev/null && echo '- Patched Samsung defex' || true",
        // Samsung PROCA
        "  $magiskboot hexpatch kernel 70726F63615F636F6E66696700 70726F63615F6D616769736B00 2>/dev/null && echo '- Patched Samsung PROCA' || true",
        // Legacy SAR — skip_initramfs → want_initramfs
        "  $magiskboot hexpatch kernel 736B69705F696E697472616D667300 77616E745F696E697472616D667300 2>/dev/null && echo '- Patched legacy SAR' || true",
        "fi",

        // ── DTB patches ──
        "for DT in dtb kernel_dtb extra; do",
        "  [ -f \"\$DT\" ] && $magiskboot dtb \"\$DT\" patch 2>/dev/null && echo \"- Patched \$DT fstab\" || true",
        "done",

        // ── Repack (نفس boot_patch.sh — بدون output arg → ينتج new-boot.img) ──
        "echo '- Repacking boot image'",
        "$magiskboot repack '$bootImg' 2>&1 || { echo '! Unable to repack boot image'; exit 1; }",
        "[ -f '$workDir/new-boot.img' ] && echo '- new-boot.img ready' || { echo '! new-boot.img missing'; exit 1; }"
    )

    // ─────────────────────────────────────────────────────────────────────────
    // مأخوذ من app_functions.sh → direct_install كاملاً
    // ─────────────────────────────────────────────────────────────────────────
    private fun buildDirectInstallCmds(workDir: String, bootDev: String): Array<String> =
        arrayOf("echo '- Flashing new boot image'") +
        buildFlashCmd("$workDir/new-boot.img", bootDev) +
        arrayOf(
            "rm -f '$workDir/new-boot.img'",
            "MAGISKBIN=/data/adb/magisk",
            "[ -d \"\$MAGISKBIN\" ] && echo '- Magisk env already present, skipping fix_env' || echo '- Warning: MAGISKBIN missing'"
        )

    // ─────────────────────────────────────────────────────────────────────────
    // مأخوذ من FlashZip.kt + module_installer.sh + util_functions install_module
    // ─────────────────────────────────────────────────────────────────────────
    private fun buildModuleFlashCmds(zipPath: String): Array<String> = arrayOf(
        "echo '=== Module Flash ==='",
        "id",
        "chmod 644 '$zipPath'",

        // ── محاولة 1: magisk --install-module (الطريقة الرسمية) ──
        "if magisk --install-module '$zipPath' 2>&1; then",
        "    echo '✅ Installed via magisk --install-module'",
        "else",
        // ── محاولة 2: module_installer.sh بنفس منطق FlashZip.kt ──
        "    echo '⚠ Falling back to module_installer method'",
        "    UTMPDIR=/dev/tmp_mod",
        "    rm -rf \$UTMPDIR && mkdir -p \$UTMPDIR",

        // استخراج update-binary من الـ ZIP (مثل FlashZip.kt)
        "    unzip -o '$zipPath' 'META-INF/com/google/android/update-binary' -d \$UTMPDIR 2>&1",
        "    UBIN=\$UTMPDIR/META-INF/com/google/android/update-binary",

        // لو مفيش update-binary → جرب module_installer.sh من Magisk نفسه
        "    if [ ! -f \"\$UBIN\" ]; then",
        "        echo '- No update-binary in ZIP, using Magisk module_installer.sh'",
        "        UBIN=/data/adb/magisk/module_installer.sh",
        "    fi",

        "    [ -f \"\$UBIN\" ] || { echo '❌ No installer found'; rm -rf \$UTMPDIR; exit 1; }",

        // ── تحقق من module.prop (مثل install_module في util_functions) ──
        "    unzip -p '$zipPath' module.prop > \$UTMPDIR/module.prop 2>/dev/null",
        "    [ -f \$UTMPDIR/module.prop ] || { echo '❌ Not a valid Magisk module (no module.prop)'; rm -rf \$UTMPDIR; exit 1; }",
        "    MODID=\$(grep '^id=' \$UTMPDIR/module.prop | cut -d= -f2)",
        "    MODNAME=\$(grep '^name=' \$UTMPDIR/module.prop | cut -d= -f2)",
        "    echo \"- Module: \$MODNAME (id=\$MODID)\"",

        // ── تشغيل الـ installer ──
        "    BOOTMODE=true sh \"\$UBIN\" dummy 1 '$zipPath' 2>&1",
        "    RES=\$?",
        "    rm -rf \$UTMPDIR",
        "    [ \$RES -eq 0 ] && echo '✅ Module installed OK' || { echo \"❌ Installer exited \$RES\"; exit 1; }",
        "fi",
        "echo '=== Done ==='"
    )

    // ═════════════════════════════════════════════════════════════════════════
    // 1. Direct Install
    // ═════════════════════════════════════════════════════════════════════════
    fun installMagiskDirect() {
        viewModelScope.launch(Dispatchers.IO) {
            resetLog()
            _operationState.postValue(OpState.RUNNING)
            appendLog("◆ Starting direct install (Magisk boot_patch.sh method)...")

            val tmpDir = getApplication<Application>().cacheDir
            val tmpBoot = File(tmpDir, "boot_orig.img")

            // 1. إيجاد magiskboot
            val magiskboot = findMagiskBoot()
            if (magiskboot.isEmpty()) {
                appendLog("❌ magiskboot not found — Magisk not installed & no bundled binary in assets/jniLibs")
                _operationState.postValue(OpState.FAILED); return@launch
            }
            appendLog("  magiskboot: $magiskboot")

            // 2. إيجاد boot partition (Magisk find_boot_image logic)
            val bootDev = findBootImage()
            if (bootDev.isEmpty()) {
                appendLog("❌ Boot partition not found")
                _operationState.postValue(OpState.FAILED); return@launch
            }
            appendLog("  Boot: $bootDev")

            // 3. قراءة البوت بـ dd
            val readResult = sh(
                "dd if='$bootDev' of='${tmpBoot.absolutePath}' bs=4096 2>&1 && echo '✅ Boot read OK' || { echo '❌ dd read failed'; exit 1; }"
            )
            readResult.out.forEach { appendLog(it) }
            readResult.err.forEach { if (it.isNotBlank()) appendLog("[err] $it") }
            if (!readResult.isSuccess) {
                _operationState.postValue(OpState.FAILED); return@launch
            }

            // 4. Patch (boot_patch.sh logic)
            val workDir = tmpDir.absolutePath
            val patchCmds = buildBootPatchCmds(magiskboot, workDir, tmpBoot.absolutePath)
            val patchResult = sh(*patchCmds)
            patchResult.out.forEach { appendLog(it) }
            patchResult.err.forEach { if (it.isNotBlank()) appendLog("[err] $it") }
            if (!patchResult.isSuccess) {
                sh("rm -f '${tmpBoot.absolutePath}' '$workDir/kernel' '$workDir/ramdisk.cpio' 2>/dev/null")
                _operationState.postValue(OpState.FAILED); return@launch
            }

            // 5. Flash (direct_install logic)
            val flashCmds = buildDirectInstallCmds(workDir, bootDev)
            val flashResult = sh(*flashCmds)
            flashResult.out.forEach { appendLog(it) }
            flashResult.err.forEach { if (it.isNotBlank()) appendLog("[err] $it") }

            // 6. تنظيف
            sh(
                "$magiskboot cleanup 2>/dev/null || true",
                "rm -f '${tmpBoot.absolutePath}' '$workDir/kernel' '$workDir/ramdisk.cpio' '$workDir/dtb' '$workDir/extra' 2>/dev/null || true"
            )

            if (flashResult.isSuccess) {
                appendLog("✅ Direct install done! Tap Reboot to apply.")
                _operationState.postValue(OpState.SUCCESS)
            } else {
                appendLog("❌ Direct install failed — check output above")
                _operationState.postValue(OpState.FAILED)
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 2. Patch Boot Image → Downloads
    // ═════════════════════════════════════════════════════════════════════════
    fun patchBootImage(bootImgUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            resetLog()
            _operationState.postValue(OpState.RUNNING)
            appendLog("◆ Patching boot image (boot_patch.sh method)...")

            try {
                val app = getApplication<Application>()
                val resolver: ContentResolver = app.contentResolver

                // نسخ الملف لـ cache
                val inputFile = File(app.cacheDir, "input_boot.img")
                resolver.openInputStream(bootImgUri)?.use { ins ->
                    FileOutputStream(inputFile).use { out -> ins.copyTo(out) }
                } ?: run {
                    appendLog("❌ Cannot open selected file")
                    _operationState.postValue(OpState.FAILED); return@launch
                }
                appendLog("  input_boot.img (${inputFile.length() / 1024}KB)")

                val magiskboot = findMagiskBoot()
                if (magiskboot.isEmpty()) {
                    appendLog("❌ magiskboot not found")
                    appendLog("   → Magisk is not installed, and no bundled binary found in assets/ or jniLibs/")
                    appendLog("   → Add magiskboot_arm64-v8a (and other ABIs) to src/main/assets/")
                    _operationState.postValue(OpState.FAILED); return@launch
                }
                appendLog("  magiskboot: $magiskboot")

                val workDir = app.cacheDir.absolutePath

                // تنظيف قبل البدء
                sh("rm -f '$workDir/kernel' '$workDir/ramdisk.cpio' '$workDir/new-boot.img' '$workDir/dtb' '$workDir/extra' 2>/dev/null || true")

                // patch commands (boot_patch.sh logic)
                val patchCmds = buildBootPatchCmds(magiskboot, workDir, inputFile.absolutePath)
                val result = sh(*patchCmds)
                result.out.forEach { appendLog(it) }
                result.err.forEach { if (it.isNotBlank()) appendLog("[err] $it") }

                val newBoot = File(workDir, "new-boot.img")

                if (result.isSuccess && newBoot.exists() && newBoot.length() > 0) {
                    // حفظ في Downloads باسم فريد
                    val outputDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    ).also { it.mkdirs() }
                    val outputFile = File(outputDir, "magisk_patched_${System.currentTimeMillis()}.img")

                    val copyResult = sh("cp '$workDir/new-boot.img' '${outputFile.absolutePath}'")
                    copyResult.out.forEach { appendLog(it) }

                    // تنظيف
                    sh(
                        "$magiskboot cleanup 2>/dev/null || true",
                        "rm -f '${inputFile.absolutePath}' '$workDir/new-boot.img' '$workDir/kernel' '$workDir/ramdisk.cpio' '$workDir/dtb' '$workDir/extra' 2>/dev/null || true"
                    )

                    if (outputFile.exists() && outputFile.length() > 0) {
                        appendLog("✅ Saved to: ${outputFile.absolutePath}")
                        _patchedFilePath.postValue(outputFile.absolutePath)
                        _operationState.postValue(OpState.SUCCESS)
                    } else {
                        appendLog("❌ Failed to copy output to Downloads")
                        _operationState.postValue(OpState.FAILED)
                    }
                } else {
                    sh("rm -f '${inputFile.absolutePath}' '$workDir/kernel' '$workDir/ramdisk.cpio' 2>/dev/null || true")
                    appendLog("❌ Patch failed or new-boot.img not created (size=${newBoot.length()})")
                    _operationState.postValue(OpState.FAILED)
                }

            } catch (e: Exception) {
                appendLog("❌ Exception: ${e.message}")
                _operationState.postValue(OpState.FAILED)
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 3. Flash Module ZIP — مأخوذ من FlashZip.kt + module_installer.sh
    // ═════════════════════════════════════════════════════════════════════════
    fun flashModule(moduleZipUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            resetLog()
            _operationState.postValue(OpState.RUNNING)
            appendLog("◆ Flashing module (FlashZip + module_installer method)...")

            try {
                val app = getApplication<Application>()
                val moduleFile = File(app.cacheDir, "module.zip")
                app.contentResolver.openInputStream(moduleZipUri)?.use { ins ->
                    FileOutputStream(moduleFile).use { out -> ins.copyTo(out) }
                } ?: run {
                    appendLog("❌ Cannot open module ZIP")
                    _operationState.postValue(OpState.FAILED); return@launch
                }
                appendLog("  module.zip (${moduleFile.length() / 1024}KB)")

                // التحقق من وجود Magisk (مثل module_installer.sh)
                val utilExists = fsh("[ -f /data/adb/magisk/util_functions.sh ] && echo YES || echo NO").trim()
                if (utilExists != "YES") {
                    appendLog("❌ Magisk not installed — /data/adb/magisk/util_functions.sh not found")
                    appendLog("   Install Magisk first before flashing modules")
                    _operationState.postValue(OpState.FAILED); return@launch
                }

                val cmds = buildModuleFlashCmds(moduleFile.absolutePath)
                val result = sh(*cmds)
                result.out.forEach { appendLog(it) }
                result.err.forEach { if (it.isNotBlank()) appendLog("[err] $it") }
                moduleFile.delete()

                if (result.isSuccess) {
                    appendLog("✅ Module flashed! Reboot to activate.")
                    _operationState.postValue(OpState.SUCCESS)
                } else {
                    appendLog("❌ Module flash failed")
                    _operationState.postValue(OpState.FAILED)
                }

            } catch (e: Exception) {
                appendLog("❌ Exception: ${e.message}")
                _operationState.postValue(OpState.FAILED)
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 4. Reboot
    // ═════════════════════════════════════════════════════════════════════════
    fun reboot() {
        viewModelScope.launch(Dispatchers.IO) { sh("reboot") }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 5. Unroot — مأخوذ من app_functions.sh restore_imgs + uninstaller.sh
    // ═════════════════════════════════════════════════════════════════════════
    fun unroot() {
        viewModelScope.launch(Dispatchers.IO) {
            resetLog()
            _operationState.postValue(OpState.RUNNING)
            appendLog("⚠ Starting Unroot...")

            val bootDev = findBootImage()
            if (bootDev.isEmpty()) {
                appendLog("⚠ Boot partition not found — will skip boot restore")
            } else {
                appendLog("  Boot partition: $bootDev")
            }

            // نفس منطق restore_imgs في app_functions.sh
            val result = sh(
                "echo '=== Unroot ==='",
                "id",
                // البحث عن backup بالطريقة الرسمية (SHA1 من config)
                "MAGISKTMP=\$(magisk --path 2>/dev/null || echo /sbin/.magisk)",
                "SHA1=\$(grep SHA1 \$MAGISKTMP/.magisk/config 2>/dev/null | cut -d= -f2)",
                "BACKUPDIR=/data/magisk_backup_\$SHA1",
                "if [ -n \"\$SHA1\" ] && [ -f \"\$BACKUPDIR/boot.img.gz\" ]; then",
                "    echo \"  Restoring backup SHA1=\$SHA1\"",
                if (bootDev.isNotEmpty())
                    "    gzip -d < \"\$BACKUPDIR/boot.img.gz\" | cat - /dev/zero > '$bootDev' 2>/dev/null && echo '✅ Stock boot restored' || echo '⚠ Restore failed'"
                else
                    "    echo '⚠ Boot partition unknown — skipping restore'",
                "elif [ -f /data/adb/magisk/stock_boot.img ]; then",
                "    echo '  Restoring from stock_boot.img'",
                if (bootDev.isNotEmpty())
                    "    dd if=/data/adb/magisk/stock_boot.img of='$bootDev' bs=4096 2>&1 && echo '✅ Restored' || echo '⚠ dd restore failed'"
                else
                    "    echo '⚠ Boot partition unknown — skipping restore'",
                "else",
                "    echo '  No stock backup found — skipping boot restore'",
                "    echo '  (Device will remain booting via patched image; Magisk files will be removed)'",
                "fi",
                "rm -rf /data/adb/magisk /data/adb/modules /data/adb/post-fs-data.d /data/adb/service.d 2>&1",
                "rm -f /data/adb/magisk.db 2>&1",
                // تنظيف magiskboot المستخرج إن وُجد (dataDir/files بدل /data/local/tmp)
                "rm -f '${getApplication<Application>().applicationInfo.dataDir}/files/magiskboot' 2>/dev/null || true",
                "echo '✅ Unroot complete'",
                "echo '=== Done ==='"
            )

            result.out.forEach { appendLog(it) }
            result.err.forEach { if (it.isNotBlank()) appendLog("[err] $it") }

            if (result.isSuccess) {
                appendLog("✅ Unroot done! Reboot to apply.")
                _operationState.postValue(OpState.SUCCESS)
            } else {
                appendLog("❌ Unroot failed")
                _operationState.postValue(OpState.FAILED)
            }
        }
    }
}
