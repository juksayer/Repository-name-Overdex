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

class ServicePanelState(
    val observations: List<ServiceObservation<*>>,
    val assessment: RegistrationAssessment
)

object ServiceConsoleModel {
    
    fun createPanelState(
        results: Map<String, List<RecognitionResult<*>>>,
        assessment: RegistrationAssessment
    ): ServicePanelState {
        android.util.Log.d("PIPELINE_INSTRUMENTATION", "ServiceConsoleModel Input | Results Keys: ${results.keys}")
        val panelObs = mutableListOf<ServiceObservation<*>>()
        
        // Species
        val speciesRaw = results["SpeciesName"]?.firstOrNull { it.recognizer == "SpeciesNameRecognizer" }?.value as? String
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
        val familyRaw = results["CandyPanel"]?.firstOrNull { it.recognizer == "CandyPanelFamilyRecognizer" }?.value as? String
        panelObs.add(ServiceObservation(
            label = "Evolution Family",
            value = CandyNormalizer.normalize(familyRaw),
            status = if (familyRaw != null) ObservationStatus.RECOGNIZED else ObservationStatus.MISSING,
            rawValue = familyRaw,
            regionId = "CandyPanel"
        ))

        // CP
        val cpRaw = results["CombatPower"]?.firstOrNull { it.recognizer == "CombatPowerRecognizer" }?.value
        panelObs.add(ServiceObservation(
            label = "Combat Power",
            value = cpRaw?.toString(),
            status = if (cpRaw != null) ObservationStatus.RECOGNIZED else ObservationStatus.MISSING,
            rawValue = cpRaw?.toString(),
            regionId = "CombatPower"
        ))

        android.util.Log.d("PIPELINE_INSTRUMENTATION", "ServicePanelState | Observations: ${panelObs.size}")

        return ServicePanelState(
            observations = panelObs,
            assessment = assessment
        )
    }
}
