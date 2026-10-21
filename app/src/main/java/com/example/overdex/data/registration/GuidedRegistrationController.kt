package com.example.overdex.data.registration

import android.content.Context
import android.graphics.Bitmap
import android.util.AtomicFile
import com.example.overdex.battle.observation.DroidballService
import com.example.overdex.data.GameMasterLoader
import com.example.overdex.data.PokemonRepository
import com.example.overdex.data.local.PokedexDatabase
import com.example.overdex.data.local.toEntity
import com.example.overdex.data.observation.SharedLatinTextRecognizer
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class GuidedRegistrationState(
    val draft: RegistrationDraft? = null,
    val resolution: RegistrationResolution? = null,
    val scanning: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val message: String get() = when {
        loading -> "Loading local reference data…"
        error != null -> error
        saving -> "Saving card…"
        draft?.saved == true -> "Card saved in All Owned."
        resolution?.conflict != null -> resolution.conflict
        resolution?.species == null && !resolution?.candyFamilies.isNullOrEmpty() -> "Nickname found. Review the species choices."
        resolution?.ready == true -> "Ready to save. Add any remaining details, then save the card."
        resolution != null -> "Still needed: ${resolution.missing.joinToString(", ")}."
        else -> "Scroll a field into its box, then tap the box to read it."
    }
}

data class RegistrationFieldReview(
    val region: com.example.overdex.model.CaptureRegion,
    val cardId: String, val requestId: Long,
    val busy: Boolean = false, val read: DetailPageRead? = null,
    val rawText: String = "", val message: String = "",
    val capturedAt: Long = 0, val width: Int = 0, val height: Int = 0,
)

