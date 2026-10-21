package com.example.overdex.data

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.CaptureTemplateManager
import com.example.overdex.battle.team.*
import com.example.overdex.model.*
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

/** Isolated preferences: these tests never modify the trainer's saved teams or calibration. */
class BinderAndTeamStoreDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun preferences(test: (String) -> Unit) {
        val name = "test_library_${UUID.randomUUID()}"
        try { test(name) } finally { context.deleteSharedPreferences(name) }
    }

    @Test fun bindersPersistEditsAndRejectDuplicatesWithoutChangingEarlierRules() = preferences { name ->
        val original = OwnedPokemonBinder("custom", "Great Fire", BinderRules(league = BinderLeague.GREAT,
            allowedTypes = setOf(PokemonType.FIRE)))
        OwnedBinderStore(context, name).save(original)
        val restored = OwnedBinderStore(context, name)
        assertEquals(original, restored.binders.value.single())
        val updated = original.copy(displayName = "Great Shadows", rules = original.rules.copy(shadow = BinderFlag.ONLY))
        restored.save(updated)
        assertEquals(listOf(updated), OwnedBinderStore(context, name).binders.value)
        assertTrue(runCatching { restored.save(updated.copy(routeKey = "duplicate")) }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(listOf(updated), OwnedBinderStore(context, name).binders.value)
        assertTrue(runCatching { restored.save(OwnedPokemonBinder.ALL) }.isFailure)
    }

    @Test fun namedTeamLibraryCoexistsWithLegacyActiveTeamAndSurvivesReload() = preferences { name ->
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().putString("team", """{"members":[{"slot":1,"speciesId":25,"speciesName":"Pikachu","fastMoveName":"Thunder Shock","chargedMoveNames":["Discharge"]}]}""").commit()
        val store = CurrentBattleTeamStore(context, name)
        val old = store.team.value
        val team = CurrentBattleTeam((1..3).map { old.members.single().copy(slot = it, ownedPokemonId = "card-$it") })
        val saved = store.saveNamed("First", team)
        store.saveNamed("Second", team)
        assertEquals(old, store.team.value)
        val updated = store.saveNamed("Updated", team.copy(members = team.members.reversed()), saved.id)
        val reloaded = CurrentBattleTeamStore(context, name)
        assertEquals(2, reloaded.savedTeams.value.size)
        assertEquals(updated, reloaded.savedTeams.value.first { it.id == saved.id })
        assertTrue(runCatching { reloaded.saveNamed("Updated", team) }.isFailure)
        reloaded.replace(updated.team)
        assertEquals(updated.team, CurrentBattleTeamStore(context, name).team.value)
        assertEquals("card-1", reloaded.team.value.members.first().ownedPokemonId)
    }

    @Suppress("DEPRECATION")
    @Test fun screenshotBoxSavePreservesLegacyTemplatesAndSourceSize() = preferences { name ->
        val manager = CaptureTemplateManager(context, name)
        val summary = manager.getPokemonDetailTemplate().copy(name = "PokemonGoSummaryTemplate")
        val original = summary.regions.first().copy(x = .2f, y = .3f)
        manager.saveAdjustment(summary.name, original)
        val detail = manager.getPokemonDetailTemplate()
        assertEquals(original, detail.regions.first())
        val adjusted = detail.copy(regions = detail.regions.map { it.adjustPixels(5f, 7f, 1080, 2400, false) })
        manager.saveTemplate(adjusted, 1080, 2400)
        val reloaded = CaptureTemplateManager(context, name)
        assertEquals(adjusted, reloaded.getPokemonDetailTemplate())
        assertEquals(adjusted, reloaded.getSummaryTemplate())
        assertEquals(original.y, context.getSharedPreferences(name, Context.MODE_PRIVATE).getFloat("${summary.name}_${original.id}_y", -1f), .0001f)
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        assertEquals(1080, prefs.getInt("${detail.name}_source_width", 0))
        assertEquals(2400, prefs.getInt("${detail.name}_source_height", 0))
    }
}
