package com.github.artusm.jetbrainspluginjiraworklog.services

import com.github.artusm.jetbrainspluginjiraworklog.model.TimeTrackingStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for JiraWorklogPersistentState.
 * Tests state management, auto-pause flags, and time tracking.
 */
class JiraWorklogPersistentStateTest {
    
    private lateinit var persistentState: JiraWorklogPersistentState
    
    @Before
    fun setUp() {
        persistentState = JiraWorklogPersistentState()
    }
    
    // ========== Basic State Management Tests ==========
    
    @Test
    fun `test initial state values`() {
        assertEquals(0L, persistentState.getTotalTimeMs())
        assertEquals(TimeTrackingStatus.STOPPED, persistentState.getStatus())
        assertFalse(persistentState.isAutoPausedByFocus())
        assertFalse(persistentState.isAutoPausedByProjectSwitch())
    }
    
    @Test
    fun `test getState returns current state`() {
        val state = persistentState.getState()
        
        assertNotNull(state)
        assertEquals(0L, state.totalTimeMs)
        assertEquals(TimeTrackingStatus.STOPPED.name, state.status)
        assertFalse(state.autoPausedByFocus)
        assertFalse(state.autoPausedByProjectSwitch)
    }
    
    @Test
    fun `test loadState updates internal state`() {
        val newState = JiraWorklogPersistentState.State(
            totalTimeMs = 5000L,
            status = TimeTrackingStatus.RUNNING.name,
            lastUpdateTimestamp = 123456L,
            lastIssueKey = "TEST-123",
            lastComment = "Test comment",
            autoPausedByFocus = true,
            autoPausedByProjectSwitch = false
        )
        
        persistentState.loadState(newState)
        
        assertEquals(5000L, persistentState.getTotalTimeMs())
        assertEquals(TimeTrackingStatus.RUNNING, persistentState.getStatus())
        assertEquals(123456L, persistentState.getLastUpdateTimestamp())
        assertTrue(persistentState.isAutoPausedByFocus())
        assertFalse(persistentState.isAutoPausedByProjectSwitch())
    }
    
    // ========== Time Management Tests ==========
    
    @Test
    fun `test setTotalTimeMs updates time`() {
        persistentState.setTotalTimeMs(10000L)
        
        assertEquals(10000L, persistentState.getTotalTimeMs())
    }
    
    @Test
    fun `test setTotalTimeMs with zero`() {
        persistentState.setTotalTimeMs(5000L)
        persistentState.setTotalTimeMs(0L)
        
        assertEquals(0L, persistentState.getTotalTimeMs())
    }
    
    @Test
    fun `test setTotalTimeMs with large value`() {
        val largeValue = Long.MAX_VALUE - 1000L
        persistentState.setTotalTimeMs(largeValue)
        
        assertEquals(largeValue, persistentState.getTotalTimeMs())
    }
    
    @Test
    fun `test addTimeMs accumulates time`() {
        persistentState.setTotalTimeMs(1000L)
        persistentState.addTimeMs(500L)
        
        assertEquals(1500L, persistentState.getTotalTimeMs())
    }
    
    @Test
    fun `test addTimeMs multiple times`() {
        persistentState.addTimeMs(100L)
        persistentState.addTimeMs(200L)
        persistentState.addTimeMs(300L)
        
        assertEquals(600L, persistentState.getTotalTimeMs())
    }
    
    @Test
    fun `test addTimeMs with zero`() {
        persistentState.setTotalTimeMs(1000L)
        persistentState.addTimeMs(0L)
        
        assertEquals(1000L, persistentState.getTotalTimeMs())
    }
    
    @Test
    fun `test addTimeMs with negative value`() {
        persistentState.setTotalTimeMs(1000L)
        persistentState.addTimeMs(-500L)
        
        assertEquals(500L, persistentState.getTotalTimeMs())
    }
    
    // ========== Status Management Tests ==========
    
    @Test
    fun `test setStatus to RUNNING`() {
        persistentState.setStatus(TimeTrackingStatus.RUNNING)
        
        assertEquals(TimeTrackingStatus.RUNNING, persistentState.getStatus())
    }
    
    @Test
    fun `test setStatus to IDLE`() {
        persistentState.setStatus(TimeTrackingStatus.IDLE)
        
        assertEquals(TimeTrackingStatus.IDLE, persistentState.getStatus())
    }
    
    @Test
    fun `test setStatus to STOPPED`() {
        persistentState.setStatus(TimeTrackingStatus.STOPPED)
        
        assertEquals(TimeTrackingStatus.STOPPED, persistentState.getStatus())
    }
    
