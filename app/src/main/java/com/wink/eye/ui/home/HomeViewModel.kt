package com.wink.eye.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import com.wink.eye.WinkApp
import com.wink.eye.data.IntervalUnit
import com.wink.eye.data.Rule
import com.wink.eye.data.RuleType
import com.wink.eye.service.IntervalAlarmScheduler
import com.wink.eye.service.ScreenMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WinkApp.instance.ruleRepository
    private val appContext = application.applicationContext

    private val _rules = MutableStateFlow<List<Rule>>(emptyList())
    val rules: StateFlow<List<Rule>> = _rules

    init {
        loadRules()
    }

    fun loadRules() {
        _rules.value = repository.getAll()
    }

    fun deleteRule(id: String) {
        val rule = repository.getById(id)
        repository.delete(id)
        if (rule != null) {
            syncServicesAfterChange()
        }
        loadRules()
    }

    fun toggleEnabled(rule: Rule) {
        val updated = rule.copy(enabled = !rule.enabled)
        repository.save(updated)
        if (updated.enabled && updated.type is RuleType.Interval) {
            val intervalMs = when (updated.type.unit) {
                IntervalUnit.SECONDS -> updated.type.value * 1000L
                IntervalUnit.MINUTES -> updated.type.value * 60 * 1000L
            }
            IntervalAlarmScheduler.schedule(appContext, updated.id, intervalMs)
        }
        syncServicesAfterChange()
        loadRules()
    }

    private fun syncServicesAfterChange() {
        val rules = repository.getAll()

        val hasScreenTimeRule = rules.any { it.enabled && it.type is RuleType.ScreenTime }
        if (hasScreenTimeRule) {
            ScreenMonitorService.start(appContext)
        } else {
            ScreenMonitorService.stop(appContext)
        }

        rules.filter { !it.enabled && it.type is RuleType.Interval }.forEach {
            IntervalAlarmScheduler.cancel(appContext, it.id)
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            val application = WinkApp.instance
            return HomeViewModel(application) as T
        }
    }
}
