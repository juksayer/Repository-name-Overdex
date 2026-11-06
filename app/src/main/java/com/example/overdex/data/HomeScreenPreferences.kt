package com.example.overdex.data

import android.content.Context
import android.content.SharedPreferences
import com.example.overdex.model.navigation.HomeScreenStyle

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

    private companion object {
        const val PREFERENCES = "overdex_home_screen"
        const val HOME_STYLE = "home_style"
    }
}
