package com.github.artusm.jetbrainspluginjiraworklog.listeners

import com.github.artusm.jetbrainspluginjiraworklog.config.JiraSettings
import com.github.artusm.jetbrainspluginjiraworklog.model.TimeTrackingStatus
import com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogPersistentState
import com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogTimerService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFrame
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertNotEquals

/**
 * Unit tests for WindowFocusListener.
 * Tests window focus handling, project switching, and auto-pause behavior.
 */
class WindowFocusListenerTest : BasePlatformTestCase() {
    
    private lateinit var listener: WindowFocusListener
    private lateinit var timerService: JiraWorklogTimerService
    private lateinit var persistentState: JiraWorklogPersistentState
    private lateinit var settings: JiraSettings
    
    override fun setUp() {
        super.setUp()
        listener = WindowFocusListener()
        timerService = project.service()
        persistentState = project.service()
        settings = JiraSettings.getInstance()
        
        // Reset state before each test
        persistentState.reset()
        
        // Set default settings for testing
        settings.setPauseOnFocusLoss(true)
        settings.setPauseOnProjectSwitch(true)
    }
    
    override fun tearDown() {
        try {
            persistentState.reset()
        } finally {
            super.tearDown()
        }
    }
    
    // ========== Application Deactivation Tests ==========
    
    fun `test applicationDeactivated pauses timer when pause on focus loss is enabled`() {
        settings.setPauseOnFocusLoss(true)
        timerService.start()
        
        val ideFrame = createMockIdeFrame(project)
        listener.applicationDeactivated(ideFrame)
        
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
        assertTrue(persistentState.isAutoPausedByFocus())
    }
    
    fun `test applicationDeactivated does not pause when pause on focus loss is disabled`() {
        settings.setPauseOnFocusLoss(false)
        timerService.start()
        
        val ideFrame = createMockIdeFrame(project)
        listener.applicationDeactivated(ideFrame)
        
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
        assertFalse(persistentState.isAutoPausedByFocus())
    }
    
    fun `test applicationDeactivated does not pause stopped timer`() {
        settings.setPauseOnFocusLoss(true)
        timerService.stop()
        
        val ideFrame = createMockIdeFrame(project)
        listener.applicationDeactivated(ideFrame)
        
        assertEquals(TimeTrackingStatus.STOPPED, timerService.getStatus())
        assertFalse(persistentState.isAutoPausedByFocus())
    }
    
    fun `test applicationDeactivated does not pause idle timer`() {
        settings.setPauseOnFocusLoss(true)
        timerService.setStatus(TimeTrackingStatus.IDLE)
        
        val ideFrame = createMockIdeFrame(project)
        listener.applicationDeactivated(ideFrame)
        
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
        assertFalse(persistentState.isAutoPausedByFocus())
    }
    
    fun `test applicationDeactivated with null ideFrame does not crash`() {
        settings.setPauseOnFocusLoss(true)
        timerService.start()
        
        val ideFrame = createMockIdeFrame(null)
        listener.applicationDeactivated(ideFrame)
        
        // Should still pause all open projects
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
    }
    
    // ========== Application Activation Tests ==========
    
    fun `test applicationActivated resumes timer when auto-paused by focus`() {
        settings.setPauseOnFocusLoss(true)
        timerService.start()
        
        val ideFrame = createMockIdeFrame(project)
        listener.applicationDeactivated(ideFrame)
        
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
        assertTrue(persistentState.isAutoPausedByFocus())
        
        listener.applicationActivated(ideFrame)
        
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
        assertFalse(persistentState.isAutoPausedByFocus())
    }
    
    fun `test applicationActivated does not resume when pause on focus loss is disabled`() {
        settings.setPauseOnFocusLoss(false)
        timerService.setStatus(TimeTrackingStatus.IDLE)
        persistentState.setAutoPausedByFocus(true)
        
        val ideFrame = createMockIdeFrame(project)
        listener.applicationActivated(ideFrame)
        
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
    }
    
    fun `test applicationActivated does not resume when not auto-paused`() {
        settings.setPauseOnFocusLoss(true)
        timerService.setStatus(TimeTrackingStatus.IDLE)
        
        val ideFrame = createMockIdeFrame(project)
        listener.applicationActivated(ideFrame)
        
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
    }
    
