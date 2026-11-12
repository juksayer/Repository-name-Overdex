package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.TerminalParticleSample
import kotlinx.serialization.json.*
import java.util.zip.GZIPInputStream

/** Compiled production reader, OpenCV-resized recorded rasters; never new phone testimony. */
internal object TerminalAppearanceFixtures {
    private val json=Json { ignoreUnknownKeys=true }
    private val scans by lazy {
        javaClass.getResourceAsStream("/battle/recorded-evidence/2026-11-12-terminal-sequences/player-appearance-scans.json.gz")!!
            .use { GZIPInputStream(it).bufferedReader().use { json.parseToJsonElement(it.readText()).jsonArray } }
    }
    fun samples(stamp: String): List<TerminalParticleSample> = scans.single {
        it.jsonObject.getValue("stamp").jsonPrimitive.content==stamp
    }.jsonObject.getValue("samples").jsonArray.map { json.decodeFromJsonElement<TerminalParticleSample>(it) }
}
