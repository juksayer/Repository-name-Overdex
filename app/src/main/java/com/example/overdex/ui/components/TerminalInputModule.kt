package com.example.overdex.ui.components

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * Controller for the hardware-driven TerminalKeyboard firmware module.
 */
class TerminalKeyboardController(
    initialLayout: List<List<String>> = TestKeyboardLayout
) {
    var layout by mutableStateOf(initialLayout)
    var isVisible by mutableStateOf(false)
    var currentRow by mutableIntStateOf(0)
    var currentCol by mutableIntStateOf(0)

    fun open() {
        isVisible = true
    }

    fun close() {
        isVisible = false
    }

    fun updateLayout(newLayout: List<List<String>>) {
        layout = newLayout
        currentRow = 0
        currentCol = 0
    }

    fun handleUp() {
        if (currentRow > 0) {
            currentRow--
            // Clamp column to the new row's length
            currentCol = currentCol.coerceAtMost(layout[currentRow].size - 1)
            // Skip empty cells
            if (layout[currentRow][currentCol].isEmpty()) {
                handleLeft() // Try to find a non-empty cell in the same row
            }
        }
    }

    fun handleDown() {
        if (currentRow < layout.size - 1) {
            currentRow++
            // Clamp column to the new row's length
            currentCol = currentCol.coerceAtMost(layout[currentRow].size - 1)
            // Skip empty cells
            if (layout[currentRow][currentCol].isEmpty()) {
                handleLeft()
            }
        }
    }

    fun handleLeft() {
        if (currentCol > 0) {
            currentCol--
            if (layout[currentRow][currentCol].isEmpty()) handleLeft()
        }
    }

    fun handleRight() {
        if (currentCol < layout[currentRow].size - 1) {
            currentCol++
            if (layout[currentRow][currentCol].isEmpty()) handleRight()
        }
    }

    /**
     * Maps to hardware 'A'.
     * Activates the currently selected key on the grid.
     */
    fun handleA(query: String, onKeyActivated: (String) -> Unit) {
        val key = layout[currentRow][currentCol]
        if (key == "#") {
            handleModeSwitch()
        } else if (key.isNotEmpty()) {
            onKeyActivated(key)
        }
    }

    /**
     * Toggles between primary (Letters) and secondary (Token) layouts.
     */
    fun handleModeSwitch() {
        val nextLayout = if (layout == LettersLayout) TokenLayout else LettersLayout
        updateLayout(nextLayout)
    }

    /**
     * Deletes the last character of the query.
     * Logic belongs to the keyboard module.
     */
    fun performBackspace(query: String, onQueryChange: (String) -> Unit) {
        if (query.isNotEmpty()) {
            onQueryChange(query.dropLast(1))
        }
    }

    /**
     * Maps to hardware 'B'.
     * B retains its global meaning of Back/Cancel.
     */
    fun handleB(): Boolean {
        return if (isVisible) {
            close()
            true
        } else false
    }

    /**
     * Maps to hardware 'START'.
     * Commits the input and closes the keyboard.
     */
    fun handleStart(): Boolean {
        if (isVisible) {
            close()
            return true
        }
        return false
    }
}

@Composable
fun rememberTerminalKeyboardController(): TerminalKeyboardController {
    // Simple state holder for now, could add saver if needed for rotation
    return remember { TerminalKeyboardController() }
}