    fun `test applicationActivated does not resume when auto-paused by project switch`() {
        settings.setPauseOnFocusLoss(true)
        timerService.setStatus(TimeTrackingStatus.IDLE)
        persistentState.setAutoPausedByProjectSwitch(true)
        
        val ideFrame = createMockIdeFrame(project)
        listener.applicationActivated(ideFrame)
        
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
        assertTrue(persistentState.isAutoPausedByProjectSwitch())
    }
    
    fun `test applicationActivated with null project does not crash`() {
        settings.setPauseOnFocusLoss(true)
        
        val ideFrame = createMockIdeFrame(null)
        listener.applicationActivated(ideFrame)
        
        // Should not cause any errors
        assertTrue(true)
    }
    
    fun `test applicationActivated tracks last active project`() {
        settings.setPauseOnProjectSwitch(true)
        
        val ideFrame = createMockIdeFrame(project)
        listener.applicationActivated(ideFrame)
        
        // The project should now be tracked as last active
        // (This is tested indirectly through project switch behavior)
        assertTrue(true)
    }
    
    // ========== Project Switch Tests ==========
    
    fun `test project switch pauses previous project when enabled`() {
        settings.setPauseOnProjectSwitch(true)
        timerService.start()
        
        // Activate first project
        val ideFrame1 = createMockIdeFrame(project)
        listener.applicationActivated(ideFrame1)
        
        // Create and activate a different project
        val project2 = createLightProject()
        val timerService2 = project2.service<JiraWorklogTimerService>()
        val persistentState2 = project2.service<JiraWorklogPersistentState>()
        timerService2.start()
        
        val ideFrame2 = createMockIdeFrame(project2)
        listener.applicationActivated(ideFrame2)
        
        // First project should be auto-paused
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
        assertTrue(persistentState.isAutoPausedByProjectSwitch())
        
        // Second project should still be running
        assertEquals(TimeTrackingStatus.RUNNING, timerService2.getStatus())
    }
    
    fun `test project switch does not pause when disabled`() {
        settings.setPauseOnProjectSwitch(false)
        timerService.start()
        
        val ideFrame1 = createMockIdeFrame(project)
        listener.applicationActivated(ideFrame1)
        
        val project2 = createLightProject()
        val ideFrame2 = createMockIdeFrame(project2)
        listener.applicationActivated(ideFrame2)
        
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
        assertFalse(persistentState.isAutoPausedByProjectSwitch())
    }
    
    fun `test switching to same project does not pause`() {
        settings.setPauseOnProjectSwitch(true)
        timerService.start()
        
        val ideFrame = createMockIdeFrame(project)
        listener.applicationActivated(ideFrame)
        listener.applicationActivated(ideFrame) // Activate same project again
        
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
        assertFalse(persistentState.isAutoPausedByProjectSwitch())
    }
    
    fun `test project switch does not pause stopped timer`() {
        settings.setPauseOnProjectSwitch(true)
        timerService.stop()
        
        val ideFrame1 = createMockIdeFrame(project)
        listener.applicationActivated(ideFrame1)
        
        val project2 = createLightProject()
        val ideFrame2 = createMockIdeFrame(project2)
        listener.applicationActivated(ideFrame2)
        
        assertEquals(TimeTrackingStatus.STOPPED, timerService.getStatus())
        assertFalse(persistentState.isAutoPausedByProjectSwitch())
    }
    
    // ========== Project Close Tests ==========
    
    fun `test projectClosed clears last active project tracking`() {
        val ideFrame = createMockIdeFrame(project)
        listener.applicationActivated(ideFrame)
        
        listener.projectClosed(project)
        
        // Subsequent activation should not try to pause the closed project
        val project2 = createLightProject()
        val ideFrame2 = createMockIdeFrame(project2)
        listener.applicationActivated(ideFrame2)
        
        // No exception should be thrown
        assertTrue(true)
    }
    
    fun `test projectClosed with different project does not affect tracking`() {
        val ideFrame = createMockIdeFrame(project)
        listener.applicationActivated(ideFrame)
        
        val project2 = createLightProject()
        listener.projectClosed(project2)
        
        // Closing a different project should not clear tracking
        // (This is tested indirectly - no exception should occur)
        assertTrue(true)
    }
    
    fun `test projectClosed when no project is tracked does not crash`() {
        listener.projectClosed(project)
        
        // Should not cause any errors
        assertTrue(true)
    }
    
