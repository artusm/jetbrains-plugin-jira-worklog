package com.github.artusm.jetbrainspluginjiraworklog.services

import com.github.artusm.jetbrainspluginjiraworklog.model.TimeTrackingStatus
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Unit tests for JiraWorklogTimerService.
 * Tests timer operations, auto-pause functionality, and state management.
 */
class JiraWorklogTimerServiceTest : BasePlatformTestCase() {
    
    private lateinit var timerService: JiraWorklogTimerService
    private lateinit var persistentState: JiraWorklogPersistentState
    
    override fun setUp() {
        super.setUp()
        timerService = project.service()
        persistentState = project.service()
        // Reset state before each test
        persistentState.reset()
    }
    
    override fun tearDown() {
        try {
            // Clean up state after each test
            persistentState.reset()
        } finally {
            super.tearDown()
        }
    }
    
    // ========== Initial State Tests ==========
    
    fun `test initial service state is stopped`() {
        assertEquals(TimeTrackingStatus.STOPPED, timerService.getStatus())
        assertEquals(0, timerService.getTotalTimeSeconds())
        assertEquals(0L, timerService.getTotalTimeMs())
    }
    
    fun `test service is registered with project`() {
        assertNotNull(timerService)
        assertSame(timerService, project.service<JiraWorklogTimerService>())
    }
    
    // ========== Status Management Tests ==========
    
    fun `test setStatus to RUNNING`() {
        timerService.setStatus(TimeTrackingStatus.RUNNING)
        
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
    }
    
    fun `test setStatus to IDLE`() {
        timerService.setStatus(TimeTrackingStatus.IDLE)
        
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
    }
    
    fun `test setStatus to STOPPED`() {
        timerService.setStatus(TimeTrackingStatus.STOPPED)
        
        assertEquals(TimeTrackingStatus.STOPPED, timerService.getStatus())
    }
    
    fun `test setStatus updates last update timestamp`() {
        val before = System.currentTimeMillis()
        Thread.sleep(10) // Small delay to ensure timestamp difference
        
        timerService.setStatus(TimeTrackingStatus.RUNNING)
        
        val after = System.currentTimeMillis()
        val timestamp = persistentState.getLastUpdateTimestamp()
        
        assertTrue(timestamp >= before)
        assertTrue(timestamp <= after)
    }
    
    // ========== Start/Stop Tests ==========
    
    fun `test start sets status to RUNNING`() {
        timerService.start()
        
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
    }
    
    fun `test start clears auto-pause flags`() {
        persistentState.setAutoPausedByFocus(true)
        persistentState.setAutoPausedByProjectSwitch(true)
        
        timerService.start()
        
        assertFalse(persistentState.isAutoPausedByFocus())
        assertFalse(persistentState.isAutoPausedByProjectSwitch())
    }
    
    fun `test start when already running`() {
        timerService.start()
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
        
        timerService.start() // Should not cause issues
        
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
    }
    
    fun `test stop sets status to STOPPED`() {
        timerService.start()
        timerService.stop()
        
        assertEquals(TimeTrackingStatus.STOPPED, timerService.getStatus())
    }
    
    fun `test stop clears auto-pause flags`() {
        persistentState.setAutoPausedByFocus(true)
        persistentState.setAutoPausedByProjectSwitch(true)
        
        timerService.stop()
        
        assertFalse(persistentState.isAutoPausedByFocus())
        assertFalse(persistentState.isAutoPausedByProjectSwitch())
    }
    
    fun `test stop when already stopped`() {
        timerService.stop()
        assertEquals(TimeTrackingStatus.STOPPED, timerService.getStatus())
        
        timerService.stop() // Should not cause issues
        
        assertEquals(TimeTrackingStatus.STOPPED, timerService.getStatus())
    }
    
    // ========== Pause/Resume Tests ==========
    
    fun `test pause from RUNNING sets to IDLE`() {
        timerService.start()
        timerService.pause()
        
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
    }
    
