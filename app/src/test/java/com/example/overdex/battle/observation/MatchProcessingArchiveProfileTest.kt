package com.example.overdex.battle.observation

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.*
import com.example.overdex.data.*
import com.example.overdex.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.lang.management.ManagementFactory
import java.util.UUID
import java.util.zip.ZipFile

/** Opt-in desktop experiment; never reads a phone or embeds private matches in the repository. */
class MatchProcessingArchiveProfileTest {
    private lateinit var outputDirectory: File
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; classDiscriminator = "kind" }
    @Test fun profileSavedMatches() = runBlocking {
        val input = System.getenv("OVERDEX_PROFILE_INPUT")
        assumeTrue("Set OVERDEX_PROFILE_INPUT to the preserved archive folder", input != null)
        val out = (System.getenv("OVERDEX_PROFILE_OUTPUT")?.let(::File) ?: File(input!!, "processing-profile")).apply { mkdirs() }
        outputDirectory = out
        val archives = File(input!!).listFiles()!!.filter { it.name.endsWith(".zip") }.sortedBy { it.name }
        assertEquals(2, archives.size)
        for (zip in archives) {
            val archive = ZipFile(zip).use { json.decodeFromString<MatchArchive>(it.getInputStream(it.getEntry("timeline.json")).reader().readText()) }
            val source = archive.articles.filter { it.sequenceNumber != null }.sortedBy { it.sequenceNumber }
            assertEquals(source.size, source.map { it.sequenceNumber }.distinct().size)
            val failures = mutableListOf<String>()
            val records = source.mapNotNull { a -> runCatching { a to ProcessingArchiveInput.restore(a) }
                .getOrElse { failures += "${a.articleId}: ${a.payload.javaClass.simpleName}: ${it.message}"; null } }
            File(out, "${archive.matchId}-mapping.json").writeText(buildJsonObject {
                put("archiveArticles",archive.articles.size);put("directRecords",source.size);put("mappedRecords",records.size)
                put("derivedExcluded",archive.articles.size-source.size);put("failures",JsonArray(failures.map(::JsonPrimitive)))
                put("timingLimitation","Custody arrival is not archived; paced mode uses a nondecreasing capture-time envelope in custody order. It is not original arrival timing.")
                put("compactLimitation","Uncited capture records may be absent. Witnesses are not rerun. Archived diagnostic observations are replayed once; newly generated host-clock diagnostics are counted but not re-admitted.")
            }.toString())
            assertTrue("Unmapped records: $failures",failures.isEmpty())
            if (System.getenv("OVERDEX_PROFILE_MAPPING_ONLY") == "1") continue
            val knowledge = knowledge()
            val uncachedWarm = runCase(archive.matchId,records,knowledge,false,true,useCache=false)
            val warm = runCase(archive.matchId,records,knowledge,false,true)
            assertEquals("Cache changed semantic output",uncachedWarm.semantic,warm.semantic)
            File(out,"${archive.matchId}-warmup.json").writeText(warm.report.toString())
            val repetitions = System.getenv("OVERDEX_PROFILE_REPEATS")?.toInt() ?: 3
            for (paced in listOf(false,true)) {
                if (paced && System.getenv("OVERDEX_PROFILE_BURST_ONLY") == "1") continue
                repeat(repetitions) { i ->
                    val result = runCase(archive.matchId,records,knowledge,paced,true)
                    File(out,"${archive.matchId}-${if(paced) "paced" else "burst"}-${i+1}.json").writeText(result.report.toString())
                    assertEquals("Instrumentation changed semantic output",warm.semantic,result.semantic)
                    if (!paced) {
                        val control = runCase(archive.matchId,records,knowledge,false,true,useCache=false)
                        val controlDirectory = File(out,"uncached").apply { mkdirs() }
                        File(controlDirectory,"${archive.matchId}-burst-${i+1}.json").writeText(control.report.toString())
                        assertEquals("Cache changed semantic output",control.semantic,result.semantic)
                    }
                }
            }
            val plain = runCase(archive.matchId,records,knowledge,false,false)
            assertEquals("Enabled/disabled semantic equivalence",warm.semantic,plain.semantic)
            File(out,"${archive.matchId}-uninstrumented.json").writeText(plain.report.toString())
        }
    }
    private data class Result(val report: JsonObject,val semantic: List<String>)
    private suspend fun runCase(id:String, rows:List<Pair<ArchivedRealityArticle,TestimonyPayload>>,
        knowledge:PokemonKnowledge,paced:Boolean,enabled:Boolean,useCache:Boolean = true):Result = coroutineScope {
        val cpu=ManagementFactory.getThreadMXBean()
        val diagnostic=if(enabled) MatchProcessingDiagnostics(System::nanoTime) { cpu.currentThreadCpuTime } else null
        val custody=ProfileCustody(); val publicationTime=java.util.concurrent.atomic.AtomicLong(0); val timeline=InMemoryRealityTimeline()
        DroidballOverlayPresentation.reset()
        val match=Match(id,custody=custody,realityTimeline=timeline,pokemonKnowledge=knowledge,
            processingDiagnostics=diagnostic,recordArticleId={publicationTime.set(custody.publicationTime(it));custody.articleId(it)},publicationClockMillis=publicationTime::get,
            faintHistoryCache=if(useCache) com.example.overdex.battle.inference.FaintHistoryCache() else null)
        val start=System.nanoTime(); val progress=mutableListOf<JsonElement>()
        val sampler=launch(Dispatchers.Default) {
            while(isActive) {
                val s=diagnostic?.snapshot();val records=custody.getRecords().filterIsInstance<TestimonyRecord>()
                val pending=records.filter { it.sequenceNumber > (s?.lastCompletedSequence ?: -1) }
                val oldest=pending.firstOrNull()?.let { custody.arrived(it.sequenceNumber) }
                val now=System.nanoTime()
                progress += buildJsonObject {
                    put("elapsedMs",(now-start)/1e6);put("accepted",records.size);put("completed",s?.completed ?: 0)
                    put("pending",pending.size);put("oldestPendingMs",oldest?.let { (now-it)/1e6 } ?: 0.0)
                    put("activeSequence",s?.activeSequence);put("lastCompletedSequence",s?.lastCompletedSequence);put("activeStage",s?.activeStage);put("activeAgeMs",(s?.activeAgeNanos ?: 0)/1e6)
                }
                File(outputDirectory, "progress-current.json").writeText(progress.last().toString())
                delay(1000)
            }
        }
        try {
            var offset=0L;val first=rows.minOf { it.first.perceivedAt }
            rows.forEach { (a,p) ->
                if(paced) {offset=maxOf(offset,a.perceivedAt-first);val remaining=offset-(System.nanoTime()-start)/1_000_000; if(remaining>0) delay(remaining)}
                custody.feed(a,p)
            }
            withTimeout(300_000) { match.awaitAcceptedEvidencePublished() }
            val drainedMs=(System.nanoTime()-start)/1e6
            sampler.cancelAndJoin()
            val raw=timeline.getArticles().filter { it.sequenceNumber != null }.associateBy { it.id.value }
            rows.forEach { (a,p) ->
                val actual=raw.getValue(a.articleId)
                assertEquals(p,actual.payload);assertEquals(a.monotonicTimeNanos,actual.monotonicTimeNanos)
                assertEquals(a.recordedAt,actual.recordedAt);assertEquals(a.perceivedAt,actual.perceivedAt);assertEquals(a.evidenceReferences.orEmpty(),actual.evidenceReferences.orEmpty())
            }
            val snap=diagnostic?.snapshot()
            val originalIds=rows.map { it.first.articleId }.toSet()
            val uuid=Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
            val semantic=timeline.getArticles().filter { it.sequenceNumber == null }.map {
                val mapped=RealityArticleArchiveMapper.map(it)
                // UUIDs are assigned anew to derivations. Compare payload, source and source time, not UUIDs.
                uuid.replace("${mapped.sourceId}|${mapped.perceivedAt}|${mapped.monotonicTimeNanos}|${json.encodeToString(mapped.payload)}|${mapped.confidence}|${mapped.predecessorIds}") { if(it.value in originalIds) it.value else "<derived-id>" }
            }
            val report=buildJsonObject {
                put("histogramUpperBoundsNanos",JsonArray(MatchProcessingDiagnostics.BOUNDS.map(::JsonPrimitive)))
                put("completedTestimony",snap?.completed);put("acceptedTestimony",custody.getRecords().filterIsInstance<TestimonyRecord>().size)
                put("cacheEnabled", useCache)
                match.faintHistoryCache?.snapshot()?.let { c -> put("cache",buildJsonObject {
                    put("hits",c.hits);put("misses",c.misses);put("oversized",c.oversized);put("evictions",c.evictions)
                    put("entries",c.entries);put("retainedRows",c.retainedRows)
                }) }
                put("mode",if(paced) "capture-paced" else "burst");put("instrumented",enabled)
                put("elapsedMs",drainedMs);put("regeneratedDiagnosticsExcluded",custody.regeneratedDiagnostics);put("inputRecords",rows.size);put("publishedArticles",timeline.getArticles().size)
                put("progress",JsonArray(progress));put("stages",buildJsonObject {
                    snap?.stages?.forEach { (key,v) ->put(key,buildJsonObject {
                        put("calls",v.calls);put("elapsedMs",v.elapsedNanos/1e6);put("cpuMs",v.cpuNanos/1e6);put("maxMs",v.maximumNanos/1e6)
                        put("maxCpuMs",v.maximumCpuNanos/1e6);put("cpuHistogram",JsonArray(v.cpuHistogram.map(::JsonPrimitive)));put("invalidCpu",v.invalidCpuSamples);put("histogram",JsonArray(v.histogram.map(::JsonPrimitive)))
                    }) }
                });put("rebuilds",buildJsonObject { snap?.rebuilds?.forEach { (key,v) ->put(key,buildJsonObject {
                    put("calls",v.calls);put("totalInputs",v.totalInputs);put("maxInputs",v.maximumInputs)
                }) } })
                put("milestones",JsonArray(rows.filter { (_,p)->p is ActivePokemonSpeciesWitnessed || p is ChargeMoveUsedAnnounced || p is OpponentBattleResourceCountMeasured || p is PlayerPokeBallCountMeasured || (p is RawTestimony && p.data.toString().contains("used", ignoreCase=true)) }.map { (a,_)->buildJsonObject {
                    put("inputIndex",rows.indexOfFirst { it.first.articleId == a.articleId });put("publicationOffsetMs",a.recordedAt-rows.first().first.recordedAt);put("captureOffsetMs",a.perceivedAt-first);put("source",a.sourceId);put("payload",json.encodeToJsonElement(a.payload))
                } }))
            }
            Result(report,semantic)
        } finally {sampler.cancelAndJoin();match.release();DroidballOverlayPresentation.reset()}
    }
    private fun knowledge():PokemonKnowledge {
        val loader=GameMasterLoader { File("src/main/assets/gamemaster.json").readText() }
        fun domain(p:GameMasterPokemon):Pokemon {
            fun move(id:String,fast:Boolean):Move?=loader.getMove(id)?.let { m ->Move(m.name,PokemonType.valueOf(m.type.uppercase()),m.power,
                if(fast)m.energyGain else kotlin.math.abs(m.energy),fast,if(fast)m.turns else null) }
            return Pokemon(p.dex,p.speciesName,p.types.filter { it.uppercase() != "NONE" }.map { PokemonType.valueOf(it.uppercase()) },"",
                fastMoves=p.fastMoves.mapNotNull {move(it,true)},chargedMoves=p.chargedMoves.mapNotNull {move(it,false)})
        }
        return object:PokemonKnowledge {
            override suspend fun getPokemonByName(name:String)=PokemonFormReference.byName(name,loader.allPokemon())?.let(::domain)
            override suspend fun getPokemonById(id:Int)=loader.getPokemonByDex(id)?.let(::domain)
            override suspend fun getAllSpeciesNames()=loader.allPokemonNames()
        }
    }
}

