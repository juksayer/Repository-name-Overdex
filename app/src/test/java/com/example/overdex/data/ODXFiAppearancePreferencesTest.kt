package com.example.overdex.data

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class ODXFiAppearancePreferencesTest {
    @Test fun `hex entry accepts complete RGB codes and rejects incomplete or alpha forms`() {
        assertEquals(0xFF12ABEF.toInt(), parseODXFiHexColor("#12abEF"))
        assertEquals(0xFF000000.toInt(), parseODXFiHexColor("#000000"))
        assertEquals(0xFFFFFFFF.toInt(), parseODXFiHexColor("#FFFFFF"))
        for (invalid in listOf("", "#", "123456", "#123", "#12345", "#1234567", "#80123456", "#12GG56", " #123456", "#123456\n")) {
            assertNull("Should reject $invalid", parseODXFiHexColor(invalid))
        }
    }

    @Test fun `display codes round trip RGB with opaque alpha regardless of incoming transparency`() {
        for (color in listOf(0x00123456, 0x80123456.toInt(), 0xFF123456.toInt(), -1, 0)) {
            val displayed = odxFiColorHex(color)
            assertEquals(7, displayed.length)
            assertEquals(color or 0xFF000000.toInt(), parseODXFiHexColor(displayed))
        }
        assertEquals("#12ABEF", odxFiColorHex(0x5512ABEF))
        assertEquals(ODXFiAppearance(0xFF123456.toInt(), 0xFFABCDEF.toInt(), 0xFF112233.toInt()),
            ODXFiAppearance(0x00123456, 0x80ABCDEF.toInt(), 0x00112233).opaque())
    }

    @Test fun `missing and individually corrupt values retain the established shell defaults`() {
        assertEquals(ODXFiAppearance(), ODXFiAppearancePreferences(FakePreferences().preferences).read())
        val savedColors = mapOf("case_color" to "#112233", "button_color" to "#445566", "bezel_color" to "#778899")
        val saved = ODXFiAppearance(0xFF112233.toInt(), 0xFF445566.toInt(), 0xFF778899.toInt())
        for (corrupt in listOf(12, true, "green", "#123", "#8000FF41")) {
            for (key in savedColors.keys) {
                val store = FakePreferences(savedColors + (key to corrupt))
                val expected = when (key) {
                    "case_color" -> saved.copy(caseColorArgb = ODXFiAppearance.DEFAULT_CASE_COLOR_ARGB)
                    "button_color" -> saved.copy(buttonColorArgb = ODXFiAppearance.DEFAULT_BUTTON_COLOR_ARGB)
                    else -> saved.copy(bezelColorArgb = ODXFiAppearance.DEFAULT_BEZEL_COLOR_ARGB)
                }
                assertEquals("$key should recover independently from $corrupt", expected,
                    ODXFiAppearancePreferences(store.preferences).read())
            }
        }
    }

    @Test fun `individual choices survive reopening without changing the other colors`() {
        val store = FakePreferences()
        val preferences = ODXFiAppearancePreferences(store.preferences)
        assertTrue(preferences.writeCaseColor(0x80112233.toInt()))
        assertEquals(ODXFiAppearance(caseColorArgb = 0xFF112233.toInt()),
            ODXFiAppearancePreferences(store.preferences).read())
        assertEquals(setOf("case_color"), store.writtenKeys)
        assertTrue(preferences.writeButtonColor(0x00445566))
        assertEquals(ODXFiAppearance(0xFF112233.toInt(), 0xFF445566.toInt()),
            ODXFiAppearancePreferences(store.preferences).read())
        assertEquals(setOf("case_color", "button_color"), store.writtenKeys)
        assertTrue(preferences.writeBezelColor(0x80778899.toInt()))
        assertEquals(ODXFiAppearance(0xFF112233.toInt(), 0xFF445566.toInt(), 0xFF778899.toInt()),
            ODXFiAppearancePreferences(store.preferences).read())
        assertEquals("#112233", store.values["case_color"])
        assertEquals("#445566", store.values["button_color"])
        assertEquals("#778899", store.values["bezel_color"])
    }

    @Test fun `reset restores all colors while retaining unrelated stored values`() {
        val store = FakePreferences(mapOf("unrelated" to "keep"))
        val preferences = ODXFiAppearancePreferences(store.preferences)
        assertTrue(preferences.write(ODXFiAppearance(0xFF102030.toInt(), 0xFFABCDEF.toInt(), 0xFF778899.toInt())))
        assertTrue(preferences.resetDefaults())
        assertEquals(ODXFiAppearance(), ODXFiAppearancePreferences(store.preferences).read())
        assertEquals("keep", store.values["unrelated"])
        assertEquals(setOf("case_color", "button_color", "bezel_color", "hud_text", "hud_shadow",
            "hud_font_sp", "hud_padding", "hud_bloom"), store.writtenKeys)
    }

    @Test fun `failed durable saves report failure and preserve all saved colors including reset`() {
        val stored = mapOf("case_color" to "#112233", "button_color" to "#445566", "bezel_color" to "#778899")
        val store = FakePreferences(stored, commitSucceeds = false)
        val preferences = ODXFiAppearancePreferences(store.preferences)
        assertFalse(preferences.writeCaseColor(0xFF778899.toInt()))
        assertFalse(preferences.writeButtonColor(0xFF778899.toInt()))
        assertFalse(preferences.writeBezelColor(0xFF112233.toInt()))
        assertFalse(preferences.write(ODXFiAppearance()))
        assertFalse(preferences.resetDefaults())
        assertEquals(stored, store.values)
        assertEquals(ODXFiAppearance(0xFF112233.toInt(), 0xFF445566.toInt(), 0xFF778899.toInt()), preferences.read())
    }

    @Test fun `custom HUD survives reopening and invalid sizes stay within bounds`() {
        val store = FakePreferences()
        val custom = ODXFiAppearance(hud = HudMoveAppearance(0xffddffaa.toInt(), 0xff122334.toInt(), 11, 60, 3))
        assertTrue(ODXFiAppearancePreferences(store.preferences).write(custom))
        assertEquals(custom, ODXFiAppearancePreferences(store.preferences).read())
        store.values["hud_font_sp"] = "invalid"
        store.values["hud_padding"] = 99
        assertEquals(custom.hud.copy(fontSp = 7, rowPaddingDp = 8), ODXFiAppearancePreferences(store.preferences).read().hud)
    }

    @Test fun `legacy HUD font shrinks once and the next saved size reads back as entered`() {
        val store = FakePreferences(mapOf("hud_font" to 11))
        val preferences = ODXFiAppearancePreferences(store.preferences)
        val migrated = preferences.read()
        assertEquals(10, migrated.hud.fontSp)
        assertTrue(preferences.write(migrated))
        assertEquals(10, preferences.read().hud.fontSp)
    }

    private class FakePreferences(initialValues: Map<String, Any> = emptyMap(), private val commitSucceeds: Boolean = true) {
        val values = initialValues.toMutableMap()
        val writtenKeys = mutableSetOf<String>()
        private val pending = mutableMapOf<String, Any>()
        private val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "putString", "putInt" -> {
                    val key = args!![0] as String
                    writtenKeys += key
                    pending[key] = args[1]
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
                "getInt" -> values[args!![0] as String]?.let { it as Int } ?: args[1]
                "getString" -> {
                    val value = values[args!![0] as String]
                    if (value != null && value !is String) throw ClassCastException()
                    value ?: args[1]
                }
                "edit" -> editor
                else -> error("Unexpected preference access: ${method.name}")
            }
        } as SharedPreferences
    }
}
