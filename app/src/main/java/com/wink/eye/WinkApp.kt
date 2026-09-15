package com.wink.eye

import android.app.Application
import com.wink.eye.data.EarClockRepository
import com.wink.eye.data.RuleRepository

class WinkApp : Application() {
    val ruleRepository: RuleRepository by lazy {
        RuleRepository(this)
    }

    val earClockRepository: EarClockRepository by lazy {
        EarClockRepository(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: WinkApp
            private set
    }
}
