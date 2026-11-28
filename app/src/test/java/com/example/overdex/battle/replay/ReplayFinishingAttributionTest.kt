package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class ReplayFinishingAttributionTest {
    @Test fun `saved final cry credits the opposing Pokemon without inventing a projectile`() {
        val at = 7*S
        val cry = row("cry", at, ArchivedBattleCryLocalized(364, "reference", .97f, S, "test"),
            "LOCALIZED_BATTLE_CRY_WITNESS")
        val loss = faint.copy(payload = ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Sealeo", 364,
            com.example.overdex.battle.inference.DepartureCryFaint.LAST), evidenceReferences = listOf("opponent", "cry"))
        val decision = row("credit", at, ArchivedFinishingHitCredited("faint", "PLAYER", "Camerupt",
            "OPPONENT", "Sealeo", "Unknown move", "UNKNOWN", "cry",
            com.example.overdex.battle.inference.LiveFinishingHitResolver.FINAL_CRY_OWNER, at, at, "cry"),
            "FINISHING_HIT_INTERPRETER", listOf("faint", "opponent", "player-${S}", "cry"))
        val team = row("roster", S / 2,
            ArchivedPlayerTeamRosterConfirmation(true, listOf("Camerupt", "Turtonator", "Gourgeist")))
        val badge = row("sealeo-badge", 3*S, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sealeo", 364),
            "SPECIES_WITNESS")
        val ball = row("last-ball", 4*S, ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 1, 3),
            "OPPONENT_POKE_BALLS_WITNESS")
        val archive = match(listOf(team, cry.copy(articleId = "entry", monotonicTimeNanos = 2*S), badge,
            hp("live", 4*S, .03f, "RED"), ball, cry, loss, decision))
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive)
        val marker = model.eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT }
        assertEquals("Faint · Sealeo · KO credited to Camerupt", marker.label)
        assertEquals("PLAYER", marker.initiatingSide)
        assertEquals(at, marker.atNanos)
        assertTrue("credit" in marker.sourceArticleIds)
        assertTrue(model.finishingAttributions.isEmpty())
        assertTrue(model.sceneAt(at).fastMoveActions.isEmpty())
        assertTrue(model.sceneAt(at).chargedMoveActions.isEmpty())
        assertEquals(original, MatchArchiveSerializer.serialize(archive))

        val revoked = decision.copy(articleId = "withdrawal",
            payload = (decision.payload as ArchivedFinishingHitCredited).copy(valid = false))
        val revised = MatchReplayModel(archive.copy(articles = archive.articles + revoked))
        assertNull(revised.eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT }.lastWitnessedAttack)
    }

    @Test fun `credited fast landing keeps both appearances when the attacker has switched by the faint`() {
        val archive = match(fastRows() + player(6*S, "Turtonator", 776) + faint)
        val before = MatchArchiveSerializer.serialize(archive)
        val normal = MatchReplayModel(archive)
        val delayed = MatchReplayModel(archive.copy(articles = archive.articles.reversed().mapIndexed { index, article ->
            article.copy(recordedAt = 900_000L + index)
        }))
        val link = normal.finishingFastMoveAssociations.single()
        assertEquals(listOf(link), delayed.finishingAttributions)
        assertEquals("Camerupt", link.attackerSpeciesName)
        assertEquals("Sealeo", link.recipientSpeciesName)
        assertEquals("Incinerate", link.moveName)
        assertEquals(HIT, link.reception!!.atNanos)
        assertEquals(HIT - 100_000_000L, link.moveSourceAtNanos)
        assertEquals(7*S, link.faintSourceAtNanos)
        assertEquals(listOf("use", "tick", "empty", "profile", "faint").toSet(), link.evidenceArticleIds.toSet())
        val landing = normal.sceneAt(link.landingAtNanos)
        assertEquals(link, landing.fastMoveActions.single().finishingAssociation)
        assertEquals(1f, landing.fastMoveActions.single().progress, 0f)
        assertEquals(link, landing.opponent!!.finishingFastMoveAssociation)
        assertEquals("Turtonator", normal.sceneAt(7*S).player!!.speciesName)
        assertEquals("Camerupt", normal.sceneAt(link.landingAtNanos).player!!.speciesName)
        assertEquals(landing, normal.sceneAt(link.landingAtNanos))
        assertEquals(HIT, link.landingAtNanos)
        assertEquals(landing.copy(latestEvidenceLabel = ""), delayed.sceneAt(link.landingAtNanos).copy(latestEvidenceLabel = ""))
        val markers = normal.eventMarkers.filter { it.finishingAttribution == link }
        assertEquals(setOf(ReplayEventMarkerKind.FAST_MOVE, ReplayEventMarkerKind.FAINT), markers.map { it.kind }.toSet())
        assertEquals("Faint · Sealeo · Incinerate by Camerupt", markers.single { it.kind == ReplayEventMarkerKind.FAINT }.label)
        val faintTime = markers.single { it.kind == ReplayEventMarkerKind.FAINT }.faintTiming as ReplayFaintTiming.Window
        assertEquals(HIT, faintTime.by.atNanos)
        assertTrue(faintTime.by.articleId in link.reception.articleIds)
        assertEquals(faintTime, delayed.eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT }.faintTiming)
        assertEquals(before, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun blockedSurfThenIncinerateKeepsItsOwnLandingAndFaintCredit() {
        val used = row("surf", 4*S, ArchivedChargedMoveEnergySpent("OPPONENT", "Sealeo", "Surf", 40, "ANNOUNCEMENT"))
        val blockedAt = HIT - S/4
        val block = row("blocked", blockedAt, ArchivedRawText("BLOCKED!"), "MATCH_OUTCOME_TEXT_WITNESS")
        val replay = MatchReplayModel(match(fastRows() + listOf(used, block, faint)))
        val blocked = replay.sceneAt(blockedAt)
        assertEquals(ReplayProtectShield("PLAYER", 0), blocked.protectShield)
        val surf = blocked.chargedMoveActions.single { it.hitsProtectShield }
        assertEquals("Surf", surf.moveName)
        assertEquals(1f, surf.progress, 0f)
        assertNull(surf.finishingAssociation)
        val link = replay.finishingAttributions.single()
        val atHit = replay.sceneAt(HIT)
        val fire = atHit.fastMoveActions.single { it.finishingAssociation == link }
        assertEquals("Incinerate", fire.moveName)
        assertEquals(1f, fire.progress, 0f)
        assertEquals("Faint · Sealeo · Incinerate by Camerupt",
            replay.eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT }.label)
        assertEquals(blocked, replay.sceneAt(blockedAt))
        assertEquals(atHit, replay.sceneAt(HIT))
    }

    @Test fun `a complete charge recipient measurement must cite this completion phase`() {
        val source = chargeRows() + faint + player(6*S, "Turtonator", 776)
        val replay = MatchReplayModel(match(source))
        val link = replay.finishingChargeAssociations.single()
        assertEquals("Earth Power", link.moveName)
        assertEquals("Camerupt", link.attackerSpeciesName)
        assertEquals(HIT, link.reception!!.atNanos)
        assertEquals(listOf("empty", "profile"), link.reception.articleIds)
        assertEquals(4*S, link.moveSourceAtNanos)
        assertEquals(7*S, link.faintSourceAtNanos)
        assertTrue(link.attackerAppearanceAtNanos < 6*S)
        val atLanding = replay.sceneAt(link.landingAtNanos)
        assertEquals(link, atLanding.opponent!!.finishingChargeAssociation)
        val action = atLanding.chargedMoveActions.single()
        assertEquals(link, action.finishingAssociation)
        assertEquals(1f, ReplayChargePose.at(action, true).projectileProgress, 0f)
        assertEquals(link, replay.sceneAt(link.landingAtNanos + 33_000_000L).chargedMoveActions.single().finishingAssociation)
        assertEquals(2, replay.eventMarkers.count { it.finishingAttribution == link })
        assertEquals("Faint · Sealeo · Earth Power by Camerupt",
            replay.eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT }.label)
        assertEquals(HIT, (replay.eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT }
            .faintTiming as ReplayFaintTiming.Window).by.atNanos)
        replay.sceneAt(8*S)
        assertEquals(atLanding, replay.sceneAt(link.landingAtNanos))
        val delayed = MatchReplayModel(match(source.reversed().map { it.copy(recordedAt = 100_000L - it.perceivedAt) }))
        assertEquals(replay.finishingAttributions, delayed.finishingAttributions)
        assertEquals(atLanding.copy(latestEvidenceLabel = ""), delayed.sceneAt(link.landingAtNanos).copy(latestEvidenceLabel = ""))

        for (ref in listOf("ready", "unrelated", "wrong-side")) {
            val wrong = source.map { if (it.articleId == "empty") it.copy(evidenceReferences = listOf(ref)) else it }
            assertTrue(ref, MatchReplayModel(match(wrong)).finishingAttributions.isEmpty())
        }
    }

    @Test fun `unnamed charge needs unambiguous ownership to get generic credit`() {
        val rows = chargeRows().filterNot { it.articleId == "spent" } + faint
        val replay = MatchReplayModel(match(rows))
        val link = replay.finishingChargeAssociations.single()
        assertNull(link.moveName)
        assertEquals("Faint · Sealeo · Charged Move by Camerupt",
            replay.eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT }.label)
        val oppositeCue = row("other-ready", 2*S + S/2, ArchivedAttackIncoming)
        assertTrue(MatchReplayModel(match(rows + oppositeCue)).finishingAttributions.isEmpty())
    }

    @Test fun `later live sliver living switches missing caps and results with no faint keep credit unresolved`() {
        val returnAlive = listOf(
            row("replacement", 6*S, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Walrein", 365)),
            row("return", 7*S, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sealeo", 364)))
        val live = hp("later-sliver", HIT + 500_000_000L, .0001f, "RED")
        for (rows in listOf(fastRows() + faint + live, fastRows() + returnAlive,
            fastRows() + faint + returnAlive + hp("returned-hp", 8*S, .0001f, "RED"),
            fastRows().filterNot { it.articleId == "profile" } + faint,
            fastRows() + row("result", 7*S, ArchivedMatchEnded("WIN")))) {
            assertTrue(MatchReplayModel(match(rows)).finishingAttributions.isEmpty())
        }
        // A dying bar's preceding sliver is evidence of how much HP the final hit removed.
        assertEquals(1, MatchReplayModel(match(fastRows() + faint)).finishingAttributions.size)
    }

    @Test fun `last opponent receives the only completed unshielded charge without an invented HP frame`() {
        val charge = chargeRows().filter { it.articleId in setOf("ready", "typed", "spent", "no-shields") }
        val badge = row("last-ball", 2*S, ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 1, 3))
        val win = row("result", 8*S, ArchivedMatchEnded("WIN"))
        val original = match(charge + badge + faint + win)
        for (input in listOf(original, original.copy(articles = original.articles.reversed()))) {
            val replay = MatchReplayModel(input)
            val link = replay.finishingChargeAssociations.single()
            assertEquals(ReplayFinishingEvidence.LAST_RECIPIENT_BASIS, link.basis)
            assertEquals("Earth Power", link.moveName)
            assertEquals("Sealeo", link.recipientSpeciesName)
            assertTrue("last-ball" in link.evidenceArticleIds)
            assertNull(link.reception)
            val marker = replay.eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT }
            assertEquals(7*S, marker.atNanos)
            assertEquals(7*S, marker.sourceAtNanos)
        }
        val laterAttack = row("later-use", 6*S + S/2, ArchivedFastMoveUseObserved("new", "OPPONENT", "PLAYER",
            attackerSpeciesName = "Sealeo", evidenceKinds = listOf("HP_BORDER_PULSE"), basis = "test"))
        val switched = row("switch", 6*S, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Vaporeon", 134))
        val shield = row("blocked", 5*S, ArchivedRawText("Blocked!"), "ANNOUNCEMENT_WITNESS")
        for (events in listOf(charge + badge + win, charge + badge + faint + win + laterAttack,
            charge + badge + faint + win + switched, charge + badge + faint + win + shield,
            charge + badge + faint + win + hp("living", 6*S + S/2, .001f, "RED"))) {
            assertTrue(MatchReplayModel(match(events)).finishingAttributions.isEmpty())
        }
    }

    @Test fun `last recipient fast use requires an independently resolved faint and unique attack`() {
        val badge = row("last-ball", S, ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 1, 3))
        val use = row("use", HIT, ArchivedFastMoveUseObserved("unique", "PLAYER", "UNKNOWN",
            attackerSpeciesName = "Camerupt", evidenceKinds = listOf("HP_BORDER_PULSE"), basis = "test"))
        val won = row("win", 8*S, ArchivedMatchEnded("WIN"))
        val replay = MatchReplayModel(match(listOf(badge, use, faint, won)))
        val link = replay.finishingFastMoveAssociations.single()
        assertEquals(ReplayFinishingEvidence.LAST_RECIPIENT_BASIS, link.basis)
        assertEquals("Camerupt", link.attackerSpeciesName)
        assertNull(link.reception)
        assertEquals(link, replay.sceneAt(link.landingAtNanos).fastMoveActions.single().finishingAssociation)
        val marker = replay.eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT }
        assertEquals(7*S, marker.atNanos)
        assertEquals(7*S, marker.sourceAtNanos)
        val second = use.copy(articleId = "rival", payload = (use.payload as ArchivedFastMoveUseObserved).copy(useId = "other"))
        assertTrue(MatchReplayModel(match(listOf(badge, use, second, faint, won))).finishingAttributions.isEmpty())
        assertTrue(MatchReplayModel(match(listOf(badge, use, won))).finishingAttributions.isEmpty())
    }

    @Test fun `last witnessed fallback excludes verified survival only after the hit`() {
        val useAt = HIT - 250_000_000L
        val use = row("wing", useAt, ArchivedFastMoveUseObserved("wing", "PLAYER", "OPPONENT",
            attackerSpeciesName = "Camerupt", evidenceKinds = listOf("HP_DAMAGE_TICK"), basis = "test"),
            refs = listOf("tick"))
        val tick = row("tick", HIT, ArchivedActiveHpBarDamageTickMeasured("OPPONENT", .04f, .003f, .037f))
        val badge = row("last-ball", 2*S, ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 1, 3),
            "OPPONENT_POKE_BALLS_WITNESS")
        // The interval only supports the last-witnessed fallback, so verify that
        // the marker does not restore an occurrence ruled out by later HP.
        val finalFaint = faint.copy(monotonicTimeNanos = 9*S)
        fun marker(reading: ArchivedRealityArticle) = MatchReplayModel(match(listOf(badge, use, reading, tick, finalFaint)))
            .eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT }

        val beforeImpact = hp("before-impact", HIT - 100_000_000L, .04f, "RED")
        val survived = hp("post-impact", HIT + 200_000_000L, .003f, "RED")
        assertEquals(listOf("wing"), marker(beforeImpact).lastWitnessedAttack?.sourceArticleIds)
        assertNull(marker(survived).lastWitnessedAttack)
        assertEquals(listOf("wing"), marker(survived.copy(confidence = .50f)).lastWitnessedAttack?.sourceArticleIds)
    }

    @Test fun `orange pending damage can revert to red when the recipient knocks out the attacker`() {
        val orange = (profile.payload as ArchivedCompleteHpBarObserved).copy(
            status = "DAMAGE_COLOR", liveColumns = 0, damageColumns = 3)
        val opposingCharge = row("counter-charge", HIT + S, ArchivedChargedMoveEnergySpent(
            "OPPONENT", "Sealeo", "Surf", 45, "test"))
        val events = listOf(
            hp("red-before", HIT - S, .015f, "RED"),
            row("attempt", HIT - S/2, ArchivedFastMoveUseObserved("pending", "PLAYER", "OPPONENT",
                attackerSpeciesName = "Camerupt", evidenceKinds = listOf("HP_BORDER_PULSE"), basis = "test")),
            profile.copy(payload = orange), opposingCharge,
            row("counter-announced", HIT + S, ArchivedChargeMoveUsedAnnounced, refs = listOf("counter-charge")),
            row("player-faint", HIT + 2*S, ArchivedActivePokemonFaintedWitnessed("PLAYER", "Camerupt", 323, "independent")),
            hp("red-again", HIT + 3*S, .015f, "RED")
        )
        for (rows in listOf(events, events.reversed())) {
            val archive = match(rows)
            val saved = MatchArchiveSerializer.serialize(archive)
            val replay = MatchReplayModel(archive)
            assertTrue(replay.sceneAt(HIT + 3*S).player!!.isFainted)
            assertFalse(replay.sceneAt(HIT + 3*S).opponent!!.isFainted)
            assertTrue(replay.finishingAttributions.none { it.recipientSpeciesName == "Sealeo" })
            assertEquals(saved, MatchArchiveSerializer.serialize(archive))
        }
    }

    @Test fun `shielded charge yields to separately received pending fast hit`() {
        val incoming = listOf(
            row("incoming", 3*S, ArchivedAttackIncoming),
            row("sent", 4*S + 850_000_000L, ArchivedChargedMoveEnergySpent("OPPONENT", "Sealeo", "Surf", 45, "test")),
            row("shield-before", 4*S, ArchivedPlayerShieldCountMeasured(1)),
            row("shield-after", HIT + 1, ArchivedPlayerShieldCountMeasured(0)))
        val source = match(fastRows() + incoming + faint)
        // Reception and faint establish the use even without a move ID.
        val replay = MatchReplayModel(source.copy(articles = source.articles.filterNot { it.articleId == "name" }))
        val link = replay.finishingFastMoveAssociations.single()
        assertNull(link.moveName)
        assertEquals(HIT, link.landingAtNanos)
        assertEquals(link, replay.sceneAt(HIT).fastMoveActions.single().finishingAssociation)
        assertEquals("Faint · Sealeo · Fast Move by Camerupt",
            replay.eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT }.label)
        assertTrue(replay.finishingChargeAssociations.isEmpty())
    }

    @Test fun `shielded and competing unnamed attacks withhold charged credit while retaining the faint`() {
        val shielded = row("shielded", 4*S + 100_000_000L, ArchivedOpponentBattleResourceCountMeasured("SHIELDS", 0, 2))
        val prior = row("prior-shield", 3*S + 700_000_000L, ArchivedOpponentBattleResourceCountMeasured("SHIELDS", 1, 2))
        val pending = row("pending", 4*S - 50_000_000L,
            ArchivedFastMoveUseObserved("another", "PLAYER", "OPPONENT", evidenceKinds = listOf("HP_BORDER_PULSE"), basis = "test"))
        val unknown = row("unknown-charge", 4*S + 100_000_000L, ArchivedChargeMoveUsedAnnounced)
        for (rows in listOf(chargeRows().filterNot { it.articleId == "no-shields" } + listOf(prior, shielded),
            chargeRows() + pending, chargeRows() + unknown, chargeRows() + fastRows().filter { it.articleId in setOf("use", "tick") })) {
            val replay = MatchReplayModel(match(rows + faint))
            assertTrue(replay.finishingAttributions.toString(), replay.finishingAttributions.isEmpty())
            val marker = replay.eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT }
            val record = requireNotNull(replay.faintDecision(marker))
            assertNull(record.credit)
            assertFalse(record.entries.any { "credited use" in it.description })
            assertTrue(record.entries.isNotEmpty())
        }
    }

    @Test fun `a terminal tick cited as evidence also shares the canonical use marker`() {
        val rows = fastRows().map { if (it.articleId == "use") it.copy(predecessorIds = emptyList()) else it }
        val input = match(rows + faint)
        val replay = MatchReplayModel(input.copy(articles = input.articles.filterNot { it.articleId == "name" }))
        val link = replay.finishingFastMoveAssociations.single()
        assertNull(link.moveName)
        assertEquals(link, replay.eventMarkers.single { it.kind == ReplayEventMarkerKind.FAST_MOVE }.finishingAttribution)
        assertEquals(link, replay.sceneAt(HIT).fastMoveActions.single().finishingAssociation)
        assertEquals(link, replay.sceneAt(HIT + 33_000_000L).fastMoveActions.single().finishingAssociation)
    }

    @Test fun `one occurrence merges corroboration but fast and charge candidates compete`() {
        val fast = MatchReplayModel(match(fastRows() + faint)).finishingFastMoveAssociations.single()
        val charge = MatchReplayModel(match(chargeRows() + faint)).finishingChargeAssociations.single()
        val second = fast.copy(reception = null, moveArticleIds = fast.moveArticleIds + "same-use-audio",
            audioArticleIds = listOf("audio"), basis = "audio inference")
        val merged = ReplayFinishingAttributions.resolve(listOf(fast, second)) { true }.single()
        assertEquals(fast.reception, merged.reception)
        assertTrue(merged.evidenceArticleIds.containsAll(listOf("audio", "same-use-audio")))
        val conflict = ReplayFinishingAttributions.evaluate(listOf(fast, charge)) { null }
        assertTrue(conflict.links.isEmpty())
        assertEquals(setOf(fast, charge), conflict.rejections.map { it.candidate }.toSet())
        assertTrue(conflict.rejections.all { "disagree" in it.reason })
    }

    @Test fun `terminal faint can cite a completed charge with no measured reception`() {
        val rows = chargeRows().filterNot { it.articleId in setOf("empty", "profile", "no-shields") }
        val charge = ReplayFinishingChargeCandidate("PLAYER", "Earth Power", 3*S, 4*S, HIT, setOf("spent"), true)
        val evidence = ReplayFinishingEvidence(rows, ReplayHpEvidence(rows), listOf(charge), emptyList(),
            { side, _ -> if (side == "PLAYER") ReplayCombatant("Camerupt", 323, S) else ReplayCombatant("Sealeo", 364, S) },
            { _, _ -> S }, { _, _ -> null })
        val loss = ReplayKnockout("OPPONENT", "Sealeo", S, 6*S, 6*S, null,
            listOf("terminal-particles", "typed"), basis = com.example.overdex.battle.inference.TerminalChargedFaintResolver.BASIS)
        val link = evidence.observed(listOf(loss)).single()
        assertEquals("Earth Power", link.moveName)
        assertNull(link.reception)
        assertTrue(evidence.allows(link, listOf(loss)))
        assertTrue(evidence.observed(listOf(loss.copy(sourceArticleIds = listOf("terminal-particles", "ready")))).isEmpty())
    }

    @Test fun `Rocket video finding stays separate from the original archive`() {
        val path = System.getenv("OVERDEX_ROCKET_FINISH_ARCHIVE")
        assumeTrue(!path.isNullOrBlank())
        val file = File(requireNotNull(path))
        val hash = ReplayRetainedAudioRecovery.sha(file.readBytes())
        assertEquals("9ac743b936ac57ec7ec1c464281ccf4cbe8fc3108559d8edbbda7baffe1a2096", hash)
        val archive = ZipFile(file).use { zip ->
            MatchArchiveSerializer.deserialize(zip.getInputStream(zip.getEntry("timeline.json")).bufferedReader().readText())
        }
        val original = MatchArchiveSerializer.serialize(archive)
        for (input in listOf(archive, archive.copy(articles = archive.articles.reversed()))) {
            val model = pairedMirrorModel(input)
            assertTrue(model.eventMarkers.none { it.kind == ReplayEventMarkerKind.FAINT && "Annihilape" in it.label })
            assertTrue(model.finishingAttributions.none { it.recipientSpeciesName == "Annihilape" })
            assertTrue(model.eventMarkers.none { it.kind == ReplayEventMarkerKind.CHARGED_MOVE && "Earth Power" in it.label })
        }
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
        assertEquals(hash, ReplayRetainedAudioRecovery.sha(file.readBytes()))
    }

    @Test fun `only living HP captured for the fainted visit narrows the window`() {
        val lastAlive = hp("last-alive", 3*S, .001f, "RED")
        val away = row("switch", 4*S, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Vaporeon", 134))
        val back = row("return", 6*S, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sealeo", 364))
        val foreign = lastAlive.copy(articleId = "foreign", matchId = "other", monotonicTimeNanos = 6*S + S/10)
        val otherSide = hp("other-side", 6*S + S/4, .5f, "GREEN").let {
            it.copy(payload = (it.payload as ArchivedActiveHpBarMeasured).copy(side = "PLAYER"))
        }
        val events = listOf(lastAlive, away, hp("switch-hp", 5*S, .9f, "GREEN"), back, foreign, otherSide,
            hp("uncertain", 6*S + S/3, .02f, "RED").copy(confidence = .5f),
            hp("orange", 6*S + S/2, .01f, "ORANGE"))
        fun time(events: List<ArchivedRealityArticle>) = MatchReplayModel(match(events + faint))
            .eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT }.faintTiming
        val by = ReplayTimeBound(7*S, "faint")
        assertEquals(ReplayFaintTiming.Window(null, by), time(events))
        val live = hp("one-pixel", 6*S + 10, .001f, "RED")
        assertEquals(ReplayFaintTiming.Window(ReplayTimeBound(6*S + 10, "one-pixel"), by), time(events + live))
        assertEquals(ReplayFaintTiming.Unknown, time(events + live.copy(monotonicTimeNanos = 7*S)))
    }

    @Test fun onlyOneSidedDepartureCryCanSetTheMarker() {
        val live = hp("sliver", 5*S, .01f, "RED")
        val sound = ArchivedBattleCryLocalized(364, "reference", .97f, S, "test")
        val cry = row("heard", 6*S, sound, "LOCALIZED_BATTLE_CRY_WITNESS")
        fun timing(vararg events: ArchivedRealityArticle) =
            MatchReplayModel(match(listOf(live, faint) + events)).eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT }
        val expected = ReplayFaintTiming.Cry(ReplayTimeBound(6*S, "heard"))
        assertEquals(expected, timing(cry).faintTiming)
        assertEquals(6*S, timing(cry).atNanos)
        val replay = MatchReplayModel(match(listOf(live, faint, cry)))
        assertFalse(replay.sceneAt(6*S - 1).opponent!!.isFainted)
        assertTrue(replay.sceneAt(6*S).opponent!!.isFainted)
        assertEquals(faint.monotonicTimeNanos, replay.sceneAt(6*S).opponent!!.faintedAtNanos)
        assertEquals(expected, timing(cry.copy(recordedAt = 1000000)).faintTiming)
        for (control in listOf(
            listOf(cry.copy(payload = sound.copy(speciesId = 323))),
            listOf(cry.copy(payload = sound.copy(correlation = .6f))),
            listOf(cry.copy(monotonicTimeNanos = 2*S)),
            listOf(cry, cry.copy(articleId = "second", monotonicTimeNanos = 6*S + S/2)),
            listOf(cry, hp("still-alive", 6*S + S/2, .001f, "RED")),
            listOf(cry, player(5*S, "Sealeo", 364)),
        )) assertTrue(timing(*control.toTypedArray()).faintTiming is ReplayFaintTiming.Window)
    }

    private fun match(events: List<ArchivedRealityArticle>) = MatchArchive(matchId = "test",
        articles = listOf(row("go", S, ArchivedMatchStarted), player(S, "Camerupt", 323),
            row("opponent", S, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sealeo", 364)),
            row("name", 2*S, ArchivedFastMoveIdentified("PLAYER", "Camerupt", "Incinerate", 2_500_000_000L,
                cadenceSampleCount = 2, basis = "test"))) + events)

    private fun fastRows() = listOf(
        hp("sliver", HIT - S/2, .002f, "RED"), hp("empty", HIT, 0f, "EMPTY"), profile,
        row("tick", HIT, ArchivedActiveHpBarDamageTickMeasured("OPPONENT", .002f, 0f, .002f)),
        row("use", HIT - 100_000_000L, ArchivedFastMoveUseObserved("finishing-fast", "PLAYER", "OPPONENT",
            attackerSpeciesName = "Camerupt", evidenceKinds = listOf("HP_DAMAGE_TICK"), basis = "test"), refs = listOf("tick"))
            .copy(predecessorIds = listOf("tick")))

    private fun chargeRows() = listOf(
        hp("sliver", HIT - S/2, .002f, "RED"),
        hp("empty", HIT, 0f, "EMPTY", "CHARGED_IMPACT_OPPONENT_HP_BAR_FRAME_WITNESS", listOf("ready", "typed")),
        profile, row("ready", 3*S, ArchivedGetReadyWitnessed),
        row("spent", 4*S, ArchivedChargedMoveEnergySpent("PLAYER", "Camerupt", "Earth Power", 55, "test")),
        row("typed", 4*S, ArchivedChargeMoveUsedAnnounced, refs = listOf("spent")),
        row("no-shields", 3*S + 500_000_000L, ArchivedOpponentBattleResourceCountMeasured("SHIELDS", 0, 2)))

    private val profile = row("profile", HIT, ArchivedCompleteHpBarObserved("OPPONENT",
        400, 500, 600, 517, 1080, 2400, 192, 0, 0, "VISIBLE_EMPTY", null, null, .95f, .2f))
    private val faint = row("faint", 7*S, ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Sealeo", 364, "independent"))
    private fun player(at: Long, name: String, id: Int) =
        row("player-$at", at, ArchivedActivePokemonSpeciesWitnessed("PLAYER", name, id))
    private fun hp(id: String, at: Long, fraction: Float, color: String, source: String = "OPPONENT_ACTIVE_HP_BAR_WITNESS",
        refs: List<String> = emptyList()) = row(id, at,
        ArchivedActiveHpBarMeasured("OPPONENT", 400, 500, 600, 517, fraction, 1080, 2400, color), source, refs)
    private fun row(id: String, at: Long, payload: ArchivedTestimonyPayload, source: String = "TEST",
        refs: List<String> = emptyList()) = ArchivedRealityArticle(
        id, "test", at / 1_000_000L, at / 1_000_000L, source, payload, emptyList(), .95f, null, refs, at)

    private companion object {
        const val S = 1_000_000_000L
        const val HIT = 5*S
    }
}