    @Test
    fun `test setStatus transitions`() {
        persistentState.setStatus(TimeTrackingStatus.RUNNING)
        assertEquals(TimeTrackingStatus.RUNNING, persistentState.getStatus())
        
        persistentState.setStatus(TimeTrackingStatus.IDLE)
        assertEquals(TimeTrackingStatus.IDLE, persistentState.getStatus())
        
        persistentState.setStatus(TimeTrackingStatus.STOPPED)
        assertEquals(TimeTrackingStatus.STOPPED, persistentState.getStatus())
    }
    
    @Test
    fun `test getStatus with invalid status string defaults to STOPPED`() {
        val state = JiraWorklogPersistentState.State(
            status = "INVALID_STATUS"
        )
        persistentState.loadState(state)
        
        assertEquals(TimeTrackingStatus.STOPPED, persistentState.getStatus())
    }
    
    @Test
    fun `test getStatus with empty string defaults to STOPPED`() {
        val state = JiraWorklogPersistentState.State(
            status = ""
        )
        persistentState.loadState(state)
        
        assertEquals(TimeTrackingStatus.STOPPED, persistentState.getStatus())
    }
    
    // ========== Timestamp Tests ==========
    
    @Test
    fun `test setLastUpdateTimestamp updates timestamp`() {
        val timestamp = System.currentTimeMillis()
        persistentState.setLastUpdateTimestamp(timestamp)
        
        assertEquals(timestamp, persistentState.getLastUpdateTimestamp())
    }
    
    @Test
    fun `test setLastUpdateTimestamp with zero`() {
        persistentState.setLastUpdateTimestamp(0L)
        
        assertEquals(0L, persistentState.getLastUpdateTimestamp())
    }
    
    @Test
    fun `test setLastUpdateTimestamp with future time`() {
        val futureTime = System.currentTimeMillis() + 10000L
        persistentState.setLastUpdateTimestamp(futureTime)
        
        assertEquals(futureTime, persistentState.getLastUpdateTimestamp())
    }
    
    // ========== Auto-Pause Flag Tests ==========
    
    @Test
    fun `test setAutoPausedByFocus to true`() {
        persistentState.setAutoPausedByFocus(true)
        
        assertTrue(persistentState.isAutoPausedByFocus())
    }
    
    @Test
    fun `test setAutoPausedByFocus to false`() {
        persistentState.setAutoPausedByFocus(true)
        persistentState.setAutoPausedByFocus(false)
        
        assertFalse(persistentState.isAutoPausedByFocus())
    }
    
    @Test
    fun `test setAutoPausedByProjectSwitch to true`() {
        persistentState.setAutoPausedByProjectSwitch(true)
        
        assertTrue(persistentState.isAutoPausedByProjectSwitch())
    }
    
    @Test
    fun `test setAutoPausedByProjectSwitch to false`() {
        persistentState.setAutoPausedByProjectSwitch(true)
        persistentState.setAutoPausedByProjectSwitch(false)
        
        assertFalse(persistentState.isAutoPausedByProjectSwitch())
    }
    
    @Test
    fun `test both auto-pause flags can be set independently`() {
        persistentState.setAutoPausedByFocus(true)
        persistentState.setAutoPausedByProjectSwitch(false)
        
        assertTrue(persistentState.isAutoPausedByFocus())
        assertFalse(persistentState.isAutoPausedByProjectSwitch())
    }
    
    @Test
    fun `test both auto-pause flags can be true simultaneously`() {
        persistentState.setAutoPausedByFocus(true)
        persistentState.setAutoPausedByProjectSwitch(true)
        
        assertTrue(persistentState.isAutoPausedByFocus())
        assertTrue(persistentState.isAutoPausedByProjectSwitch())
    }
    
    @Test
    fun `test clearAutoPauseFlags clears both flags`() {
        persistentState.setAutoPausedByFocus(true)
        persistentState.setAutoPausedByProjectSwitch(true)
        
        persistentState.clearAutoPauseFlags()
        
        assertFalse(persistentState.isAutoPausedByFocus())
        assertFalse(persistentState.isAutoPausedByProjectSwitch())
    }
    
    @Test
    fun `test clearAutoPauseFlags when flags already false`() {
        persistentState.clearAutoPauseFlags()
        
        assertFalse(persistentState.isAutoPausedByFocus())
        assertFalse(persistentState.isAutoPausedByProjectSwitch())
    }
    
    @Test
    fun `test clearAutoPauseFlags only clears focus flag when project switch is false`() {
        persistentState.setAutoPausedByFocus(true)
        persistentState.setAutoPausedByProjectSwitch(false)
        
        persistentState.clearAutoPauseFlags()
        
        assertFalse(persistentState.isAutoPausedByFocus())
        assertFalse(persistentState.isAutoPausedByProjectSwitch())
    }
    
