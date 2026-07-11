package com.example.overdex.data

import android.content.Context
import com.example.overdex.model.SharedEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Manages the chronological feed of events shared between partners.
 */
interface SharedTimelineRepository {
    val events: StateFlow<List<SharedEvent>>
    suspend fun recordEvent(event: SharedEvent)
    suspend fun clear()
}

class SharedPreferencesTimelineRepository(private val context: Context) : SharedTimelineRepository {
    private val prefs = context.getSharedPreferences("overdex_timeline_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _events = MutableStateFlow<List<SharedEvent>>(emptyList())
    override val events: StateFlow<List<SharedEvent>> = _events.asStateFlow()

    init {
        val storedJson = prefs.getString("shared_events", "[]")
        if (storedJson != null) {
            try {
                _events.value = json.decodeFromString<List<SharedEvent>>(storedJson)
            } catch (e: Exception) {
                // Handle malformed data
            }
        }
    }

    override suspend fun recordEvent(event: SharedEvent) {
        val currentList = _events.value.toMutableList()
        currentList.add(event)
        val sortedList = currentList.sortedByDescending { it.timestamp }
        
        val timelineJson = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SharedEvent.serializer()), sortedList)
        prefs.edit().putString("shared_events", timelineJson).apply()
        _events.value = sortedList
    }

    override suspend fun clear() {
        prefs.edit().remove("shared_events").apply()
        _events.value = emptyList()
    }
}
