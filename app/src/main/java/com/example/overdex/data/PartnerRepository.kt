package com.example.overdex.data

import android.content.Context
import com.example.overdex.model.PartnerIdentity
import com.example.overdex.model.PublicTrainerIdentity
import com.example.overdex.model.SharedEvent
import com.example.overdex.model.SharedEventType
import com.example.overdex.model.TrainerIdentity
import com.example.overdex.model.Milestone
import com.example.overdex.model.MilestoneType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Manages the connection with another trainer.
 */
interface PartnerRepository {
    val partner: StateFlow<PartnerIdentity?>
    suspend fun link(identity: PublicTrainerIdentity, myIdentity: TrainerIdentity, recordLinkEvent: (SharedEvent) -> Unit)
    suspend fun unlink()
}

class SharedPreferencesPartnerRepository(private val context: Context) : PartnerRepository {
    private val prefs = context.getSharedPreferences("overdex_partner_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _partner = MutableStateFlow<PartnerIdentity?>(null)
    override val partner: StateFlow<PartnerIdentity?> = _partner.asStateFlow()

    init {
        val storedJson = prefs.getString("partner_identity", null)
        if (storedJson != null) {
            try {
                _partner.value = json.decodeFromString<PartnerIdentity>(storedJson)
            } catch (e: Exception) {
                // Handle malformed data
            }
        }
    }

    override suspend fun link(
        identity: PublicTrainerIdentity,
        myIdentity: TrainerIdentity,
        recordLinkEvent: (SharedEvent) -> Unit
    ) {
        val partnerIdentity = PartnerIdentity(
            trainerId = identity.trainerId,
            displayName = identity.displayName,
            avatarSeed = identity.avatarSeed,
            linkedAt = Instant.now(),
            protocolVersion = identity.protocolVersion
        )
        
        val partnerJson = json.encodeToString(PartnerIdentity.serializer(), partnerIdentity)
        prefs.edit().putString("partner_identity", partnerJson).apply()
        _partner.value = partnerIdentity

        // Record the LINKED event as a Milestone
        recordLinkEvent(
            SharedEvent(
                authorTrainerId = myIdentity.trainerId.toString(),
                type = SharedEventType.MILESTONE,
                payload = "${myIdentity.displayName ?: "You"} linked with ${identity.displayName ?: "Stevie"}.",
                milestone = Milestone(
                    type = MilestoneType.FIRST_LINK,
                    achievedBy = myIdentity.trainerId.toString(),
                    payload = "established partnership"
                )
            )
        )
    }

    override suspend fun unlink() {
        prefs.edit().remove("partner_identity").apply()
        _partner.value = null
        // Note: Timeline is preserved for historical record
    }
}
