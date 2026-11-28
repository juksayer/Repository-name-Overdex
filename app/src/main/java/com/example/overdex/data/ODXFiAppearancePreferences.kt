package com.example.overdex.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

data class HudMoveAppearance(
    val textColor: Int = -1,
    val shadowColor: Int = 0xFF000000.toInt(),
    val fontSp: Int = 7,
    val bloomPercent: Int = 100,
    val rowPaddingDp: Int = 0,
) {
    fun bounded() = copy(
        textColor = textColor or OPAQUE_ALPHA, shadowColor = shadowColor or OPAQUE_ALPHA,
        fontSp = fontSp.coerceIn(6, 16), bloomPercent = bloomPercent.coerceIn(0, 150),
        rowPaddingDp = rowPaddingDp.coerceIn(0, 8),
    )
}

data class ODXFiAppearance(
    val caseColorArgb: Int = DEFAULT_CASE_COLOR_ARGB,
    val buttonColorArgb: Int = DEFAULT_BUTTON_COLOR_ARGB,
    val bezelColorArgb: Int = DEFAULT_BEZEL_COLOR_ARGB,
    val hud: HudMoveAppearance = HudMoveAppearance(),
) {
    fun opaque(): ODXFiAppearance = copy(
        caseColorArgb = caseColorArgb or OPAQUE_ALPHA,
        buttonColorArgb = buttonColorArgb or OPAQUE_ALPHA,
        bezelColorArgb = bezelColorArgb or OPAQUE_ALPHA,
        hud = hud.bounded(),
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

    fun observeHud(onChange: (HudMoveAppearance) -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange(read().hud) }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onChange(read().hud)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun read(): ODXFiAppearance = ODXFiAppearance(
        caseColorArgb = readColor(CASE_COLOR, ODXFiAppearance.DEFAULT_CASE_COLOR_ARGB),
        buttonColorArgb = readColor(BUTTON_COLOR, ODXFiAppearance.DEFAULT_BUTTON_COLOR_ARGB),
        bezelColorArgb = readColor(BEZEL_COLOR, ODXFiAppearance.DEFAULT_BEZEL_COLOR_ARGB),
        hud = HudMoveAppearance().let { defaults ->
            HudMoveAppearance(
                readColor("hud_text", defaults.textColor), readColor("hud_shadow", defaults.shadowColor),
                // Read the legacy size one point smaller until the trainer saves a new size.
                readInt(HUD_FONT_SP, readInt("hud_font", defaults.fontSp + 1).coerceIn(6, 16) - 1),
                readInt("hud_bloom", defaults.bloomPercent),
                readInt("hud_padding", defaults.rowPaddingDp),
            ).bounded()
        },
    )

    /** The caller should change visible state only after a successful durable save. */
    fun write(appearance: ODXFiAppearance): Boolean = preferences.edit()
        .putString(CASE_COLOR, odxFiColorHex(appearance.caseColorArgb))
        .putString(BUTTON_COLOR, odxFiColorHex(appearance.buttonColorArgb))
        .putString(BEZEL_COLOR, odxFiColorHex(appearance.bezelColorArgb))
        .apply {
            val hud = appearance.hud.bounded()
            putString("hud_text", odxFiColorHex(hud.textColor))
            putString("hud_shadow", odxFiColorHex(hud.shadowColor))
            putInt(HUD_FONT_SP, hud.fontSp)
            putInt("hud_bloom", hud.bloomPercent)
            putInt("hud_padding", hud.rowPaddingDp)
        }
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

    /** Restore the shell and HUD appearance defaults. */
    fun resetDefaults(): Boolean = write(ODXFiAppearance())

    private fun readInt(key: String, default: Int): Int = try {
        preferences.getInt(key, default)
    } catch (_: ClassCastException) {
        default
    }

    private fun readColor(key: String, default: Int): Int = try {
        preferences.getString(key, null)?.let(::parseODXFiHexColor) ?: default
    } catch (_: ClassCastException) {
        default
    }

    private companion object {
        const val HUD_FONT_SP = "hud_font_sp"
        const val PREFERENCES = "overdex_odx_fi_appearance"
        const val CASE_COLOR = "case_color"
        const val BUTTON_COLOR = "button_color"
        const val BEZEL_COLOR = "bezel_color"
    }
}

private const val OPAQUE_ALPHA: Int = 0xFF000000.toInt()
private val RGB_HEX = Regex("#[0-9A-Fa-f]{6}")
