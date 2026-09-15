package com.wink.eye.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** EarClock 耳机闹钟仓库：基于 SharedPreferences + kotlinx-serialization 的 CRUD */
class EarClockRepository(context: Context) {

    private val prefs = context.getSharedPreferences("earclock_alarms", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val KEY_ALARMS = "alarms"
    }

    fun getAll(): List<EarClockAlarm> {
        val raw = prefs.getString(KEY_ALARMS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<EarClockAlarm>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getById(id: String): EarClockAlarm? = getAll().find { it.id == id }

    fun save(alarm: EarClockAlarm) {
        val alarms = getAll().toMutableList()
        val index = alarms.indexOfFirst { it.id == alarm.id }
        if (index >= 0) {
            alarms[index] = alarm
        } else {
            alarms.add(alarm)
        }
        prefs.edit().putString(KEY_ALARMS, json.encodeToString(alarms)).apply()
    }

    fun delete(id: String) {
        val alarms = getAll().filter { it.id != id }
        prefs.edit().putString(KEY_ALARMS, json.encodeToString(alarms)).apply()
    }
}