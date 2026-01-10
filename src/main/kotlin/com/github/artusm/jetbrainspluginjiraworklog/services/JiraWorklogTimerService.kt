package com.github.artusm.jetbrainspluginjiraworklog.services

import com.github.artusm.jetbrainspluginjiraworklog.config.JiraSettings
import com.github.artusm.jetbrainspluginjiraworklog.model.TimeTrackingStatus
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Service(Service.Level.PROJECT)
open class JiraWorklogTimerService(
    private val project: Project,
    private val coroutineScope: CoroutineScope
) : CoroutineScope {
    
    override val coroutineContext = coroutineScope.coroutineContext
    
    companion object {
        private const val SLEEP_DETECTION_THRESHOLD_MS = 5000L
        private const val TICK_INTERVAL_MS = 1000L
    }
    
    protected open val persistentState: JiraWorklogPersistentState 
        get() = project.service()
        
    protected open val settings: JiraSettings get() = JiraSettings.getInstance()
    
    // Lazy to avoid accessing persistentState in init if mocked
    private val _timeFlow by lazy { MutableStateFlow(persistentState.getTotalTimeMs()) }
    val timeFlow: StateFlow<Long> get() = _timeFlow.asStateFlow()
    
    private val _statusFlow by lazy { MutableStateFlow(persistentState.getStatus()) }
    val statusFlow: StateFlow<TimeTrackingStatus> get() = _statusFlow.asStateFlow()
    
    private var tickerJob: Job? = null
    private var lastTickTime: Long = 0L
    
    init {
        // Restore state
        if (_statusFlow.value == TimeTrackingStatus.RUNNING) {
            startTicking()
        }
    }
    
    private fun startTicking() {
        tickerJob?.cancel()
        lastTickTime = System.currentTimeMillis()
        
        tickerJob = launch {
            while (isActive) {
                delay(TICK_INTERVAL_MS)
                tick()
            }
        }
    }
    
    private fun stopTicking() {
        tickerJob?.cancel()
        tickerJob = null
    }
    
    private fun tick() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastTickTime
        
        // Check for abnormal time gap indicating system sleep
        if (elapsed > SLEEP_DETECTION_THRESHOLD_MS && settings.isPauseOnSystemSleep()) {
            handleSystemSleep(elapsed)
            lastTickTime = now
            return
        }
        
        lastTickTime = now
        
        if (_statusFlow.value == TimeTrackingStatus.RUNNING) {
            val timeToAdd = if (elapsed > SLEEP_DETECTION_THRESHOLD_MS) 0L else elapsed
            
            // Update state atomically
            val newTime = _timeFlow.value + timeToAdd
            _timeFlow.value = newTime
            
            // Persist periodically or on change (optimized to not write to disk every second if strictly needed,
            // but for PersistentStateComponent it caches in memory)
            persistentState.setTotalTimeMs(newTime)
            persistentState.setLastUpdateTimestamp(now)
        }
    }
    
    private fun handleSystemSleep(sleepDurationMs: Long) {
        if (_statusFlow.value == TimeTrackingStatus.RUNNING) {
            setStatus(TimeTrackingStatus.IDLE)
            println("Jira Worklog Timer: System sleep detected (${sleepDurationMs}ms gap), timer paused")
        }
    }
    
    fun getStatus(): TimeTrackingStatus = _statusFlow.value
    open fun getTotalTimeMs(): Long = _timeFlow.value
    fun getTotalTimeSeconds(): Int = (_timeFlow.value / 1000).toInt()
    
    fun toggleRunning() {
        persistentState.clearAutoPauseFlags()
        when (_statusFlow.value) {
            TimeTrackingStatus.RUNNING, TimeTrackingStatus.IDLE -> setStatus(TimeTrackingStatus.STOPPED)
            TimeTrackingStatus.STOPPED -> setStatus(TimeTrackingStatus.RUNNING)
        }
    }
    
    fun setStatus(status: TimeTrackingStatus) {
        val now = System.currentTimeMillis()
        
        _statusFlow.value = status
        persistentState.setStatus(status)
        persistentState.setLastUpdateTimestamp(now)
        
        if (status == TimeTrackingStatus.RUNNING) {
            startTicking()
        } else {
            stopTicking()
        }
        
        lastTickTime = now
    }
    
    fun pause() {
        persistentState.clearAutoPauseFlags()
        if (_statusFlow.value == TimeTrackingStatus.RUNNING) {
            setStatus(TimeTrackingStatus.IDLE)
        }
    }
    
    fun resume() {
        if (_statusFlow.value == TimeTrackingStatus.IDLE) {
            setStatus(TimeTrackingStatus.RUNNING)
        }
    }
    
    open fun reset() {
        stopTicking()
        _timeFlow.value = 0L
        persistentState.reset()
        persistentState.clearAutoPauseFlags()
        setStatus(TimeTrackingStatus.STOPPED)
    }
    
    fun addTimeMs(timeMs: Long) {
        _timeFlow.update { current ->
            val newTime = current + timeMs
            persistentState.setTotalTimeMs(newTime)
            newTime
        }
    }
    
    fun autoPauseByFocus() {
        if (_statusFlow.value == TimeTrackingStatus.RUNNING) {
            persistentState.setAutoPausedByFocus(true)
            setStatus(TimeTrackingStatus.IDLE)
        }
    }
    
    fun autoResumeFromFocus() {
        if (persistentState.isAutoPausedByFocus() && _statusFlow.value == TimeTrackingStatus.IDLE) {
            persistentState.setAutoPausedByFocus(false)
            setStatus(TimeTrackingStatus.RUNNING)
        }
    }
    
    fun autoPauseByProjectSwitch() {
        if (_statusFlow.value == TimeTrackingStatus.RUNNING) {
            persistentState.setAutoPausedByProjectSwitch(true)
            setStatus(TimeTrackingStatus.IDLE)
        }
    }
    

}
