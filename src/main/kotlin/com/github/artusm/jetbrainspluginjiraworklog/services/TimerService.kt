package com.github.artusm.jetbrainspluginjiraworklog.services

import com.github.artusm.jetbrainspluginjiraworklog.model.TimeTrackingStatus
import kotlinx.coroutines.flow.StateFlow

interface TimerService {
    val timeFlow: StateFlow<Long>
    val statusFlow: StateFlow<TimeTrackingStatus>

    fun getStatus(): TimeTrackingStatus
    fun getTotalTimeMs(): Long
    fun getTotalTimeSeconds(): Int
    fun toggleRunning()
    fun setStatus(status: TimeTrackingStatus)
    fun pause()
    fun resume()
    fun reset()
    fun addTimeMs(timeMs: Long)
    fun autoPauseByFocus()
    fun autoResumeFromFocus()
    fun autoPauseByProjectSwitch()
}
