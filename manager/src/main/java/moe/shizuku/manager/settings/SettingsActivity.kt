package moe.shizuku.manager.settings

import android.content.res.Resources
import android.os.Bundle
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppBarFragmentActivity

class SettingsActivity : AppBarFragmentActivity() {

    override fun onApplyUserThemeResource(theme: Resources.Theme, isDecorView: Boolean) {
        super.onApplyUserThemeResource(theme, isDecorView)
        val prefThemeId = theme.resources?.getIdentifier(
            "ThemeOverlay_Rikka_Material3_Preference", "style",
            "dev.rikka.rikkax.material"
        ) ?: 0
        if (prefThemeId != 0) theme.applyStyle(prefThemeId, true)
        theme.applyStyle(R.style.LegendaryDialog, true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, SettingsFragment())
                    .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
