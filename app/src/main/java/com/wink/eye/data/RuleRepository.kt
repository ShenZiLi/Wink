package com.wink.eye.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RuleRepository(context: Context) {

    private val prefs = context.getSharedPreferences("wink_rules", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val KEY_RULES = "rules"
    }

    fun getAll(): List<Rule> {
        val raw = prefs.getString(KEY_RULES, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<Rule>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getById(id: String): Rule? = getAll().find { it.id == id }

    fun save(rule: Rule) {
        val rules = getAll().toMutableList()
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index >= 0) {
            rules[index] = rule
        } else {
            rules.add(rule)
        }
        prefs.edit().putString(KEY_RULES, json.encodeToString(rules)).apply()
    }

    fun delete(id: String) {
        val rules = getAll().filter { it.id != id }
        prefs.edit().putString(KEY_RULES, json.encodeToString(rules)).apply()
    }
}
