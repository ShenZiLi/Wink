package com.wink.eye.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.wink.eye.WinkApp
import com.wink.eye.data.IntervalUnit
import com.wink.eye.data.Rule
import com.wink.eye.data.RuleRepository
import com.wink.eye.data.RuleType
import com.wink.eye.service.IntervalAlarmScheduler
import com.wink.eye.service.ScreenMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel(private val repository: RuleRepository) : ViewModel() {

    private val _rules = MutableStateFlow<List<Rule>>(emptyList())
    val rules: StateFlow<List<Rule>> = _rules

    init {
        loadRules()
    }

    fun loadRules() {
        _rules.value = repository.getAll()
    }

    fun deleteRule(context: Context, id: String) {
        val rule = repository.getById(id)
        repository.delete(id)
        if (rule != null) {
            syncServicesAfterChange(context)
        }
        loadRules()
    }

    fun toggleEnabled(context: Context, rule: Rule) {
        val updated = rule.copy(enabled = !rule.enabled)
        repository.save(updated)
        if (updated.enabled && updated.type is RuleType.Interval) {
            val intervalMs = when (updated.type.unit) {
                IntervalUnit.SECONDS -> updated.type.value * 1000L
                IntervalUnit.MINUTES -> updated.type.value * 60 * 1000L
            }
            IntervalAlarmScheduler.schedule(context, updated.id, intervalMs)
        }
        syncServicesAfterChange(context)
        loadRules()
    }

    private fun syncServicesAfterChange(context: Context) {
        val rules = repository.getAll()

        val hasScreenTimeRule = rules.any { it.enabled && it.type is RuleType.ScreenTime }
        if (hasScreenTimeRule) {
            ScreenMonitorService.start(context)
        } else {
            ScreenMonitorService.stop(context)
        }

        rules.filter { !it.enabled && it.type is RuleType.Interval }.forEach {
            IntervalAlarmScheduler.cancel(context, it.id)
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = WinkApp.instance.ruleRepository
            return HomeViewModel(repository) as T
        }
    }
}
