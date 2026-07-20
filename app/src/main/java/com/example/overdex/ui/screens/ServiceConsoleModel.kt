package com.example.overdex.ui.screens

import androidx.compose.runtime.Stable
import com.example.overdex.model.observation.*
import com.example.overdex.data.observation.*

@Stable
data class ServiceObservation<T>(
    val label: String,
    val value: T?,
    val status: ObservationStatus,
    val rawValue: String? = null,
    val regionId: String? = null
)

data class ServicePanelState(
    val observations: List<ServiceObservation<*>>,
    val assessment: RegistrationAssessment,
    val captureId: String = "",
    val isProcessing: Boolean = false
)

object ServiceConsoleModel {
    
    private val defaultResolver = DefaultObservationResolver()

    fun createPanelState(
        captureId: String,
        history: Map<String, List<Observation>>,
        assessment: RegistrationAssessment
    ): ServicePanelState {
        android.util.Log.d("PIPELINE_INSTRUMENTATION", "ServiceConsoleModel Input | History Keys: ${history.keys}")
        val panelObs = mutableListOf<ServiceObservation<*>>()
        
        // Species
        val speciesObs = history["SpeciesName"]?.let { defaultResolver.resolve(it) } as? PokemonNameObservation
        val speciesRaw = speciesObs?.species
        val candidate = assessment.candidates.firstOrNull()
        panelObs.add(ServiceObservation(
            label = "Species Name",
            value = candidate?.name ?: SpeciesNormalizer.normalize(speciesRaw),
            status = when {
                assessment.recommendedAction == RegistrationAction.REGISTER && candidate != null -> ObservationStatus.RECOGNIZED
                speciesRaw != null -> ObservationStatus.OBSERVED
                else -> ObservationStatus.MISSING
            },
            rawValue = speciesRaw,
            regionId = "SpeciesName"
        ))

        // Family
        val familyObs = history["CandyPanel"]?.let { defaultResolver.resolve(it) } as? EvolutionFamilyObservation
        val familyRaw = familyObs?.familySpecies
        panelObs.add(ServiceObservation(
            label = "Evolution Family",
            value = CandyNormalizer.normalize(familyRaw),
            status = if (familyRaw != null) ObservationStatus.RECOGNIZED else ObservationStatus.MISSING,
            rawValue = familyRaw,
            regionId = "CandyPanel"
        ))

        // CP
        val cpObs = history["CombatPower"]?.let { defaultResolver.resolve(it) } as? CombatPowerObservation
        val cpRaw = cpObs?.cp
        panelObs.add(ServiceObservation(
            label = "Combat Power",
            value = cpRaw?.toString(),
            status = if (cpRaw != null) ObservationStatus.RECOGNIZED else ObservationStatus.MISSING,
            rawValue = cpRaw?.toString(),
            regionId = "CombatPower"
        ))

        // LOG: ServicePanelState
        val res_fm = (history["FastMoveRow"] ?: history["SummaryFastMove"])?.let { defaultResolver.resolve(it) }?.toString() ?: "MISSING"
        val res_cma = history["ChargedMoveRowA"]?.let { defaultResolver.resolve(it) }?.toString() ?: "MISSING"
        val res_cmb = history["ChargedMoveRowB"]?.let { defaultResolver.resolve(it) }?.toString() ?: "MISSING"

        TraceLogger.logStage(
            captureId = captureId,
            stage = "ServicePanelState",
            species = panelObs.find { it.label == "Species Name" }?.value?.toString() ?: "MISSING",
            family = panelObs.find { it.label == "Evolution Family" }?.value?.toString() ?: "MISSING",
            cp = panelObs.find { it.label == "Combat Power" }?.value?.toString() ?: "MISSING",
            fast = res_fm,
            chgA = res_cma,
            chgB = res_cmb,
            confidence = (assessment.confidence * 100).toInt().toString() + "%"
        )

        android.util.Log.d("PIPELINE_INSTRUMENTATION", "ServicePanelState | Observations: ${panelObs.size}")

        return ServicePanelState(
            observations = panelObs,
            assessment = assessment,
            captureId = captureId
        )
    }
}
