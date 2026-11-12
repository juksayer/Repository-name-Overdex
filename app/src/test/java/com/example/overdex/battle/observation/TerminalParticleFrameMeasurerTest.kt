package com.example.overdex.battle.observation

import java.awt.image.BufferedImage
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Real finishing pixels and deliberately retained counterexamples; no synthetic KO labels. */
class TerminalParticleFrameMeasurerTest {
    @Test fun `recorded positive and competing pixels retain features through live batching and archive`() {
        for (f in fixtures) {
            var clock = f.previous.at
            val reader = TerminalParticleFrameMeasurer()
            val rows = mutableListOf<com.example.overdex.battle.archive.ArchivedRealityArticle>()
            val buffer = TerminalParticleObservationBuffer(reader::reset, { payload, refs, at, wall ->
                val article = com.example.overdex.battle.reality.RealityArticle(
                    com.example.overdex.battle.reality.ArticleId("sample-${rows.size}"), wall, wall,
                    com.example.overdex.battle.custody.SourceId("TERMINAL_PARTICLE_WITNESS"), payload,
                    evidenceReferences = refs, matchId = MatchId("fixture"), monotonicTimeNanos = at)
                rows += com.example.overdex.battle.archive.RealityArticleArchiveMapper.map(article)
            }, { clock })
            buffer.synchronize(TerminalParticleAssignment(com.example.overdex.battle.custody.ActivePokemonSide.OPPONENT,
                clock, f.current.at + 1_000_000_000L, listOf("synthetic-scheduling-cue")), 1000)
            for (frame in listOf(f.previous, f.current)) {
                clock = frame.at
                buffer.accept(f.width,f.height,f.bounds,clock,1000,null,true) { f.read(reader,frame) }
            }
            buffer.synchronize(null,1000)
            val archive = com.example.overdex.battle.archive.MatchArchive(matchId="fixture",articles=rows)
            val restored = com.example.overdex.battle.archive.MatchArchiveSerializer.deserialize(
                com.example.overdex.battle.archive.MatchArchiveSerializer.serialize(archive))
            val batch = restored.articles.single().payload as com.example.overdex.battle.archive.ArchivedTerminalParticleObservations
            assertEquals(listOf("BASELINE","SAMPLED"),batch.samples.map { it.status })
            assertEquals(f.current.at,batch.samples.last().sourceAtNanos)
            assertEquals(f.json.getValue("referenceCluster").jsonArray.size,batch.samples.last().points.size)
            assertTrue(batch.ended)
        }
    }

    @Test fun `recorded final charged collapses have clusters at native and capture-like dimensions`() {
        for (name in listOf("cramorant-finish", "leavanny-finish")) for (width in listOf(816, 672)) {
            val f = fixture(name, width)
            val result = f.pair()
            assertEquals(TerminalParticleReadStatus.SAMPLED, result.status)
            assertTrue("$name $width", result.cluster.size >= 4)
            assertEquals(f.previous.at, result.previousSourceAtNanos)
            assertEquals(f.current.at, result.sourceAtNanos)
            assertReference(f, result)
        }
    }

    @Test fun `shield QTE and attacker effects remain explicit competing particle signals`() {
        for (name in listOf("shield", "qte", "pelipper-attack")) {
            val f = fixture(name, 816)
            // This deliberately expects a cluster in NON-KO pixels. Any future
            // inference must establish receiving phase and terminal sequence.
            assertTrue(name, f.pair().cluster.size >= 4)
        }
    }

    @Test fun `living recall concession and selected ordinary charge do not supply this cluster`() {
        for (name in listOf("carbink-recall", "roselia-concession", "mismagius-living")) for (width in listOf(816, 672)) {
            val result = fixture(name, width).pair()
            assertEquals(TerminalParticleReadStatus.SAMPLED, result.status)
            assertTrue("$name $width", result.cluster.isEmpty())
        }
    }

    @Test fun `all retained video pairs agree with independent OpenCV reference positions`() {
        for (f in fixtures) assertReference(f, f.pair())
    }

