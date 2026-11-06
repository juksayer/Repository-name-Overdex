package com.example.overdex.model.navigation

import com.example.overdex.model.OwnedPokemonBinder
import org.junit.Assert.*
import org.junit.Test

class OverdextopControllerTest {
    @Test fun `desktop arrows follow three columns without wrapping between rows`() {
        val controller = OverdextopController()
        controller.move(OverdextopDirection.UP)
        controller.move(OverdextopDirection.LEFT)
        assertEquals("droidball", controller.selectedEntry.id)
        controller.move(OverdextopDirection.RIGHT)
        controller.move(OverdextopDirection.UP)
        assertEquals("pokedex", controller.selectedEntry.id)
        controller.move(OverdextopDirection.RIGHT)
        controller.move(OverdextopDirection.RIGHT)
        assertEquals("current_team", controller.selectedEntry.id)
        controller.move(OverdextopDirection.DOWN)
        assertEquals("explorer", controller.selectedEntry.id)
        controller.move(OverdextopDirection.RIGHT)
        assertEquals("explorer", controller.selectedEntry.id)
        controller.move(OverdextopDirection.LEFT)
        assertEquals("match_history", controller.selectedEntry.id)
        controller.move(OverdextopDirection.UP)
        assertEquals("pokedex", controller.selectedEntry.id)
    }

    @Test fun `the programs row is reachable from every bottom column and preserves that column`() {
        for (id in listOf("binders", "match_history", "explorer")) {
            val controller = OverdextopController()
            assertTrue(controller.select(id))
            controller.move(OverdextopDirection.DOWN)
            assertEquals("programs", controller.selectedEntry.id)
            controller.move(OverdextopDirection.LEFT)
            controller.move(OverdextopDirection.RIGHT)
            controller.move(OverdextopDirection.DOWN)
            assertEquals("programs", controller.selectedEntry.id)
            controller.move(OverdextopDirection.UP)
            assertEquals(id, controller.selectedEntry.id)
        }
    }

    @Test fun `programs and settings have independent selection and a return stack`() {
        val controller = OverdextopController()
        controller.select("programs")
        assertNull(controller.confirm())
        assertEquals(OverdextopSurface.PROGRAMS, controller.surface)
        controller.select("settings")
        assertNull(controller.confirm())
        assertEquals(OverdextopSurface.SETTINGS, controller.surface)
        controller.select("home_directory")
        assertEquals(OverdextopAction.ChooseHomeStyle(HomeScreenStyle.DIRECTORY), controller.confirm())
        assertEquals(OverdextopSurface.SETTINGS, controller.surface)
        assertTrue(controller.back())
        assertEquals("settings", controller.selectedEntry.id)
        assertTrue(controller.back())
        assertEquals(OverdextopSurface.DESKTOP, controller.surface)
        assertEquals("programs", controller.selectedEntry.id)
        assertFalse(controller.back())
        controller.openPrograms()
        assertEquals("settings", controller.selectedEntry.id)
        controller.openSettings()
        assertEquals("home_directory", controller.selectedEntry.id)
    }

    @Test fun `home closes all menus and preserves the desktop selection`() {
        val controller = OverdextopController()
        controller.select("match_history")
        controller.openPrograms()
        controller.select("profile")
        controller.openSettings()
        controller.home()
        assertEquals(OverdextopSurface.DESKTOP, controller.surface)
        assertEquals("match_history", controller.selectedEntry.id)
        controller.openPrograms()
        assertEquals("profile", controller.selectedEntry.id)
    }

    @Test fun `settings opened directly return directly home`() {
        val controller = OverdextopController()
        controller.select("explorer")
        controller.openSettings()
        assertTrue(controller.back())
        assertEquals(OverdextopSurface.DESKTOP, controller.surface)
        assertEquals("explorer", controller.selectedEntry.id)
    }

    @Test fun `vertical lists clamp at both ends and never launch on direction input`() {
        val controller = OverdextopController()
        controller.openPrograms()
        repeat(100) { controller.move(OverdextopDirection.UP) }
        assertEquals("droidball", controller.selectedEntry.id)
        repeat(100) { controller.move(OverdextopDirection.DOWN) }
        assertEquals("settings", controller.selectedEntry.id)
        controller.move(OverdextopDirection.LEFT)
        controller.move(OverdextopDirection.RIGHT)
        assertEquals("settings", controller.selectedEntry.id)
        assertEquals(OverdextopSurface.PROGRAMS, controller.surface)
        assertFalse(controller.select("missing"))
        assertEquals("settings", controller.selectedEntry.id)
    }

    @Test fun `shortcuts dispatch existing programs while Explorer remains distinct`() {
        val controller = OverdextopController()
        val expected = mapOf(
            "droidball" to InstrumentCommand.LaunchDroidball,
            "pokedex" to InstrumentCommand.OpenSearch,
            "current_team" to InstrumentCommand.OpenCurrentTeam,
            "binders" to InstrumentCommand.OpenOwnedBinder(OwnedPokemonBinder.ALL),
            "match_history" to InstrumentCommand.OpenBattleHistory,
        )
        for ((id, command) in expected) {
            controller.select(id)
            assertEquals(OverdextopAction.OpenProgram(command), controller.confirm())
            assertEquals(OverdextopSurface.DESKTOP, controller.surface)
        }
        controller.select("explorer")
        assertEquals(OverdextopAction.OpenExplorer, controller.confirm())
        controller.openPrograms()
        controller.select("replay_editor")
        assertEquals(OverdextopAction.OpenProgram(InstrumentCommand.OpenReplayEditor), controller.confirm())
        controller.select("match_calibration")
        assertEquals(OverdextopAction.OpenProgram(InstrumentCommand.OpenMatchCalibration), controller.confirm())
        controller.select("crop_calibration")
        assertEquals(OverdextopAction.OpenProgram(InstrumentCommand.OpenDirectoryCalibration), controller.confirm())
    }
}
