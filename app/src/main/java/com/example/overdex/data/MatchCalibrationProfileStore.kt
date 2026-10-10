package com.example.overdex.data

import android.content.Context
import com.example.overdex.battle.observation.TeamSelectCalibration
import com.example.overdex.model.AnchorRegion
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One named, device-described snapshot of every Draggy Box on Match Calibration. */
data class MatchCalibrationProfile(
    val id: String,
    val name: String,
    val deviceModel: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val savedAtMillis: Long,
    val battleCalibration: BattleCalibration,
    val teamSelectCalibration: TeamSelectCalibration
)

data class MatchCalibrationProfileSummary(
    val id: String,
    val name: String,
    val deviceModel: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val savedAtMillis: Long
)

/**
 * App-private calibration profiles survive ordinary APK installs and updates.
 * A profile is a complete user-owned snapshot. Reading it never moves a box,
 * even if its coordinates resemble a former default. Defaults apply only to
 * fields that have never been saved.
 */
class MatchCalibrationProfileStore(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, DIRECTORY)
    private val prefs = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun activeProfile(): MatchCalibrationProfile? =
        prefs.getString(ACTIVE_PROFILE_ID, null)?.let(::load)

    fun activeSummary(): MatchCalibrationProfileSummary? = activeProfile()?.summary()

    fun list(): List<MatchCalibrationProfileSummary> = directory
        .listFiles { file -> file.isFile && file.extension == "json" }
        .orEmpty()
        .mapNotNull { file -> runCatching { decode(file.readText()).summary() }.getOrNull() }
        .sortedByDescending { it.savedAtMillis }

    fun saveAs(
        name: String,
        deviceModel: String,
        screenWidth: Int,
        screenHeight: Int,
        battleCalibration: BattleCalibration,
        teamSelectCalibration: TeamSelectCalibration
    ): MatchCalibrationProfile? = synchronized(profileLock) {
        val cleanName = name.trim().ifEmpty { "$deviceModel ${screenWidth}x$screenHeight" }
        val existing = list().firstOrNull {
            it.name.equals(cleanName, ignoreCase = true) &&
                it.deviceModel.equals(deviceModel.trim(), ignoreCase = true) &&
                it.screenWidth == screenWidth && it.screenHeight == screenHeight
        }
        val profile = MatchCalibrationProfile(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = cleanName,
            deviceModel = deviceModel.trim(),
            screenWidth = screenWidth.coerceAtLeast(1),
            screenHeight = screenHeight.coerceAtLeast(1),
            savedAtMillis = System.currentTimeMillis(),
            battleCalibration = battleCalibration,
            teamSelectCalibration = teamSelectCalibration
        )
        if (write(profile) == null) return@synchronized null
        if (!prefs.edit().putString(ACTIVE_PROFILE_ID, profile.id).commit()) return@synchronized null
        profile
    }

    /** Update the same snapshot that load() reads, without stale copies of its other half. */
    fun updateBattleCalibration(calibration: BattleCalibration): Boolean =
        updateActive { it.copy(battleCalibration = calibration) }

    fun updateTeamSelectCalibration(calibration: TeamSelectCalibration): Boolean =
        updateActive { it.copy(teamSelectCalibration = calibration) }

    private fun updateActive(transform: (MatchCalibrationProfile) -> MatchCalibrationProfile): Boolean =
        synchronized(profileLock) {
            val id = prefs.getString(ACTIVE_PROFILE_ID, null) ?: return@synchronized true
            // A selected but unreadable profile is a failed save, not permission
            // to write elsewhere and report success while load() uses old data.
            val current = load(id) ?: return@synchronized false
            write(transform(current).copy(savedAtMillis = System.currentTimeMillis())) != null
        }

    fun activate(id: String): MatchCalibrationProfile? = synchronized(profileLock) {
        val profile = load(id) ?: return@synchronized null
        if (!prefs.edit().putString(ACTIVE_PROFILE_ID, id).commit()) return@synchronized null
        profile
    }

    private fun load(id: String): MatchCalibrationProfile? {
        val file = File(directory, "$id.json")
        return runCatching { decode(file.readText()) }.getOrNull()
    }

    private fun write(profile: MatchCalibrationProfile): MatchCalibrationProfile? = runCatching {
        check(directory.isDirectory || directory.mkdirs()) { "Could not create calibration directory" }
        val target = File(directory, "${profile.id}.json")
        val temporary = File.createTempFile("${profile.id}-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(json.encodeToString(StoredProfile.serializer(), profile.toStored()).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            // Never delete the last good profile before publishing its replacement.
            // Readers see either the complete old snapshot or the complete new one.
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
        profile
    }.getOrNull()

    private fun decode(encoded: String): MatchCalibrationProfile =
        decodeStored(encoded).toProfile()

    private fun decodeStored(encoded: String): StoredProfile =
        json.decodeFromString(StoredProfile.serializer(), encoded)

    private fun MatchCalibrationProfile.summary() = MatchCalibrationProfileSummary(
        id, name, deviceModel, screenWidth, screenHeight, savedAtMillis
    )

    private companion object {
        const val DIRECTORY = "calibration_profiles"
        const val PREFERENCES = "match_calibration_profile_selection"
        const val ACTIVE_PROFILE_ID = "active_profile_id"
        val profileLock = Any()
    }
}