    @Test fun `a first image or restart supplies a baseline rather than a motion assertion`() {
        val f = fixture("cramorant-finish", 672)
        val reader = TerminalParticleFrameMeasurer()
        assertEquals(TerminalParticleReadStatus.BASELINE, f.read(reader, f.current).status)
        reader.reset()
        val result = f.read(reader, f.current)
        assertEquals(TerminalParticleReadStatus.BASELINE, result.status)
        assertNull(result.previousSourceAtNanos)
        assertTrue(result.cluster.isEmpty())
    }

    @Test fun `a source gap cannot pretend a widely separated picture is the preceding animation frame`() {
        val f = fixture("cramorant-finish", 672)
        val reader = TerminalParticleFrameMeasurer()
        f.read(reader, f.previous)
        val result = f.read(reader, f.current, at = f.previous.at + TerminalParticleFrameMeasurer.MAX_FRAME_GAP_NANOS + 1)
        assertEquals(TerminalParticleReadStatus.SOURCE_GAP, result.status)
        assertNull(result.previousSourceAtNanos)
        assertTrue(result.cluster.isEmpty())
    }

    @Test fun `duplicate and older images cannot replace a newer baseline including malformed old geometry`() {
        val f = fixture("cramorant-finish", 672)
        val reader = TerminalParticleFrameMeasurer()
        f.read(reader, f.previous)
        assertEquals(TerminalParticleReadStatus.OLD_FRAME, f.read(reader, f.previous).status)
        val old = reader.measure(0, 0, BattleCropBounds(0,0,0,0), f.previous.at - 1) { _, _ ->
            error("Old images must not be read")
        }
        assertEquals(TerminalParticleReadStatus.OLD_FRAME, old.status)
        assertReference(f, f.read(reader, f.current))
    }

    @Test fun `source dimensions or a moved crop retire the previous geometry`() {
        val f = fixture("cramorant-finish", 672)
        val reader = TerminalParticleFrameMeasurer()
        f.read(reader, f.previous)
        val resized = reader.measure(f.width + 1, f.height, f.bounds, f.current.at, f.current::pixelAt)
        assertEquals(TerminalParticleReadStatus.GEOMETRY_CHANGED, resized.status)
        assertTrue(resized.cluster.isEmpty())
        val cropped = f.bounds.copy(left = f.bounds.left + 1)
        val moved = reader.measure(f.width + 1, f.height, cropped, f.current.at + 50_000_000, f.current::pixelAt)
        assertEquals(TerminalParticleReadStatus.GEOMETRY_CHANGED, moved.status)
        assertTrue(moved.cluster.isEmpty())
    }

    @Test fun `a newer invalid image also prevents delayed frames from rebuilding an older baseline`() {
        val f = fixture("cramorant-finish", 672)
        val reader = TerminalParticleFrameMeasurer()
        f.read(reader, f.previous)
        val invalid = reader.measure(0,0,BattleCropBounds(0,0,0,0),f.current.at + 1) { _, _ -> error("Invalid") }
        assertEquals(TerminalParticleReadStatus.INVALID, invalid.status)
        assertEquals(TerminalParticleReadStatus.OLD_FRAME, f.read(reader, f.current).status)
        assertEquals(TerminalParticleReadStatus.BASELINE, f.read(reader, f.current, at = f.current.at + 2).status)
    }

    @Test fun `unreadable pixels interrupt continuity instead of looking like an absent particle`() {
        val f = fixture("cramorant-finish", 672)
        for (throws in listOf(false, true)) {
            val reader = TerminalParticleFrameMeasurer()
            f.read(reader, f.previous)
            val unreadable = reader.measure(f.width, f.height, f.bounds, f.previous.at + 1) { _, _ ->
                if (throws) throw IllegalStateException("Frame lost") else 0
            }
            assertEquals(TerminalParticleReadStatus.UNREADABLE, unreadable.status)
            assertEquals(TerminalParticleReadStatus.BASELINE, f.read(reader, f.current).status)
        }
    }

    @Test fun `whiteout is unavailable clutter rather than a confirmed absence of this pattern`() {
        val reader = TerminalParticleFrameMeasurer()
        val b = BattleCropBounds(0,0,64,64)
        reader.measure(64,64,b,1) { _, _ -> 0xff102030.toInt() }
        val result = reader.measure(64,64,b,2) { _, _ -> -1 }
        assertEquals(TerminalParticleReadStatus.CLUTTER, result.status)
        assertTrue(result.cluster.isEmpty())
    }

