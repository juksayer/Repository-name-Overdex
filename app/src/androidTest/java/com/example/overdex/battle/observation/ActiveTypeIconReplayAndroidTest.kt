package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.R
import com.example.overdex.model.PokemonType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Replays a known battle still through the production crop and type matcher. */
@RunWith(AndroidJUnit4::class)
class ActiveTypeIconReplayAndroidTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val orderedTypes = listOf(
        PokemonType.FAIRY, PokemonType.PSYCHIC, PokemonType.FIGHTING, PokemonType.ROCK,
        PokemonType.FIRE, PokemonType.STEEL, PokemonType.WATER, PokemonType.FLYING,
        PokemonType.GHOST, PokemonType.GRASS, PokemonType.BUG, PokemonType.GROUND,
        PokemonType.ICE, PokemonType.DARK, PokemonType.DRAGON, PokemonType.NORMAL,
        PokemonType.ELECTRIC, PokemonType.POISON,
    )

    @Test
    fun known_turtonator_and_sneasel_still_matches_active_type_badges() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        PokemonGoTypeIconMatcher.initialize(context)
        val source = context.assets.open("battle_samples/Screenshot_20260813-142635.png").use(BitmapFactory::decodeStream)
        requireNotNull(source)
        try {
            // Use the circles in this source image, not superseded calibration defaults.
            val player = crop(source, 10, 155, 135, 225)
            val opponent = crop(source, 940, 155, 1070, 225)
            try {
                val playerMatches = PokemonGoTypeIconMatcher.matchCrop(player)
                val opponentMatches = PokemonGoTypeIconMatcher.matchCrop(opponent)
                assertEquals("player raw=$playerMatches", listOf(PokemonType.FIRE, PokemonType.DRAGON), ActiveTypeIconReading.from(playerMatches)?.types)
                assertEquals("opponent raw=$opponentMatches", listOf(PokemonType.DARK, PokemonType.ICE), ActiveTypeIconReading.from(opponentMatches)?.types)
            } finally { player.recycle(); opponent.recycle() }
        } finally { source.recycle() }
    }

    @Test fun sky_in_the_superseded_opponent_reference_box_does_not_match_a_type() {
        PokemonGoTypeIconMatcher.initialize(context)
        val source = context.assets.open("battle_samples/Screenshot_20260813-142635.png").use(BitmapFactory::decodeStream)
        requireNotNull(source)
        val sky = crop(source, 795, 145, 925, 205)
        try { assertNull(ActiveTypeIconReading.from(PokemonGoTypeIconMatcher.matchCrop(sky))) }
        finally { sky.recycle(); source.recycle() }
    }

    @Test fun all_reference_types_survive_scale_and_asymmetric_crop_margins() {
        PokemonGoTypeIconMatcher.initialize(context)
        for (type in orderedTypes) for (diameter in listOf(24, 30, 42)) {
            val bitmap = referenceStrip(listOf(type), diameter, top = diameter / 2, bottom = 11)
            try { assertTypes("$type at $diameter px", bitmap, listOf(type)) }
            finally { bitmap.recycle() }
        }
    }

    @Test fun every_type_survives_in_a_pair_with_extra_space_around_the_row() {
        PokemonGoTypeIconMatcher.initialize(context)
        for ((index, type) in orderedTypes.withIndex()) {
            val pair = listOf(type, orderedTypes[(index + 1) % orderedTypes.size])
            val bitmap = referenceStrip(pair, 28, top = 23, bottom = 7)
            try { assertTypes("pair $pair", bitmap, pair) }
            finally { bitmap.recycle() }
        }
    }

    @Test fun blank_transparent_and_solid_bright_crops_do_not_identify_a_type() {
        PokemonGoTypeIconMatcher.initialize(context)
        for (color in listOf(Color.TRANSPARENT, Color.BLACK, Color.WHITE, Color.rgb(172, 208, 227))) {
            val bitmap = Bitmap.createBitmap(100, 65, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(color)
                assertNull("solid color $color", ActiveTypeIconReading.from(PokemonGoTypeIconMatcher.matchCrop(bitmap)))
            } finally { bitmap.recycle() }
        }
    }

    @Test fun three_badges_and_a_clipped_badge_pair_do_not_become_complete_readings() {
        PokemonGoTypeIconMatcher.initialize(context)
        val three = referenceStrip(listOf(PokemonType.FIRE, PokemonType.WATER, PokemonType.GRASS), 30, 9, 12)
        val pair = referenceStrip(listOf(PokemonType.POISON, PokemonType.DARK), 30, 9, 12)
        val clipped = Bitmap.createBitmap(pair, 17, 0, pair.width - 17, pair.height)
        val deeplyClipped = Bitmap.createBitmap(pair, 28, 0, pair.width - 28, pair.height)
        try {
            assertNull("three badges", ActiveTypeIconReading.from(PokemonGoTypeIconMatcher.matchCrop(three)))
            assertNull("clipped first badge", ActiveTypeIconReading.from(PokemonGoTypeIconMatcher.matchCrop(clipped)))
            assertNull("first badge center is outside the crop", ActiveTypeIconReading.from(PokemonGoTypeIconMatcher.matchCrop(deeplyClipped)))
        } finally { three.recycle(); pair.recycle(); clipped.recycle(); deeplyClipped.recycle() }
    }

    @Test fun unsupported_second_glyph_does_not_turn_a_pair_into_a_single_type() {
        PokemonGoTypeIconMatcher.initialize(context)
        val bitmap = referenceStrip(listOf(PokemonType.FIRE, PokemonType.DARK), 30, 12, 12)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { color = Color.rgb(83, 88, 100) }
        canvas.drawCircle(62f, 27f, 15f, paint)
        paint.color = Color.WHITE
        for (row in 0..3) for (column in 0..3) if ((row + column) % 2 == 0) {
            canvas.drawRect(53f + column * 4, 18f + row * 4, 57f + column * 4, 22f + row * 4, paint)
        }
        try { assertNull(ActiveTypeIconReading.from(PokemonGoTypeIconMatcher.matchCrop(bitmap))) }
        finally { bitmap.recycle() }
    }

    @Test fun white_text_outside_the_badge_row_does_not_change_its_types() {
        PokemonGoTypeIconMatcher.initialize(context)
        val pair = listOf(PokemonType.POISON, PokemonType.FLYING)
        val bitmap = referenceStrip(pair, 30, 32, 12)
        Canvas(bitmap).drawText("CP 1495", 2f, 15f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 12f
        })
        try { assertTypes("text above row", bitmap, pair) }
        finally { bitmap.recycle() }
    }

    /** Private source images are supplied to the emulator; no phone archive is a bundled asset. */
    @Test fun supplied_recorded_crops_match_their_independently_reviewed_labels() {
        val directory = InstrumentationRegistry.getArguments().getString("typeIconFixtureDir")
        assumeTrue("Run with -e typeIconFixtureDir pointing at the private replay fixtures", directory != null)
        val root = File(requireNotNull(directory))
        val fixtures = Json.parseToJsonElement(File(root, "manifest.json").readText()).jsonArray
        PokemonGoTypeIconMatcher.initialize(context)
        val errors = mutableListOf<String>()
        val measurements = mutableListOf<kotlinx.serialization.json.JsonObject>()
        for (fixture in fixtures) {
            val row = fixture.jsonObject
            val name = row.getValue("file").jsonPrimitive.content
            require('/' !in name && '\\' !in name)
            val file = File(root, name)
            val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals("$name source hash", row.getValue("sha256").jsonPrimitive.content, hash)
            val expected = row.getValue("expectedTypes").jsonArray.map { PokemonType.valueOf(it.jsonPrimitive.content) }
            val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
            try {
                val started = SystemClock.elapsedRealtimeNanos()
                val matches = PokemonGoTypeIconMatcher.matchCrop(bitmap)
                val elapsed = SystemClock.elapsedRealtimeNanos() - started
                val actual = ActiveTypeIconReading.from(matches)?.types
                if (expected.takeIf { it.isNotEmpty() } != actual) errors += "$name expected=$expected actual=$actual raw=$matches"
                measurements += buildJsonObject {
                    put("file", name)
                    put("expectedTypes", JsonArray(expected.map { JsonPrimitive(it.name) }))
                    put("actualTypes", JsonArray(actual.orEmpty().map { JsonPrimitive(it.name) }))
                    put("elapsedNanos", elapsed)
                    put("rawMatches", matches.toString())
                }
            } finally { bitmap.recycle() }
        }
        File(root, "actual-results.json").writeText(JsonArray(measurements).toString())
        assertTrue("${errors.size}/${fixtures.size} recorded cases differed:\n${errors.joinToString("\n")}", errors.isEmpty())
    }

    private fun assertTypes(label: String, bitmap: Bitmap, expected: List<PokemonType>) {
        val matches = PokemonGoTypeIconMatcher.matchCrop(bitmap)
        assertEquals("$label raw=$matches", expected, ActiveTypeIconReading.from(matches)?.types)
    }

    private fun referenceStrip(types: List<PokemonType>, diameter: Int, top: Int, bottom: Int): Bitmap {
        val margin = 8
        val gap = 9
        val bitmap = Bitmap.createBitmap(margin * 2 + diameter * types.size + gap * (types.size - 1),
            top + diameter + bottom, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(172, 208, 227))
        val canvas = Canvas(bitmap)
        val sheet = BitmapFactory.decodeResource(context.resources, R.drawable.pokemon_go_type_icon_reference)
        try {
            for ((position, type) in types.withIndex()) {
                val index = orderedTypes.indexOf(type)
                val centerX = 150 + index % 6 * 180
                val centerY = 150 + index / 6 * 180
                val tile = Bitmap.createBitmap(sheet, centerX - 65, centerY - 65, 130, 130)
                try {
                    val left = (margin + position * (diameter + gap)).toFloat()
                    canvas.save()
                    canvas.clipPath(Path().apply { addCircle(left + diameter / 2f, top + diameter / 2f,
                        diameter / 2f, Path.Direction.CW) })
                    canvas.drawBitmap(tile, null, RectF(left, top.toFloat(), left + diameter, (top + diameter).toFloat()),
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    canvas.restore()
                } finally { tile.recycle() }
            }
        } finally { sheet.recycle() }
        return bitmap
    }

    private fun crop(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int) =
        Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
}