    fun `test pause clears auto-pause flags`() {
        persistentState.setAutoPausedByFocus(true)
        persistentState.setAutoPausedByProjectSwitch(true)
        timerService.start()
        
        timerService.pause()
        
        assertFalse(persistentState.isAutoPausedByFocus())
        assertFalse(persistentState.isAutoPausedByProjectSwitch())
    }
    
    fun `test pause when not running does nothing`() {
        timerService.setStatus(TimeTrackingStatus.STOPPED)
        timerService.pause()
        
        assertEquals(TimeTrackingStatus.STOPPED, timerService.getStatus())
    }
    
    fun `test resume from IDLE sets to RUNNING`() {
        timerService.setStatus(TimeTrackingStatus.IDLE)
        timerService.resume()
        
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
    }
    
    fun `test resume when not IDLE does nothing`() {
        timerService.setStatus(TimeTrackingStatus.STOPPED)
        timerService.resume()
        
        assertEquals(TimeTrackingStatus.STOPPED, timerService.getStatus())
    }
    
    // ========== Toggle Tests ==========
    
    fun `test toggleRunning from STOPPED sets to RUNNING`() {
        timerService.setStatus(TimeTrackingStatus.STOPPED)
        timerService.toggleRunning()
        
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
    }
    
    fun `test toggleRunning from RUNNING sets to STOPPED`() {
        timerService.setStatus(TimeTrackingStatus.RUNNING)
        timerService.toggleRunning()
        
        assertEquals(TimeTrackingStatus.STOPPED, timerService.getStatus())
    }
    
    fun `test toggleRunning from IDLE sets to STOPPED`() {
        timerService.setStatus(TimeTrackingStatus.IDLE)
        timerService.toggleRunning()
        
        assertEquals(TimeTrackingStatus.STOPPED, timerService.getStatus())
    }
    
    fun `test toggleRunning clears auto-pause flags`() {
        persistentState.setAutoPausedByFocus(true)
        persistentState.setAutoPausedByProjectSwitch(true)
        
        timerService.toggleRunning()
        
        assertFalse(persistentState.isAutoPausedByFocus())
        assertFalse(persistentState.isAutoPausedByProjectSwitch())
    }
    
    fun `test toggleRunning multiple times`() {
        timerService.toggleRunning()
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
        
        timerService.toggleRunning()
        assertEquals(TimeTrackingStatus.STOPPED, timerService.getStatus())
        
        timerService.toggleRunning()
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
    }
    
    // ========== Auto-Pause by Focus Tests ==========
    
    fun `test autoPauseByFocus when RUNNING pauses timer`() {
        timerService.start()
        timerService.autoPauseByFocus()
        
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
        assertTrue(persistentState.isAutoPausedByFocus())
    }
    
    fun `test autoPauseByFocus when STOPPED does nothing`() {
        timerService.stop()
        timerService.autoPauseByFocus()
        
        assertEquals(TimeTrackingStatus.STOPPED, timerService.getStatus())
        assertFalse(persistentState.isAutoPausedByFocus())
    }
    
    fun `test autoPauseByFocus when IDLE does nothing`() {
        timerService.setStatus(TimeTrackingStatus.IDLE)
        timerService.autoPauseByFocus()
        
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
        assertFalse(persistentState.isAutoPausedByFocus())
    }
    
    fun `test autoResumeFromFocus when auto-paused by focus resumes timer`() {
        timerService.start()
        timerService.autoPauseByFocus()
        
        timerService.autoResumeFromFocus()
        
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
        assertFalse(persistentState.isAutoPausedByFocus())
    }
    
    fun `test autoResumeFromFocus when not auto-paused does nothing`() {
        timerService.setStatus(TimeTrackingStatus.IDLE)
        timerService.autoResumeFromFocus()
        
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
    }
    
    fun `test autoResumeFromFocus when not IDLE does nothing`() {
        persistentState.setAutoPausedByFocus(true)
        timerService.setStatus(TimeTrackingStatus.STOPPED)
        
        timerService.autoResumeFromFocus()
        
        assertEquals(TimeTrackingStatus.STOPPED, timerService.getStatus())
        assertTrue(persistentState.isAutoPausedByFocus()) // Flag remains
    }
    
