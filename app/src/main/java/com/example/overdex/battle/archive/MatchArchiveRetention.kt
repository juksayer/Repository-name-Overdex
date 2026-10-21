package com.example.overdex.battle.archive

import java.io.File

/**
 * Bounds completed internal recordings by Match ID, not the number of export
 * copies. Reads Timeline metadata only; never decodes or imports source media.
 * Checkpoints, unrecognized files, and unreadable archives are left alone.
 */
class MatchArchiveRetention(private val directory: File) {
    data class Result(val deletedNames: List<String>, val bytesRemoved: Long, val failures: Map<String, String>)
    private data class Metadata(val matchId: String, val startedAt: Long, val bytes: Long, val modifiedAt: Long)
    private val metadata = mutableMapOf<String, Metadata>()

    @Synchronized
    fun trim(
        keepMatches: Int = RECENT_MATCH_LIMIT,
        protectedMatchIds: Set<String> = emptySet(),
        beforeDelete: (File) -> Unit = {}
    ): Result {
        require(keepMatches >= 1)
        val files = directory.listFiles().orEmpty().filter {
            it.isFile && !it.name.startsWith("active-") && !it.name.startsWith(".") &&
                it.name.endsWith(".odxmatch.zip") && it.canonicalFile.parentFile == directory.canonicalFile
        }
        metadata.keys.retainAll(files.map { it.name }.toSet())
        if (files.size <= keepMatches) return Result(emptyList(), 0L, emptyMap())
        val failures = linkedMapOf<String, String>()
        val identified = files.mapNotNull { file ->
            runCatching {
                val cached = metadata[file.name]?.takeIf {
                    it.bytes == file.length() && it.modifiedAt == file.lastModified()
                }
                val entry = cached ?: file.inputStream().use {
                    val archive = MatchArchivePackageReader.read(it)
                    Metadata(
                        archive.matchId,
                        archive.articles.minOfOrNull { article -> article.perceivedAt }
                            ?: error("Empty Timeline; kept for inspection"),
                        file.length(),
                        file.lastModified()
                    ).also { metadata[file.name] = it }
                }
                file to entry
            }.onFailure {
                failures[file.name] = it.message ?: "Unable to inspect archive"
            }.getOrNull()
        }
        val groups = identified.groupBy { it.second.matchId }
        val newestIds = groups.entries.sortedWith(
            compareByDescending<Map.Entry<String, List<Pair<File, Metadata>>>> {
                // Exporting an old match again must not make it a newer battle.
                it.value.minOf { (_, info) -> info.startedAt }
            }.thenBy { it.key }
        ).take(keepMatches).map { it.key }.toSet()
        val keepIds = newestIds + protectedMatchIds
        val deleted = mutableListOf<String>()
        var bytesRemoved = 0L
        identified.filter { it.second.matchId !in keepIds }.forEach { (file, info) ->
            runCatching {
                // Preserve lifetime statistics before retiring a replay file.
                beforeDelete(file)
                check(file.length() == info.bytes && file.lastModified() == info.modifiedAt) {
                    "Archive changed during cleanup; kept"
                }
                check(file.delete()) { "Unable to remove archive" }
                deleted += file.name
                bytesRemoved += info.bytes
                metadata.remove(file.name)
            }.onFailure {
                failures[file.name] = it.message ?: "Unable to remove archive"
            }
        }
        return Result(deleted, bytesRemoved, failures)
    }

    companion object {
        const val RECENT_MATCH_LIMIT = 50
    }
}
