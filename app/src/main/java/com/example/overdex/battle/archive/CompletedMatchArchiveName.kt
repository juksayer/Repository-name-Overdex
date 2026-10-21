package com.example.overdex.battle.archive

import com.example.overdex.battle.observation.MatchId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import java.io.IOException

/** Stable across a late evidence refresh and process recovery; distinct back-to-back Matches
 * cannot overwrite each other even when their wall-clock start falls in the same second.
 */
fun completedMatchArchiveName(matchId: MatchId, startedAtMillis: Long): String {
    require(matchId.value.matches(Regex("[A-Za-z0-9_-]+"))) { "Invalid Match archive id" }
    val date = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date(startedAtMillis))
    return "${date}_${matchId.value}.odxmatch.zip"
}

/** Both files live in the same app-owned directory. Linux/Android rename replaces
 * atomically; never delete the last good archive before publishing its replacement.
 */
internal fun publishStagedMatchArchive(staged: File, destination: File) {
    if (!staged.renameTo(destination)) {
        staged.delete()
        throw IOException("Unable to publish internal match archive.")
    }
}