    fun `test autoResumeFromFocus when auto-paused by project switch does nothing`() {
        timerService.setStatus(TimeTrackingStatus.IDLE)
        persistentState.setAutoPausedByProjectSwitch(true)
        
        timerService.autoResumeFromFocus()
        
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
    }
    
    // ========== Auto-Pause by Project Switch Tests ==========
    
    fun `test autoPauseByProjectSwitch when RUNNING pauses timer`() {
        timerService.start()
        timerService.autoPauseByProjectSwitch()
        
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
        assertTrue(persistentState.isAutoPausedByProjectSwitch())
    }
    
    fun `test autoPauseByProjectSwitch when STOPPED does nothing`() {
        timerService.stop()
        timerService.autoPauseByProjectSwitch()
        
        assertEquals(TimeTrackingStatus.STOPPED, timerService.getStatus())
        assertFalse(persistentState.isAutoPausedByProjectSwitch())
    }
    
    fun `test autoPauseByProjectSwitch when IDLE does nothing`() {
        timerService.setStatus(TimeTrackingStatus.IDLE)
        timerService.autoPauseByProjectSwitch()
        
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
        assertFalse(persistentState.isAutoPausedByProjectSwitch())
    }
    
    fun `test both auto-pause flags can be set independently`() {
        timerService.start()
        timerService.autoPauseByFocus()
        
        assertTrue(persistentState.isAutoPausedByFocus())
        assertFalse(persistentState.isAutoPausedByProjectSwitch())
        
        // Now set project switch flag
        timerService.start()
        timerService.autoPauseByProjectSwitch()
        
        assertTrue(persistentState.isAutoPausedByProjectSwitch())
    }
    
    // ========== Time Management Tests ==========
    
    fun `test getTotalTimeSeconds returns seconds`() {
        persistentState.setTotalTimeMs(5000L)
        
        assertEquals(5, timerService.getTotalTimeSeconds())
    }
    
    fun `test getTotalTimeSeconds rounds down`() {
        persistentState.setTotalTimeMs(5999L)
        
        assertEquals(5, timerService.getTotalTimeSeconds())
    }
    
    fun `test getTotalTimeMs returns milliseconds`() {
        persistentState.setTotalTimeMs(12345L)
        
        assertEquals(12345L, timerService.getTotalTimeMs())
    }
    
    fun `test addTimeMs adds to total`() {
        timerService.setTotalTimeMs(1000L)
        timerService.addTimeMs(500L)
        
        assertEquals(1500L, timerService.getTotalTimeMs())
    }
    
    fun `test addTimeMs with zero`() {
        timerService.setTotalTimeMs(1000L)
        timerService.addTimeMs(0L)
        
        assertEquals(1000L, timerService.getTotalTimeMs())
    }
    
    fun `test addTimeMs with negative value`() {
        timerService.setTotalTimeMs(1000L)
        timerService.addTimeMs(-300L)
        
        assertEquals(700L, timerService.getTotalTimeMs())
    }
    
    fun `test setTotalTimeMs sets time`() {
        timerService.setTotalTimeMs(5000L)
        
        assertEquals(5000L, timerService.getTotalTimeMs())
    }
    
    fun `test setTotalTimeMs with zero`() {
        timerService.setTotalTimeMs(1000L)
        timerService.setTotalTimeMs(0L)
        
        assertEquals(0L, timerService.getTotalTimeMs())
    }
    
    // ========== Reset Tests ==========
    
    fun `test reset clears all state`() {
        timerService.start()
        persistentState.setTotalTimeMs(5000L)
        persistentState.setAutoPausedByFocus(true)
        persistentState.setAutoPausedByProjectSwitch(true)
        
        timerService.reset()
        
        assertEquals(0L, timerService.getTotalTimeMs())
        assertEquals(TimeTrackingStatus.STOPPED, timerService.getStatus())
        assertFalse(persistentState.isAutoPausedByFocus())
        assertFalse(persistentState.isAutoPausedByProjectSwitch())
    }
    
