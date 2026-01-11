package com.github.artusm.jetbrainspluginjiraworklog.services

import com.github.artusm.jetbrainspluginjiraworklog.config.JiraSettings
import com.github.artusm.jetbrainspluginjiraworklog.model.TimeTrackingStatus
import com.github.artusm.jetbrainspluginjiraworklog.utils.SystemTimeProvider
import com.github.artusm.jetbrainspluginjiraworklog.utils.TimeProvider
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Service(Service.Level.PROJECT)
class JiraWorklogTimerService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
    private val timeProvider: TimeProvider
) : CoroutineScope, TimerService {

    constructor(project: Project, coroutineScope: CoroutineScope) : this(
        project,
        coroutineScope,
        SystemTimeProvider()
    )
    
    override val coroutineContext = coroutineScope.coroutineContext
    
    companion object {
        private const val SLEEP_DETECTION_THRESHOLD_MS = 5000L
        private const val TICK_INTERVAL_MS = 1000L
    }
    
    private val persistentState: JiraWorklogPersistentState 
        get() = project.service()
        
    private val settings: JiraSettings get() = JiraSettings.getInstance()
    
    // Lazy to avoid accessing persistentState in init if mocked
    private val _timeFlow by lazy { MutableStateFlow(persistentState.getTotalTimeMs()) }
    override val timeFlow: StateFlow<Long> get() = _timeFlow.asStateFlow()
    
    private val _statusFlow by lazy { MutableStateFlow(persistentState.getStatus()) }
    override val statusFlow: StateFlow<TimeTrackingStatus> get() = _statusFlow.asStateFlow()

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
        lastTickTime = timeProvider.currentTimeMillis()
        
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
        val now = timeProvider.currentTimeMillis()
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
            
            // Update state atomically to avoid race with addTimeMs()
            _timeFlow.update { current -> current + timeToAdd }
            val newTime = _timeFlow.value
            
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

    override fun getStatus(): TimeTrackingStatus = _statusFlow.value
    override fun getTotalTimeMs(): Long = _timeFlow.value
    override fun getTotalTimeSeconds(): Int = (_timeFlow.value / 1000).toInt()
    
    override fun toggleRunning() {
        persistentState.clearAutoPauseFlags()
        when (_statusFlow.value) {
            TimeTrackingStatus.RUNNING, TimeTrackingStatus.IDLE -> setStatus(TimeTrackingStatus.STOPPED)
            TimeTrackingStatus.STOPPED -> setStatus(TimeTrackingStatus.RUNNING)
        }
    }
    
    override fun setStatus(status: TimeTrackingStatus) {
        val now = timeProvider.currentTimeMillis()
        
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
    
    override fun pause() {
        persistentState.clearAutoPauseFlags()
        if (_statusFlow.value == TimeTrackingStatus.RUNNING) {
            setStatus(TimeTrackingStatus.IDLE)
        }
    }
    
    override fun resume() {
        if (_statusFlow.value == TimeTrackingStatus.IDLE) {
            setStatus(TimeTrackingStatus.RUNNING)
        }
    }
    
    override fun reset() {
        stopTicking()
        _timeFlow.value = 0L
        persistentState.reset()
        persistentState.clearAutoPauseFlags()
        setStatus(TimeTrackingStatus.STOPPED)
    }
    
    override fun addTimeMs(timeMs: Long) {
        _timeFlow.update { current ->
            val newTime = current + timeMs
            persistentState.setTotalTimeMs(newTime)
            newTime
        }
    }
    
    override fun autoPauseByFocus() {
        if (_statusFlow.value == TimeTrackingStatus.RUNNING) {
            persistentState.setAutoPausedByFocus(true)
            setStatus(TimeTrackingStatus.IDLE)
        }
    }
    
    override fun autoResumeFromFocus() {
        if (persistentState.isAutoPausedByFocus() && _statusFlow.value == TimeTrackingStatus.IDLE) {
            persistentState.setAutoPausedByFocus(false)
            setStatus(TimeTrackingStatus.RUNNING)
        }
    }
    
    override fun autoPauseByProjectSwitch() {
        if (_statusFlow.value == TimeTrackingStatus.RUNNING) {
            persistentState.setAutoPausedByProjectSwitch(true)
            setStatus(TimeTrackingStatus.IDLE)
        }
    }
    

}
