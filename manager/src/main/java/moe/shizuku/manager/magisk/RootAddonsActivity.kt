package moe.shizuku.manager.magisk

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.topjohnwu.superuser.Shell
import moe.shizuku.manager.R
import moe.shizuku.manager.databinding.ActivityRootAddonsBinding

/**
 * Root Modules — standalone screen (Magisk-style)
 * Shows installed modules with enable/disable/delete controls.
 */
class RootAddonsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRootAddonsBinding
    private lateinit var viewModel: RootAddonsViewModel

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.installAddon(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRootAddonsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[RootAddonsViewModel::class.java]

        setupUI()
        observeViewModel()
        viewModel.loadInstalledAddons()
    }

    private fun setupUI() {
        binding.btnBack?.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        binding.btnInstallAddon.setOnClickListener {
            if (!isRootAvailable()) return@setOnClickListener
            filePicker.launch("application/zip")
        }

        binding.btnRefresh.setOnClickListener {
            viewModel.loadInstalledAddons()
        }

        binding.navBtnHome?.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        val adapter = RootAddonAdapter(
            onDelete = { addon ->
                if (isRootAvailable()) viewModel.deleteAddon(addon)
            },
            onToggle = { addon, enabled ->
                if (isRootAvailable()) viewModel.toggleAddon(addon, enabled)
            }
        )
        binding.recyclerAddons.adapter = adapter
        binding.recyclerAddons.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(this)
    }

    private fun observeViewModel() {
        viewModel.addons.observe(this) { list ->
            if (list.isEmpty()) {
                binding.tvEmptyAddons.visibility = View.VISIBLE
                binding.recyclerAddons.visibility = View.GONE
            } else {
                binding.tvEmptyAddons.visibility = View.GONE
                binding.recyclerAddons.visibility = View.VISIBLE
                (binding.recyclerAddons.adapter as? RootAddonAdapter)?.submitList(list)
            }
        }

        viewModel.consoleOutput.observe(this) { output ->
            if (output.isNotEmpty()) {
                binding.tvConsoleAddons.text = output
                binding.consoleCardAddons.visibility = View.VISIBLE
            }
        }

        viewModel.operationState.observe(this) { state ->
            binding.progressBarAddons.visibility =
                if (state == RootAddonsViewModel.OpState.RUNNING) View.VISIBLE else View.GONE
            binding.btnInstallAddon.isEnabled =
                state != RootAddonsViewModel.OpState.RUNNING
        }
    }

    /**
     * Root check via libsu — consistent with MagiskInstallActivity
     * Shell.isAppGrantedRoot() == null means "not yet asked" → let it proceed (su will prompt)
     * Shell.isAppGrantedRoot() == false means explicitly denied
     */
    private fun isRootAvailable(): Boolean {
        val granted = Shell.isAppGrantedRoot()
        if (granted == false) {
            Toast.makeText(this, R.string.magisk_requires_root, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
