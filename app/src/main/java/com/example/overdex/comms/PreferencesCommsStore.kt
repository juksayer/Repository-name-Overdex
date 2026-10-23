package com.example.overdex.comms

import android.content.Context
import kotlinx.serialization.json.Json

/** Original legacy preferences are never deleted or rewritten during migration. */
class PreferencesCommsStore(context: Context) : CommsStore {
    private val prefs = context.getSharedPreferences("trainer_comms_accounts", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("overdex_chat_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun load(accountId: String): CommsSnapshot {
        prefs.getString("history_$accountId", null)?.let { return json.decodeFromString(it) }
        // Pre-account history belongs to this device. Migrate once, into its first signed-in account.
        val owner = prefs.getString("legacy_owner", null)
        if (owner != null && owner != accountId) return CommsSnapshot()
        val raw = legacy.getString("chat_messages", null) ?: return CommsSnapshot()
        val snapshot = LegacyCommsHistory.read(raw, accountId)
        check(prefs.edit().putString("legacy_owner", accountId)
            .putString("history_$accountId", json.encodeToString(CommsSnapshot.serializer(), snapshot)).commit()) {
            "Unable to preserve local chat history."
        }
        return snapshot
    }

    override suspend fun save(accountId: String, snapshot: CommsSnapshot) {
        check(prefs.edit().putString("history_$accountId",
            json.encodeToString(CommsSnapshot.serializer(), snapshot)).commit()) {
            "Unable to save conversations on this device."
        }
    }
}
