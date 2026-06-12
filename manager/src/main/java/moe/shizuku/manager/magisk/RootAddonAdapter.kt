package moe.shizuku.manager.magisk

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import moe.shizuku.manager.databinding.ItemRootAddonBinding

class RootAddonAdapter(
    private val onDelete: (RootAddon) -> Unit,
    private val onToggle: (RootAddon, Boolean) -> Unit
) : ListAdapter<RootAddon, RootAddonAdapter.ViewHolder>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<RootAddon>() {
            override fun areItemsTheSame(a: RootAddon, b: RootAddon) = a.id == b.id
            override fun areContentsTheSame(a: RootAddon, b: RootAddon) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRootAddonBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemRootAddonBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(addon: RootAddon) {
            binding.tvAddonName.text = addon.name
            binding.tvAddonVersion.text = addon.version
            binding.tvAddonDesc.text = addon.description
            binding.switchAddon.isChecked = addon.enabled

            binding.switchAddon.setOnCheckedChangeListener(null)
            binding.switchAddon.setOnCheckedChangeListener { _, checked ->
                onToggle(addon, checked)
            }
            binding.btnRemoveAddon.setOnClickListener {
                onDelete(addon)
            }
        }
    }
}
