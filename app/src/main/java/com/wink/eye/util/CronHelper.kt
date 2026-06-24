package com.wink.eye.util

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import java.time.ZonedDateTime

object CronHelper {

    private val parser = CronParser(
        CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ)
    )

    fun isValid(expression: String): Boolean {
        return try {
            parser.parse(expression)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun nextExecutionTime(expression: String): ZonedDateTime? {
        return try {
            val cron = parser.parse(expression)
            val executionTime = ExecutionTime.forCron(cron)
            executionTime.nextExecution(ZonedDateTime.now()).orElse(null)
        } catch (_: Exception) {
            null
        }
    }

    fun description(expression: String): String {
        return try {
            val cron = parser.parse(expression)
            cron.asString()
        } catch (_: Exception) {
            expression
        }
    }
}
