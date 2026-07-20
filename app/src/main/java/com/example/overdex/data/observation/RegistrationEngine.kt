package com.example.overdex.data.observation

import com.example.overdex.model.observation.*
import com.example.overdex.model.Pokemon
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.battle.debug.observatory.*

object RegistrationEngine {

    private val defaultResolver = DefaultObservationResolver()

    suspend fun assess(
        session: ObservationSession,
        manualSpecies: Pokemon?,
        viewModel: PokedexViewModel
    ): RegistrationAssessment {
        val captureId = session.sessionId
        val history = session.history
        
        val missing = mutableListOf<String>()
        val conflicts = mutableListOf<String>()
        val candidates = mutableListOf<CandidateSpecies>()
        
        // 1. Resolve Evidence using DefaultObservationResolver
        val speciesNameObs = history["SpeciesName"]?.let { defaultResolver.resolve(it) } as? PokemonNameObservation
        val candyPanelObs = history["CandyPanel"]?.let { defaultResolver.resolve(it) } as? EvolutionFamilyObservation
        
        val rawSpecies = speciesNameObs?.species
        val rawFamily = candyPanelObs?.familySpecies
        
        val normalizedSpeciesName = SpeciesNormalizer.normalize(rawSpecies)
        val normalizedFamilyName = CandyNormalizer.normalize(rawFamily)
        
        var solvedSpecies = manualSpecies
        if (solvedSpecies == null && normalizedSpeciesName != null) {
            solvedSpecies = viewModel.getPokemonByName(normalizedSpeciesName)
        }

        // 2. Conflict Detection (Integrity logic can also be called here)
        if (normalizedSpeciesName != null && normalizedFamilyName != null) {
            val familyList = viewModel.getEvolutionFamily(normalizedFamilyName)
            if (familyList.isNotEmpty() && !familyList.contains(normalizedSpeciesName)) {
                conflicts.add("Species Name ($normalizedSpeciesName) vs Candy Family ($normalizedFamilyName)")
            }
        }

        // 3. Build Candidates
        if (solvedSpecies != null) {
            candidates.add(CandidateSpecies(
                id = solvedSpecies.id,
                name = solvedSpecies.name,
                confidence = if (manualSpecies != null) 1.0f else speciesNameObs?.confidence?.score ?: 0.0f,
                reasoning = if (manualSpecies != null) "Manually Confirmed" else "Matched Species Name OCR"
            ))
        } else if (normalizedFamilyName != null) {
            val familyList = viewModel.getEvolutionFamily(normalizedFamilyName)
            familyList.forEach { member ->
                viewModel.getPokemonByName(member)?.let { p ->
                    candidates.add(CandidateSpecies(
                        id = p.id,
                        name = p.name,
                        confidence = (candyPanelObs?.confidence?.score ?: 0.0f) * 0.5f,
                        reasoning = "Member of detected $normalizedFamilyName Family"
                    ))
                }
            }
        }

        // 4. Check Missing
        if (history["CombatPower"].isNullOrEmpty()) missing.add("Combat Power")
        if (history["FastMoveRow"].isNullOrEmpty() && history["SummaryFastMove"].isNullOrEmpty()) missing.add("Fast Move")

        // 5. Additive Registration Confidence
        var speciesPoints = 0.0f
        var familyPoints = 0.0f
        var cpPoints = 0.0f
        var fastPoints = 0.0f
        var chgAPoints = 0.0f
        var chgBPoints = 0.0f

        if (normalizedSpeciesName != null) speciesPoints = 0.35f
        if (normalizedFamilyName != null) familyPoints = 0.20f
        if (history["CombatPower"]?.isNotEmpty() == true) cpPoints = 0.15f
        if (history["FastMoveRow"]?.isNotEmpty() == true || history["SummaryFastMove"]?.isNotEmpty() == true) fastPoints = 0.15f
        if (history["ChargedMoveRowA"]?.isNotEmpty() == true) chgAPoints = 0.075f
        if (history["ChargedMoveRowB"]?.isNotEmpty() == true) chgBPoints = 0.075f

        val mainConfidence = (speciesPoints + familyPoints + cpPoints + fastPoints + chgAPoints + chgBPoints).coerceIn(0.0f, 1.0f)
        
        val identityConfidence = candidates.maxOfOrNull { it.confidence } ?: 0.0f

        val action = when {
            manualSpecies != null || identityConfidence >= 0.8f -> RegistrationAction.REGISTER
            candidates.isNotEmpty() -> RegistrationAction.SELECT_SPECIES
            else -> RegistrationAction.NONE
        }

        // LOG: RegistrationAssessment
        val fm = (history["FastMoveRow"] ?: history["SummaryFastMove"])?.let { defaultResolver.resolve(it) }?.toString() ?: "MISSING"
        val cma = history["ChargedMoveRowA"]?.let { defaultResolver.resolve(it) }?.toString() ?: "MISSING"
        val cmb = history["ChargedMoveRowB"]?.let { defaultResolver.resolve(it) }?.toString() ?: "MISSING"

        TraceLogger.logStage(
            captureId = captureId,
            stage = "RegistrationAssessment",
            species = solvedSpecies?.name ?: normalizedSpeciesName ?: "MISSING",
            family = normalizedFamilyName ?: "MISSING",
            cp = history["CombatPower"]?.let { defaultResolver.resolve(it) }?.toString() ?: "MISSING",
            fast = fm,
            chgA = cma,
            chgB = cmb,
            confidence = (mainConfidence * 100).toInt().toString() + "%"
        )

        val confidenceDetails = mutableListOf<String>()
        confidenceDetails.add("Species..............${(speciesPoints * 100).toInt()}${if (speciesPoints > 0) " ✓" else " ✗"}")
        confidenceDetails.add("Family...............${(familyPoints * 100).toInt()}${if (familyPoints > 0) " ✓" else " ✗"}")
        confidenceDetails.add("CP...................${(cpPoints * 100).toInt()}${if (cpPoints > 0) " ✓" else " ✗"}")
        confidenceDetails.add("Fast Move............${(fastPoints * 100).toInt()}${if (fastPoints > 0) " ✓" else " ✗"}")
        confidenceDetails.add("Charged A............$chgAPoints${if (chgAPoints > 0) " ✓" else " ✗"}")
        confidenceDetails.add("Charged B............$chgBPoints${if (chgBPoints > 0) " ✓" else " ✗"}")

        TraceLogger.logConfidenceTrace(
            captureId = captureId,
            source = "Registration Confidence (Additive Model)",
            value = (mainConfidence * 100).toInt().toString() + "%",
            details = confidenceDetails
        )

        val verdicts = mutableMapOf<String, String>()
        verdicts["OCR"] = if (history.isNotEmpty()) "PASS" else "FAIL"
        verdicts["Recognition"] = if (candidates.isNotEmpty()) "PASS" else "FAIL"
        verdicts["Assessment"] = "PASS"
        verdicts["Confidence"] = "PASS"
        TraceLogger.logPipelineVerdict(captureId, verdicts)

        android.util.Log.d("PIPELINE_INSTRUMENTATION", "RegistrationAssessment | Confidence: $mainConfidence | Candidates: ${candidates.size} | Action: $action")

        return RegistrationAssessment(
            confidence = mainConfidence,
            candidates = candidates,
            missingObservations = missing,
            conflictingObservations = conflicts,
            recommendedAction = action
        )
    }
}