    @Test fun `invalid or excessive territories are rejected before allocation and pixel access`() {
        val reader = TerminalParticleFrameMeasurer()
        for ((index,b) in listOf(BattleCropBounds(-1,0,100,100), BattleCropBounds(0,0,2400,2400), BattleCropBounds(20,20,10,10)).withIndex()) {
            val result = reader.measure(2400,2400,b,10L + index) { _, _ -> error("Invalid territory must not be read") }
            assertEquals(TerminalParticleReadStatus.INVALID, result.status)
        }
    }

    private fun fixture(name: String, width: Int) = fixtures.single { it.name == name && it.width == width }

    private fun assertReference(f: Fixture, result: TerminalParticleFrameMeasurement) {
        assertEquals("${f.name} ${f.width}", TerminalParticleReadStatus.SAMPLED, result.status)
        val reference = f.json.getValue("referenceCluster").jsonArray.map { it.jsonObject }
        assertEquals("${f.name} ${f.width}", reference.size, result.cluster.size)
        for (point in reference) {
            val x = point.getValue("x").jsonPrimitive.double
            val y = point.getValue("y").jsonPrimitive.double
            val actual = result.cluster.singleOrNull { kotlin.math.abs(it.x - x) < 1e-8 && kotlin.math.abs(it.y - y) < 1e-8 }
            assertNotNull("${f.name} ${f.width}: missing $x,$y", actual)
            assertEquals(point.getValue("area").jsonPrimitive.int, requireNotNull(actual).area)
            assertTrue(actual.freshFraction >= .3)
            assertTrue(actual.haloCoverage >= .5)
            assertTrue(actual.haloWeakestQuadrant >= .25)
            assertTrue(actual.brightnessFalloff >= 15)
        }
    }

    private class Fixture(val json: JsonObject) {
        val name = json.getValue("name").jsonPrimitive.content
        val width = json.getValue("width").jsonPrimitive.int
        val height = json.getValue("height").jsonPrimitive.int
        private val coords = json.getValue("bounds").jsonArray.map { it.jsonPrimitive.int }
        val bounds = BattleCropBounds(coords[0], coords[1], coords[2], coords[3])
        private val frames = json.getValue("frames").jsonArray.map { Frame(it.jsonObject, bounds) }
        val previous get() = frames[0]
        val current get() = frames[1]
        fun read(reader: TerminalParticleFrameMeasurer, frame: Frame, at: Long = frame.at) =
            reader.measure(width, height, bounds, at, frame::pixelAt)
        fun pair(): TerminalParticleFrameMeasurement = TerminalParticleFrameMeasurer().let { reader ->
            assertEquals(TerminalParticleReadStatus.BASELINE, read(reader, previous).status)
            read(reader, current)
        }
    }

    private class Frame(json: JsonObject, private val bounds: BattleCropBounds) {
        val at = json.getValue("sourceAtNanos").jsonPrimitive.long
        private val bytes = resource(json.getValue("file").jsonPrimitive.content)
        private val image: BufferedImage = ImageIO.read(bytes.inputStream())
        init {
            assertEquals(json.getValue("sha256").jsonPrimitive.content,
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
            assertEquals(bounds.right - bounds.left, image.width)
            assertEquals(bounds.bottom - bounds.top, image.height)
        }
        fun pixelAt(x: Int, y: Int): Int {
            check(x in bounds.left until bounds.right && y in bounds.top until bounds.bottom)
            return image.getRGB(x - bounds.left, y - bounds.top)
        }
    }

    companion object {
        private fun resource(name: String): ByteArray = requireNotNull(TerminalParticleFrameMeasurerTest::class.java
            .getResourceAsStream("/battle/recorded-evidence/2026-11-12-terminal-particles/$name")).use { it.readBytes() }
        private val fixtures by lazy {
            Json.parseToJsonElement(resource("provenance.json").toString(Charsets.UTF_8)).jsonObject
                .getValue("cases").jsonArray.map { Fixture(it.jsonObject) }
        }
    }
}
