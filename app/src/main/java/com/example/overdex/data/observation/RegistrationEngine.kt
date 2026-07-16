package com.example.overdex.data.observation

import com.example.overdex.model.observation.*
import com.example.overdex.model.Pokemon
import com.example.overdex.ui.PokedexViewModel
import kotlinx.coroutines.flow.first

object RegistrationEngine {

    suspend fun assess(
        captureId: String,
        recognitionResults: Map<String, List<RecognitionResult<*>>>,
        manualSpecies: Pokemon?,
        viewModel: PokedexViewModel
    ): RegistrationAssessment {
        val missing = mutableListOf<String>()
        val conflicts = mutableListOf<String>()
        val candidates = mutableListOf<CandidateSpecies>()
        
        // 1. Resolve Evidence
        val rawSpecies = recognitionResults["SpeciesName"]?.firstOrNull { it.recognizer == "SpeciesNameRecognizer" }?.value as? String
        val rawFamily = recognitionResults["CandyPanel"]?.firstOrNull { it.recognizer == "CandyPanelFamilyRecognizer" }?.value as? String
        
        val normalizedSpeciesName = SpeciesNormalizer.normalize(rawSpecies)
        val normalizedFamilyName = CandyNormalizer.normalize(rawFamily)
        
        var solvedSpecies = manualSpecies
        if (solvedSpecies == null && normalizedSpeciesName != null) {
            solvedSpecies = viewModel.getPokemonByName(normalizedSpeciesName)
        }

        // 2. Conflict Detection
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
                confidence = if (manualSpecies != null) 1.0f else 0.8f,
                reasoning = if (manualSpecies != null) "Manually Confirmed" else "Matched Species Name OCR"
            ))
        } else if (normalizedFamilyName != null) {
            val familyList = viewModel.getEvolutionFamily(normalizedFamilyName)
            familyList.forEach { member ->
                viewModel.getPokemonByName(member)?.let { p ->
                    candidates.add(CandidateSpecies(
                        id = p.id,
                        name = p.name,
                        confidence = 0.4f,
                        reasoning = "Member of detected $normalizedFamilyName Family"
                    ))
                }
            }
        }

        // 4. Check Missing
        if (recognitionResults["CombatPower"] == null) missing.add("Combat Power")
        if (recognitionResults["FastMoveRow"] == null && recognitionResults["SummaryFastMove"] == null) missing.add("Fast Move")

        val mainConfidence = candidates.maxOfOrNull { it.confidence } ?: 0.0f
        
        val action = when {
            manualSpecies != null || mainConfidence >= 0.8f -> RegistrationAction.REGISTER
            candidates.isNotEmpty() -> RegistrationAction.SELECT_SPECIES
            else -> RegistrationAction.NONE
        }

        // LOG: RegistrationAssessment
        val fm = (recognitionResults["FastMoveRow"] ?: recognitionResults["SummaryFastMove"])?.firstOrNull()?.value?.toString() ?: "MISSING (No RecognitionResult)"
        val cma = recognitionResults["ChargedMoveRowA"]?.firstOrNull()?.value?.toString() ?: "MISSING (No RecognitionResult)"
        val cmb = recognitionResults["ChargedMoveRowB"]?.firstOrNull()?.value?.toString() ?: "MISSING (No RecognitionResult)"

        TraceLogger.logStage(
            captureId = captureId,
            stage = "RegistrationAssessment",
            species = solvedSpecies?.name ?: normalizedSpeciesName ?: "MISSING",
            family = normalizedFamilyName ?: "MISSING",
            cp = recognitionResults["CombatPower"]?.firstOrNull()?.value?.toString() ?: "MISSING",
            fast = fm,
            chgA = cma,
            chgB = cmb,
            confidence = (mainConfidence * 100).toInt().toString() + "%"
        )

        TraceLogger.logConfidenceTrace(
            captureId = captureId,
            source = "RegistrationEngine.candidates.maxOfOrNull { it.confidence }",
            value = (mainConfidence * 100).toInt().toString() + "%",
            details = candidates.map { "${it.name}: ${it.confidence} (${it.reasoning})" }
        )

        val verdicts = mutableMapOf<String, String>()
        verdicts["OCR"] = if (recognitionResults.isNotEmpty()) "PASS" else "FAIL"
        verdicts["Recognition"] = if (candidates.isNotEmpty()) "PASS" else "FAIL"
        verdicts["Assessment"] = "PASS"
        verdicts["Confidence"] = "NOT IMPLEMENTED (Heuristic Scoring)"
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
