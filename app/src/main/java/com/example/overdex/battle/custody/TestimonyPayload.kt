package com.example.overdex.battle.custody

/**
 * A domain-neutral container for what a Witness experiences.
 * 
 * TestimonyPayload is explicitly decoupled from the phenomenon being observed.
 * It exists only to carry data from a Witness into Custody without forcing the
 * Custody layer to understand or interpret the sensing technology used.
 */
interface TestimonyPayload

/**
 * A simple, neutral implementation of [TestimonyPayload] used for generic data.
 */
data class RawTestimony(val data: Any) : TestimonyPayload
