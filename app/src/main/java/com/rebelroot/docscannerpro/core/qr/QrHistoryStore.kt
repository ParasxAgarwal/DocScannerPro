package com.rebelroot.docscannerpro.core.qr

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.qrHistoryDataStore by preferencesDataStore(name = "qr_history")

data class QrHistoryEntry(
    val id: String,
    val rawValue: String,
    val label: String,
    val kind: String,
    val timestamp: Long
)

/**
 * Local-only scan history. No cloud sync, no telemetry; lives in app-private storage.
 */
class QrHistoryStore(private val context: Context) {

    private val key = stringPreferencesKey("entries")

    val entries: Flow<List<QrHistoryEntry>> = context.qrHistoryDataStore.data.map { prefs ->
        decode(prefs[key] ?: "[]")
    }

    suspend fun add(rawValue: String, label: String, kind: String) = withContext(Dispatchers.IO) {
        context.qrHistoryDataStore.edit { prefs ->
            val list = decode(prefs[key] ?: "[]")
                .filterNot { it.rawValue == rawValue }
                .toMutableList()
            list.add(0, QrHistoryEntry(UUID.randomUUID().toString(), rawValue, label, kind, System.currentTimeMillis()))
            prefs[key] = encode(list.take(MAX_ENTRIES))
        }
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        context.qrHistoryDataStore.edit { prefs ->
            prefs[key] = encode(decode(prefs[key] ?: "[]").filterNot { it.id == id })
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        context.qrHistoryDataStore.edit { prefs ->
            prefs[key] = "[]"
        }
    }

    private fun encode(list: List<QrHistoryEntry>): String {
        val array = JSONArray()
        list.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("raw", entry.rawValue)
                    .put("label", entry.label)
                    .put("kind", entry.kind)
                    .put("ts", entry.timestamp)
            )
        }
        return array.toString()
    }

    private fun decode(json: String): List<QrHistoryEntry> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            QrHistoryEntry(
                id = obj.optString("id"),
                rawValue = obj.optString("raw"),
                label = obj.optString("label"),
                kind = obj.optString("kind", "TEXT"),
                timestamp = obj.optLong("ts")
            )
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val MAX_ENTRIES = 200
    }
}
