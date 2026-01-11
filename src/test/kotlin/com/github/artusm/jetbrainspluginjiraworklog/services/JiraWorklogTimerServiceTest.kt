package com.github.artusm.jetbrainspluginjiraworklog.services

import com.github.artusm.jetbrainspluginjiraworklog.config.JiraSettings
import com.github.artusm.jetbrainspluginjiraworklog.model.TimeTrackingStatus
import com.github.artusm.jetbrainspluginjiraworklog.utils.TimeProvider
import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class JiraWorklogTimerServiceTest {

    private lateinit var timerService: JiraWorklogTimerService
    private lateinit var timeProvider: TimeProvider
    private lateinit var coroutineScope: CoroutineScope
    private val currentTime = AtomicLong(1000000L)
    private lateinit var mockSettings: JiraSettings
    private lateinit var mockProject: Project
    private lateinit var persistentState: JiraWorklogPersistentState

    @Before
    fun setUp() {
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        timeProvider = object : TimeProvider {
            override fun currentTimeMillis(): Long = currentTime.get()
        }

        mockSettings = mockk<JiraSettings>(relaxed = true)
        mockkStatic(JiraSettings::class)
        every { JiraSettings.getInstance() } returns mockSettings

        mockProject = mockk(relaxed = true)

        // Mock the service() extension function
        mockkStatic("com.intellij.openapi.components.ServiceKt")
        persistentState = JiraWorklogPersistentState()
        // We can't easily mock the extension function return value globally without PowerMock or specific MockK setups that might conflict.
        // Instead, since JiraWorklogTimerService calls project.service<JiraWorklogPersistentState>(), we can mock that if possible.
        // Or better, we can assume getService() is called on project.
        every { mockProject.getService(JiraWorklogPersistentState::class.java) } returns persistentState

        timerService = JiraWorklogTimerService(mockProject, coroutineScope, timeProvider)
    }

    @After
    fun tearDown() {
        coroutineScope.cancel()
    }

    @Test
    fun `test start ticking updates time`() {
        timerService.setStatus(TimeTrackingStatus.RUNNING)

        // Simulate 2 seconds passing
        currentTime.addAndGet(2000)
        Thread.sleep(1100) // Wait for tick coroutine to run at least once

        assertTrue(timerService.getTotalTimeMs() >= 1000L)
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
    }

    @Test
    fun `test stop ticking stops updates`() {
        timerService.setStatus(TimeTrackingStatus.RUNNING)
        currentTime.addAndGet(1000)
        Thread.sleep(1100)

        val timeAfterRun = timerService.getTotalTimeMs()
        assertTrue(timeAfterRun > 0)

        timerService.setStatus(TimeTrackingStatus.STOPPED)
        currentTime.addAndGet(5000)
        Thread.sleep(1100)

        assertEquals(timeAfterRun, timerService.getTotalTimeMs())
        assertEquals(TimeTrackingStatus.STOPPED, timerService.getStatus())
    }

    @Test
    fun `test toggle running`() {
        assertEquals(TimeTrackingStatus.STOPPED, timerService.getStatus())

        timerService.toggleRunning()
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())

        timerService.toggleRunning()
        assertEquals(TimeTrackingStatus.STOPPED, timerService.getStatus())
    }

    @Test
    fun `test auto pause by focus`() {
        timerService.setStatus(TimeTrackingStatus.RUNNING)
        timerService.autoPauseByFocus()

        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())

        // Should resume
        timerService.autoResumeFromFocus()
        assertEquals(TimeTrackingStatus.RUNNING, timerService.getStatus())
    }

    @Test
    fun `test system sleep detection`() {
        every { mockSettings.isPauseOnSystemSleep() } returns true

        timerService.setStatus(TimeTrackingStatus.RUNNING)

        // Simulate a large gap in time (e.g. sleep for 10 seconds)
        // We advance time but do NOT wait for tick in between
        val gap = 10000L
        currentTime.addAndGet(gap)

        // Wait for next tick to process the gap
        Thread.sleep(1100)

        assertEquals(TimeTrackingStatus.IDLE, timerService.getStatus())
        // Time should not have increased by the full gap
        assertTrue(timerService.getTotalTimeMs() < gap)
    }

    @Test
    fun `test reset clears time`() {
        timerService.addTimeMs(5000)
        assertEquals(5000L, timerService.getTotalTimeMs())

        timerService.reset()
        assertEquals(0L, timerService.getTotalTimeMs())
        assertEquals(TimeTrackingStatus.STOPPED, timerService.getStatus())
    }
}
