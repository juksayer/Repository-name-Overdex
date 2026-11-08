package com.example.overdex.model.navigation

import org.junit.Assert.*
import org.junit.Test

class LauncherShortcutConfigurationTest {
    @Test fun `default placement preserves existing pins and adds field notes in both launchers`() {
        val controller = OverdextopController()
        assertEquals(listOf("droidball", "pokedex", "current_team", "binders", "match_history", "explorer", "field_notes", "programs"), controller.entries.map { it.id })
        controller.openPrograms()
        assertEquals(OverdextopCatalog.destinations, controller.entries)
        assertEquals(OverdextopCatalog.destinations.size, controller.entries.map { it.id }.distinct().size)
    }

    @Test fun `a new field notes shortcut preserves old hidden choices and can itself stay hidden`() {
        val previous = LauncherShortcutConfiguration(mapOf(
            "pokedex" to LauncherShortcutVisibility(false, false),
            "binders" to LauncherShortcutVisibility(false, true),
        ))
        val upgraded = previous.normalized()
        assertEquals(LauncherShortcutVisibility(false, false), upgraded.visibility("pokedex"))
        assertEquals(LauncherShortcutVisibility(false, true), upgraded.visibility("binders"))
        assertEquals(LauncherShortcutVisibility(true, true), upgraded.visibility("field_notes"))
        val hidden = upgraded.withVisibility("field_notes", LauncherShortcutLocation.DESKTOP, false)
            .withVisibility("field_notes", LauncherShortcutLocation.PROGRAMS, false)
        val reopened = OverdextopController(LauncherShortcutConfiguration(hidden.destinations))
        assertFalse(reopened.entries.any { it.id == "field_notes" })
        reopened.openPrograms()
        assertFalse(reopened.entries.any { it.id == "field_notes" })
        reopened.openShortcuts()
        assertTrue("Hidden programs remain recoverable in shortcut settings", reopened.select("field_notes"))
        assertEquals(LauncherShortcutVisibility(false, false), reopened.shortcutVisibility("field_notes"))
    }

    @Test fun `ordinary destinations support both desktop only programs only and neither`() {
        for ((desktop, programs) in listOf(true to true, true to false, false to true, false to false)) {
            val configuration = LauncherShortcutConfiguration()
                .withVisibility("profile", LauncherShortcutLocation.DESKTOP, desktop)
                .withVisibility("profile", LauncherShortcutLocation.PROGRAMS, programs)
            val controller = OverdextopController(configuration)
            assertEquals(desktop, controller.entries.any { it.id == "profile" })
            controller.openPrograms()
            assertEquals(programs, controller.entries.any { it.id == "profile" })
            assertEquals(LauncherShortcutVisibility(desktop, programs), controller.shortcutVisibility("profile"))
        }
    }

    @Test fun `hiding every ordinary destination keeps settings and shortcut recovery reachable`() {
        val controller = OverdextopController(hiddenConfiguration())
        assertEquals(listOf("programs"), controller.entries.map { it.id })
        for (direction in OverdextopDirection.entries) controller.move(direction)
        assertEquals("programs", controller.selectedEntry.id)
        assertNull(controller.confirm())
        assertEquals(listOf("settings"), controller.entries.map { it.id })
        assertNull(controller.confirm())
        assertEquals(OverdextopSurface.SETTINGS, controller.surface)
        assertTrue(controller.select("shortcuts"))
        assertNull(controller.confirm())
        assertEquals(OverdextopSurface.SHORTCUTS, controller.surface)
        assertTrue(controller.select(controller.shortcutTargetId("droidball", LauncherShortcutLocation.DESKTOP)))
        val action = controller.confirm() as OverdextopAction.ChangeShortcutLocation
        assertEquals(OverdextopAction.ChangeShortcutLocation("droidball", LauncherShortcutLocation.DESKTOP, true), action)
        controller.replaceShortcuts(controller.shortcuts.withVisibility(action.destinationId, action.location, action.visible))
        controller.home()
        assertEquals(listOf("droidball", "programs"), controller.entries.map { it.id })
    }

    @Test fun `settings may be pinned but its recovery location cannot be unchecked`() {
        val configuration = hiddenConfiguration().withVisibility("settings", LauncherShortcutLocation.DESKTOP, true)
        val controller = OverdextopController(configuration)
        assertEquals(listOf("settings", "programs"), controller.entries.map { it.id })
        controller.openSettings()
        controller.openShortcuts()
        assertTrue(controller.select(controller.shortcutTargetId("settings", LauncherShortcutLocation.PROGRAMS)))
        assertEquals(LauncherShortcutVisibility(true, true), controller.shortcutVisibility("settings"))
        assertFalse(controller.shortcutLocationEditable("settings", LauncherShortcutLocation.PROGRAMS))
        assertTrue(controller.shortcutLocationEditable("settings", LauncherShortcutLocation.DESKTOP))
        assertNull(controller.confirm())
    }

    @Test fun `checkbox pointer targets and arrows choose the same pending action`() {
        val controller = OverdextopController()
        controller.openSettings()
        controller.select("shortcuts")
        assertNull(controller.confirm())
        assertTrue(controller.select("shortcut:profile:desktop"))
        assertEquals("profile", controller.selectedEntry.id)
        assertEquals("shortcut:profile:desktop", controller.selectedPointerTargetId)
        assertEquals(OverdextopAction.ChangeShortcutLocation("profile", LauncherShortcutLocation.DESKTOP, true), controller.confirm())
        controller.move(OverdextopDirection.RIGHT)
        assertEquals("shortcut:profile:programs", controller.selectedPointerTargetId)
        assertEquals(OverdextopAction.ChangeShortcutLocation("profile", LauncherShortcutLocation.PROGRAMS, false), controller.confirm())
        controller.move(OverdextopDirection.DOWN)
        assertEquals("intueor", controller.selectedEntry.id)
        assertEquals(LauncherShortcutLocation.PROGRAMS, controller.selectedShortcutLocation)
        controller.move(OverdextopDirection.LEFT)
        assertEquals("shortcut:intueor:desktop", controller.selectedPointerTargetId)
        assertFalse(controller.select("shortcut:profile:unknown"))
        assertFalse(controller.select("shortcut:missing:desktop"))
        assertEquals("intueor", controller.selectedEntry.id)
    }

