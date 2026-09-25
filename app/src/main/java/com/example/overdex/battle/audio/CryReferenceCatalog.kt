package com.example.overdex.battle.audio

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

/** A stable, verifiable reference recording available to the battle-cry matcher. */
data class CryReference(
    val assetPath: String,
    val speciesId: Int,
    val variant: String?,
    val sha256: String,
    val byteCount: Long,
    val mediaType: String
)

/**
 * Reads the bundled cry corpus without identifying any live sound. A matcher must open and
 * verify the specific reference it uses, then cite both the captured audio article and this
 * immutable reference identity in its later conclusion.
 */
class CryReferenceCatalog private constructor(val references: List<CryReference>) {
    val speciesIds: Set<Int> = references.map { it.speciesId }.toSet()

    fun referencesFor(speciesId: Int): List<CryReference> =
        references.filter { it.speciesId == speciesId }

    fun openVerified(context: Context, reference: CryReference): ByteArray? = try {
        val bytes = context.assets.open(reference.assetPath).use { it.readBytes() }
        if (bytes.size.toLong() != reference.byteCount || sha256(bytes) != reference.sha256) null else bytes
    } catch (_: Exception) {
        null
    }

    companion object {
        const val MANIFEST_PATH = "cry_references/manifest.json"

        fun load(context: Context): CryReferenceCatalog =
            fromManifest(context.assets.open(MANIFEST_PATH).bufferedReader().use { it.readText() })

        internal fun fromManifest(json: String): CryReferenceCatalog {
            val values = JSONObject(json).getJSONArray("references")
            return CryReferenceCatalog((0 until values.length()).map { index ->
                values.getJSONObject(index).toCryReference()
            })
        }

        private fun JSONObject.toCryReference(): CryReference = CryReference(
            assetPath = getString("assetPath"),
            speciesId = getInt("speciesId"),
            variant = if (isNull("variant")) null else getString("variant"),
            sha256 = getString("sha256"),
            byteCount = getLong("byteCount"),
            mediaType = getString("mediaType")
        )

        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
