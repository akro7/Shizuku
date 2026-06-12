package moe.shizuku.manager.home

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import moe.shizuku.manager.R
import moe.shizuku.manager.databinding.HomeItemContainerBinding
import moe.shizuku.manager.databinding.HomeRootHideBinding
import moe.shizuku.manager.hide.RootHideActivity
import moe.shizuku.manager.model.ServiceStatus
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

/**
 * RootHideViewHolder — كارت الـ Root Hide Scanner في الصفحة الرئيسية.
 * يظهر فقط لما يكون الجهاز روتد.
 */
class RootHideViewHolder(
    private val binding: HomeRootHideBinding,
    root: View
) : BaseViewHolder<ServiceStatus>(root) {

    companion object {
        val CREATOR = Creator<ServiceStatus> { inflater: LayoutInflater, parent: ViewGroup? ->
            val outer = HomeItemContainerBinding.inflate(inflater, parent, false)
            val inner = HomeRootHideBinding.inflate(inflater, outer.root, true)
            RootHideViewHolder(inner, outer.root)
        }
    }

    init {
        itemView.setOnClickListener {
            it.context.startActivity(Intent(it.context, RootHideActivity::class.java))
        }
    }

    override fun onBind() {
        val status = data ?: return
        val isRoot = status.uid == 0 && status.isRunning
        binding.tvHideStatus.text = if (isRoot)
            context.getString(R.string.hide_status_clean)
        else
            context.getString(R.string.hide_status_leaks)

        val statusColor = if (isRoot) 0xFF00C853.toInt() else 0xFFFFAB00.toInt()
        binding.tvHideStatus.setTextColor(statusColor)
    }
}
