package com.hhuezo.pdfconverter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.recentPdfsStore: DataStore<Preferences> by preferencesDataStore(
    name = "andros_recent_pdfs"
)

class RecentPdfsRepository(private val context: Context) {

    private val key = stringPreferencesKey("recent_pdfs_json")

    val recentPdfs: Flow<List<RecentPdf>> = context.recentPdfsStore.data.map { prefs ->
        parse(prefs[key].orEmpty())
    }

    suspend fun addOrUpdate(
        uri: String,
        displayName: String,
        sizeBytes: Long,
        lastPageIndex: Int? = null,
    ) {
        context.recentPdfsStore.edit { prefs ->
            val current = parse(prefs[key].orEmpty()).toMutableList()
            val existingIndex = current.indexOfFirst { it.uri == uri }
            val previousPage = current.getOrNull(existingIndex)?.lastPageIndex ?: 0
            val entry = RecentPdf(
                uri = uri,
                displayName = displayName,
                sizeBytes = sizeBytes,
                lastOpenedAt = System.currentTimeMillis(),
                lastPageIndex = lastPageIndex ?: previousPage,
            )
            if (existingIndex >= 0) {
                current.removeAt(existingIndex)
            }
            current.add(0, entry)
            prefs[key] = serialize(current.take(MAX_RECENT))
        }
    }

    suspend fun updateLastPage(uri: String, pageIndex: Int) {
        context.recentPdfsStore.edit { prefs ->
            val current = parse(prefs[key].orEmpty()).toMutableList()
            val index = current.indexOfFirst { it.uri == uri }
            if (index >= 0) {
                val old = current[index]
                current[index] = old.copy(lastPageIndex = pageIndex.coerceAtLeast(0))
                prefs[key] = serialize(current)
            }
        }
    }

    suspend fun remove(uri: String) {
        context.recentPdfsStore.edit { prefs ->
            val current = parse(prefs[key].orEmpty()).filterNot { it.uri == uri }
            prefs[key] = serialize(current)
        }
    }

    suspend fun getLastPage(uri: String): Int {
        return recentPdfs.map { list ->
            list.firstOrNull { it.uri == uri }?.lastPageIndex ?: 0
        }.first()
    }

    private fun serialize(items: List<RecentPdf>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("uri", item.uri)
                    .put("displayName", item.displayName)
                    .put("sizeBytes", item.sizeBytes)
                    .put("lastOpenedAt", item.lastOpenedAt)
                    .put("lastPageIndex", item.lastPageIndex)
            )
        }
        return array.toString()
    }

    private fun parse(json: String): List<RecentPdf> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        RecentPdf(
                            uri = obj.getString("uri"),
                            displayName = obj.getString("displayName"),
                            sizeBytes = obj.optLong("sizeBytes", 0L),
                            lastOpenedAt = obj.optLong("lastOpenedAt", 0L),
                            lastPageIndex = obj.optInt("lastPageIndex", 0),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val MAX_RECENT = 20
    }
}
