package com.github.artusm.jetbrainspluginjiraworklog.services

import com.github.artusm.jetbrainspluginjiraworklog.config.JiraSettings
import com.github.artusm.jetbrainspluginjiraworklog.model.TimeTrackingStatus
import com.github.artusm.jetbrainspluginjiraworklog.ui.JiraWorklogWidget
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Project-level service managing the timer state and time accumulation.
 * Adapted from the reference TimeTrackerService.
 */
@Service(Service.Level.PROJECT)
class JiraWorklogTimerService(private val project: Project) {
    
    companion object {
        // Consider system asleep if tick gap exceeds 5 seconds
        private const val SLEEP_DETECTION_THRESHOLD_MS = 5000L
    }
    
    private val persistentState: JiraWorklogPersistentState = project.service()
    private val settings: JiraSettings = JiraSettings.getInstance()
    private var widget: JiraWorklogWidget? = null
    
    private var tickFuture: ScheduledFuture<*>? = null
    private var lastTickTime: Long = 0L
    
    private val tickIntervalMs = 1000L // Update every second
    
    init {
        startTicking()
    }
    
    /**
     * Register the widget for updates.
     */
    fun registerWidget(widget: JiraWorklogWidget) {
        this.widget = widget
        widget.repaint()
    }
    
    /**
     * Start the background ticker.
     */
    private fun startTicking() {
        lastTickTime = System.currentTimeMillis()
        
        tickFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            { tick() },
            tickIntervalMs,
            tickIntervalMs,
            TimeUnit.MILLISECONDS
        )
    }
    
    /**
     * Called every second to update the timer.
     * Also detects system sleep via abnormal time gaps.
     */
    private fun tick() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastTickTime
        
        // Check for abnormal time gap indicating system sleep
        if (elapsed > SLEEP_DETECTION_THRESHOLD_MS && settings.isPauseOnSystemSleep()) {
            handleSystemSleep(elapsed)
        }
        
        lastTickTime = now
        
        synchronized(this) {
            val status = persistentState.getStatus()
            
            if (status == TimeTrackingStatus.RUNNING) {
                // Don't add time if we just recovered from sleep
                // (elapsed would be huge, we only want to add real working time)
                val timeToAdd = if (elapsed > SLEEP_DETECTION_THRESHOLD_MS) {
                    0L // Don't count sleep time
                } else {
                    elapsed
                }
                
                persistentState.addTimeMs(timeToAdd)
                persistentState.setLastUpdateTimestamp(now)
                
                // Update widget on EDT
                widget?.repaint()
            }
        }
    }
    
    /**
     * Handle system sleep detection.
     * Automatically pauses the timer when a large time gap is detected.
     */
    private fun handleSystemSleep(sleepDurationMs: Long) {
        synchronized(this) {
            if (persistentState.getStatus() == TimeTrackingStatus.RUNNING) {
                setStatus(TimeTrackingStatus.IDLE)
                // Log for debugging (user can see this in IDE logs)
                println("Jira Worklog Timer: System sleep detected (${sleepDurationMs}ms gap), timer paused")
            }
        }
    }
    
    /**
     * Get the current timer status.
     */
    fun getStatus(): TimeTrackingStatus {
        return synchronized(this) {
            persistentState.getStatus()
        }
    }
    
    /**
     * Get total accumulated time in seconds.
     */
    fun getTotalTimeSeconds(): Int {
        return synchronized(this) {
            (persistentState.getTotalTimeMs() / 1000).toInt()
        }
    }
    
    /**
     * Total accumulated time tracked by the service in milliseconds.
     *
     * @return The total accumulated time in milliseconds.
     */
    fun getTotalTimeMs(): Long {
        return synchronized(this) {
            persistentState.getTotalTimeMs()
        }
    }
    
    /**
     * Toggle the timer between running and stopped states.
     *
     * If the current status is `RUNNING` or `IDLE`, sets the status to `STOPPED`; if it is `STOPPED`, sets the status to `RUNNING`.
     * Clears any auto-pause flags when invoked.
     */
    fun toggleRunning() {
        synchronized(this) {
            val currentStatus = persistentState.getStatus()
            
            persistentState.clearAutoPauseFlags() // Clear auto-pause flags on manual toggle
            when (currentStatus) {
                TimeTrackingStatus.RUNNING, TimeTrackingStatus.IDLE -> {
                    setStatus(TimeTrackingStatus.STOPPED)
                }
                TimeTrackingStatus.STOPPED -> {
                    setStatus(TimeTrackingStatus.RUNNING)
                }
            }
        }
    }
    
    /**
     * Set the timer status.
     */
    fun setStatus(status: TimeTrackingStatus) {
        synchronized(this) {
            val now = System.currentTimeMillis()
            persistentState.setStatus(status)
            persistentState.setLastUpdateTimestamp(now)
            lastTickTime = now
            
            // Update widget
            widget?.repaint()
        }
    }
    
    /**
     * Pause the timer and put it into the IDLE state.
     *
     * Clears any auto-pause flags and, if the timer is currently RUNNING, transitions it to IDLE.
     */
    fun pause() {
        synchronized(this) {
            persistentState.clearAutoPauseFlags() // Clear auto-pause flags on manual pause
            if (persistentState.getStatus() == TimeTrackingStatus.RUNNING) {
                setStatus(TimeTrackingStatus.IDLE)
            }
        }
    }
    
    /**
     * Resume timing when currently paused by setting the status to RUNNING.
     *
     * If the current status is not IDLE, this has no effect.
     */
    fun resume() {
        synchronized(this) {
            if (persistentState.getStatus() == TimeTrackingStatus.IDLE) {
                setStatus(TimeTrackingStatus.RUNNING)
            }
        }
    }
    
    /**
     * Starts or resumes time tracking for the current project.
     *
     * Clears any automatic pause flags set by focus or project-switch events and transitions the tracker to the RUNNING state.
     */
    fun start() {
        synchronized(this) {
            persistentState.clearAutoPauseFlags() // Clear auto-pause flags on manual start
            setStatus(TimeTrackingStatus.RUNNING)
        }
    }
    
    /**
     * Stops the timer and marks time tracking as stopped.
     *
     * Clears any automatic-pause flags and sets the service status to STOPPED.
     */
    fun stop() {
        synchronized(this) {
            persistentState.clearAutoPauseFlags() // Clear auto-pause flags on manual stop
            setStatus(TimeTrackingStatus.STOPPED)
        }
    }
    
    /**
     * Triggers an automatic pause when the window loses focus.
     *
     * If the timer is currently running, records that it was auto-paused due to focus loss and transitions the timer to idle.
     */
    fun autoPauseByFocus() {
        autoPause { persistentState.setAutoPausedByFocus(true) }
    }
    
    /**
     * Resumes the timer when the application window gains focus if it was previously auto-paused due to focus loss.
     *
     * If the timer is currently `IDLE` and marked as auto-paused by focus, clears that flag and transitions the timer to `RUNNING`.
     */
    fun autoResumeFromFocus() {
        synchronized(this) {
            if (persistentState.isAutoPausedByFocus() && 
                persistentState.getStatus() == TimeTrackingStatus.IDLE) {
                persistentState.setAutoPausedByFocus(false)
                setStatus(TimeTrackingStatus.RUNNING)
            }
        }
    }
    
    /**
     * Auto-pause timer when switching projects.
     * Only pauses if currently running.
     */
    fun autoPauseByProjectSwitch() {
        autoPause { persistentState.setAutoPausedByProjectSwitch(true) }
    }
    
    /**
     * Conditionally apply an automatic pause when the timer is currently running.
     *
     * If the timer status is RUNNING, executes the provided flag-setting lambda and transitions the timer to IDLE.
     *
     * @param setFlag A lambda that sets the appropriate auto-pause indicator (e.g., focus or project-switch flag).
     */
    private fun autoPause(setFlag: () -> Unit) {
        synchronized(this) {
            if (persistentState.getStatus() == TimeTrackingStatus.RUNNING) {
                setFlag()
                setStatus(TimeTrackingStatus.IDLE)
            }
        }
    }
    
    /**
     * Reset the accumulated time to zero, clear any auto-pause flags, and update the UI widget.
     *
     * Performs a synchronized reset of persistent state and clears auto-pause markers; repaints the widget if one is registered.
     */
    fun reset() {
        synchronized(this) {
            persistentState.reset()
            persistentState.clearAutoPauseFlags()
        }
        widget?.repaint()
    }
    
    /**
     * Manually add time in milliseconds.
     */
    fun addTimeMs(timeMs: Long) {
        synchronized(this) {
            persistentState.addTimeMs(timeMs)
            widget?.repaint()
        }
    }
    
    /**
     * Set the total time in milliseconds.
     */
    fun setTotalTimeMs(timeMs: Long) {
        synchronized(this) {
            persistentState.setTotalTimeMs(timeMs)
            widget?.repaint()
        }
    }
    
    /**
     * Get a reference to the widget.
     */
    fun widget(): JiraWorklogWidget {
        if (widget == null) {
            widget = JiraWorklogWidget(this, project)
        }
        return widget!!
    }
    
    /**
     * Dispose of resources.
     */
    fun dispose() {
        tickFuture?.cancel(false)
    }
}