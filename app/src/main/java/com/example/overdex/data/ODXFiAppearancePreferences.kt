package com.example.overdex.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

/** Cosmetic shell colors only; these values never change observation or calibration. */
data class ODXFiAppearance(
    val caseColorArgb: Int = DEFAULT_CASE_COLOR_ARGB,
    val buttonColorArgb: Int = DEFAULT_BUTTON_COLOR_ARGB,
    val bezelColorArgb: Int = DEFAULT_BEZEL_COLOR_ARGB,
) {
    fun opaque(): ODXFiAppearance = copy(
        caseColorArgb = caseColorArgb or OPAQUE_ALPHA,
        buttonColorArgb = buttonColorArgb or OPAQUE_ALPHA,
        bezelColorArgb = bezelColorArgb or OPAQUE_ALPHA,
    )

    companion object {
        const val DEFAULT_CASE_COLOR_ARGB: Int = 0xFF2E7D32.toInt()
        const val DEFAULT_BUTTON_COLOR_ARGB: Int = 0xFF444444.toInt()
        const val DEFAULT_BEZEL_COLOR_ARGB: Int = 0xFF2A732E.toInt()
    }
}

/** Accept a complete RGB hex code; transparency is intentionally unavailable for the shell. */
fun parseODXFiHexColor(text: String): Int? {
    if (!RGB_HEX.matches(text)) return null
    return text.substring(1).toIntOrNull(16)?.or(OPAQUE_ALPHA)
}

fun odxFiColorHex(argb: Int): String =
    String.format(Locale.ROOT, "#%06X", argb and 0x00FFFFFF)

class ODXFiAppearancePreferences internal constructor(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE))

    fun read(): ODXFiAppearance = ODXFiAppearance(
        caseColorArgb = readColor(CASE_COLOR, ODXFiAppearance.DEFAULT_CASE_COLOR_ARGB),
        buttonColorArgb = readColor(BUTTON_COLOR, ODXFiAppearance.DEFAULT_BUTTON_COLOR_ARGB),
        bezelColorArgb = readColor(BEZEL_COLOR, ODXFiAppearance.DEFAULT_BEZEL_COLOR_ARGB),
    )

    /** The caller should change visible state only after a successful durable save. */
    fun write(appearance: ODXFiAppearance): Boolean = preferences.edit()
        .putString(CASE_COLOR, odxFiColorHex(appearance.caseColorArgb))
        .putString(BUTTON_COLOR, odxFiColorHex(appearance.buttonColorArgb))
        .putString(BEZEL_COLOR, odxFiColorHex(appearance.bezelColorArgb))
        .commit()

    fun writeCaseColor(argb: Int): Boolean = preferences.edit()
        .putString(CASE_COLOR, odxFiColorHex(argb))
        .commit()

    fun writeButtonColor(argb: Int): Boolean = preferences.edit()
        .putString(BUTTON_COLOR, odxFiColorHex(argb))
        .commit()

    fun writeBezelColor(argb: Int): Boolean = preferences.edit()
        .putString(BEZEL_COLOR, odxFiColorHex(argb))
        .commit()

    /** Reset just the three appearance choices, preserving any other stored values. */
    fun resetDefaults(): Boolean = write(ODXFiAppearance())

    private fun readColor(key: String, default: Int): Int = try {
        preferences.getString(key, null)?.let(::parseODXFiHexColor) ?: default
    } catch (_: ClassCastException) {
        default
    }

    private companion object {
        const val PREFERENCES = "overdex_odx_fi_appearance"
        const val CASE_COLOR = "case_color"
        const val BUTTON_COLOR = "button_color"
        const val BEZEL_COLOR = "bezel_color"
    }
}

private const val OPAQUE_ALPHA: Int = 0xFF000000.toInt()
private val RGB_HEX = Regex("#[0-9A-Fa-f]{6}")
