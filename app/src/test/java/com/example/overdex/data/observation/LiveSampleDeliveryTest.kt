package com.example.overdex.data.observation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSampleDeliveryTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `a continuously busy crop cannot monopolize the shared capture lane`() = runBlocking {
        withContext(Dispatchers.IO.limitedParallelism(1)) {
            val frames = MutableSharedFlow<Int>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            frames.tryEmit(0)
            var processed = 0
            val busyCrop = launch {
                frames.take(100).collectLiveSamples {
                    processed++
                    // The next frame arrives before this crop finishes encoding.
                    // SharedFlow can deliver it without suspending the collector.
                    frames.tryEmit(processed)
                }
            }
            val shieldCrop = async { processed }
            val countWhenShieldGotItsTurn = shieldCrop.await()
            busyCrop.join()
            assertTrue("Shield worker waited for all $countWhenShieldGotItsTurn frames", countWhenShieldGotItsTurn < 100)
        }
    }
}
