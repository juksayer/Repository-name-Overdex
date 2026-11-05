package com.example.overdex.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalKeyboardControllerTest {
    @Test fun `new keyboards use QWERTY while an explicit alphabet keyboard remains available`() {
        assertEquals(QwertyLayout, TerminalKeyboardController().layout)
        assertEquals(LettersLayout, TerminalKeyboardController(LettersLayout).layout)
        assertEquals(('A'..'Z').toSet(), QwertyLayout.flatten().filter { it.length == 1 && it[0].isLetter() }.map { it[0] }.toSet())
    }

    @Test fun `finishing a search leaves keyboard visible without owning navigation`() {
        val keyboard = TerminalKeyboardController(keepVisibleAfterInput = true)
        keyboard.open()
        assertTrue(keyboard.handleStart())
        assertTrue(keyboard.isVisible)
        assertFalse(keyboard.isFocused)
        keyboard.activateKey("S") { assertEquals("S", it) }
        assertTrue(keyboard.isFocused)
    }

    @Test fun `END finishes input without inserting its label and B still hides keys`() {
        val keyboard = TerminalKeyboardController(keepVisibleAfterInput = true)
        keyboard.open()
        keyboard.activateKey("END") { error("END is a control, not nickname text") }
        assertTrue(keyboard.isVisible)
        assertFalse(keyboard.isFocused)
        assertTrue(keyboard.handleB())
        assertFalse(keyboard.isVisible)
    }

    @Test fun `existing keyboard screens still close on END`() {
        val keyboard = TerminalKeyboardController()
        keyboard.open()
        keyboard.activateKey("END") { error("END must not be inserted") }
        assertFalse(keyboard.isVisible)
        assertFalse(keyboard.isFocused)
    }

    @Test fun `touch and A both switch pages without adding a hash to the nickname`() {
        val keyboard = TerminalKeyboardController(LettersLayout)
        val emitted = mutableListOf<String>()
        keyboard.activateKey("#", emitted::add)
        assertEquals(TokenLayout, keyboard.layout)
        keyboard.activateKey("TYPE:FIRE", emitted::add)
        assertEquals(listOf("TYPE:FIRE"), emitted)
        keyboard.currentRow = TokenLayout.lastIndex
        keyboard.currentCol = TokenLayout.last().indexOf("#")
        keyboard.handleA("") { emitted += it }
        assertEquals(LettersLayout, keyboard.layout)
        assertEquals(listOf("TYPE:FIRE"), emitted)
    }

    @Test fun `a missing move list does not crash when right or A is pressed`() {
        val keyboard = TerminalKeyboardController(emptyList())
        keyboard.handleRight()
        keyboard.handleA("") { error("No selectable move") }
        assertEquals(0, keyboard.currentRow)
    }

    @Test fun `touch layout choice survives token pages and a field reopening letters`() {
        val keyboard = TerminalKeyboardController()
        val emitted = mutableListOf<String>()
        keyboard.activateKey("ABC", emitted::add)
        assertEquals(LettersLayout, keyboard.layout)
        keyboard.activateKey("#", emitted::add)
        assertEquals(TokenLayout, keyboard.layout)
        keyboard.activateKey("#", emitted::add)
        assertEquals(LettersLayout, keyboard.layout)

        val numbers = listOf(listOf("1", "2", "3"), listOf("0", "DELETE"))
        keyboard.updateLayout(numbers)
        keyboard.updateLayout(LettersLayout)
        assertEquals(LettersLayout, keyboard.layout)
        keyboard.activateKey("QWERTY", emitted::add)
        keyboard.updateLayout(numbers)
        keyboard.updateLayout(LettersLayout)
        assertEquals(QwertyLayout, keyboard.layout)
        assertTrue(emitted.isEmpty())
    }

    @Test fun `instrument A can choose a letter layout directly from the token page`() {
        val keyboard = TerminalKeyboardController()
        val emitted = mutableListOf<String>()
        keyboard.activateKey("#", emitted::add)
        keyboard.currentRow = TokenLayout.lastIndex
        keyboard.currentCol = TokenLayout.last().indexOf("ABC")
        keyboard.handleA("", emitted::add)
        assertEquals(LettersLayout, keyboard.layout)
        assertEquals(0, keyboard.currentRow)
        assertEquals(0, keyboard.currentCol)

        keyboard.activateKey("#", emitted::add)
        keyboard.currentRow = TokenLayout.lastIndex
        keyboard.currentCol = TokenLayout.last().indexOf("QWERTY")
        keyboard.handleA("", emitted::add)
        assertEquals(QwertyLayout, keyboard.layout)
        assertTrue(emitted.isEmpty())
    }

    @Test fun `finishing and returning to input preserve the selected letter page`() {
        val keyboard = TerminalKeyboardController(keepVisibleAfterInput = true)
        keyboard.activateKey("ABC") { error("A layout name must not become text") }
        keyboard.activateKey("END") { error("END must not become text") }
        assertFalse(keyboard.isFocused)
        assertTrue(keyboard.isVisible)
        keyboard.open()
        assertEquals(LettersLayout, keyboard.layout)
        assertTrue(keyboard.handleB())
        keyboard.open()
        assertEquals(LettersLayout, keyboard.layout)
    }

    @Test fun `mode switching cannot replace numeric or whole move grids`() {
        val numbers = listOf(listOf("1", "2", "3"), listOf("0", "DELETE"))
        val moves = listOf(listOf("DRAGON BREATH", "CHARM"), listOf("NONE"))
        val keyboard = TerminalKeyboardController()
        val emitted = mutableListOf<String>()
        for (custom in listOf(numbers, moves)) {
            keyboard.updateLayout(custom)
            keyboard.handleModeSwitch()
            assertEquals(custom, keyboard.layout)
            keyboard.activateKey("#", emitted::add)
            assertEquals(custom, keyboard.layout)
        }
        keyboard.activateKey("DRAGON BREATH", emitted::add)
        assertEquals(listOf("DRAGON BREATH"), emitted)
        assertEquals(QwertyLayout, keyboard.primaryLetterLayout)
    }
}
