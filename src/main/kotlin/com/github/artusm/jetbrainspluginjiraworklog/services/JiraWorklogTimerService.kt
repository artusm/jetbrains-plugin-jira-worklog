package com.github.artusm.jetbrainspluginjiraworklog.services

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
    
    private val persistentState: JiraWorklogPersistentState = project.service()
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
     */
    private fun tick() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastTickTime
        lastTickTime = now
        
        synchronized(this) {
            val status = persistentState.getStatus()
            
            if (status == TimeTrackingStatus.RUNNING) {
                persistentState.addTimeMs(elapsed)
                persistentState.setLastUpdateTimestamp(now)
                
                // Update widget on EDT
                widget?.repaint()
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
     * Get total accumulated time in milliseconds.
     */
    fun getTotalTimeMs(): Long {
        return synchronized(this) {
            persistentState.getTotalTimeMs()
        }
    }
    
    /**
     * Toggle between running and stopped states.
     */
    fun toggleRunning() {
        synchronized(this) {
            val currentStatus = persistentState.getStatus()
            
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
     * Pause the timer (set to IDLE state).
     */
    fun pause() {
        synchronized(this) {
            if (persistentState.getStatus() == TimeTrackingStatus.RUNNING) {
                setStatus(TimeTrackingStatus.IDLE)
            }
        }
    }
    
    /**
     * Resume the timer from IDLE state.
     */
    fun resume() {
        synchronized(this) {
            if (persistentState.getStatus() == TimeTrackingStatus.IDLE) {
                setStatus(TimeTrackingStatus.RUNNING)
            }
        }
    }
    
    /**
     * Start the timer if not already running.
     */
    fun start() {
        synchronized(this) {
            if (persistentState.getStatus() != TimeTrackingStatus.RUNNING) {
                setStatus(TimeTrackingStatus.RUNNING)
            }
        }
    }
    
    /**
     * Stop the timer.
     */
    fun stop() {
        synchronized(this) {
            setStatus(TimeTrackingStatus.STOPPED)
        }
    }
    
    /**
     * Reset the timer to zero.
     */
    fun reset() {
        synchronized(this) {
            persistentState.reset()
            widget?.repaint()
        }
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