    // ========== Reset Tests ==========
    
    @Test
    fun `test reset clears all state`() {
        persistentState.setTotalTimeMs(5000L)
        persistentState.setStatus(TimeTrackingStatus.RUNNING)
        persistentState.setLastUpdateTimestamp(123456L)
        persistentState.setAutoPausedByFocus(true)
        persistentState.setAutoPausedByProjectSwitch(true)
        
        persistentState.reset()
        
        assertEquals(0L, persistentState.getTotalTimeMs())
        assertEquals(TimeTrackingStatus.STOPPED, persistentState.getStatus())
        assertEquals(0L, persistentState.getLastUpdateTimestamp())
        assertFalse(persistentState.isAutoPausedByFocus())
        assertFalse(persistentState.isAutoPausedByProjectSwitch())
    }
    
    @Test
    fun `test reset when already at default values`() {
        persistentState.reset()
        
        assertEquals(0L, persistentState.getTotalTimeMs())
        assertEquals(TimeTrackingStatus.STOPPED, persistentState.getStatus())
        assertEquals(0L, persistentState.getLastUpdateTimestamp())
    }
    
    @Test
    fun `test reset multiple times`() {
        persistentState.setTotalTimeMs(1000L)
        persistentState.reset()
        persistentState.setTotalTimeMs(2000L)
        persistentState.reset()
        
        assertEquals(0L, persistentState.getTotalTimeMs())
        assertEquals(TimeTrackingStatus.STOPPED, persistentState.getStatus())
    }
    
    // ========== Integration Tests ==========
    
    @Test
    fun `test complete workflow - start, accumulate time, pause, resume, reset`() {
        // Start
        persistentState.setStatus(TimeTrackingStatus.RUNNING)
        assertEquals(TimeTrackingStatus.RUNNING, persistentState.getStatus())
        
        // Accumulate time
        persistentState.addTimeMs(1000L)
        assertEquals(1000L, persistentState.getTotalTimeMs())
        
        // Auto-pause by focus
        persistentState.setAutoPausedByFocus(true)
        persistentState.setStatus(TimeTrackingStatus.IDLE)
        assertTrue(persistentState.isAutoPausedByFocus())
        assertEquals(TimeTrackingStatus.IDLE, persistentState.getStatus())
        
        // Resume
        persistentState.setAutoPausedByFocus(false)
        persistentState.setStatus(TimeTrackingStatus.RUNNING)
        assertFalse(persistentState.isAutoPausedByFocus())
        
        // Accumulate more time
        persistentState.addTimeMs(500L)
        assertEquals(1500L, persistentState.getTotalTimeMs())
        
        // Reset
        persistentState.reset()
        assertEquals(0L, persistentState.getTotalTimeMs())
        assertEquals(TimeTrackingStatus.STOPPED, persistentState.getStatus())
    }
    
    @Test
    fun `test state persists after multiple operations`() {
        persistentState.setTotalTimeMs(100L)
        persistentState.addTimeMs(50L)
        persistentState.setStatus(TimeTrackingStatus.RUNNING)
        persistentState.setAutoPausedByFocus(true)
        
        // Verify state is preserved
        assertEquals(150L, persistentState.getTotalTimeMs())
        assertEquals(TimeTrackingStatus.RUNNING, persistentState.getStatus())
        assertTrue(persistentState.isAutoPausedByFocus())
    }
    
    @Test
    fun `test auto-pause flags don't affect time accumulation`() {
        persistentState.setTotalTimeMs(1000L)
        persistentState.setAutoPausedByFocus(true)
        persistentState.setAutoPausedByProjectSwitch(true)
        
        persistentState.addTimeMs(500L)
        
        assertEquals(1500L, persistentState.getTotalTimeMs())
        assertTrue(persistentState.isAutoPausedByFocus())
        assertTrue(persistentState.isAutoPausedByProjectSwitch())
    }
    
    @Test
    fun `test status changes don't affect auto-pause flags`() {
        persistentState.setAutoPausedByFocus(true)
        persistentState.setAutoPausedByProjectSwitch(true)
        
        persistentState.setStatus(TimeTrackingStatus.RUNNING)
        
        assertTrue(persistentState.isAutoPausedByFocus())
        assertTrue(persistentState.isAutoPausedByProjectSwitch())
    }
    
    @Test
    fun `test edge case - maximum long value for time`() {
        persistentState.setTotalTimeMs(Long.MAX_VALUE)
        
        assertEquals(Long.MAX_VALUE, persistentState.getTotalTimeMs())
    }
    
    @Test
    fun `test edge case - negative time values`() {
        persistentState.setTotalTimeMs(-1000L)
        
        assertEquals(-1000L, persistentState.getTotalTimeMs())
    }
}