package moe.shizuku.manager.magisk

import android.app.Application
import android.net.Uri
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

data class RootAddon(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val enabled: Boolean
)

class RootAddonsViewModel(app: Application) : AndroidViewModel(app) {

    enum class OpState { IDLE, RUNNING, SUCCESS, FAILED }

    private val _addons = MutableLiveData<List<RootAddon>>(emptyList())
    val addons: LiveData<List<RootAddon>> = _addons

    private val _consoleOutput = MutableLiveData("")
    val consoleOutput: LiveData<String> = _consoleOutput

    private val _operationState = MutableLiveData(OpState.IDLE)
    val operationState: LiveData<OpState> = _operationState

    private val logBuilder = StringBuilder()

    // libsu — نفس الـ Shell المُهيأ في ShizukuApplication
    private fun shell(): Shell = Shell.getShell()

    private fun sh(vararg cmds: String): Shell.Result {
        val out = ArrayList<String>()
        val err = ArrayList<String>()
        return shell().newJob().add(*cmds).to(out, err).exec()
    }

    private fun fsh(cmd: String) = ShellUtils.fastCmd(shell(), cmd)

    private fun appendLog(line: String) {
        logBuilder.appendLine(line)
        _consoleOutput.postValue(logBuilder.toString())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Load modules — نفس منطق install_module في util_functions.sh
    // ─────────────────────────────────────────────────────────────────────────
    fun loadInstalledAddons() {
        viewModelScope.launch(Dispatchers.IO) {
            // السكريبت كسكريبت واحد متكامل (مش أسطر منفصلة)
            val result = sh("""
                for DIR in /data/adb/modules/*/; do
                    [ -f "${'$'}DIR/module.prop" ] || continue
                    ID=${'$'}(grep '^id=' "${'$'}DIR/module.prop" | cut -d= -f2)
                    NAME=${'$'}(grep '^name=' "${'$'}DIR/module.prop" | cut -d= -f2)
                    VER=${'$'}(grep '^version=' "${'$'}DIR/module.prop" | cut -d= -f2)
                    DESC=${'$'}(grep '^description=' "${'$'}DIR/module.prop" | cut -d= -f2)
                    ENABLED=${'$'}([ -f "${'$'}DIR/disable" ] && echo '0' || echo '1')
                    echo "MODULE:${'$'}ID|${'$'}NAME|${'$'}VER|${'$'}DESC|${'$'}ENABLED"
                done
            """.trimIndent())

            val addonList = result.out
                .filter { it.startsWith("MODULE:") }
                .map { line ->
                    val parts = line.removePrefix("MODULE:").split("|")
                    RootAddon(
                        id          = parts.getOrElse(0) { "unknown" },
                        name        = parts.getOrElse(1) { "Unknown Module" },
                        version     = parts.getOrElse(2) { "" },
                        description = parts.getOrElse(3) { "" },
                        enabled     = parts.getOrElse(4) { "1" } == "1"
                    )
                }
            _addons.postValue(addonList)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Install addon — مأخوذ من FlashZip.kt + module_installer.sh
    // ─────────────────────────────────────────────────────────────────────────
    fun installAddon(zipUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            logBuilder.clear()
            _operationState.postValue(OpState.RUNNING)
            appendLog("◆ Installing root add-on...")

            try {
                val app = getApplication<Application>()
                val file = File(app.cacheDir, "addon.zip")
                app.contentResolver.openInputStream(zipUri)?.use { ins ->
                    FileOutputStream(file).use { out -> ins.copyTo(out) }
                }
                appendLog("  ZIP copied (${file.length() / 1024}KB)")

                // تحقق من Magisk util_functions (مثل module_installer.sh)
                val utilOk = fsh("[ -f /data/adb/magisk/util_functions.sh ] && echo YES || echo NO").trim()
                if (utilOk != "YES") {
                    appendLog("❌ Magisk not installed — install Magisk first")
                    _operationState.postValue(OpState.FAILED)
                    return@launch
                }

                // تحقق من module.prop قبل أي شيء
                val hasProp = fsh("unzip -p '${file.absolutePath}' module.prop 2>/dev/null | grep -c '^id=' || echo 0").trim()
                if (hasProp == "0") {
                    appendLog("❌ Not a valid Magisk module — module.prop missing or invalid")
                    _operationState.postValue(OpState.FAILED)
                    return@launch
                }

                val result = sh(
                    "chmod 644 '${file.absolutePath}'",
                    // محاولة 1: magisk --install-module
                    "if magisk --install-module '${file.absolutePath}' 2>&1; then",
                    "    echo '✅ Installed via magisk --install-module'",
                    "else",
                    "    echo '⚠ Falling back to module_installer.sh'",
                    "    UTMP=/dev/tmp_addon",
                    "    rm -rf \$UTMP && mkdir -p \$UTMP",
                    // استخراج update-binary (مثل FlashZip.kt)
                    "    unzip -o '${file.absolutePath}' 'META-INF/com/google/android/update-binary' -d \$UTMP 2>&1",
                    "    UBIN=\$UTMP/META-INF/com/google/android/update-binary",
                    // fallback لـ Magisk module_installer.sh
                    "    [ -f \"\$UBIN\" ] || UBIN=/data/adb/magisk/module_installer.sh",
                    "    [ -f \"\$UBIN\" ] || { echo '❌ No installer found'; rm -rf \$UTMP; exit 1; }",
                    // تشغيل الـ installer بـ BOOTMODE=true (مثل FlashZip.kt)
                    "    BOOTMODE=true sh \"\$UBIN\" dummy 1 '${file.absolutePath}' 2>&1",
                    "    RES=\$?",
                    "    rm -rf \$UTMP",
                    "    [ \$RES -eq 0 ] && echo '✅ Manual install OK' || { echo \"❌ Installer failed (exit \$RES)\"; exit 1; }",
                    "fi"
                )
                result.out.forEach { appendLog(it) }
                result.err.forEach { if (it.isNotBlank()) appendLog("[err] $it") }
                file.delete()

                if (result.isSuccess) {
                    appendLog("✅ Add-on installed! Reboot to activate.")
                    _operationState.postValue(OpState.SUCCESS)
                    loadInstalledAddons()
                } else {
                    appendLog("❌ Install failed")
                    _operationState.postValue(OpState.FAILED)
                }
            } catch (e: Exception) {
                appendLog("❌ ${e.message}")
                _operationState.postValue(OpState.FAILED)
            }
        }
    }

    fun toggleAddon(addon: RootAddon, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = "/data/adb/modules/${addon.id}"
            if (!enabled) sh("touch '$dir/disable'") else sh("rm -f '$dir/disable'")
            loadInstalledAddons()
        }
    }

    fun deleteAddon(addon: RootAddon) {
        viewModelScope.launch(Dispatchers.IO) {
            // الطريقة الرسمية لـ Magisk: touch remove file
            sh("touch '/data/adb/modules/${addon.id}/remove'")
            loadInstalledAddons()
        }
    }

    fun removeAddon(addon: RootAddon) = deleteAddon(addon)
}
