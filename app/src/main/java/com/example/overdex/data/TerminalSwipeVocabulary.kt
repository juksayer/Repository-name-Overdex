package com.example.overdex.data

import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Reuses the app's current battle catalogue; it never opens or parses another copy of it. */
class TerminalSwipeVocabulary(
    private val loader: GameMasterLoader,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private var cached: List<String>? = null

    /** Asset loading, initial catalogue parsing, and vocabulary construction stay off the UI thread. */
    suspend fun load(): List<String> = withContext(dispatcher) {
        mutex.withLock {
            cached ?: buildList {
                addAll(loader.allPokemonNames())
                addAll(loader.allMoves().map { it.name })
            }.map(String::trim).filter(String::isNotEmpty)
                .distinctBy { it.lowercase(Locale.ROOT) }.sorted().also { cached = it }
        }
    }
}
