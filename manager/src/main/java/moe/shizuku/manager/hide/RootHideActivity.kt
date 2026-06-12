package moe.shizuku.manager.hide

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import moe.shizuku.manager.R
import moe.shizuku.manager.databinding.ActivityRootHideBinding

/**
 * RootHideActivity — شاشة فحص الإخفاء.
 * بتعرض نتائج الفحص الشامل لآليات الـ root hiding.
 */
class RootHideActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRootHideBinding
    private val viewModel: RootHideViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRootHideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeViewModel()

        // ابدأ الفحص تلقائيًا
        viewModel.runScan(this)
    }

    private fun setupUI() {
        binding.btnBack?.setOnClickListener { finish() }

        binding.btnRescan.setOnClickListener {
            viewModel.runScan(this)
        }
    }

    private fun observeViewModel() {
        viewModel.scanState.observe(this) { state ->
            when (state) {
                RootHideViewModel.ScanState.SCANNING -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnRescan.isEnabled = false
                    binding.tvStatusSummary.text = getString(R.string.hide_scanning)
                }
                RootHideViewModel.ScanState.DONE, RootHideViewModel.ScanState.ERROR -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnRescan.isEnabled = true
                }
                else -> {}
            }
        }

        viewModel.hideStatus.observe(this) { status ->
            status ?: return@observe
            binding.tvStatusSummary.text = status.summary

            // الألوان بناءً على النتيجة
            val summaryColor = if (status.isClean) 0xFF00C853.toInt() else 0xFFFF3D00.toInt()
            binding.tvStatusSummary.setTextColor(summaryColor)

            // Mount leaks
            if (status.mountLeaks.isEmpty()) {
                binding.tvMountResult.text = getString(R.string.hide_no_leaks)
                binding.tvMountResult.setTextColor(0xFF00C853.toInt())
            } else {
                binding.tvMountResult.text = status.mountLeaks.joinToString("\n")
                binding.tvMountResult.setTextColor(0xFFFF3D00.toInt())
            }

            // SELinux
            if (!status.selinuxLeaked) {
                binding.tvSelinuxResult.text = getString(R.string.hide_no_leaks)
                binding.tvSelinuxResult.setTextColor(0xFF00C853.toInt())
            } else {
                binding.tvSelinuxResult.text = getString(R.string.hide_selinux_leaked)
                binding.tvSelinuxResult.setTextColor(0xFFFF3D00.toInt())
            }

            // Props
            if (status.suspiciousProps.isEmpty()) {
                binding.tvPropsResult.text = getString(R.string.hide_no_leaks)
                binding.tvPropsResult.setTextColor(0xFF00C853.toInt())
            } else {
                binding.tvPropsResult.text = status.suspiciousProps.joinToString("\n")
                binding.tvPropsResult.setTextColor(0xFFFFAB00.toInt())
            }

            // /proc/maps
            if (status.mapsLeaks.isEmpty()) {
                binding.tvMapsResult.text = getString(R.string.hide_no_leaks)
                binding.tvMapsResult.setTextColor(0xFF00C853.toInt())
            } else {
                binding.tvMapsResult.text = status.mapsLeaks.joinToString("\n")
                binding.tvMapsResult.setTextColor(0xFFFFAB00.toInt())
            }

            // SuCompat (sucompat.c)
            binding.tvSuCompatResult?.let { tv ->
                if (status.suCompatLeaks.isEmpty()) {
                    tv.text = getString(R.string.hide_no_leaks)
                    tv.setTextColor(0xFF00C853.toInt())
                } else {
                    tv.text = status.suCompatLeaks.joinToString("\n")
                    tv.setTextColor(0xFFFFAB00.toInt())
                }
            }
        }

        viewModel.errorMsg.observe(this) { msg ->
            if (msg != null) {
                binding.tvStatusSummary.text = "❌ Error: $msg"
                binding.tvStatusSummary.setTextColor(0xFFFF3D00.toInt())
            }
        }
    }
}
