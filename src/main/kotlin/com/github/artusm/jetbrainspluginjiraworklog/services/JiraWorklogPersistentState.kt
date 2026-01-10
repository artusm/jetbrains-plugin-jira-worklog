package com.github.artusm.jetbrainspluginjiraworklog.services

import com.github.artusm.jetbrainspluginjiraworklog.model.TimeTrackingStatus
import com.intellij.openapi.components.*

/**
 * Persistent state for storing timer data per project.
 * Stored in the workspace (.idea folder), not in version control.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "JiraWorklogPersistentState",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class JiraWorklogPersistentState : PersistentStateComponent<JiraWorklogPersistentState.State> {
    
    private var state = State()

    data class State(
        var totalTimeMs: Long = 0L,
        var status: String = TimeTrackingStatus.STOPPED.name,
        var lastUpdateTimestamp: Long = System.currentTimeMillis(),
        var lastIssueKey: String? = null,
        var lastComment: String? = null,
        
        // Auto-pause state tracking
        var autoPausedByFocus: Boolean = false,
        var autoPausedByProjectSwitch: Boolean = false
    )

    /**
 * Provide the current in-memory persistent state for the component.
 *
 * @return The current `State` instance used for persistence. 
 */
override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun getTotalTimeMs(): Long = state.totalTimeMs
    
    fun setTotalTimeMs(timeMs: Long) {
        state.totalTimeMs = timeMs
    }
    
    fun addTimeMs(timeMs: Long) {
        state.totalTimeMs += timeMs
    }
    
    fun getStatus(): TimeTrackingStatus {
        return try {
            TimeTrackingStatus.valueOf(state.status)
        } catch (e: IllegalArgumentException) {
            TimeTrackingStatus.STOPPED
        }
    }
    
    fun setStatus(status: TimeTrackingStatus) {
        state.status = status.name
    }
    
    fun getLastUpdateTimestamp(): Long = state.lastUpdateTimestamp
    
    /**
     * Updates the stored last-update timestamp for the worklog state.
     *
     * @param timestamp The timestamp to store, in milliseconds since the Unix epoch.
     */
    fun setLastUpdateTimestamp(timestamp: Long) {
        state.lastUpdateTimestamp = timestamp
    }
    
    /**
 * Indicates whether time tracking is currently auto-paused because the IDE lost focus.
 *
 * @return `true` if tracking is auto-paused due to focus loss, `false` otherwise.
 */
    fun isAutoPausedByFocus(): Boolean = state.autoPausedByFocus
    
    /**
     * Sets whether the timer was automatically paused due to IDE focus loss.
     *
     * @param paused `true` to mark the timer as auto-paused because the IDE lost focus, `false` to clear the flag.
     */
    fun setAutoPausedByFocus(paused: Boolean) {
        state.autoPausedByFocus = paused
    }
    
    /**
 * Indicates whether the timer is currently auto-paused because the project was switched.
 *
 * @return `true` if the timer was auto-paused due to a project switch, `false` otherwise.
 */
fun isAutoPausedByProjectSwitch(): Boolean = state.autoPausedByProjectSwitch
    
    /**
     * Sets whether the timer was auto-paused due to a project switch.
     *
     * @param paused `true` to mark the timer as auto-paused by a project switch, `false` to clear the flag.
     */
    fun setAutoPausedByProjectSwitch(paused: Boolean) {
        state.autoPausedByProjectSwitch = paused
    }
    
    /**
     * Clears the auto-pause state.
     *
     * Resets both `autoPausedByFocus` and `autoPausedByProjectSwitch` to `false`.
     */
    fun clearAutoPauseFlags() {
        state.autoPausedByFocus = false
        state.autoPausedByProjectSwitch = false
    }
    
    /**
     * Reset the persistent timer state to its initial values.
     *
     * Resets accumulated time to 0, sets the status to `STOPPED`, clears the last update timestamp,
     * and clears both auto-pause flags.
     */
    fun reset() {
        state.totalTimeMs = 0L
        state.status = TimeTrackingStatus.STOPPED.name
        state.lastUpdateTimestamp = 0L
        clearAutoPauseFlags() // Clear auto-pause flags on reset
    }
}