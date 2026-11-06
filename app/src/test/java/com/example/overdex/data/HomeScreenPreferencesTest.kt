package com.example.overdex.data

import android.content.SharedPreferences
import com.example.overdex.model.navigation.HomeScreenStyle
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

    private class FakePreferences(initialValue: Any? = null, private val commitSucceeds: Boolean = true) {
        private var value = initialValue
        private var pending: String? = null
        val writtenKeys = mutableSetOf<String>()
        private val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "putString" -> {
                    writtenKeys += args!![0] as String
                    pending = args[1] as String?
                    proxy
                }
                "commit" -> {
                    if (commitSucceeds) value = pending
                    commitSucceeds
                }
                else -> error("Unexpected preference edit: ${method.name}")
            }
        } as SharedPreferences.Editor
        val preferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getString" -> {
                    if (value != null && value !is String) throw ClassCastException()
                    value
                }
                "edit" -> editor
                else -> error("Unexpected preference access: ${method.name}")
            }
        } as SharedPreferences
    }
}