/** One bounded OCR lane and one durable draft per specimen; no battle witnesses or PNG log. */
class GuidedRegistrationController(
    context: Context,
    private val scope: CoroutineScope,
    private val knowledge: PokemonRepository,
    private val gameMaster: GameMasterLoader,
    private val isOverdexVisible: () -> Boolean,
) {
    val alignment = RegistrationLiveAlignment(context)
    private val _fieldReview = MutableStateFlow<RegistrationFieldReview?>(null)
    val fieldReview = _fieldReview.asStateFlow()
    private val fieldRequest = java.util.concurrent.atomic.AtomicLong()
    private var fieldTimeout: Job? = null
    @Volatile private var readAfterNanos = Long.MAX_VALUE
    private val appContext = context.applicationContext
    private val _cry = MutableStateFlow(RegistrationCryState())
    val cry = _cry.asStateFlow()
    private var cryJob: Job? = null
    private val cryRequest = java.util.concurrent.atomic.AtomicLong()
    private var cryReferences: List<com.example.overdex.battle.audio.CryAcousticMatcher.PreparedReference>? = null
    private val directory = File(context.filesDir, "registration")
    private val draftFile = AtomicFile(File(directory, "current.json"))
    private val database = PokedexDatabase.getDatabase(context)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    @Volatile private var catalogue: RegistrationCatalogue? = null
    private var job: Job? = null
    private val _state = MutableStateFlow(GuidedRegistrationState())
    val state = _state.asStateFlow()
    val readyToScan: Boolean get() = catalogue != null && !state.value.loading
    val wantsFrames: Boolean get() = state.value.scanning && !isOverdexVisible() && !cry.value.busy &&
        fieldReview.value?.busy == true && System.nanoTime() >= readAfterNanos &&
        state.value.draft?.saved != true && state.value.draft?.boundaryConflict != true

    /** Called only after the service has removed every secure guide and hidden its control panel. */
    fun onGuidesHidden() { if (fieldReview.value?.busy == true) readAfterNanos = System.nanoTime() + 350_000_000L }

    fun readField(id: String) {
        val draft = state.value.draft?.takeUnless { it.saved || it.boundaryConflict } ?: return
        if (!state.value.scanning || fieldReview.value?.busy == true || cry.value.busy || alignment.state.value.editing) return
        val region = alignment.state.value.template?.regions?.find { it.id == id } ?: return
        val token = fieldRequest.incrementAndGet()
        val image = region.type == com.example.overdex.model.ObservationType.IMAGE_MATCH
        readAfterNanos = Long.MAX_VALUE
        _fieldReview.value = RegistrationFieldReview(region, draft.cardId, token, busy = !image,
            message = if (image) "Confirm the visible icon yourself." else "Reading ${RegistrationFields.label(id)}…")
        if (image) return
        alignment.hideForRead(true)
        fieldTimeout?.cancel()
        fieldTimeout = scope.launch {
            delay(10_000)
            if (fieldReview.value?.requestId == token && fieldReview.value?.busy == true) {
                fieldRequest.incrementAndGet()
                _fieldReview.value = fieldReview.value?.copy(busy = false, message = "No readable frame arrived. Keep Pokémon GO visible and retry.")
                alignment.hideForRead(false)
            }
        }
    }

    fun dismissField() {
        fieldRequest.incrementAndGet()
        fieldTimeout?.cancel()
        _fieldReview.value = null
        readAfterNanos = Long.MAX_VALUE
        alignment.hideForRead(false)
    }

    fun confirmField(choice: Pair<String, String>? = null, updateExisting: Boolean = false, startNewCard: Boolean = false) {
        val reviewed = fieldReview.value?.takeUnless { it.busy } ?: return
        val choices = RegistrationFields.choices(reviewed.region.id).map { it.second }
        if (choice != null && choice !in choices) return
        val read = if (choice == null) reviewed.read else {
            val r = reviewed.region
            DetailPageRead(true, listOf(DetailFieldRead(choice.first, choice.second,
                DetailTextLine("User confirmed: ${choice.second}", r.x, r.y, r.x + r.width, r.y + r.height))))
        }
        if (read == null || !read.isDetailPage || read.fields.isEmpty()) return
        mutate {
            if (fieldReview.value?.requestId != reviewed.requestId || state.value.draft?.cardId != reviewed.cardId) return@mutate
            val current = state.value.draft ?: return@mutate
            if (!startNewCard && !updateExisting && current.identityChanges(read).isNotEmpty()) return@mutate
            val draft = if (startNewCard) prepareNextDraft() else current
            acceptDraft(draft.acceptField(reviewed.region.id, read,
                reviewed.capturedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                reviewed.width.takeIf { it > 0 } ?: alignment.state.value.sourceWidth,
                reviewed.height.takeIf { it > 0 } ?: alignment.state.value.sourceHeight,
                "field:${reviewed.requestId}:${reviewed.capturedAt}"))
            dismissField()
        }
    }


    init {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val entries = knowledge.getRegistrationCatalogue()
                    check(entries.isNotEmpty()) { "The Pokédex is still loading. Reopen registration shortly." }
                    catalogue = buildRegistrationCatalogue(entries, gameMaster.allPokemon(), gameMaster::getMove)
                    val draft = if (draftFile.baseFile.exists()) json.decodeFromString<RegistrationDraft>(draftFile.readFully().toString(Charsets.UTF_8)) else null
                    _state.value = GuidedRegistrationState(draft = draft, resolution = draft?.resolve(requireNotNull(catalogue)), loading = false)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    _state.value = GuidedRegistrationState(loading = false, error = error.message ?: "Unable to load registration.")
                }
            }
        }
    }

    fun newCard() = mutate {
        dismissField()
        val draft = prepareNextDraft()
        persist(draft)
        _state.value = GuidedRegistrationState(draft, draft.resolve(requireNotNull(catalogue)), scanning = _state.value.scanning, loading = false)
    }

    private fun prepareNextDraft(): RegistrationDraft {
        // Preserve unfinished work locally without inserting an incomplete owned card.
        state.value.draft?.takeIf { !it.saved && (it.evidence.isNotEmpty() || it.cries.isNotEmpty() || it.confirmedSpeciesId != null) }
            ?.let { writeAtomic(AtomicFile(File(directory, "${it.cardId}.json")), json.encodeToString(it)) }
        cryRequest.incrementAndGet()
        cryJob?.cancel()
        _cry.value = RegistrationCryState()
        return RegistrationDraft()
    }

    fun confirmSpecies(id: Int) = mutate {
        if (_state.value.draft?.saved == true) return@mutate
        val draft = (_state.value.draft ?: RegistrationDraft()).copy(confirmedSpeciesId = id)
        acceptDraft(draft)
    }

    fun saveCard() = mutate { _state.value.draft?.let { persistCard(it) } }
    fun retrySave() = saveCard()

    fun listenForCry() {
        if (cryJob?.isActive == true || state.value.draft?.saved == true || state.value.draft?.boundaryConflict == true || alignment.state.value.editing || fieldReview.value?.busy == true) return
        val cardId = state.value.draft?.cardId ?: return
        val request = cryRequest.incrementAndGet()
        fun publish(value: RegistrationCryState) {
            if (cryRequest.get() == request) _cry.value = value
        }
        publish(RegistrationCryState(true, "Preparing local cries…"))
        cryJob = scope.launch(Dispatchers.IO) {
            try {
                val references = cryReferences ?: com.example.overdex.battle.audio.CryReferenceCatalog.load(appContext).let { catalog ->
                    catalog.references.distinctBy { it.sha256 }.mapNotNull { reference ->
                        ensureActive()
                        catalog.openVerified(appContext, reference)?.let(com.example.overdex.battle.audio.WavPcm16::decode)
                            ?.let { com.example.overdex.battle.audio.CryAcousticMatcher.prepare(reference, it) }
                    }.also { cryReferences = it }
                }
                ensureActive()
                publish(RegistrationCryState(true, "Tap the Pokémon now. Listening for 5 seconds…"))
                val at = System.currentTimeMillis()
                val audio = DroidballService.captureRegistrationCry()
                ensureActive()
                publish(RegistrationCryState(true, "Comparing the cry…"))
                val candidates = com.example.overdex.battle.audio.CryAcousticMatcher.rankPrepared(audio, references, 20)
                    .filter { it.similarity >= 0.8f }.distinctBy { it.speciesId }.take(3).mapNotNull { match ->
                        catalogue?.species?.find { it.id == match.speciesId }?.let { species ->
                            RegistrationCryCandidate(species.id, species.name, match.similarity, match.referenceSha256)
                        }
                    }
                val evidence = RegistrationCryEvidence(at, RegistrationCryCapture.fingerprint(audio),
                    audio.samples.size * 1000L / audio.sampleRateHz, candidates)
                mutex.withLock {
                    if (cryRequest.get() != request) return@withLock
                    val draft = state.value.draft?.takeIf { it.cardId == cardId && !it.saved && !it.boundaryConflict } ?: return@withLock
                    val next = draft.copy(cries = (draft.cries + evidence).takeLast(8))
                    persist(next)
                    _state.value = state.value.copy(draft = next)
                    publish(RegistrationCryState(false, if (candidates.isEmpty()) "No useful cry match. Retry or choose species." else "Cry candidates — choose only if correct.", evidence))
                }
            } catch (e: Exception) {
                if (e is CancellationException) { publish(RegistrationCryState()); throw e }
                publish(RegistrationCryState(false, e.message ?: "Unable to read game audio. Retry or choose species."))
            } finally {
                if (_cry.value.busy) publish(RegistrationCryState())
            }
        }
    }

    fun confirmCrySpecies(id: Int) = mutate {
        val draft = state.value.draft?.takeUnless { it.saved || it.boundaryConflict } ?: return@mutate
        val evidence = cry.value.evidence?.takeIf { read -> read.candidates.any { it.speciesId == id } } ?: return@mutate
        if (draft.cries.none { it == evidence }) return@mutate
        acceptDraft(draft.copy(confirmedSpeciesId = id, cries = draft.cries.map { if (it == evidence) it.copy(confirmedSpeciesId = id) else it }))
        _cry.value = RegistrationCryState(false, "Species confirmed by you.")
    }

    suspend fun previewScreenshot(bitmap: Bitmap, template: com.example.overdex.model.CaptureTemplate): DetailPageRead =
        withContext(Dispatchers.IO) {
            val reference = catalogue ?: error("Reference data is still loading.")
            DetailPageReader(reference).read(RegistrationScreenshotReader.read(bitmap, template))
        }

    suspend fun previewField(bitmap: Bitmap, region: com.example.overdex.model.CaptureRegion): DetailPageRead =
        withContext(Dispatchers.IO) {
            RegistrationFieldReader(catalogue ?: error("Reference data is still loading.")).read(
                region.id, RegistrationScreenshotReader.readField(bitmap, region))
        }

    fun acceptReviewedField(read: DetailPageRead, regionId: String, sourceId: String, width: Int, height: Int) = mutate {
        val draft = state.value.draft ?: RegistrationDraft()
        check(!draft.saved && !draft.boundaryConflict) { "Start a new scan for another Pokémon." }
        acceptDraft(draft.acceptField(regionId, read, System.currentTimeMillis(), width, height, sourceId))
    }

    /** Only called after the user sees and confirms the screenshot's parsed fields. */
    fun acceptReviewedScreenshot(read: DetailPageRead, sourceId: String, width: Int, height: Int) = mutate {
        require(read.isDetailPage) { "This screenshot was not recognized as a Pokémon detail page." }
        val draft = _state.value.draft ?: RegistrationDraft()
        check(!draft.saved && !draft.boundaryConflict) { "Start a new scan before adding another Pokémon." }
        acceptDraft(draft.accept(read, System.currentTimeMillis(), width, height, sourceId, confirmedByUser = true))
    }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            try {
                mutex.withLock {
                    if (catalogue == null) return@launch
                    _state.value = _state.value.copy(scanning = true, error = null)
                    if (_state.value.draft == null) {
                        val draft = RegistrationDraft()
                        persist(draft)
                        _state.value = _state.value.copy(draft = draft, resolution = draft.resolve(requireNotNull(catalogue)))
                    }

                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(error = "Unable to start scan: ${error.message}")
                return@launch
            }
            DroidballService.frames.collect { frame ->
                val request = fieldReview.value?.takeIf { it.busy } ?: return@collect
                if (!wantsFrames || frame.capturedAtMonotonicTimeNanos < readAfterNanos) return@collect
                try {
                    val bitmap = frame.bitmap
                    val lines = RegistrationScreenshotReader.readField(bitmap, request.region)
                    val read = RegistrationFieldReader(requireNotNull(catalogue)).read(request.region.id, lines)
                    mutex.withLock {
                        if (fieldRequest.get() != request.requestId || state.value.draft?.cardId != request.cardId) return@withLock
                        fieldTimeout?.cancel()
                        _fieldReview.value = request.copy(busy = false, read = read, rawText = lines.joinToString("\n") { it.text },
                            message = if (read.fields.isEmpty()) "No clear ${RegistrationFields.label(request.region.id)}. Align and retry." else "Use this reading?",
                            capturedAt = frame.capturedAtWallTimeMillis, width = bitmap.width, height = bitmap.height)
                        readAfterNanos = Long.MAX_VALUE
                        alignment.hideForRead(false)
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (fieldRequest.get() == request.requestId) {
                        fieldTimeout?.cancel()
                        _fieldReview.value = request.copy(busy = false, message = "Unable to read: ${error.message}")
                        alignment.hideForRead(false)
                    }
                }
            }
        }
    }

    fun stop() {
        dismissField()
        cryRequest.incrementAndGet()
        cryJob?.cancel()
        cryJob = null
        _cry.value = RegistrationCryState()
        alignment.close()
        job?.cancel()
        job = null
        _state.value = _state.value.copy(scanning = false)
    }

    private suspend fun acceptDraft(draft: RegistrationDraft) {
        val resolution = draft.resolve(requireNotNull(catalogue))
        persist(draft)
        _state.value = _state.value.copy(draft = draft, resolution = resolution, error = null)
    }

    private suspend fun persistCard(draft: RegistrationDraft) {
        val resolution = draft.resolve(requireNotNull(catalogue))
        val card = draft.card(resolution) ?: return
        if (draft.saved) return
        _state.value = _state.value.copy(saving = true)
        try {
            // Stable cardId survives process loss between the insert and the saved flag.
            database.ownedPokemonDao().addScannedPokemon(card.toEntity())
            val saved = draft.copy(saved = true)
            persist(saved)
            writeAtomic(AtomicFile(File(directory, "${draft.cardId}.json")), json.encodeToString(saved))
            dismissField()
            _state.value = _state.value.copy(draft = saved)
        } finally { _state.value = _state.value.copy(saving = false) }
    }

    private fun mutate(action: suspend () -> Unit) {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                try {
                    if (catalogue == null) return@withLock
                    action()
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    _state.value = _state.value.copy(error = "Unable to save: ${error.message}")
                }
            }
        }
    }

    private fun persist(draft: RegistrationDraft) = writeAtomic(draftFile, json.encodeToString(draft))

    private fun writeAtomic(file: AtomicFile, value: String) {
        directory.mkdirs()
        val stream = file.startWrite()
        try { stream.write(value.toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
}
