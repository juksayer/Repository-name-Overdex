package com.example.overdex.validation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.CaptureTemplateManager
import com.example.overdex.data.observation.GuidedObservationPipeline
import com.example.overdex.model.observation.ChargedMoveObservation
import com.example.overdex.model.observation.CombatPowerObservation
import com.example.overdex.model.observation.CountdownObservation
import com.example.overdex.model.observation.DefaultObservationResolver
import com.example.overdex.model.observation.EvolutionFamilyObservation
import com.example.overdex.model.observation.FastMoveObservation
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.ObservationObjective
import com.example.overdex.model.observation.ObservationSession
import com.example.overdex.model.observation.PokemonNameObservation
import com.example.overdex.model.observation.SessionSource
import com.example.overdex.model.observation.ShadowStatusObservation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Validates the Observation Engine against the defined validation sessions.
 * This test iterates through sessions in assets/validation/sessions/,
 * runs the pipeline, and compares the resulting ObservationSession state against expected.json.
 */
class ObservationEngineValidator {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun validateAllSessions() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        val assetManager = context.assets
        val sessionsRoot = "validation/sessions"
        
        val sessions = assetManager.list(sessionsRoot) ?: return@runBlocking
        
        for (sessionDirName in sessions) {
            val sessionPath = "$sessionsRoot/$sessionDirName"
            val readmePath = "$sessionPath/BrutalSwingOriginStory.md"
            val expectedPath = "$sessionPath/expected.json"
            
            // 1. Read Expected Data
            val expectedJsonString = assetManager.open(expectedPath).bufferedReader().use { it.readText() }
            val expectedData = json.parseToJsonElement(expectedJsonString) as JsonObject
            
            val objectiveLabel = expectedData["objective"]?.jsonPrimitive?.content ?: "RegisterSpecimen"
            val objective = when (objectiveLabel) {
                "IdentifySpecimen" -> ObservationObjective.IdentifySpecimen
                else -> ObservationObjective.RegisterSpecimen
            }
            
            // 2. Prepare Inputs
            val inputs = assetManager.list(sessionPath)
                ?.filter { it.endsWith(".png") || it.endsWith(".jpg") }
                ?.sorted() ?: emptyList()
                
            var currentSession: ObservationSession? = null
            val templateManager = CaptureTemplateManager(InstrumentationRegistry.getInstrumentation().targetContext)
            val template = templateManager.getPokemonDetailTemplate()

            // 3. Run Pipeline for each input
            for (inputFileName in inputs) {
                val bitmap = assetManager.open("$sessionPath/$inputFileName").use { 
                    BitmapFactory.decodeStream(it)
                }
                
                val observationInput = object : ObservationInput {
                    override val source = SessionSource.SCREENSHOT
                    override suspend fun supply(onVisualData: suspend (Bitmap) -> Unit) {
                        onVisualData(bitmap)
                    }
                }
                
                GuidedObservationPipeline.run(
                    input = observationInput,
                    template = template,
                    existingSession = currentSession,
                    objective = objective
                ) { status ->
                    currentSession = status.session
                }
            }
            
            // 4. Validate Results
            validateSession(sessionDirName, currentSession!!, expectedData)
        }
    }

    private fun validateSession(sessionName: String, session: ObservationSession, expected: JsonObject) {
        val resolver = DefaultObservationResolver()
        val resolved = session.history.mapValues { (_, observations) ->
            resolver.resolve(observations)
        }
        val expectedFields = expected["expectedResolvedFields"]?.let { it as? JsonObject } ?: JsonObject(emptyMap())

        expectedFields.forEach { (field, expectedValue) ->
            val actualResults = resolved[field]
            val actualValue = when (actualResults) {
                is PokemonNameObservation -> actualResults.species
                is FastMoveObservation -> actualResults.moveName
                is ChargedMoveObservation -> actualResults.moveName
                is CombatPowerObservation -> actualResults.cp
                is ShadowStatusObservation -> actualResults.isShadow
                is EvolutionFamilyObservation -> actualResults.familySpecies
                is CountdownObservation -> actualResults.value
                null -> null
            }

            val expectedContent = expectedValue.jsonPrimitive.content

            assertEquals(
                "[$sessionName] Mismatch in field: $field",
                expectedContent,
                actualValue?.toString() ?: "null"
            )
        }
        
        val expectedIntegrity = expected["expectedIntegrity"]?.jsonPrimitive?.content
        if (expectedIntegrity != null) {
            assertEquals(
                "[$sessionName] Mismatch in Integrity Status",
                expectedIntegrity,
                session.evaluateIntegrity(resolver).status.name
            )
        }
        
        val expectedProgress = expected["expectedProgress"]?.jsonPrimitive?.floatOrNull
        if (expectedProgress != null) {
            assertEquals(
                "[$sessionName] Mismatch in Progress percentage",
                expectedProgress,
                session.evaluateProgress().percentComplete,
                0.01f
            )
        }
    }
}
