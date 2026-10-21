package com.example.overdex.ui

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.observation.DecisionTimerAppearance
import com.example.overdex.battle.observation.DecisionTimerReadingFilter
import com.example.overdex.battle.observation.DecisionTimerTextReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DecisionTimerDeviceTest {
    @Test fun readsActualShieldCountdownCrops(): Unit = runBlocking {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        for (name in listOf("4", "3", "absent", "moondrop-3", "moondrop-1", "moondrop-absent")) {
            val bitmap = assets.open("battle/decision-timer/$name.png").use(BitmapFactory::decodeStream)
            try {
                assertEquals(name, !name.endsWith("absent"), DecisionTimerAppearance.hasDial(bitmap))
                if (!name.endsWith("absent")) {
                    val raw = DecisionTimerTextReader.read(bitmap)
                    assertEquals("$name OCR: $raw", name.substringAfterLast('-').toInt(), DecisionTimerReadingFilter.parse(raw))
                }
            } finally { bitmap.recycle() }
        }
    }
}