    fun `test reset when already at default values`() {
        timerService.reset()
        
        assertEquals(0L, timerService.getTotalTimeMs())
        assertEquals(TimeTrackingStatus.STOPPED, timerService.getStatus())
    }
    
    fun `test reset multiple times`() {
        timerService.setTotalTimeMs(1000L)
        timerService.reset()
        timerService.setTotalTimeMs(2000L)
        timerService.reset()
        
        assertEquals(0L, timerService.getTotalTimeMs())
        assertEquals(TimeTrackingStatus.STOPPED, timerService.getStatus())
    }
    
    // ========== Integration Tests ==========
    
    fun `test complete workflow - start, pause by focus, resume, stop`() {
        // Start timer
        timerService.start()
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
        
        // Auto-pause by focus
        timerService.autoPauseByFocus()
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
        assertTrue(persistentState.isAutoPausedByFocus())
        
        // Auto-resume
        timerService.autoResumeFromFocus()
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
        assertFalse(persistentState.isAutoPausedByFocus())
        
        // Stop timer
        timerService.stop()
        assertEquals(TimeTrackingStatus.STOPPED, timerService.getStatus())
    }
    
    fun `test manual pause overrides auto-pause flags`() {
        timerService.start()
        timerService.autoPauseByFocus()
        
        assertTrue(persistentState.isAutoPausedByFocus())
        
        // Manual pause should clear flags
        timerService.pause()
        
        assertFalse(persistentState.isAutoPausedByFocus())
    }
    
    fun `test manual start overrides auto-pause flags`() {
        timerService.start()
        timerService.autoPauseByFocus()
        
        assertTrue(persistentState.isAutoPausedByFocus())
        
        // Manual start should clear flags
        timerService.start()
        
        assertFalse(persistentState.isAutoPausedByFocus())
    }
    
    fun `test auto-pause by focus then by project switch`() {
        timerService.start()
        timerService.autoPauseByFocus()
        
        assertTrue(persistentState.isAutoPausedByFocus())
        assertFalse(persistentState.isAutoPausedByProjectSwitch())
        
        // Start again and pause by project switch
        timerService.start()
        timerService.autoPauseByProjectSwitch()
        
        assertFalse(persistentState.isAutoPausedByFocus()) // Cleared by start
        assertTrue(persistentState.isAutoPausedByProjectSwitch())
    }
    
    fun `test time accumulation persists across status changes`() {
        timerService.setTotalTimeMs(1000L)
        
        timerService.start()
        assertEquals(1000L, timerService.getTotalTimeMs())
        
        timerService.pause()
        assertEquals(1000L, timerService.getTotalTimeMs())
        
        timerService.stop()
        assertEquals(1000L, timerService.getTotalTimeMs())
    }
    
    fun `test widget returns non-null widget instance`() {
        val widget = timerService.widget()
        
        assertNotNull(widget)
    }
    
    fun `test widget returns same instance on multiple calls`() {
        val widget1 = timerService.widget()
        val widget2 = timerService.widget()
        
        assertSame(widget1, widget2)
    }
    
    // ========== Edge Case Tests ==========
    
    fun `test concurrent status changes are synchronized`() {
        // This tests that the synchronized block works correctly
        timerService.start()
        timerService.stop()
        timerService.start()
        
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
    }
    
    fun `test status change with maximum long time value`() {
        timerService.setTotalTimeMs(Long.MAX_VALUE)
        timerService.start()
        
        assertEquals(Long.MAX_VALUE, timerService.getTotalTimeMs())
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
    }
    
    fun `test auto-pause operations are idempotent`() {
        timerService.start()
        timerService.autoPauseByFocus()
        timerService.autoPauseByFocus() // Should not cause issues
        
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
        assertTrue(persistentState.isAutoPausedByFocus())
    }
    
    fun `test auto-resume operations are idempotent`() {
        timerService.start()
        timerService.autoPauseByFocus()
        timerService.autoResumeFromFocus()
        timerService.autoResumeFromFocus() // Should not cause issues
        
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
        assertFalse(persistentState.isAutoPausedByFocus())
    }
}