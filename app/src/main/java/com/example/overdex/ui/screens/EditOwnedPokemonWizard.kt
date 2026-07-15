package com.example.overdex.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.overdex.model.Pokemon
import com.example.overdex.ui.MyCollectionViewModel
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.*

@Composable
fun EditOwnedPokemonWizard(
    ownedId: String,
    pokedexViewModel: PokedexViewModel,
    collectionViewModel: MyCollectionViewModel,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    isServiceRunning: Boolean = false,
) {
    val ownedPokemon by collectionViewModel.getOwnedPokemon(ownedId).collectAsState(initial = null)
    var species by remember { mutableStateOf<Pokemon?>(null) }
    
    // Wizard state
    var currentStep by remember { mutableStateOf(WizardStep.CP_INPUT) }
    var cpValue by remember { mutableStateOf("0000") }
    var isShadow by remember { mutableStateOf(false) }
    var isPurified by remember { mutableStateOf(false) }
    var isShiny by remember { mutableStateOf(false) }

    // Initialize state from existing specimen
    LaunchedEffect(ownedPokemon) {
        ownedPokemon?.let {
            if (species == null) {
                species = pokedexViewModel.getPokemonById(it.speciesId)
                cpValue = it.cp?.toString()?.padStart(4, '0') ?: "0000"
                isShadow = it.isShadow
                isPurified = it.isPurified
                isShiny = it.isShiny
            }
        }
    }

    // Focus state
    var focusIndex by remember(currentStep) { mutableIntStateOf(0) }

    fun handleAction(index: Int) {
        when (currentStep) {
            WizardStep.CP_INPUT -> {
                if (index == 4) {
                    currentStep = WizardStep.ATTRIBUTES
                } else {
                    if (index < 4) focusIndex++
                }
            }
            WizardStep.ATTRIBUTES -> {
                when (index) {
                    0 -> { isShadow = !isShadow; if (isShadow) isPurified = false }
                    1 -> { isPurified = !isPurified; if (isPurified) isShadow = false }
                    2 -> { isShiny = !isShiny }
                    3 -> {
                        ownedPokemon?.let { original ->
                            collectionViewModel.updateOwnedPokemon(
                                original.copy(
                                    cp = cpValue.toIntOrNull(),
                                    isShadow = isShadow,
                                    isPurified = isPurified,
                                    isShiny = isShiny,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            onFinish()
                        }
                    }
                }
            }
            else -> {}
        }
    }

    PokedexFrame(
        onUp = {
            when (currentStep) {
                WizardStep.CP_INPUT -> {
                    if (focusIndex < 4) {
                        val charArray = cpValue.toCharArray()
                        val currentDigit = charArray[focusIndex].digitToInt()
                        charArray[focusIndex] = ((currentDigit + 1) % 10).digitToChar()
                        cpValue = String(charArray)
                    }
                }
                WizardStep.ATTRIBUTES -> if (focusIndex > 0) focusIndex--
                else -> {}
            }
        },
        onDown = {
            when (currentStep) {
                WizardStep.CP_INPUT -> {
                    if (focusIndex < 4) {
                        val charArray = cpValue.toCharArray()
                        val currentDigit = charArray[focusIndex].digitToInt()
                        charArray[focusIndex] = ((currentDigit + 9) % 10).digitToChar()
                        cpValue = String(charArray)
                    }
                }
                WizardStep.ATTRIBUTES -> if (focusIndex < 3) focusIndex++
                else -> {}
            }
        },
        onLeft = {
            if (currentStep == WizardStep.CP_INPUT && focusIndex > 0) focusIndex--
        },
        onRight = {
            if (currentStep == WizardStep.CP_INPUT && focusIndex < 4) focusIndex++
        },
        onA = {
            handleAction(focusIndex)
        },
        onB = {
            when (currentStep) {
                WizardStep.CP_INPUT -> onCancel()
                WizardStep.ATTRIBUTES -> currentStep = WizardStep.CP_INPUT
                else -> onCancel()
            }
        },
        isServiceRunning = isServiceRunning,
        viewModel = pokedexViewModel
    ) { _ ->
        Column(modifier = Modifier.fillMaxSize()) {
            TerminalHeader(text = "edit specimen - ${currentStep.ordinal}/2")

            when (currentStep) {
                WizardStep.CP_INPUT -> {
                    CPInputStep(
                        speciesName = species?.name ?: "UNKNOWN",
                        cpValue = cpValue,
                        focusIndex = focusIndex,
                        onFocusChange = { focusIndex = it },
                        onNext = { handleAction(4) }
                    )
                }
                WizardStep.ATTRIBUTES -> {
                    AttributesStep(
                        isShadow = isShadow,
                        isPurified = isPurified,
                        isShiny = isShiny,
                        focusIndex = focusIndex,
                        onFocusChange = { focusIndex = it },
                        onAction = { handleAction(it) }
                    )
                }
                else -> {}
            }
        }
    }
}