    @Test fun `shortcut edits preserve the settings programs and desktop return selections`() {
        val controller = OverdextopController()
        controller.select("explorer")
        controller.openPrograms()
        controller.select("settings")
        assertNull(controller.confirm())
        controller.select("shortcuts")
        assertNull(controller.confirm())
        controller.select("shortcut:profile:programs")
        assertTrue(controller.back())
        assertEquals(OverdextopSurface.SETTINGS, controller.surface)
        assertEquals("shortcuts", controller.selectedEntry.id)
        assertTrue(controller.back())
        assertEquals(OverdextopSurface.PROGRAMS, controller.surface)
        assertEquals("settings", controller.selectedEntry.id)
        assertTrue(controller.back())
        assertEquals("explorer", controller.selectedEntry.id)
        controller.openSettings()
        controller.openShortcuts()
        assertEquals("shortcut:profile:programs", controller.selectedPointerTargetId)
    }

    @Test fun `ragged rows clamp to an existing lower icon before reaching programs`() {
        var configuration = hiddenConfiguration()
        OverdextopCatalog.destinations.take(5).forEach {
            configuration = configuration.withVisibility(it.id, LauncherShortcutLocation.DESKTOP, true)
        }
        val controller = OverdextopController(configuration)
        controller.select("current_team")
        controller.move(OverdextopDirection.DOWN)
        assertEquals("manage_binders", controller.selectedEntry.id)
        controller.move(OverdextopDirection.RIGHT)
        assertEquals("manage_binders", controller.selectedEntry.id)
        controller.move(OverdextopDirection.DOWN)
        assertEquals("programs", controller.selectedEntry.id)
        controller.move(OverdextopDirection.UP)
        assertEquals("manage_binders", controller.selectedEntry.id)
        controller.move(OverdextopDirection.UP)
        assertEquals("pokedex", controller.selectedEntry.id)
    }

    @Test fun `every desktop size keeps its footer reachable and directional selections valid`() {
        for (pinCount in 0..OverdextopCatalog.destinations.size) {
            var configuration = hiddenConfiguration()
            OverdextopCatalog.destinations.take(pinCount).forEach {
                configuration = configuration.withVisibility(it.id, LauncherShortcutLocation.DESKTOP, true)
            }
            val controller = OverdextopController(configuration)
            assertEquals(pinCount + 1, controller.entries.size)
            repeat(20) { controller.move(OverdextopDirection.DOWN) }
            assertEquals("programs", controller.selectedEntry.id)
            controller.move(OverdextopDirection.LEFT)
            controller.move(OverdextopDirection.RIGHT)
            assertEquals("programs", controller.selectedEntry.id)
            controller.move(OverdextopDirection.UP)
            assertEquals(pinCount == 0, controller.selectedEntry.id == "programs")
            for (entry in controller.entries.toList()) {
                assertTrue(controller.select(entry.id))
                for (direction in OverdextopDirection.entries) {
                    controller.move(direction)
                    assertTrue(controller.selectedIndex in controller.entries.indices)
                }
            }
        }
    }

    @Test fun `updates retain surviving selection identity and replace hidden selections safely`() {
        val controller = OverdextopController()
        controller.select("binders")
        controller.openPrograms()
        controller.select("profile")
        controller.replaceShortcuts(controller.shortcuts.withVisibility("droidball", LauncherShortcutLocation.PROGRAMS, false))
        assertEquals("profile", controller.selectedEntry.id)
        controller.replaceShortcuts(controller.shortcuts.withVisibility("profile", LauncherShortcutLocation.PROGRAMS, false))
        assertEquals("intueor", controller.selectedEntry.id)
        controller.replaceShortcuts(controller.shortcuts.withVisibility("binders", LauncherShortcutLocation.DESKTOP, false))
        controller.home()
        assertEquals("match_history", controller.selectedEntry.id)
        assertTrue(controller.select("programs"))
        controller.replaceShortcuts(hiddenConfiguration())
        assertEquals("programs", controller.selectedEntry.id)
    }

    @Test fun `unknown configuration cannot invent destinations or override missing defaults`() {
        val configuration = LauncherShortcutConfiguration(mapOf(
            "unknown_future_program" to LauncherShortcutVisibility(true, true),
            "droidball" to LauncherShortcutVisibility(false, false),
        ))
        val controller = OverdextopController(configuration)
        assertFalse(controller.shortcuts.destinations.containsKey("unknown_future_program"))
        assertFalse(controller.entries.any { it.id == "droidball" })
        assertTrue(controller.entries.any { it.id == "pokedex" })
        assertEquals(LauncherShortcutVisibility(false, false), controller.shortcutVisibility("unknown_future_program"))
        controller.openPrograms()
        assertTrue(controller.entries.any { it.id == "tournaments" })
        assertFalse(controller.entries.any { it.id == "unknown_future_program" })
    }

    private fun hiddenConfiguration(): LauncherShortcutConfiguration = OverdextopCatalog.destinations.fold(LauncherShortcutConfiguration()) { current, entry ->
        current.withVisibility(entry.id, LauncherShortcutLocation.DESKTOP, false)
            .withVisibility(entry.id, LauncherShortcutLocation.PROGRAMS, false)
    }
}