@Serializable
private data class StoredProfile(
    val schemaVersion: Int = MATCH_CALIBRATION_PROFILE_SCHEMA_VERSION,
    val id: String,
    val name: String,
    val deviceModel: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val savedAtMillis: Long,
    val battleRegions: Map<String, StoredRegion>,
    val teamSelectRegions: Map<String, StoredRegion>
)

@Serializable
private data class StoredRegion(val x: Float, val y: Float, val width: Float, val height: Float) {
    fun toRegion() = AnchorRegion(x, y, width, height)
}

private fun AnchorRegion.stored() = StoredRegion(x, y, width, height)

private fun MatchCalibrationProfile.toStored() = StoredProfile(
    id = id,
    name = name,
    deviceModel = deviceModel,
    screenWidth = screenWidth,
    screenHeight = screenHeight,
    savedAtMillis = savedAtMillis,
    battleRegions = battleCalibration.toRegionMap(),
    teamSelectRegions = teamSelectCalibration.toRegionMap()
)

private fun StoredProfile.toProfile(): MatchCalibrationProfile {
    val battleDefaults = BattleCalibration()
    val teamDefaults = TeamSelectCalibration.measured1080x2400
    fun Map<String, StoredRegion>.region(name: String, fallback: AnchorRegion) =
        get(name)?.toRegion() ?: fallback
    val storedBattleCalibration = BattleCalibration(
        enemyNameRegion = battleRegions.region("enemy_name", battleDefaults.enemyNameRegion),
        hpBarRegion = battleRegions.region("hp_bar", battleDefaults.hpBarRegion),
        teamIconsRegion = battleRegions.region("team_icons", battleDefaults.teamIconsRegion),
        moveBannerRegion = battleRegions.region("move_banner", battleDefaults.moveBannerRegion),
        countdownRegion = battleRegions.region("countdown", battleDefaults.countdownRegion),
        vsScreenRegion = battleRegions.region("vs_screen", battleDefaults.vsScreenRegion),
        youWinRegion = battleRegions.region("you_win", battleDefaults.youWinRegion),
        goodEffortRegion = battleRegions.region("good_effort", battleDefaults.goodEffortRegion),
        opponentShieldsRegion = battleRegions.region("opponent_shields", battleDefaults.opponentShieldsRegion),
        playerTeamInfoRegion = battleRegions.region("player_team_info", battleDefaults.playerTeamInfoRegion),
        announcementRegion = battleRegions.region("announcement", battleDefaults.announcementRegion),
        opponentTeamInfoRegion = battleRegions.region("opponent_team_info", battleDefaults.opponentTeamInfoRegion),
        playerSpeciesNameRegion = battleRegions.region("player_species_name", battleDefaults.playerSpeciesNameRegion),
        opponentSpeciesNameRegion = battleRegions.region("opponent_species_name", battleDefaults.opponentSpeciesNameRegion),
        playerPokeBallsRegion = battleRegions.region("player_poke_balls", battleDefaults.playerPokeBallsRegion),
        opponentPokeBallsRegion = battleRegions.region("opponent_poke_balls", battleDefaults.opponentPokeBallsRegion),
        trainerActiveTypeRegion = battleRegions.region("trainer_active_type", battleDefaults.trainerActiveTypeRegion),
        opponentActiveTypeRegion = battleRegions.region("opponent_active_type", battleDefaults.opponentActiveTypeRegion),
        trainerHpRegion = battleRegions.region("trainer_hp", battleDefaults.trainerHpRegion),
        opponentHpRegion = battleRegions.region("opponent_hp", battleDefaults.opponentHpRegion),
        chargeMoveExecutionRegion = battleRegions.region("charge_move_execution", battleDefaults.chargeMoveExecutionRegion),
        trainerChargeMoveControlsRegion = battleRegions.region("trainer_charge_move_controls", battleDefaults.trainerChargeMoveControlsRegion),
        trainerInactivePokemonRegion = battleRegions.region("trainer_inactive_pokemon", battleDefaults.trainerInactivePokemonRegion),
        battlePartyTabsRegion = battleRegions.region("battle_party_tabs", battleDefaults.battlePartyTabsRegion),
        outOfBattleMenuRegion = battleRegions.region("out_of_battle_menu", battleDefaults.outOfBattleMenuRegion),
        matchOutcomeRegion = battleRegions.region("match_outcome", battleDefaults.matchOutcomeRegion)
    )
    return MatchCalibrationProfile(
        id = id,
        name = name,
        deviceModel = deviceModel,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        savedAtMillis = savedAtMillis,
        battleCalibration = storedBattleCalibration,
        teamSelectCalibration = TeamSelectCalibration(
            rosterSlot1 = teamSelectRegions.region("roster_1", teamDefaults.rosterSlot1),
            rosterSlot2 = teamSelectRegions.region("roster_2", teamDefaults.rosterSlot2),
            rosterSlot3 = teamSelectRegions.region("roster_3", teamDefaults.rosterSlot3),
            rosterName1 = teamSelectRegions.region("roster_name_1", teamDefaults.rosterName1),
            rosterName2 = teamSelectRegions.region("roster_name_2", teamDefaults.rosterName2),
            rosterName3 = teamSelectRegions.region("roster_name_3", teamDefaults.rosterName3),
            leagueBadge = teamSelectRegions.region("league_badge", teamDefaults.leagueBadge),
            leagueText = teamSelectRegions.region("league_text", teamDefaults.leagueText),
            restrictions = teamSelectRegions.region("restrictions", teamDefaults.restrictions),
            useThisParty = teamSelectRegions.region("use_this_party", teamDefaults.useThisParty)
        )
    )
}

