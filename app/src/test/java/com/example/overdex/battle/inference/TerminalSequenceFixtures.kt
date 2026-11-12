package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.TerminalParticleSample
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.*

internal object TerminalSequenceFixtures {
    private val json = Json { ignoreUnknownKeys = true }
    private val factoryScans by lazy {
        javaClass.getResourceAsStream("/battle/recorded-evidence/2026-11-12-terminal-sequences/factory-scans.json.gz")!!
            .use { input -> GZIPInputStream(input).bufferedReader().use { json.parseToJsonElement(it.readText()).jsonArray } }
    }
    fun factorySamples(stamp: String): List<TerminalParticleSample> = factoryScans.single {
        it.jsonObject.getValue("stamp").jsonPrimitive.content == stamp
    }.jsonObject.getValue("samples").jsonArray.map { json.decodeFromJsonElement<TerminalParticleSample>(it) }
    fun factoryFrames(stamp: String) = factorySamples(stamp).mapIndexed { i, s -> TerminalEffectFrame("factory-$stamp-$i", s) }
    private val playerFactoryScans by lazy {
        javaClass.getResourceAsStream("/battle/recorded-evidence/2026-11-12-terminal-sequences/player-factory-scans.json.gz")!!
            .use { input -> GZIPInputStream(input).bufferedReader().use { json.parseToJsonElement(it.readText()).jsonArray } }
    }
    fun playerFactorySamples(stamp: String): List<TerminalParticleSample> = playerFactoryScans.single {
        it.jsonObject.getValue("stamp").jsonPrimitive.content == stamp
    }.jsonObject.getValue("samples").jsonArray.map { json.decodeFromJsonElement<TerminalParticleSample>(it) }
    val scans = javaClass.getResourceAsStream("/battle/recorded-evidence/2026-11-12-terminal-sequences/scans.json.gz")!!
        .use { input -> GZIPInputStream(input).bufferedReader().use { json.parseToJsonElement(it.readText()).jsonArray } }
    fun samples(stamp: String): List<TerminalParticleSample> = scans.single {
        it.jsonObject.getValue("stamp").jsonPrimitive.content == stamp
    }.jsonObject.getValue("samples").jsonArray.map { json.decodeFromJsonElement<TerminalParticleSample>(it) }
    fun frames(stamp: String) = samples(stamp).mapIndexed { i, s -> TerminalEffectFrame("$stamp-$i", s) }
}
