package moe.shizuku.manager.magisk

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.databinding.ActivityMagiskInstallBinding
import moe.shizuku.manager.settings.SettingsActivity
import com.topjohnwu.superuser.Shell

/**
 * 🌟 Shizuku Root — Root Functions Screen
 * Direct Install, Boot Patch → shizuku_boot.img, Module Flash, Add-ons
 */
class MagiskInstallActivity : AppBarActivity() {

    private lateinit var binding: ActivityMagiskInstallBinding
    private lateinit var viewModel: MagiskInstallViewModel

    private var pendingPickCallback: ((Uri) -> Unit)? = null
    private val filePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { pendingPickCallback?.invoke(it) }
        pendingPickCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMagiskInstallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MagiskInstallViewModel::class.java]

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.magisk_root_title)

        setupUI()
        observeViewModel()
        updateRootBadge()
    }

    private fun updateRootBadge() {
        binding.tvRootStatusBadge?.apply {
            text = "● Checking root..."
            setTextColor(0xFFAAAAAA.toInt())
        }
        Shell.getShell(Shell.GetShellCallback { shell ->
            runOnUiThread {
                val isRoot = shell.isRoot
                binding.tvRootStatusBadge?.apply {
                    if (isRoot) {
                        text = "● Root Active"
                        setTextColor(0xFF00E5A0.toInt())
                    } else {
                        text = "● No Root"
                        setTextColor(0xFFFF6D00.toInt())
                    }
                }
            }
        })
    }

    private fun setupUI() {
        // ── 1. Root Install ──
        binding.btnRootInstall.setOnClickListener {
            if (!isRootAvailable()) return@setOnClickListener
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.magisk_direct_install_title)
                .setMessage(R.string.magisk_direct_install_message)
                .setPositiveButton(R.string.install) { _, _ ->
                    viewModel.installMagiskDirect()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        // ── 2. Boot Patch → shizuku_boot.img ──
        binding.btnPatchBoot.setOnClickListener {
            if (!isRootAvailable()) return@setOnClickListener
            pendingPickCallback = { uri -> viewModel.patchBootImage(uri) }
            filePicker.launch("*/*")
        }

        // ── 3. Module Flash ──
        binding.btnFlashModule.setOnClickListener {
            if (!isRootAvailable()) return@setOnClickListener
            pendingPickCallback = { uri -> viewModel.flashModule(uri) }
            filePicker.launch("application/zip")
        }

        // ── 4. Root Add-ons ──
        binding.btnRootAddons.setOnClickListener {
            if (!isRootAvailable()) return@setOnClickListener
            startActivity(Intent(this, RootAddonsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }


        // ── Unroot ──
        binding.btnUnroot?.setOnClickListener {
            if (!isRootAvailable()) return@setOnClickListener
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.unroot_confirm_title)
                .setMessage(R.string.unroot_confirm_msg)
                .setPositiveButton(R.string.unroot_btn) { _, _ ->
                    viewModel.unroot()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        // ── Telegram ──
        binding.btnTelegram?.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Shizuku_root")))
        }

        // ── Nav Home ──
        binding.navBtnHome?.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        // ── Nav Settings ──
        binding.navBtnSettings?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun observeViewModel() {
        viewModel.consoleOutput.observe(this) { output ->
            binding.tvConsole.text = output
            if (output.isNotEmpty()) {
                binding.consoleCard.visibility = View.VISIBLE
                binding.scrollConsole.post {
                    binding.scrollConsole.fullScroll(View.FOCUS_DOWN)
                }
            }
        }

        viewModel.operationState.observe(this) { state ->
            when (state) {
                MagiskInstallViewModel.OpState.IDLE -> {
                    binding.progressBar.visibility = View.GONE
                    setButtonsEnabled(true)
                }
                MagiskInstallViewModel.OpState.RUNNING -> {
                    binding.progressBar.visibility = View.VISIBLE
                    setButtonsEnabled(false)
                }
                MagiskInstallViewModel.OpState.SUCCESS -> {
                    binding.progressBar.visibility = View.GONE
                    setButtonsEnabled(true)
                    showResultDialog(true)
                }
                MagiskInstallViewModel.OpState.FAILED -> {
                    binding.progressBar.visibility = View.GONE
                    setButtonsEnabled(true)
                    showResultDialog(false)
                }
            }
        }

        viewModel.patchedFilePath.observe(this) { path ->
            if (path != null) {
                binding.tvPatchedPath.visibility = View.VISIBLE
                binding.tvPatchedPath.text = getString(R.string.magisk_patched_saved, path)
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.btnRootInstall.isEnabled = enabled
        binding.btnPatchBoot.isEnabled = enabled
        binding.btnFlashModule.isEnabled = enabled
        binding.btnRootAddons.isEnabled = enabled
        binding.btnUnroot?.isEnabled = enabled
    }

    private fun showResultDialog(success: Boolean) {
        val title = if (success) R.string.magisk_op_success else R.string.magisk_op_failed
        val msg = if (success) R.string.magisk_op_success_msg else R.string.magisk_op_failed_msg
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton(android.R.string.ok, null)
            .apply {
                if (success) {
                    setNeutralButton(R.string.reboot) { _, _ -> viewModel.reboot() }
                }
            }
            .show()
    }

    private fun isRootAvailable(): Boolean {
        // root check via libsu — مستقل تماماً عن Shizuku binder/service
        val granted = Shell.isAppGrantedRoot()
        if (granted == false) {
            Toast.makeText(this, R.string.magisk_requires_root, Toast.LENGTH_SHORT).show()
            return false
        }
        // granted == null means su request hasn't completed yet; let the
        // first shell command in the ViewModel trigger the su prompt itself.
        return true
    }

    override fun onResume() {
        super.onResume()
        updateRootBadge()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        return true
    }
}
