package com.example.overdex.data

import android.content.SharedPreferences
import com.example.overdex.model.navigation.HomeScreenStyle
import com.example.overdex.model.navigation.LauncherShortcutConfiguration
import com.example.overdex.model.navigation.LauncherShortcutLocation
import com.example.overdex.model.navigation.LauncherShortcutVisibility
import com.example.overdex.model.navigation.OverdextopCatalog
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class HomeScreenPreferencesTest {
    @Test fun `missing unknown and wrongly typed preferences default to Overdextop`() {
        for (value in listOf(null, "unknown", 42)) {
            val store = FakePreferences(value)
            assertEquals(HomeScreenStyle.OVERDEXTOP, HomeScreenPreferences(store.preferences).read())
        }
    }

    @Test fun `a saved choice survives a new preference reader`() {
        val store = FakePreferences()
        val preferences = HomeScreenPreferences(store.preferences)
        assertTrue(preferences.write(HomeScreenStyle.DIRECTORY))
        assertEquals(HomeScreenStyle.DIRECTORY, HomeScreenPreferences(store.preferences).read())
        assertTrue(preferences.write(HomeScreenStyle.OVERDEXTOP))
        assertEquals(HomeScreenStyle.OVERDEXTOP, HomeScreenPreferences(store.preferences).read())
        assertEquals(setOf("home_style"), store.writtenKeys)
    }

    @Test fun `a failed durable write is returned to the caller`() {
        val store = FakePreferences("DIRECTORY", commitSucceeds = false)
        val preferences = HomeScreenPreferences(store.preferences)
        assertFalse(preferences.write(HomeScreenStyle.OVERDEXTOP))
        assertEquals(HomeScreenStyle.DIRECTORY, HomeScreenPreferences(store.preferences).read())
    }

    @Test fun `missing shortcut preferences retain existing launcher defaults`() {
        val preferences = HomeScreenPreferences(FakePreferences().preferences)
        val shortcuts = preferences.readShortcuts()
        for (entry in OverdextopCatalog.destinations) {
            assertEquals(OverdextopCatalog.defaultVisibility(entry.id), shortcuts.visibility(entry.id))
        }
    }

    @Test fun `shortcut flags survive reopening without changing home style or unrelated keys`() {
        val store = FakePreferences("DIRECTORY", initialValues = mapOf("unrelated" to "keep"))
        val preferences = HomeScreenPreferences(store.preferences)
        val configuration = LauncherShortcutConfiguration()
            .withVisibility("profile", LauncherShortcutLocation.DESKTOP, true)
            .withVisibility("profile", LauncherShortcutLocation.PROGRAMS, false)
            .withVisibility("pokedex", LauncherShortcutLocation.DESKTOP, false)
        assertTrue(preferences.writeShortcuts(configuration))
        val reopened = HomeScreenPreferences(store.preferences)
        assertEquals(configuration.normalized(), reopened.readShortcuts())
        assertEquals(HomeScreenStyle.DIRECTORY, reopened.read())
        assertEquals("keep", store.values["unrelated"])
        assertFalse(store.writtenKeys.contains("home_style"))
        assertEquals(OverdextopCatalog.destinations.size * 2, store.writtenKeys.size)
    }

    @Test fun `corrupt shortcut flags fall back individually and unknown keys are ignored`() {
        val store = FakePreferences(initialValues = mapOf(
            "shortcut_desktop_droidball" to "wrong type",
            "shortcut_programs_droidball" to false,
            "shortcut_desktop_profile" to true,
            "shortcut_programs_settings" to false,
            "shortcut_desktop_unknown" to true,
        ))
        val shortcuts = HomeScreenPreferences(store.preferences).readShortcuts()
        assertEquals(LauncherShortcutVisibility(true, false), shortcuts.visibility("droidball"))
        assertEquals(LauncherShortcutVisibility(true, true), shortcuts.visibility("profile"))
        assertTrue(shortcuts.visibility("settings").programs)
        assertFalse(shortcuts.destinations.containsKey("unknown"))
        assertEquals(OverdextopCatalog.destinations.size, shortcuts.destinations.size)
    }

    @Test fun `failed shortcut persistence reports failure and preserves durable choices`() {
        val store = FakePreferences(commitSucceeds = false)
        val preferences = HomeScreenPreferences(store.preferences)
        val before = preferences.readShortcuts()
        assertFalse(preferences.writeShortcuts(before.withVisibility("profile", LauncherShortcutLocation.DESKTOP, true)))
        assertEquals(before, HomeScreenPreferences(store.preferences).readShortcuts())
    }

    private class FakePreferences(initialValue: Any? = null, private val commitSucceeds: Boolean = true,
        initialValues: Map<String, Any?> = emptyMap()) {
        val values = initialValues.toMutableMap().apply { if (initialValue != null) put("home_style", initialValue) }
        private val pending = mutableMapOf<String, Any?>()
        val writtenKeys = mutableSetOf<String>()
        private val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "putString", "putBoolean" -> {
                    writtenKeys += args!![0] as String
                    pending[args[0] as String] = args[1]
                    proxy
                }
                "commit" -> {
                    if (commitSucceeds) values.putAll(pending)
                    pending.clear()
                    commitSucceeds
                }
                else -> error("Unexpected preference edit: ${method.name}")
            }
        } as SharedPreferences.Editor
        val preferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getString" -> {
                    val value = values[args!![0] as String]
                    if (value != null && value !is String) throw ClassCastException()
                    value ?: args[1]
                }
                "getBoolean" -> {
                    val value = values[args!![0] as String]
                    if (value != null && value !is Boolean) throw ClassCastException()
                    value ?: args[1]
                }
                "edit" -> editor
                else -> error("Unexpected preference access: ${method.name}")
            }
        } as SharedPreferences
    }
}
