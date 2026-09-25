package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.overdex.model.PokemonType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Replays a known battle still through the production crop and type matcher. */
@RunWith(AndroidJUnit4::class)
class ActiveTypeIconReplayAndroidTest {
    @Test
    fun known_turtonator_and_sneasel_still_matches_active_type_badges() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        PokemonGoTypeIconMatcher.initialize(context)
        val source = context.assets.open("battle_samples/Screenshot_20260813-142635.png").use(BitmapFactory::decodeStream)
        requireNotNull(source)
        try {
            val player = crop(source, 20, 145, 135, 205)
            val opponent = crop(source, 795, 145, 925, 205)
            try {
                val playerMatches = PokemonGoTypeIconMatcher.matchCrop(player)
                val opponentMatches = PokemonGoTypeIconMatcher.matchCrop(opponent)
                assertEquals("player raw=$playerMatches", listOf(PokemonType.FIRE, PokemonType.DRAGON), playerMatches.mapNotNull { it.type })
                assertEquals("opponent raw=$opponentMatches", listOf(PokemonType.DARK, PokemonType.ICE), opponentMatches.mapNotNull { it.type })
            } finally { player.recycle(); opponent.recycle() }
        } finally { source.recycle() }
    }

    private fun crop(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int) =
        Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
}
