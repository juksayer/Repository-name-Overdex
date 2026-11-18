package com.example.overdex.battle.observation

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.CompleteHpBarObserved
import com.example.overdex.battle.replay.ReplayRetainedAudioRecovery
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class RecordedMoondropChargeHudTest {
    @Test fun `both complete bars warn the HUD before Earth Power or its delayed grade`() {
        val path = System.getenv("OVERDEX_MOONDROP_FAST_FINISH_CONTROL")
        assumeTrue(!path.isNullOrBlank())
        val file = File(requireNotNull(path))
        assertEquals("78384b3f6bb3fd2fddec52a4785e3e3ebee02dbf5ba04166f350b6a0896548f4",
            ReplayRetainedAudioRecovery.sha(file.readBytes()))
        val archive = file.inputStream().use(MatchArchivePackageReader::read)
        val go = archive.articles.single { it.payload is ArchivedMatchStarted }.monotonicTimeNanos!!
        val window = ChargeTextProtectionWindow()
        val seen = mutableListOf<Long>()
        val profiles = archive.articles.filter { it.payload is ArchivedCompleteHpBarObserved }
            .sortedWith(compareBy<ArchivedRealityArticle> { it.monotonicTimeNanos }.thenBy { it.articleId })
        for (row in profiles) {
            val p = row.payload as ArchivedCompleteHpBarObserved
            val at = row.monotonicTimeNanos!! - go
            val bar = CompleteHpBarObserved(ActivePokemonSide.valueOf(p.side), p.barLeft, p.barTop, p.barRight,
                p.barBottom, p.sourceWidth, p.sourceHeight, p.interiorColumns, p.liveColumns, p.damageColumns,
                p.status, p.candidateStartedAtNanos, p.appearanceStartedAtNanos, p.railBrightness, p.backgroundBrightness)
            val before = window.isProtected(at)
            window.observeBar(bar, at, at)
            if (!before && window.isProtected(at)) seen += at
        }
        assertEquals(30.606, seen.last() / 1e9, .002)
        assertTrue(window.isProtected(36_106_000_000L)) // Visible GREAT, still queued for OCR.
        assertTrue(window.isProtected(37_515_000_000L)) // Independently identified Earth Power sound.
        assertTrue(window.isProtected(38_858_000_000L)) // Vaporeon's departure cry.
        assertFalse(window.isProtected(44_245_000_000L))
    }
}
