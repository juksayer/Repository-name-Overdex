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
    @Volatile private var prepared: List<CryAcousticMatcher.PreparedReference>? = null

    suspend fun get(context: Context): List<CryAcousticMatcher.PreparedReference> {
        prepared?.let { return it }
        return loading.withLock {
            prepared ?: withContext(Dispatchers.IO) {
                val catalog = CryReferenceCatalog.load(context)
                catalog.references.distinctBy { it.sha256 }.mapNotNull { reference ->
                    currentCoroutineContext().ensureActive()
                    catalog.openVerified(context, reference)?.let(WavPcm16::decodeBundledReference)
                        ?.let { CryAcousticMatcher.prepare(reference, it) }
                }
            }.also { prepared = it }
        }
    }
}
