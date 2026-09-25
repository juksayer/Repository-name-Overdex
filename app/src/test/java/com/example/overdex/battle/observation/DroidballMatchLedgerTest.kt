package com.example.overdex.battle.observation

import com.example.overdex.battle.archive.MatchArchiveSource
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import org.junit.Assert.assertEquals
import org.junit.Test

class DroidballMatchLedgerTest {
 @Test fun `keeps each completed match rather than replacing the prior one`() {
  val ledger=DroidballMatchLedger()
  ledger.complete(MatchArchiveSource(MatchId("one"), InMemoryRealityTimeline()))
  ledger.complete(MatchArchiveSource(MatchId("two"), InMemoryRealityTimeline()))
  assertEquals(listOf("one","two"), ledger.completedMatches().map { it.matchId.value })
 }
}
