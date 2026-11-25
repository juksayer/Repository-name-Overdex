package com.example.overdex.battle.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Bundled references are immutable for the lifetime of this app process. */
internal object PreparedCryReferenceIndex {
    private val loading = Mutex()
    private var catalog: CryReferenceCatalog? = null
    private var lastSpecies = emptySet<Int>()
    private var prepared = emptyList<CryAcousticMatcher.PreparedReference>()

    suspend fun get(context: Context, species: Set<Int>): List<CryAcousticMatcher.PreparedReference> {
        require(species.size <= 6)
        if (species.isEmpty()) return emptyList()
        return loading.withLock {
            if (species == lastSpecies) return@withLock prepared
            withContext(Dispatchers.IO) {
                val index = catalog ?: CryReferenceCatalog.load(context).also { catalog = it }
                val existing = prepared.associateBy { it.reference.speciesId to it.reference.sha256 }
                species.sorted().flatMap(index::referencesFor).distinctBy { it.speciesId to it.sha256 }.mapNotNull { reference ->
                    currentCoroutineContext().ensureActive()
                    existing[reference.speciesId to reference.sha256] ?: index.openVerified(context, reference)?.let(WavPcm16::decodeBundledReference)
                        ?.let { CryAcousticMatcher.prepare(reference, it) }
                }
            }.also { prepared = it; lastSpecies = species.toSet() }
        }
    }
}
