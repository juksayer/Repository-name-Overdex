package com.example.overdex.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalKeyboardControllerTest {
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
}
