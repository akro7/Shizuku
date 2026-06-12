package moe.shizuku.manager.home

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.databinding.AboutDialogBinding
import moe.shizuku.manager.databinding.HomeActivityBinding
import moe.shizuku.manager.developer.DeveloperInfoActivity
import moe.shizuku.manager.ktx.toHtml
import moe.shizuku.manager.magisk.RootFragment
import moe.shizuku.manager.settings.SettingsActivity
import moe.shizuku.manager.ui.shizuku.ShizukuFragment
import moe.shizuku.manager.ui.superuser.SuperuserFragment
import moe.shizuku.manager.update.UpdateDialogFragment
import moe.shizuku.manager.update.UpdateResult
import moe.shizuku.manager.update.UpdateViewModel
import moe.shizuku.manager.utils.AppIconCache
import rikka.lifecycle.Status
import rikka.shizuku.Shizuku

abstract class HomeActivity : AppBarActivity() {

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { }

    private lateinit var binding: HomeActivityBinding
    private lateinit var updateViewModel: UpdateViewModel
    private var updateMenuItem: MenuItem? = null

    private var currentTab = TAB_SHIZUKU

    companion object {
        const val TAB_SHIZUKU   = 0
        const val TAB_SUPERUSER = 1

        private const val TAG_SHIZUKU   = "shizuku"
        private const val TAG_SUPERUSER = "superuser"
        private const val TAG_ROOT      = "root"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = HomeActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateViewModel = ViewModelProvider(this)[UpdateViewModel::class.java]

        if (savedInstanceState == null) {
            showTab(TAB_SHIZUKU)
        } else {
            currentTab = savedInstanceState.getInt("currentTab", TAB_SHIZUKU)
            updateNavHighlight(currentTab)
            // Restore fragment
            if (supportFragmentManager.findFragmentByTag(tagForTab(currentTab)) == null) {
                showTab(currentTab)
            }
        }

        updateViewModel.checking.observe(this) { checking ->
            updateMenuItem?.isEnabled = !checking
            updateMenuItem?.title = if (checking)
                getString(R.string.checking_update)
            else
                getString(R.string.check_update)
        }

        updateViewModel.result.observe(this) { result ->
            when (result) {
                is UpdateResult.UpdateAvailable -> {
                    if (supportFragmentManager.findFragmentByTag(UpdateDialogFragment.TAG) == null) {
                        UpdateDialogFragment.newInstance(result.info)
                            .show(supportFragmentManager, UpdateDialogFragment.TAG)
                    }
                }
                is UpdateResult.UpToDate ->
                    Toast.makeText(this, R.string.already_up_to_date, Toast.LENGTH_SHORT).show()
                is UpdateResult.Error ->
                    Toast.makeText(this,
                        getString(R.string.update_check_failed, result.message),
                        Toast.LENGTH_SHORT).show()
            }
        }

        // ── Nav buttons ──────────────────────────────────────────────────────
        binding.navBtnHome?.setOnClickListener {
            if (currentTab == TAB_SHIZUKU) {
                (supportFragmentManager.findFragmentByTag(TAG_SHIZUKU) as? ShizukuFragment)
                    ?.scrollToTop()
            } else {
                showTab(TAB_SHIZUKU)
            }
        }

        // "Root" button → Superuser grants tab
        binding.navBtnRoot?.setOnClickListener {
            showTab(TAB_SUPERUSER)
        }

        // Settings button → SettingsActivity
        binding.navBtnSettings?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("currentTab", currentTab)
    }

    private fun tagForTab(tab: Int) = when (tab) {
        TAB_SUPERUSER -> TAG_SUPERUSER
        else          -> TAG_SHIZUKU
    }

    private fun showTab(tab: Int) {
        currentTab = tab
        updateNavHighlight(tab)

        val (fragment, tag) = when (tab) {
            TAB_SUPERUSER -> SuperuserFragment() to TAG_SUPERUSER
            else          -> ShizukuFragment()   to TAG_SHIZUKU
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, tag)
            .commit()

        title = when (tab) {
            TAB_SUPERUSER -> getString(R.string.superuser)
            else          -> getString(R.string.app_name)
        }
    }

    private fun updateNavHighlight(tab: Int) {
        val activeColor   = 0xFFFFFFFF.toInt()
        val inactiveColor = resources.getColor(R.color.leg_text_secondary, theme)
        val activeBg      = resources.getDrawable(R.drawable.bg_nav_active, theme)
        val inactiveBg    = resources.getDrawable(R.drawable.bg_nav_inactive, theme)

        binding.navBtnHome?.apply {
            background = if (tab == TAB_SHIZUKU) activeBg else inactiveBg
            setTextColor(if (tab == TAB_SHIZUKU) activeColor else inactiveColor)
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(
                if (tab == TAB_SHIZUKU) activeColor else inactiveColor
            )
        }
        binding.navBtnRoot?.apply {
            background = if (tab == TAB_SUPERUSER) activeBg else inactiveBg
            setTextColor(if (tab == TAB_SUPERUSER) activeColor else inactiveColor)
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(
                if (tab == TAB_SUPERUSER) activeColor else inactiveColor
            )
        }
        // Settings button always inactive (it opens a new Activity)
        binding.navBtnSettings?.apply {
            background = inactiveBg
            setTextColor(inactiveColor)
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(inactiveColor)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        updateMenuItem = menu.findItem(R.id.action_check_update)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_check_update -> {
                updateViewModel.checkForUpdate()
                true
            }
            R.id.action_developer_info -> {
                startActivity(Intent(this, DeveloperInfoActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                true
            }
            R.id.action_about -> {
                val dlgBinding = AboutDialogBinding.inflate(LayoutInflater.from(this), null, false)
                dlgBinding.sourceCode.movementMethod = LinkMovementMethod.getInstance()
                dlgBinding.sourceCode.text = getString(
                    R.string.about_view_source_code,
                    "<b><a href=\"https://github.com/akro7/Shizuku\">GitHub</a></b>"
                ).toHtml()
                dlgBinding.icon.setImageBitmap(
                    AppIconCache.getOrLoadBitmap(
                        this, applicationInfo, Process.myUid() / 100000,
                        resources.getDimensionPixelOffset(R.dimen.default_app_icon_size)
                    )
                )
                dlgBinding.versionName.text = packageManager.getPackageInfo(packageName, 0).versionName
                MaterialAlertDialogBuilder(this).setView(dlgBinding.root).show()
                true
            }
            R.id.action_stop -> {
                if (!Shizuku.pingBinder()) return true
                MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.dialog_stop_message)
                    .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                        try { Shizuku.exit() } catch (e: Throwable) {}
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