/** Replay only the archived acceptance ledger; never re-admit regenerated host-clock diagnostics. */
private class ProfileCustody:TestimonyCustody {
    private val records=mutableListOf<CustodyRecord>();private val ids=mutableMapOf<Long,String>();private val arrivals=mutableMapOf<Long,Long>()
    @Volatile var regeneratedDiagnostics = 0L; private set
    private val publishedTimes=mutableMapOf<Long,Long>()
    private val count=MutableStateFlow(0)
    private fun flowRecords():Flow<CustodyRecord> = flow { var next=0;count.collect { n->while(next<n) emit(synchronized(this@ProfileCustody){records[next++]}) } }
    override val custodyRecordFlow=flowRecords()
    override val testimonyFlow=flowRecords().filterIsInstance<TestimonyRecord>()
    @Synchronized fun articleId(sequence:Long)=ArticleId(ids.getValue(sequence))
    @Synchronized fun publicationTime(sequence:Long)=publishedTimes.getValue(sequence)
    @Synchronized fun arrived(sequence:Long)=arrivals.getValue(sequence)
    @Synchronized override fun getRecords()=records.toList()
    @Synchronized private fun <T:CustodyRecord> add(id:String?=null,make:(Long)->T):T {
        val r=make(records.size.toLong());ids[r.sequenceNumber]=id ?: UUID.randomUUID().toString();arrivals[r.sequenceNumber]=System.nanoTime()
        records+=r;count.value=records.size;return r
    }
    fun feed(a:ArchivedRealityArticle,p:TestimonyPayload) { add(a.articleId) { n ->
        publishedTimes[n]=a.recordedAt
        if(p is WitnessOperating) SourceAvailabilityRecord(n,a.perceivedAt,SourceId(a.sourceId),p.operating,a.monotonicTimeNanos!!)
        else TestimonyRecord(n,a.perceivedAt,SourceId(a.sourceId),p,a.confidence,a.evidenceReferences.orEmpty(),a.monotonicTimeNanos!!)
    } }
    override fun submitAvailability(sourceId:SourceId,available:Boolean,timestamp:Long)=add { SourceAvailabilityRecord(it,timestamp,sourceId,available,System.nanoTime()) }
    override fun submitInputAvailability(sourceId:SourceId,available:Boolean,timestamp:Long)=add { SourceInputRecord(it,timestamp,sourceId,available,System.nanoTime()) }
    override fun submitTestimony(sourceId:SourceId,payload:TestimonyPayload,timestamp:Long,confidence:Float?,evidenceReferences:List<String>)=
        submitTestimony(sourceId,payload,timestamp,confidence,evidenceReferences,System.nanoTime())
    @Synchronized override fun submitTestimony(sourceId:SourceId,payload:TestimonyPayload,timestamp:Long,confidence:Float?,evidenceReferences:List<String>,monotonicTimeNanos:Long): TestimonyRecord {
        check(sourceId.id in setOf("SPECIES_CHECK_COORDINATOR", "HP_OBSERVATION_PIPELINE")) { "Unexpected regenerated testimony: $sourceId/$payload" }
        regeneratedDiagnostics++
        return TestimonyRecord(-1,timestamp,sourceId,payload,confidence,evidenceReferences,monotonicTimeNanos)
    }
}
