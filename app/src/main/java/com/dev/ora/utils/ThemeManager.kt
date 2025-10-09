/*
 *
 * ████████╗██╗  ██╗███████╗███╗   ███╗███████╗    ███╗   ███╗ █████╗ ███╗   ██╗ █████╗  ██████╗ ███████╗██████╗
 * ╚══██╔══╝██║  ██║██╔════╝████╗ ████║██╔════╝    ████╗ ████║██╔══██╗████╗  ██║██╔══██╗██╔════╝ ██╔════╝██╔══██╗
 *    ██║   ███████║█████╗  ██╔████╔██║█████╗      ██╔████╔██║███████║██╔██╗ ██║███████║██║  ███╗█████╗  ██████╔╝
 *    ██║   ██╔══██║██╔══╝  ██║╚██╔╝██║██╔══╝      ██║╚██╔╝██║██╔══██║██║╚██╗██║██╔══██║██║   ██║██╔══╝  ██╔══██╗
 *    ██║   ██║  ██║███████╗██║ ╚═╝ ██║███████╗    ██║ ╚═╝ ██║██║  ██║██║ ╚████║██║  ██║╚██████╔╝███████╗██║  ██║
 *    ╚═╝   ╚═╝  ╚═╝╚══════╝╚═╝     ╚═╝╚══════╝    ╚═╝     ╚═╝╚═╝  ╚═╝╚═╝  ╚═══╝╚═╝  ╚═╝ ╚═════╝ ╚══════╝╚═╝  ╚═╝
 *
 * ThemeManager.kt - The Style Master 🎨
 * Dark mode? Light mode? I've got you covered!
 * Making your app look good 24/7, because style matters!
 *
 * UI wizardry conjured by Yassine ✨
 * "Let there be light... or darkness, your choice!" - Genesis (UI Edition)
 */

package com.dev.ora.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.dev.ora.data.PreferencesManager

object ThemeManager {

    /**
     * Apply the theme based on saved preference
     * @param context Application context
     */
    fun applyTheme(context: Context) {
        val preferencesManager = PreferencesManager(context)
        val theme = preferencesManager.getThemePreference()
        applyTheme(theme)
    }

    /**
     * Apply the theme and save the preference
     * @param context Application context
     * @param theme One of: THEME_LIGHT, THEME_DARK, or THEME_SYSTEM
     */
    fun applyTheme(context: Context, theme: String) {
        val preferencesManager = PreferencesManager(context)
        preferencesManager.saveThemePreference(theme)
        applyTheme(theme)
    }

    /**
     * Apply the theme without saving
     * @param theme One of: THEME_LIGHT, THEME_DARK, or THEME_SYSTEM
     */
    private fun applyTheme(theme: String) {
        val nightMode = when (theme) {
            PreferencesManager.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            PreferencesManager.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            PreferencesManager.THEME_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    /**
     * Get the current theme preference
     * @param context Application context
     * @return One of: THEME_LIGHT, THEME_DARK, or THEME_SYSTEM
     */
    fun getCurrentTheme(context: Context): String {
        val preferencesManager = PreferencesManager(context)
        return preferencesManager.getThemePreference()
    }
}
