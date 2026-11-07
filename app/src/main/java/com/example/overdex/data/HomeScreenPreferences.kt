package com.example.overdex.data

import android.content.Context
import android.content.SharedPreferences
import com.example.overdex.model.navigation.HomeScreenStyle
import com.example.overdex.model.navigation.LauncherShortcutConfiguration
import com.example.overdex.model.navigation.LauncherShortcutVisibility
import com.example.overdex.model.navigation.OverdextopCatalog

/** This preference changes the launcher only; program data and calibration are untouched. */
class HomeScreenPreferences internal constructor(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE))

    fun read(): HomeScreenStyle = try {
        HomeScreenStyle.entries.firstOrNull { it.name == preferences.getString(HOME_STYLE, null) }
            ?: HomeScreenStyle.OVERDEXTOP
    } catch (_: ClassCastException) {
        HomeScreenStyle.OVERDEXTOP
    }

    /** The caller changes the displayed preference only when the durable save succeeds. */
    fun write(style: HomeScreenStyle): Boolean = preferences.edit().putString(HOME_STYLE, style.name).commit()

    fun readShortcuts(): LauncherShortcutConfiguration = LauncherShortcutConfiguration(
        OverdextopCatalog.destinations.associate { destination ->
            val defaults = OverdextopCatalog.defaultVisibility(destination.id)
            destination.id to LauncherShortcutVisibility(
                desktop = readBoolean("shortcut_desktop_${destination.id}", defaults.desktop),
                programs = readBoolean("shortcut_programs_${destination.id}", defaults.programs),
            )
        }
    ).normalized()

    /** Save only launcher flags; home style and all program data retain their existing keys. */
    fun writeShortcuts(configuration: LauncherShortcutConfiguration): Boolean {
        val editor = preferences.edit()
        for (destination in OverdextopCatalog.destinations) {
            val visibility = configuration.visibility(destination.id)
            editor.putBoolean("shortcut_desktop_${destination.id}", visibility.desktop)
            editor.putBoolean("shortcut_programs_${destination.id}", visibility.programs)
        }
        return editor.commit()
    }

    private fun readBoolean(key: String, default: Boolean): Boolean = try {
        preferences.getBoolean(key, default)
    } catch (_: ClassCastException) {
        default
    }

    private companion object {
        const val PREFERENCES = "overdex_home_screen"
        const val HOME_STYLE = "home_style"
    }
}