internal const val MATCH_CALIBRATION_PROFILE_SCHEMA_VERSION = 2

private fun BattleCalibration.toRegionMap(): Map<String, StoredRegion> = linkedMapOf(
    "enemy_name" to enemyNameRegion.stored(),
    "hp_bar" to hpBarRegion.stored(),
    "team_icons" to teamIconsRegion.stored(),
    "move_banner" to moveBannerRegion.stored(),
    "countdown" to countdownRegion.stored(),
    "vs_screen" to vsScreenRegion.stored(),
    "you_win" to youWinRegion.stored(),
    "good_effort" to goodEffortRegion.stored(),
    "opponent_shields" to opponentShieldsRegion.stored(),
    "player_team_info" to playerTeamInfoRegion.stored(),
    "announcement" to announcementRegion.stored(),
    "opponent_team_info" to opponentTeamInfoRegion.stored(),
    "player_species_name" to playerSpeciesNameRegion.stored(),
    "opponent_species_name" to opponentSpeciesNameRegion.stored(),
    "player_poke_balls" to playerPokeBallsRegion.stored(),
    "opponent_poke_balls" to opponentPokeBallsRegion.stored(),
    "trainer_active_type" to trainerActiveTypeRegion.stored(),
    "opponent_active_type" to opponentActiveTypeRegion.stored(),
    "trainer_hp" to trainerHpRegion.stored(),
    "opponent_hp" to opponentHpRegion.stored(),
    "charge_move_execution" to chargeMoveExecutionRegion.stored(),
    "trainer_charge_move_controls" to trainerChargeMoveControlsRegion.stored(),
    "trainer_inactive_pokemon" to trainerInactivePokemonRegion.stored(),
    "battle_party_tabs" to battlePartyTabsRegion.stored(),
    "out_of_battle_menu" to outOfBattleMenuRegion.stored(),
    "match_outcome" to matchOutcomeRegion.stored()
)

private fun TeamSelectCalibration.toRegionMap(): Map<String, StoredRegion> = linkedMapOf(
    "roster_1" to rosterSlot1.stored(),
    "roster_2" to rosterSlot2.stored(),
    "roster_3" to rosterSlot3.stored(),
    "roster_name_1" to rosterName1.stored(),
    "roster_name_2" to rosterName2.stored(),
    "roster_name_3" to rosterName3.stored(),
    "league_badge" to leagueBadge.stored(),
    "league_text" to leagueText.stored(),
    "restrictions" to restrictions.stored(),
    "use_this_party" to useThisParty.stored()
)
