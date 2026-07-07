package com.example.overdex.ui.components

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * Controller for the hardware-driven TerminalKeyboard firmware module.
 */
class TerminalKeyboardController(
    val layout: List<List<String>> = TestKeyboardLayout
) {
    var isVisible by mutableStateOf(false)
    var currentRow by mutableIntStateOf(0)
    var currentCol by mutableIntStateOf(0)

    fun open() {
        isVisible = true
    }

    fun close() {
        isVisible = false
    }

    fun handleUp() {
        if (currentRow > 0) {
            currentRow--
            currentCol = currentCol.coerceAtMost(layout[currentRow].size - 1)
        }
    }

    fun handleDown() {
        if (currentRow < layout.size - 1) {
            currentRow++
            currentCol = currentCol.coerceAtMost(layout[currentRow].size - 1)
        }
    }

    fun handleLeft() {
        if (currentCol > 0) currentCol--
    }

    fun handleRight() {
        if (currentCol < layout[currentRow].size - 1) currentCol++
    }

    fun handleA(query: String, onQueryChange: (String) -> Unit) {
        val key = layout[currentRow][currentCol]
        when (key) {
            "OK", "DONE" -> close()
            "←", "⌫" -> if (query.isNotEmpty()) onQueryChange(query.dropLast(1))
            "SPACE" -> onQueryChange(query + " ")
            "CLEAR" -> onQueryChange("")
            else -> if (key.isNotEmpty()) onQueryChange(query + key)
        }
    }

    fun handleB(): Boolean {
        return if (isVisible) {
            close()
            true
        } else false
    }
}

@Composable
fun rememberTerminalKeyboardController(): TerminalKeyboardController {
    // Simple state holder for now, could add saver if needed for rotation
    return remember { TerminalKeyboardController() }
}
