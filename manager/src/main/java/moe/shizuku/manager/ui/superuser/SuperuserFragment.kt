package moe.shizuku.manager.ui.superuser

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import rikka.widget.borderview.BorderRecyclerView

/**
 * SuperuserFragment — واجهة Root منفصلة تماماً عن Shizuku.
 * تعرض التطبيقات اللي ليها صلاحية root وتتحكم فيها.
 * تستخدم libsu مباشرة وما بتعتمدش على Shizuku خالص.
 */
class SuperuserFragment : Fragment() {

    private lateinit var viewModel: SuperuserViewModel
    private lateinit var adapter: GrantedAppsAdapter
    private lateinit var recyclerView: BorderRecyclerView
    private lateinit var emptyView: TextView
    private lateinit var statusBadge: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_superuser_standalone, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[SuperuserViewModel::class.java]

        statusBadge = view.findViewById(R.id.tv_root_status)
        recyclerView = view.findViewById(R.id.superuser_list)
        emptyView = view.findViewById(R.id.tv_empty)

        adapter = GrantedAppsAdapter { pkg, allow ->
            viewModel.updatePolicy(requireContext(), pkg, allow)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        updateRootStatus()

        viewModel.grantedApps.observe(viewLifecycleOwner) { apps ->
            adapter.submitList(apps)
            emptyView.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (apps.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.loadGrantedApps(requireContext())
    }

    override fun onResume() {
        super.onResume()
        updateRootStatus()
        viewModel.loadGrantedApps(requireContext())
    }

    private fun updateRootStatus() {
        val isRoot = Shell.isAppGrantedRoot() == true
        statusBadge.text = if (isRoot) "● Root Active" else "● Root Not Available"
        statusBadge.setTextColor(if (isRoot) 0xFF00C853.toInt() else 0xFFFF6D00.toInt())
    }
}

// ─── ViewModel ───────────────────────────────────────────────────────────────

data class GrantedApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val allowed: Boolean
)

class SuperuserViewModel : ViewModel() {

    private val _grantedApps = androidx.lifecycle.MutableLiveData<List<GrantedApp>>()
    val grantedApps: androidx.lifecycle.LiveData<List<GrantedApp>> = _grantedApps

    fun loadGrantedApps(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val apps = mutableListOf<GrantedApp>()
            try {
                // Magisk DB: policy 2 = ALLOW, 1 = DENY
                val result = Shell.cmd(
                    "sqlite3 /data/adb/magisk.db 'SELECT package_name, policy FROM policies'"
                ).exec()
                if (result.isSuccess) {
                    result.out.forEach { line ->
                        val parts = line.split("|")
                        if (parts.size == 2) {
                            val pkg = parts[0].trim()
                            val policy = parts[1].trim().toIntOrNull() ?: 0
                            try {
                                val info = pm.getApplicationInfo(pkg, 0)
                                apps.add(
                                    GrantedApp(
                                        packageName = pkg,
                                        appName = pm.getApplicationLabel(info).toString(),
                                        icon = pm.getApplicationIcon(info),
                                        allowed = policy == 2  // Magisk ALLOW = 2
                                    )
                                )
                            } catch (_: PackageManager.NameNotFoundException) {
                                // App uninstalled — skip
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            apps.sortBy { it.appName.lowercase() }
            withContext(Dispatchers.Main) {
                _grantedApps.value = apps
            }
        }
    }

    fun updatePolicy(context: Context, packageName: String, allow: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            // Magisk DB: ALLOW=2, DENY=1
            val policy = if (allow) 2 else 1
            Shell.cmd(
                "sqlite3 /data/adb/magisk.db \"UPDATE policies SET policy=$policy WHERE package_name='$packageName'\""
            ).exec()
            loadGrantedApps(context)
        }
    }
}

// ─── RecyclerView Adapter ─────────────────────────────────────────────────────

class GrantedAppsAdapter(
    private val onToggle: (String, Boolean) -> Unit
) : RecyclerView.Adapter<GrantedAppsAdapter.VH>() {

    private var items: List<GrantedApp> = emptyList()

    fun submitList(list: List<GrantedApp>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_granted_app, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], onToggle)
    }

    override fun getItemCount() = items.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.app_icon)
        val name: TextView = itemView.findViewById(R.id.app_name)
        val pkg: TextView = itemView.findViewById(R.id.app_package)
        val toggle: MaterialSwitch = itemView.findViewById(R.id.app_toggle)

        fun bind(app: GrantedApp, onToggle: (String, Boolean) -> Unit) {
            icon.setImageDrawable(app.icon)
            name.text = app.appName
            pkg.text = app.packageName
            toggle.setOnCheckedChangeListener(null)
            toggle.isChecked = app.allowed
            toggle.setOnCheckedChangeListener { _, checked ->
                onToggle(app.packageName, checked)
            }
        }
    }
}
