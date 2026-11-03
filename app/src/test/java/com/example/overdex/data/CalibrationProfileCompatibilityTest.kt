package com.example.overdex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationProfileCompatibilityTest {
    private val moondrop = profile("moondrop", "MOONDROP MIAD01", 1080, 2400)
    private val pixel = profile("pixel", "Google Pixel 4", 1080, 2280)

    @Test
    fun `device identity ignores capitalization and incidental whitespace`() {
        val saved = pixel.copy(deviceModel = "  GOOGLE  Pixel\t4 \n")
        assertTrue(CalibrationProfileCompatibility.matchesDevice(saved, "google pixel 4", 1080, 2280))
        assertTrue(CalibrationProfileCompatibility.matchesDevice(pixel, " GOOGLE\nPixel 4  ", 1080, 2280))
    }

    @Test
    fun `different hardware with identical display dimensions is excluded`() {
        val otherModel = moondrop.copy(id = "other", deviceModel = "Google Pixel 8")
        assertFalse(CalibrationProfileCompatibility.matchesDevice(otherModel, "MOONDROP MIAD01", 1080, 2400))
        assertEquals(listOf(moondrop), select(listOf(otherModel, moondrop)))
    }

    @Test
    fun `model suffixes and manufacturer remain significant`() {
        assertFalse(CalibrationProfileCompatibility.matchesDevice(pixel, "Google Pixel 4 XL", 1080, 2280))
        assertFalse(CalibrationProfileCompatibility.matchesDevice(pixel, "Pixel 4", 1080, 2280))
        assertFalse(CalibrationProfileCompatibility.matchesDevice(pixel, "Other Pixel 4", 1080, 2280))
    }

    @Test
    fun `same hardware at another resolution or orientation requires explicit selection`() {
        val scaled = moondrop.copy(id = "scaled", screenWidth = 720, screenHeight = 1600)
        val rotated = moondrop.copy(id = "rotated", screenWidth = 2400, screenHeight = 1080)
        val contentHeight = moondrop.copy(id = "app-content", screenHeight = 2244)
        assertEquals(listOf(moondrop), select(listOf(scaled, rotated, contentHeight, moondrop)))
    }

    @Test
    fun `missing or unknown device identity never certifies compatibility`() {
        for (identity in listOf("", " \t\n", "unknown", " UNKNOWN ")) {
            val profile = moondrop.copy(deviceModel = identity)
            assertFalse(CalibrationProfileCompatibility.matchesDevice(profile, identity, 1080, 2400))
        }
        assertTrue(select(listOf(moondrop), device = "").isEmpty())
    }

    @Test
    fun `zero and negative saved or detected dimensions never match`() {
        for (invalid in listOf(0, -1)) {
            assertFalse(CalibrationProfileCompatibility.matchesDevice(moondrop, moondrop.deviceModel, invalid, 2400))
            assertFalse(CalibrationProfileCompatibility.matchesDevice(moondrop, moondrop.deviceModel, 1080, invalid))
            assertFalse(CalibrationProfileCompatibility.matchesDevice(moondrop.copy(screenWidth = invalid), moondrop.deviceModel, invalid, 2400))
            assertFalse(CalibrationProfileCompatibility.matchesDevice(moondrop.copy(screenHeight = invalid), moondrop.deviceModel, 1080, invalid))
        }
    }

    @Test
    fun `default list preserves matching profile order and confirmation information`() {
        val olderWorking = moondrop.copy(id = "working", savedAtMillis = 10L, workingConfirmedAtMillis = 9L)
        val newerDraft = moondrop.copy(id = "draft", savedAtMillis = 30L, differsFromWorkingCheckpoint = true)
        assertEquals(listOf(olderWorking, newerDraft), select(listOf(pixel, olderWorking, newerDraft)))
    }

    @Test
    fun `show other devices retains every profile with stable matching first order`() {
        val other = pixel.copy(id = "pixel-other", screenHeight = 2400)
        val secondMatch = moondrop.copy(id = "moondrop-second")
        val unknown = profile("unknown", "", 0, 0)
        val savedOrder = listOf(pixel, moondrop, other, secondMatch, unknown)

        assertEquals(listOf(moondrop, secondMatch, pixel, other, unknown), select(savedOrder, includeOtherDevices = true))
        assertEquals(listOf(pixel, moondrop, other, secondMatch, unknown), savedOrder)
    }

    @Test
    fun `no compatible profile gives an empty default list with all devices still available`() {
        val savedOrder = listOf(pixel, moondrop)
        assertTrue(select(savedOrder, device = "onn Tablet", width = 800, height = 1280).isEmpty())
        assertEquals(savedOrder, select(savedOrder, device = "onn Tablet", width = 800, height = 1280, includeOtherDevices = true))
        assertTrue(select(emptyList(), includeOtherDevices = true).isEmpty())
    }

    private fun profile(id: String, device: String, width: Int, height: Int) =
        MatchCalibrationProfileSummary(id, id, device, width, height, savedAtMillis = 1L)

    private fun select(
        profiles: List<MatchCalibrationProfileSummary>,
        device: String = moondrop.deviceModel,
        width: Int = moondrop.screenWidth,
        height: Int = moondrop.screenHeight,
        includeOtherDevices: Boolean = false
    ) = CalibrationProfileCompatibility.visibleProfiles(profiles, device, width, height, includeOtherDevices)
}
