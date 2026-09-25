package com.example.overdex

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class StateFlowTest {
    @Test
    fun testStateFlowPlusAssign() {
        val flow = MutableStateFlow<List<String>>(emptyList())
        flow.value += "Hello"
        assertEquals(1, flow.value.size)
        assertEquals("Hello", flow.value[0])
        flow.value += "World"
        assertEquals(2, flow.value.size)
    }
}