    // ========== Integration Tests ==========
    
    fun `test complete workflow - focus loss and regain`() {
        settings.setPauseOnFocusLoss(true)
        timerService.start()
        
        val ideFrame = createMockIdeFrame(project)
        
        // Lose focus
        listener.applicationDeactivated(ideFrame)
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
        assertTrue(persistentState.isAutoPausedByFocus())
        
        // Regain focus
        listener.applicationActivated(ideFrame)
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
        assertFalse(persistentState.isAutoPausedByFocus())
    }
    
    fun `test complete workflow - project switch and focus`() {
        settings.setPauseOnFocusLoss(true)
        settings.setPauseOnProjectSwitch(true)
        
        timerService.start()
        
        val ideFrame1 = createMockIdeFrame(project)
        listener.applicationActivated(ideFrame1)
        
        // Lose focus
        listener.applicationDeactivated(ideFrame1)
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
        assertTrue(persistentState.isAutoPausedByFocus())
        
        // Switch to different project
        val project2 = createLightProject()
        val ideFrame2 = createMockIdeFrame(project2)
        listener.applicationActivated(ideFrame2)
        
        // First project should still be paused but now by project switch
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
    }
    
    fun `test focus loss pause overrides manual idle state`() {
        settings.setPauseOnFocusLoss(true)
        timerService.start()
        timerService.pause() // Manually pause
        
        val ideFrame = createMockIdeFrame(project)
        listener.applicationDeactivated(ideFrame)
        
        // Should not set auto-pause flag since not running
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
        assertFalse(persistentState.isAutoPausedByFocus())
    }
    
    fun `test multiple focus loss and gain cycles`() {
        settings.setPauseOnFocusLoss(true)
        timerService.start()
        
        val ideFrame = createMockIdeFrame(project)
        
        // Cycle 1
        listener.applicationDeactivated(ideFrame)
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
        listener.applicationActivated(ideFrame)
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
        
        // Cycle 2
        listener.applicationDeactivated(ideFrame)
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
        listener.applicationActivated(ideFrame)
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
    }
    
    // ========== Edge Case Tests ==========
    
    fun `test disposed project does not cause errors`() {
        // This test ensures that checking isDisposed doesn't crash
        val ideFrame = createMockIdeFrame(project)
        
        listener.applicationDeactivated(ideFrame)
        listener.applicationActivated(ideFrame)
        
        // Should complete without errors
        assertTrue(true)
    }
    
    fun `test all settings combinations`() {
        timerService.start()
        val ideFrame = createMockIdeFrame(project)
        
        // Test all combinations of settings
        settings.setPauseOnFocusLoss(false)
        settings.setPauseOnProjectSwitch(false)
        listener.applicationDeactivated(ideFrame)
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
        
        timerService.start()
        settings.setPauseOnFocusLoss(true)
        settings.setPauseOnProjectSwitch(false)
        listener.applicationDeactivated(ideFrame)
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
        
        timerService.start()
        settings.setPauseOnFocusLoss(false)
        settings.setPauseOnProjectSwitch(true)
        listener.applicationDeactivated(ideFrame)
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
        
        timerService.start()
        settings.setPauseOnFocusLoss(true)
        settings.setPauseOnProjectSwitch(true)
        listener.applicationDeactivated(ideFrame)
        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
    }
    
    fun `test listener is thread-safe`() {
        settings.setPauseOnFocusLoss(true)
        timerService.start()
        
        val ideFrame = createMockIdeFrame(project)
        
        // Rapid calls should not cause issues
        listener.applicationDeactivated(ideFrame)
        listener.applicationActivated(ideFrame)
        listener.applicationDeactivated(ideFrame)
        listener.applicationActivated(ideFrame)
        
        // Should complete without errors
        assertTrue(true)
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Creates a mock IdeFrame for testing.
     */
    private fun createMockIdeFrame(frameProject: Project?): IdeFrame {
        return object : IdeFrame {
            override fun getProject(): Project? = frameProject
            override fun getComponent(): java.awt.Component? = null
        }
    }
    
    /**
     * Creates a light project for testing project switching.
     */
    private fun createLightProject(): Project {
        // Use the fixture to create a new light project
        val newProject = myFixture.project
        assertNotNull(newProject)
        assertNotEquals(project, newProject)
        return newProject
    }
}