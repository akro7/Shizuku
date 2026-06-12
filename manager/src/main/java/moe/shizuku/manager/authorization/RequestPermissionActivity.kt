package moe.shizuku.manager.authorization

import android.app.Dialog
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.databinding.DialogGrantPermissionBinding
import moe.shizuku.manager.ktx.toHtml
import moe.shizuku.manager.utils.Logger.LOGGER
import rikka.core.res.resolveColor
import rikka.html.text.HtmlCompat
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED
import rikka.shizuku.ShizukuApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME
import rikka.shizuku.server.ktx.workerHandler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Permission grant dialog shown when an app requests Shizuku access.
 *
 * Two grant options:
 *   • Shizuku       — standard ADB-level access (FLAG_ALLOWED, no root)
 *   • Shizuku Root  — same flag + runs `su --grant <uid>` via Magisk shell
 *                     so the app gets real uid-0 root on top of Shizuku
 */
class RequestPermissionActivity : AppActivity() {

    /** Extra key injected into the reply Bundle so ShizukuService can act on it */
    companion object {
        const val REPLY_ROOT_GRANT = "root_grant"
    }

    private lateinit var dialog: Dialog

    // ── send reply to Shizuku server ──────────────────────────────────────
    private fun setResult(
        requestUid: Int,
        requestPid: Int,
        requestCode: Int,
        allowed: Boolean,
        onetime: Boolean,
        rootGrant: Boolean = false
    ) {
        val data = Bundle().apply {
            putBoolean(REQUEST_PERMISSION_REPLY_ALLOWED, allowed)
            putBoolean(REQUEST_PERMISSION_REPLY_IS_ONETIME, onetime)
            putBoolean(REPLY_ROOT_GRANT, rootGrant)
        }
        try {
            Shizuku.dispatchPermissionConfirmationResult(requestUid, requestPid, requestCode, data)
        } catch (e: Throwable) {
            LOGGER.e("dispatchPermissionConfirmationResult")
        }
    }

    // ── grant real root via Magisk su ─────────────────────────────────────
    private fun grantRootToUid(uid: Int) {
        try {
            // Magisk su supports: su --grant <uid>  (non-interactive policy set)
            Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"INSERT OR REPLACE INTO policies (uid,policy,until,logging,notification) VALUES($uid,2,0,1,1)\""))
                .waitFor()
        } catch (e: Exception) {
            LOGGER.e(e, "grantRootToUid failed for uid=$uid")
        }
    }

    // ── wait for Shizuku binder ───────────────────────────────────────────
    private fun waitForBinder(): Boolean {
        val latch = CountDownLatch(1)
        val listener = object : Shizuku.OnBinderReceivedListener {
            override fun onBinderReceived() {
                latch.countDown()
                Shizuku.removeBinderReceivedListener(this)
            }
        }
        Shizuku.addBinderReceivedListenerSticky(listener, workerHandler)
        return try {
            latch.await(5, TimeUnit.SECONDS)
            true
        } catch (e: TimeoutException) {
            LOGGER.e(e, "Binder not received in 5s")
            false
        }
    }

    private fun checkSelfPermission(): Boolean {
        val permission = Shizuku.checkRemotePermission("android.permission.GRANT_RUNTIME_PERMISSIONS") == PackageManager.PERMISSION_GRANTED
        if (permission) return true

        val icon = getDrawable(R.drawable.ic_system_icon)
        icon?.setTint(theme.resolveColor(android.R.attr.colorAccent))

        val dialog = MaterialAlertDialogBuilder(this)
            .setIcon(icon)
            .setTitle("Shizuku: ${getString(R.string.app_management_dialog_adb_is_limited_title)}")
            .setMessage(
                getString(R.string.app_management_dialog_adb_is_limited_message, Helps.ADB.get())
                    .toHtml(HtmlCompat.FROM_HTML_OPTION_TRIM_WHITESPACE)
            )
            .setPositiveButton(android.R.string.ok, null)
            .setOnDismissListener { finish() }
            .create()
        dialog.setOnShowListener {
            (it as AlertDialog).findViewById<TextView>(android.R.id.message)?.movementMethod =
                LinkMovementMethod.getInstance()
        }
        runCatching { dialog.show() }
        return false
    }

    // ── activity entry point ──────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!waitForBinder()) { finish(); return }

        val uid         = intent.getIntExtra("uid", -1)
        val pid         = intent.getIntExtra("pid", -1)
        val requestCode = intent.getIntExtra("requestCode", -1)
        val ai          = intent.getParcelableExtra<ApplicationInfo>("applicationInfo")

        if (uid == -1 || pid == -1 || ai == null) { finish(); return }
        if (!checkSelfPermission()) {
            setResult(uid, pid, requestCode, allowed = false, onetime = true)
            return
        }

        val label = runCatching { ai.loadLabel(packageManager) }.getOrElse { ai.packageName }
        val icon  = runCatching { ai.loadIcon(packageManager)  }.getOrNull()

        // ── inflate dual-grant layout ─────────────────────────────────────
        val binding = DialogGrantPermissionBinding.inflate(layoutInflater)

        icon?.let { binding.ivAppIcon.setImageDrawable(it) }

        binding.tvGrantTitle.text = HtmlCompat.fromHtml(
            getString(R.string.grant_shizuku_title, label),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )

        // Button: Shizuku (standard)
        binding.btnGrantShizuku.setOnClickListener {
            setResult(uid, pid, requestCode, allowed = true, onetime = false, rootGrant = false)
            dialog.dismiss()
        }

        // Button: Shizuku Root
        binding.btnGrantShizukuRoot.setOnClickListener {
            // 1. Grant Shizuku permission normally
            setResult(uid, pid, requestCode, allowed = true, onetime = false, rootGrant = true)
            // 2. Also grant Magisk root to the app uid
            grantRootToUid(uid)
            dialog.dismiss()
        }

        // Button: Deny
        binding.btnGrantDeny.setOnClickListener {
            setResult(uid, pid, requestCode, allowed = false, onetime = true, rootGrant = false)
            dialog.dismiss()
        }

        dialog = MaterialAlertDialogBuilder(this)
            .setView(binding.root)
            .setCancelable(false)
            .setOnDismissListener { finish() }
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }
}
