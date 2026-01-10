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
        var autoPausedByProjectSwitch: Boolean = false,
        
        // Branch → Jira Issue Key mapping
        var branchToIssueMap: MutableMap<String, String> = mutableMapOf()
    )

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
    
    fun setLastUpdateTimestamp(timestamp: Long) {
        state.lastUpdateTimestamp = timestamp
    }
    
    // Auto-pause state management
    fun isAutoPausedByFocus(): Boolean = state.autoPausedByFocus
    
    fun setAutoPausedByFocus(paused: Boolean) {
        state.autoPausedByFocus = paused
    }
    
    fun setAutoPausedByProjectSwitch(paused: Boolean) {
        state.autoPausedByProjectSwitch = paused
    }
    
    fun clearAutoPauseFlags() {
        state.autoPausedByFocus = false
        state.autoPausedByProjectSwitch = false
    }
    
    // Branch-specific issue tracking
    fun getIssueForBranch(branchName: String): String? {
        return state.branchToIssueMap[branchName]
    }
    
    fun saveIssueForBranch(branchName: String, issueKey: String) {
        state.branchToIssueMap[branchName] = issueKey
    }
    
    fun getLastIssueKey(): String? = state.lastIssueKey
    
    fun setLastIssueKey(issueKey: String?) {
        state.lastIssueKey = issueKey
    }
    
    /**
     * Clean up issue mappings for branches that no longer exist.
     * Keeps only branches that are in the provided set of active branch names.
     */
    fun cleanupDeletedBranches(activeBranchNames: Set<String>) {
        val keysToRemove = state.branchToIssueMap.keys.filter { it !in activeBranchNames }
        keysToRemove.forEach { state.branchToIssueMap.remove(it) }
    }
    
    fun reset() {
        state.totalTimeMs = 0L
        state.status = TimeTrackingStatus.STOPPED.name
        state.lastUpdateTimestamp = 0L
        clearAutoPauseFlags() // Clear auto-pause flags on reset
    }
}
