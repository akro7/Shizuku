package moe.shizuku.manager.magisk

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.topjohnwu.superuser.Shell
import moe.shizuku.manager.R
import moe.shizuku.manager.databinding.ActivityMagiskInstallBinding
import moe.shizuku.manager.settings.SettingsActivity

class RootFragment : Fragment() {

    private var _binding: ActivityMagiskInstallBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MagiskInstallViewModel

    private var pendingPickCallback: ((Uri) -> Unit)? = null
    private val filePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { pendingPickCallback?.invoke(it) }
        pendingPickCallback = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = ActivityMagiskInstallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[MagiskInstallViewModel::class.java]
        setupUI()
        observeViewModel()
        updateRootBadge()
    }

    private fun updateRootBadge() {
        // Use libsu — no SELinux block
        val isRoot = Shell.isAppGrantedRoot() == true
        binding.tvRootStatusBadge?.apply {
            if (isRoot) {
                text = "● Root Active"
                setTextColor(0xFF00E5A0.toInt())
            } else {
                text = "● Root Not Available"
                setTextColor(0xFFFF6D00.toInt())
            }
        }
    }

    private fun isSuAvailable(): Boolean {
        val granted = Shell.isAppGrantedRoot() == true
        if (!granted) {
            Toast.makeText(
                requireContext(),
                "Root access not available. Grant su permission to this app.",
                Toast.LENGTH_LONG
            ).show()
        }
        return granted
    }

    private fun setupUI() {
        binding.btnRootInstall.setOnClickListener {
            if (!isSuAvailable()) return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.magisk_direct_install_title)
                .setMessage(R.string.magisk_direct_install_message)
                .setPositiveButton(R.string.install) { _, _ -> viewModel.installMagiskDirect() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        binding.btnPatchBoot.setOnClickListener {
            // Patch boot doesn't strictly need root — but magiskboot needs it
            pendingPickCallback = { uri -> viewModel.patchBootImage(uri) }
            filePicker.launch("*/*")
        }

        binding.btnFlashModule.setOnClickListener {
            if (!isSuAvailable()) return@setOnClickListener
            pendingPickCallback = { uri -> viewModel.flashModule(uri) }
            filePicker.launch("application/zip")
        }

        binding.btnRootAddons?.setOnClickListener {
            if (!isSuAvailable()) return@setOnClickListener
            startActivity(Intent(requireContext(), RootAddonsActivity::class.java))
            requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.btnUnroot?.setOnClickListener {
            if (!isSuAvailable()) return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.unroot_confirm_title)
                .setMessage(R.string.unroot_confirm_msg)
                .setPositiveButton(R.string.unroot_btn) { _, _ -> viewModel.unroot() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        binding.btnTelegram?.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Shizuku_root")))
        }

        binding.navBtnHome?.visibility = View.GONE
        binding.navBtnSettings?.visibility = View.GONE
        binding.bottomNavBar?.visibility = View.GONE
    }

    private fun observeViewModel() {
        viewModel.consoleOutput.observe(viewLifecycleOwner) { output ->
            binding.tvConsole.text = output
            if (output.isNotEmpty()) {
                binding.consoleCard.visibility = View.VISIBLE
                binding.scrollConsole.post { binding.scrollConsole.fullScroll(View.FOCUS_DOWN) }
            }
        }

        viewModel.operationState.observe(viewLifecycleOwner) { state ->
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

        viewModel.patchedFilePath.observe(viewLifecycleOwner) { path ->
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
        binding.btnRootAddons?.isEnabled = enabled
        binding.btnUnroot?.isEnabled = enabled
    }

    private fun showResultDialog(success: Boolean) {
        val title = if (success) R.string.magisk_op_success else R.string.magisk_op_failed
        val msg = if (success) R.string.magisk_op_success_msg else R.string.magisk_op_failed_msg
        MaterialAlertDialogBuilder(requireContext())
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

    override fun onResume() {
        super.onResume()
        updateRootBadge()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
