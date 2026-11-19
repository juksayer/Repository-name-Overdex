package com.example.overdex.battle.replay

import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.archive.*
import com.example.overdex.data.GameMasterLoader
import com.example.overdex.data.LocalSpriteProvider
import com.example.overdex.data.PokemonRepository
import com.example.overdex.data.local.PokedexDatabase
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class RecordedMoondropFaintCryDeviceTest {
    private fun review(argument: String, fileName: String, expectedHash: String,
        controls: ((MatchArchive, ReplayAudioRecovery, (MatchArchive, ReplayAudioRecovery) -> MatchReplayModel) -> Unit)? = null,
        verify: (MatchReplayModel) -> Unit) = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString(argument) == "true")
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(ctx.filesDir, "match_archives/$fileName")
        val hash = ReplayRetainedAudioRecovery.sha(file.readBytes())
        assertEquals(expectedHash, hash)
        val archive = file.inputStream().use(MatchArchivePackageReader::read)
        val catalog = GameMasterLoader(ctx)
        val repo = PokemonRepository(PokedexDatabase.getDatabase(ctx).pokemonDao(), LocalSpriteProvider(ctx.assets), catalog)
        val crops = ArchivedSpeciesCropRecognizer.recognize(archive, file::inputStream, repo.getAllSpeciesNames(),
            resolveSpeciesId = { repo.getPokemonByName(it)?.id })
        val species = (MatchReplayModel.referencedSpeciesNames(archive) + crops.map { it.speciesName })
            .mapNotNull { repo.getPokemonByName(it) }
        val fast = species.associate { it.name to it.fastMoves }
        val charged = species.associate { it.name to it.chargedMoves }
        val asset = { name: String -> ctx.assets.open(name).use { it.readBytes() } }
        val participants = MatchReplayModel.audioParticipantNames(archive, crops)
        val inBattle = species.filter { it.name in participants }
        val audio = ArchivedEndingAudioRecognizer.recognize(archive, inBattle.flatMap { it.fastMoves + it.chargedMoves }.distinct(),
            inBattle.map { it.id }.toSet(), file::inputStream, asset)
        val impact = ReplayImpactFinish.read(archive, File(ctx.filesDir, "artifacts/frames/sha256"),
            species.associate { it.name to (it.fastMoves + it.chargedMoves) }, audio, file::inputStream, asset)
        val last = ReplayLastOpponentFastFinish.read(archive, catalog, file::inputStream, asset)
        fun model(input: MatchArchive, sound: ReplayAudioRecovery) = MatchReplayModel(input, species.associate { it.name to it.id }, crops,
            fastMovesBySpeciesName = fast, chargedMovesBySpeciesName = charged,
            audioRecovery = sound, impactEvidence = impact, lastOpponentFastEvidence = last)
        verify(model(archive, audio))
        controls?.invoke(archive, audio, ::model)
        assertEquals(hash, ReplayRetainedAudioRecovery.sha(file.readBytes()))
    }

    @Test fun soundMatchedEarthPowerLandsBeforeVaporeonDeparts() = review(
        "recordedNoonEarthPower", "2026-11-19_12-30-25_a92c7c13-11e1-4594-b162-44e6ad4be0fa.odxmatch.zip",
        "1c3a43ab83900d67c5808d8c2adb538639ae44c132a2e5d757dee1598237e279",
        controls = { archive, audio, replay ->
            val original = replay(archive, audio)
            val go = requireNotNull(original.matchStartNanos)
            val reordered = replay(archive.copy(articles = archive.articles.reversed()), audio)
            assertEquals(original.finishingAttributions, reordered.finishingAttributions)
            fun rejects(rows: List<ArchivedRealityArticle>) {
                assertTrue(replay(archive.copy(articles = rows), audio).finishingAttributions.none { it.recipientSpeciesName == "Vaporeon" })
            }
            val competing = archive.articles.last { (it.payload as? ArchivedFastMoveUseObserved)?.damagedSide == "OPPONENT" }
            rejects(archive.articles + competing.copy(articleId = "another-hit", monotonicTimeNanos = go + 55_000_000_000L))
            val living = archive.articles.first { (it.payload as? ArchivedActivePokemonSpeciesWitnessed)?.speciesName == "Sealeo" }
            rejects(archive.articles + living.copy(articleId = "switched", monotonicTimeNanos = go + 55_000_000_000L))
            val hp = archive.articles.last { row ->
                val bar = row.payload as? ArchivedCompleteHpBarObserved
                bar?.side == "OPPONENT" && bar.status == "LIVE_COLOR" && bar.liveColumns > 0 &&
                    row.monotonicTimeNanos!! in go + 40_000_000_000L..go + 46_000_000_000L
            }
            rejects(archive.articles + hp.copy(articleId = "surviving-column", monotonicTimeNanos = go + 55_000_000_000L,
                payload = (hp.payload as ArchivedCompleteHpBarObserved).copy(liveColumns = 1, appearanceStartedAtNanos = null)))
            rejects(archive.articles.filterNot { (it.payload as? ArchivedBattleCryLocalized)?.speciesId == 134 })
            rejects(archive.articles.filterNot { (it.payload as? ArchivedAudioCaptured)?.sha256?.startsWith("befc91bc") == true })
        }
    ) { model ->
        val faint = requireNotNull(model.eventMarkers.singleOrNull { it.label == "Faint · Vaporeon · Earth Power by Camerupt" }) {
            "Replay markers: " + model.eventMarkers.filter { it.kind in setOf(ReplayEventMarkerKind.CHARGED_MOVE, ReplayEventMarkerKind.FAINT) }.map { it.label }
        }
        val link = requireNotNull(faint.finishingAttribution)
        val go = requireNotNull(model.matchStartNanos)
        assertEquals(52.65, (link.moveSourceAtNanos - go) / 1e9, .025)
        assertEquals(54.343, (link.landingAtNanos - go) / 1e9, .002)
        assertTrue(faint.faintTiming is ReplayFaintTiming.Window)
        assertTrue("Original announcement frame", "e106dcd2d92ddd723be745a3f8c1324b6edadb637217dfe320319b2da506b0ab" in link.moveArticleIds)
        assertEquals(1, model.eventMarkers.count { it.kind == ReplayEventMarkerKind.CHARGED_MOVE && it.finishingAttribution == link })
        assertNull(link.reception)
        val landing = model.sceneAt(link.landingAtNanos)
        assertEquals("Camerupt", landing.player?.speciesName)
        assertEquals("Vaporeon", landing.opponent?.speciesName)
        assertEquals(false, landing.opponent?.isFainted)
        val action = landing.chargedMoveActions.single { it.finishingAssociation == link }
        assertEquals(1f, ReplayChargePose.at(action, true).projectileProgress, 0f)
        assertTrue(model.sceneAt(faint.atNanos).opponent!!.isFainted)
        assertEquals(landing, model.sceneAt(link.landingAtNanos))
    }

    @Test fun onlySecondDragonPulseFinishesVaporeon() = review(
        "recordedMoondropTwoPulses", "2026-11-19_12-27-58_457f590b-6d03-4469-b223-b45fcf0ac2b9.odxmatch.zip",
        "d4c0a20b0004ad53144fbc4be8358661b49914daae078a2f35158f3a569f1338",
        controls = { archive, audio, replay ->
            val go = requireNotNull(replay(archive, audio).matchStartNanos)
            val cries = archive.articles.filter { (it.payload as? ArchivedBattleCryLocalized)?.speciesId == 134 }
                .sortedBy { it.monotonicTimeNanos }
            assertEquals(2, cries.size)
            assertEquals(61.546, (cries.first().monotonicTimeNanos!! - go) / 1e9, .001)
            val occurrence = audio.terminalWholeCries.single { it.speciesId == 134 }
            assertEquals(cries.last().monotonicTimeNanos, occurrence.estimatedOnsetNanos)
            fun uncredited(input: MatchArchive, sound: ReplayAudioRecovery = audio) {
                assertTrue(replay(input, sound).finishingAttributions.none { it.recipientSpeciesName == "Vaporeon" })
            }
            // Leave both original localized cries in the archive, but remove the recovered departure.
            uncredited(archive, audio.copy(terminalWholeCries = emptyList(), partialCryCandidates = emptyList()))
            val later = occurrence.copy(estimatedOnsetNanos = occurrence.estimatedOnsetNanos + 2_000_000_000L,
                fragments = occurrence.fragments.map { it.copy(captureAtNanos = it.captureAtNanos + 2_000_000_000L) })
            uncredited(archive, audio.copy(terminalWholeCries = listOf(occurrence, later)))
            val fast = archive.articles.last { it.payload is ArchivedFastMoveUseObserved }
            uncredited(archive.copy(articles = archive.articles + fast.copy(articleId = "competing-fast-hit",
                monotonicTimeNanos = occurrence.estimatedOnsetNanos - 100_000_000L)))
            val switch = archive.articles.first { (it.payload as? ArchivedActivePokemonSpeciesWitnessed)?.speciesName == "Sealeo" }
            uncredited(archive.copy(articles = archive.articles + switch.copy(articleId = "living-switch",
                monotonicTimeNanos = occurrence.estimatedOnsetNanos)))
        },
    ) { model ->
        val go = requireNotNull(model.matchStartNanos)
        val cry = model.audioRecovery.terminalWholeCries.single { it.speciesId == 134 }
        val cryMarker = model.eventMarkers.single { it.kind == ReplayEventMarkerKind.POSSIBLE_CRY &&
            kotlin.math.abs(it.atNanos - cry.estimatedOnsetNanos) < 20_000_000L }
        assertEquals("OPPONENT", cryMarker.side)
        assertTrue(cryMarker.label, "supports faint" in cryMarker.label)
        val hits = model.eventMarkers.filter { it.kind == ReplayEventMarkerKind.FAST_MOVE &&
            it.side == "PLAYER" && it.sourceAtNanos - go in 62_000_000_000L..66_000_000_000L }
        assertEquals(2, hits.size)
        assertTrue(hits.all { "Incinerate" in it.label })
        assertEquals(62.722, (hits.first().atNanos - go) / 1e9, .001)
        assertEquals(2.5, (hits.last().atNanos - hits.first().atNanos) / 1e9, .001)
        hits.forEachIndexed { index, marker ->
            val scene = model.sceneAt(marker.atNanos)
            val action = scene.fastMoveActions.single { it.side == "PLAYER" }
            assertEquals("Incinerate", action.moveName)
            assertEquals(1f, action.progress, 0f)
            assertEquals(if (index == 0) .91f else .83f, scene.opponentHp!!.filledFraction!!, .015f)
        }
        assertEquals(90.542, (cry.estimatedOnsetNanos - go) / 1e9, .001)
        assertTrue(cry.fragments.distinctBy { it.audioSha256 }.size >= 2)
        val pulses = model.eventMarkers.filter { it.kind == ReplayEventMarkerKind.CHARGED_MOVE && "Dragon Pulse" in it.label }
        assertEquals(2, pulses.size)
        assertNull(pulses.first().finishingAttribution)
        assertFalse(model.sceneAt(go + 78_500_000_000).opponent!!.isFainted)
        val faint = model.eventMarkers.single { it.label == "Faint · Vaporeon · Dragon Pulse by Turtonator" }
        val link = requireNotNull(faint.finishingAttribution)
        assertEquals(link, pulses.last().finishingAttribution)
        assertEquals(89.631, (link.moveSourceAtNanos - go) / 1e9, .001)
        assertTrue(faint.faintTiming is ReplayFaintTiming.Cry)
        assertEquals(90.542, (faint.atNanos - go) / 1e9, .001)
        assertTrue(link.faintArticleIds.containsAll(cry.fragments.map { it.articleId }))
        val landing = model.sceneAt(link.landingAtNanos)
        val action = landing.chargedMoveActions.single { it.finishingAssociation == link }
        assertEquals(1f, ReplayChargePose.at(action, true).projectileProgress, 0f)
        assertTrue(model.sceneAt(model.endNanos).opponent!!.isFainted)
        assertEquals(landing, model.sceneAt(link.landingAtNanos))
    }

    @Test fun seedBombLandsBeforeVaporeonsPartialCryAndFlashFaint() = review(
        "recordedMoondropSeedBomb", "2026-11-19_00-10-32_af45f682-e90e-477b-8192-19df8500b7ad.odxmatch.zip",
        "d1fff5da3571cdeb4ec05d5e1b6e49954bbd3e68c4c4d7b393a5f6f0fa9c69d5",
        controls = { archive, audio, model ->
            fun uncredited(input: MatchArchive = archive, sound: ReplayAudioRecovery = audio) {
                val replay = model(input, sound)
                assertTrue(replay.finishingAttributions.none { it.recipientSpeciesName == "Vaporeon" })
                assertTrue(replay.eventMarkers.none { it.kind == ReplayEventMarkerKind.FAINT && "Vaporeon" in it.label })
            }
            val tail = audio.partialCryCandidates.single { c -> c.fragments.any { it.articleId == "73250def-3107-417a-968b-a32e34d520f7" } }
            assertTrue(tail.fragments.all { it.correlation > .99f } && tail.competingScore < .15f)
            uncredited(sound = audio.copy(partialCryCandidates = emptyList()))
            uncredited(archive.copy(articles = archive.articles.filterNot { it.payload is ArchivedTerminalParticleObservations }))
            // Same predicted onset, but the actual matched samples precede the flash.
            val earlierTail = tail.copy(fragments = tail.fragments.map {
                it.copy(captureAtNanos = it.captureAtNanos - 800_000_000L, referenceOffsetNanos = it.referenceOffsetNanos - 800_000_000L)
            })
            uncredited(sound = audio.copy(partialCryCandidates = listOf(earlierTail)))
            val earlier = archive.articles.first { (it.payload as? ArchivedActivePokemonSpeciesWitnessed)?.speciesName == "Sealeo" }
            val switch = earlier.copy(articleId = "living-switch", monotonicTimeNanos = tail.estimatedOnsetNanos)
            val vaporeon = archive.articles.first { (it.payload as? ArchivedActivePokemonSpeciesWitnessed)?.speciesName == "Vaporeon" }
            val back = vaporeon.copy(articleId = "living-return", monotonicTimeNanos = tail.estimatedOnsetNanos + 500_000_000L)
            uncredited(archive.copy(articles = archive.articles + switch))
            uncredited(archive.copy(articles = archive.articles + switch + back))
            val live = archive.articles.last { (it.payload as? ArchivedCompleteHpBarObserved)?.let { bar ->
                bar.side == "OPPONENT" && bar.status == "LIVE_COLOR" } == true }
            uncredited(archive.copy(articles = archive.articles + live.copy(articleId = "surviving-hp",
                monotonicTimeNanos = tail.estimatedOnsetNanos + 2_000_000_000L,
                payload = (live.payload as ArchivedCompleteHpBarObserved).copy(appearanceStartedAtNanos = vaporeon.monotonicTimeNanos))))
        }
    ) { model ->
        val go = requireNotNull(model.matchStartNanos)
        val faint = model.eventMarkers.single { it.label == "Faint · Vaporeon · Seed Bomb by Gourgeist" }
        val link = requireNotNull(faint.finishingAttribution)
        assertTrue(faint.faintTiming is ReplayFaintTiming.Window)
        assertEquals(77.367, (faint.atNanos - go) / 1e9, .002)
        assertEquals(76.871, (link.landingAtNanos - go) / 1e9, .002)
        assertNull(link.reception)
        assertTrue(link.faintArticleIds.containsAll(listOf("73250def-3107-417a-968b-a32e34d520f7",
            "38efc694-bd7f-46f7-b063-dc44f14073b5")))
        assertEquals(1, model.eventMarkers.count { it.kind == ReplayEventMarkerKind.CHARGED_MOVE && it.finishingAttribution == link })
        val landing = model.sceneAt(link.landingAtNanos)
        val action = landing.chargedMoveActions.single { it.finishingAssociation == link }
        assertEquals("Seed Bomb", action.moveName)
        assertEquals("Vaporeon", landing.opponent!!.speciesName)
        assertEquals(1f, ReplayChargePose.at(action, true).projectileProgress, 0f)
        assertFalse(landing.opponent!!.isFainted)
        assertTrue(model.sceneAt(faint.atNanos).opponent!!.isFainted)
        assertEquals(landing, model.sceneAt(link.landingAtNanos))
    }

    @Test fun nightSlashLandsBeforeCradilysFullCry() = review(
        "recordedMoondropCradily", "2026-11-19_00-13-40_2329d744-dca0-440f-ae4a-8b7e82220806.odxmatch.zip",
        "6b197d6e08401638953d9018d39537152b3aaf79a57c018a3a355b2c0757b5ac",
        controls = { archive, audio, model ->
            fun uncredited(input: MatchArchive, sound: ReplayAudioRecovery = audio) {
                val replay = model(input, sound)
                assertTrue(replay.finishingChargeAssociations.none { it.recipientSpeciesName == "Cradily" })
                assertTrue(replay.eventMarkers.none { it.kind == ReplayEventMarkerKind.FAINT && "Cradily" in it.label })
            }
            assertTrue("Earlier opponent remains a rival", "Cry 254" in audio.referenceCandidateNames)
            assertFalse("Configured but absent Turtonator", "Cry 776" in audio.referenceCandidateNames)
            val at = audio.terminalWholeCries.first { it.speciesId == 346 }.estimatedOnsetNanos
            uncredited(archive, audio.copy(partialCryCandidates = emptyList()))
            uncredited(archive, audio.copy(terminalWholeCries = emptyList()))
            // Without the witnessed pre-QTE reception this use could still be pending.
            uncredited(archive.copy(articles = archive.articles.map { row ->
                if (row.articleId == "d44d126b-59e2-4ee2-b8ae-9a51d3b3f2f2") row.copy(predecessorIds = emptyList()) else row
            }))
            val live = archive.articles.single { it.articleId == "37cddc39-a6d3-49e5-9490-de9314b94ac2" }
            uncredited(archive.copy(articles = archive.articles + live.copy(articleId = "later-live",
                monotonicTimeNanos = at + 250_000_000L)))
            val switch = archive.articles.first { (it.payload as? ArchivedActivePokemonSpeciesWitnessed)?.speciesName == "Sceptile" }
            uncredited(archive.copy(articles = archive.articles + switch.copy(articleId = "living-switch",
                monotonicTimeNanos = at - 100_000_000L)))
        }
    ) { model ->
        val audio = model.audioRecovery
        assertTrue("Full cry, pool errors: ${audio.unavailableReferences}", audio.terminalWholeCries.any { it.speciesId == 346 })
        assertTrue("Second source", audio.partialCryCandidates.any { it.speciesId == 346 })
        val faint = model.eventMarkers.single { it.label == "Faint · Cradily · Night Slash by Greninja" }
        val link = requireNotNull(faint.finishingAttribution)
        assertEquals(1, model.eventMarkers.count { it.kind == ReplayEventMarkerKind.FAINT && "Cradily" in it.label })
        assertTrue(faint.faintTiming is ReplayFaintTiming.Cry)
        assertEquals(106.082, (faint.atNanos - requireNotNull(model.matchStartNanos)) / 1e9, .002)
        assertTrue(link.faintArticleIds.containsAll(listOf("51561b3f-c0a4-4ad7-943a-567bac4b9da7",
            "8f9850e4-39c0-4b6a-8df7-124ca824ef9d")))
        assertNull(link.reception)
        assertEquals(1, model.eventMarkers.count { it.kind == ReplayEventMarkerKind.CHARGED_MOVE && it.finishingAttribution == link })
        val landing = model.sceneAt(link.landingAtNanos)
        val action = landing.chargedMoveActions.single { it.finishingAssociation == link }
        assertEquals("Greninja", landing.player!!.speciesName)
        assertEquals("Cradily", landing.opponent!!.speciesName)
        assertEquals("Night Slash", action.moveName)
        assertEquals(1f, ReplayChargePose.at(action, true).projectileProgress, 0f)
        assertTrue(model.sceneAt(faint.atNanos).opponent!!.isFainted)
        assertEquals(landing, model.sceneAt(link.landingAtNanos))
    }

    @Test fun electrodeAndJolteonReceiveDistinctDragonPulses() = review(
        "recordedMoondropJolteon", "2026-11-18_21-40-23_650b264c-b95e-49ea-80a6-0133d1bca575.odxmatch.zip",
        "e59dbf0c72445ef59ebc9a0ec66aca02d65b3a0216bf14d9f00aeaeee44c3da9",
        controls = { archive, audio, model ->
            fun uncredited(input: MatchArchive, sound: ReplayAudioRecovery = audio) {
                assertTrue(model(input, sound).finishingChargeAssociations.none { it.recipientSpeciesName == "Jolteon" })
            }
            uncredited(archive, audio.copy(terminalWholeCries = emptyList()))
            val cryAt = audio.terminalWholeCries.first { it.speciesId == 135 }.estimatedOnsetNanos
            val added = archive.articles.last { it.payload is ArchivedFastMoveUseObserved }
            fun withEvent(payload: ArchivedTestimonyPayload) = archive.copy(articles = archive.articles +
                added.copy(articleId = "control", payload = payload, monotonicTimeNanos = cryAt - 10_000_000L))
            uncredited(withEvent(added.payload))
            val liveBar = archive.articles.first { row ->
                val bar = row.payload as? ArchivedActiveHpBarMeasured
                bar?.side == "OPPONENT" && bar.fillColor == "RED" && bar.filledFraction > 0f
            }
            uncredited(archive.copy(articles = archive.articles + liveBar.copy(articleId = "surviving-sliver",
                monotonicTimeNanos = cryAt + 250_000_000L)))
            val entry = archive.articles.first { (it.payload as? ArchivedActivePokemonSpeciesWitnessed)?.side == "OPPONENT" }
            uncredited(withEvent((entry.payload as ArchivedActivePokemonSpeciesWitnessed).copy(speciesName = "Electrode", speciesId = 101)))
            uncredited(archive.copy(articles = archive.articles.filterNot { it.payload is ArchivedTerminalParticleObservations }))
        }
    ) { model ->
        val faints = model.eventMarkers.filter { it.kind == ReplayEventMarkerKind.FAINT }
        val links = listOf("Electrode", "Jolteon").map { target ->
            val faint = faints.single { it.label == "Faint · $target · Dragon Pulse by Turtonator" }
            val link = requireNotNull(faint.finishingAttribution)
            val landing = model.sceneAt(link.landingAtNanos)
            val hit = landing.chargedMoveActions.single { it.finishingAssociation == link }
            assertEquals("PLAYER", hit.side)
            assertEquals(target, landing.opponent?.speciesName)
            assertEquals(1f, ReplayChargePose.at(hit, true).projectileProgress, 0f)
            assertTrue(model.sceneAt(faint.atNanos).opponent!!.isFainted)
            assertEquals(landing, model.sceneAt(link.landingAtNanos))
            assertEquals(1, model.eventMarkers.count { it.kind == ReplayEventMarkerKind.CHARGED_MOVE && it.finishingAttribution == link })
            link
        }
        assertTrue(links.first().landingAtNanos < links.last().moveSourceAtNanos)
        val final = links.last()
        assertNull(final.reception)
        assertTrue("Original Jolteon WAV and last-ball badge", final.faintArticleIds.containsAll(listOf(
            "9de59276-ae02-4f0a-ae20-31442a92af26", "9011a321-2b75-4b9d-bc37-d200cd5506d2")))
        assertTrue(faints.last().faintTiming is ReplayFaintTiming.Cry)
    }

    @Test fun finalIncinerateLandsBeforeTheCryMarker() = review(
        "recordedMoondropCry", "2026-11-18_15-17-09_78813508-a0cb-4dd1-9dd9-093c61210e39.odxmatch.zip",
        "0db534ad24460b1f77f9f2c8b5913ed229c922c3a6a903cb64f05fd74eba29d2"
    ) { model ->
        val go = requireNotNull(model.matchStartNanos)
        val faints = model.eventMarkers.filter { it.kind == ReplayEventMarkerKind.FAINT && it.side == "OPPONENT" }
        assertEquals(3, faints.size)
        val marker = faints.single { "Vaporeon" in it.label }
        assertEquals(go + 80_718_263_159L, marker.atNanos)
        assertTrue(marker.faintTiming is ReplayFaintTiming.Cry)
        val credit = requireNotNull(marker.finishingAttribution)
        assertEquals("Camerupt", credit.attackerSpeciesName)
        assertEquals("Incinerate", credit.moveName)
        val landing = model.sceneAt(credit.landingAtNanos)
        val hit = landing.fastMoveActions.single { it.finishingAssociation == credit }
        assertEquals(1f, hit.progress, .001f)
        assertFalse(landing.opponent!!.isFainted)
        assertTrue(model.sceneAt(marker.atNanos).opponent!!.isFainted)
        assertEquals(landing, model.sceneAt(credit.landingAtNanos))
    }

    @Test fun finalEarthPowerNamesBothTheLandingAndVaporeonFaint() = review(
        "recordedMoondropEarthPower", "2026-11-18_16-16-50_5e39cb12-af19-4841-8d2d-3a63539666e8.odxmatch.zip",
        "7477d19a5bf0af4ebeb34e4f0fae85f0e61d7aceaf424fc2c1920dfa395333c0"
    ) { model ->
        val faints = model.eventMarkers.filter { it.kind == ReplayEventMarkerKind.FAINT && it.side == "OPPONENT" }
        assertEquals(listOf("Sneasel", "Sealeo", "Vaporeon"), faints.map { it.label.split(" · ")[1] })
        val faint = faints.last()
        assertEquals("Faint · Vaporeon · Earth Power by Camerupt", faint.label)
        val link = requireNotNull(faint.finishingAttribution)
        val banners = setOf("10f4ac13-1aec-48cb-9465-f9615a611e8b", "14210950-b359-4eb2-a38b-da7218838e8f")
        assertTrue("The finishing move retains both banner reads", link.moveArticleIds.containsAll(banners))
        // Both live and Replay derive this faint from the original collapse frames and one-ball badge.
        val departure = setOf("a23dfea2-2ee5-4968-9a56-f8104194d091", "86ca216a-e961-4943-b85f-d710ae2dea77",
            "c82039a6-81c9-4880-bf7d-ba7f3f12f570")
        assertTrue("The faint retains the original departure and count evidence", link.faintArticleIds.containsAll(departure))
        val move = model.eventMarkers.single { it.kind == ReplayEventMarkerKind.CHARGED_MOVE && it.finishingAttribution == link }
        assertTrue(move.label.contains("Earth Power"))
        val landing = model.sceneAt(link.landingAtNanos)
        val action = landing.chargedMoveActions.single { it.finishingAssociation == link }
        assertEquals("Earth Power", action.moveName)
        assertEquals("Camerupt", landing.player?.speciesName)
        assertEquals("Vaporeon", landing.opponent?.speciesName)
        assertEquals(1f, ReplayChargePose.at(action, true).projectileProgress, 0f)
        assertTrue(model.sceneAt(faint.atNanos).opponent!!.isFainted)
        assertEquals(landing, model.sceneAt(link.landingAtNanos))
    }
}
