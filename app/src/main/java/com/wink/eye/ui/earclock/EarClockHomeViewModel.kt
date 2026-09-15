package com.wink.eye.ui.earclock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import com.wink.eye.WinkApp
import com.wink.eye.data.EarClockAlarm
import com.wink.eye.service.EarClockAlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** EarClock 首页状态管理：加载/删除/启停闹钟，同步 AlarmManager 调度 */
class EarClockHomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WinkApp.instance.earClockRepository
    private val appContext = application.applicationContext

    private val _alarms = MutableStateFlow<List<EarClockAlarm>>(emptyList())
    val alarms: StateFlow<List<EarClockAlarm>> = _alarms

    init {
        loadAlarms()
    }

    fun loadAlarms() {
        _alarms.value = repository.getAll()
    }

    fun deleteAlarm(id: String) {
        repository.delete(id)
        EarClockAlarmScheduler.cancel(appContext, id)
        loadAlarms()
    }

    fun toggleEnabled(alarm: EarClockAlarm) {
        saveAlarm(alarm.copy(enabled = !alarm.enabled))
    }

    /** 保存闹钟并同步调度：启用则调度下一次，禁用则取消 */
    fun saveAlarm(alarm: EarClockAlarm) {
        repository.save(alarm)
        if (alarm.enabled) {
            EarClockAlarmScheduler.scheduleNext(appContext, alarm)
        } else {
            EarClockAlarmScheduler.cancel(appContext, alarm.id)
        }
        loadAlarms()
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return EarClockHomeViewModel(WinkApp.instance) as T
        }
    }
}