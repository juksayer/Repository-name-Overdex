package com.example.overdex.battle.audio

import org.junit.Assert.*
import org.junit.Test

class CryWaveformAcceptanceTest {
    private fun fit(species:Int,score:Float,at:Long=0)=CryWaveformFit(species,"ref-$species",score,at,1_000_000_000)
    @Test fun `weak or competing same onset species are unresolved`() {
        assertTrue(CryWaveformAcceptance.select(listOf(fit(1,.3f))).isEmpty())
        assertTrue(CryWaveformAcceptance.select(listOf(fit(1,.8f),fit(2,.75f))).isEmpty())
        assertEquals(1,CryWaveformAcceptance.select(listOf(fit(1,.8f),fit(2,.2f))).single().speciesId)
        assertTrue(CryWaveformAcceptance.select(listOf(fit(1,Float.NaN))).isEmpty())
    }
    @Test fun `distinct temporal cries coexist but duplicate reference variants are one occurrence`() {
        val result=CryWaveformAcceptance.select(listOf(fit(1,.8f),fit(1,.9f),fit(2,.8f,500_000_000)))
        assertEquals(listOf(1,2),result.map { it.speciesId })
        assertEquals(.9f,result.first().correlation)
    }
}
