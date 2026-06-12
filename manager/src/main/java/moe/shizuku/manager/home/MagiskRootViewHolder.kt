package moe.shizuku.manager.home

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import moe.shizuku.manager.R
import moe.shizuku.manager.databinding.HomeItemContainerBinding
import moe.shizuku.manager.databinding.HomeMagiskRootBinding
import moe.shizuku.manager.magisk.MagiskInstallActivity
import moe.shizuku.manager.model.ServiceStatus
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

/**
 * Home screen card that opens the Magisk Root functions screen.
 * Full-width card (span = 2), visible only when rooted.
 */
class MagiskRootViewHolder(
    private val binding: HomeMagiskRootBinding,
    root: View
) : BaseViewHolder<ServiceStatus>(root) {

    companion object {
        val CREATOR = Creator<ServiceStatus> { inflater: LayoutInflater, parent: ViewGroup? ->
            val outer = HomeItemContainerBinding.inflate(inflater, parent, false)
            val inner = HomeMagiskRootBinding.inflate(inflater, outer.root, true)
            MagiskRootViewHolder(inner, outer.root)
        }
    }

    init {
        itemView.setOnClickListener {
            val ctx = it.context
            ctx.startActivity(Intent(ctx, MagiskInstallActivity::class.java))
        }

        binding.btnMagiskInstall.setOnClickListener {
            val ctx = it.context
            val intent = Intent(ctx, MagiskInstallActivity::class.java).apply {
                putExtra("action", "install")
            }
            ctx.startActivity(intent)
        }

        binding.btnPatchBoot.setOnClickListener {
            val ctx = it.context
            val intent = Intent(ctx, MagiskInstallActivity::class.java).apply {
                putExtra("action", "patch")
            }
            ctx.startActivity(intent)
        }

        binding.btnFlashModule.setOnClickListener {
            val ctx = it.context
            val intent = Intent(ctx, MagiskInstallActivity::class.java).apply {
                putExtra("action", "module")
            }
            ctx.startActivity(intent)
        }
    }

    override fun onBind() {
        val status = data ?: return
        val isRoot = status.uid == 0 && status.isRunning
        binding.tvMagiskStatus.text = if (isRoot)
            context.getString(R.string.magisk_status_ready)
        else
            context.getString(R.string.magisk_status_root_required)

        val statusColor = if (isRoot) 0xFF00C853.toInt() else 0xFFFF6D00.toInt()
        binding.tvMagiskStatus.setTextColor(statusColor)

        binding.btnMagiskInstall.isEnabled = isRoot
        binding.btnPatchBoot.isEnabled = isRoot
        binding.btnFlashModule.isEnabled = isRoot
    }
}
