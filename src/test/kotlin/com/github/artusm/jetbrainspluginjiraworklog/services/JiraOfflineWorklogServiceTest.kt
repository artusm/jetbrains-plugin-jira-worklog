package com.github.artusm.jetbrainspluginjiraworklog.services

import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraWorklogResponse
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import java.net.ConnectException

class JiraOfflineWorklogServiceTest : BasePlatformTestCase() {

    fun `test offline queue logic`() {
        // Since we can't easily mock the internal JiraApiClient which is instantiated inside the service
        // without dependency injection or power mock, we will rely on integration/logic verification
        // via state checking if we were to mock the network calls.

        // However, for this environment, I can verify that the service is registered
        // and that persistent state handles the pending worklogs correctly.

        val state = myFixture.project.getService(JiraWorklogPersistentState::class.java)
        assertNotNull(state)
        assertTrue(state.getPendingWorklogs().isEmpty())

        val worklog = JiraWorklogPersistentState.PendingWorklog(
            issueKey = "TEST-1",
            timeSpentSeconds = 3600,
            comment = "Test comment",
            started = "2023-01-01T12:00:00Z",
            timestamp = System.currentTimeMillis()
        )

        state.addPendingWorklog(worklog)
        assertEquals(1, state.getPendingWorklogs().size)
        assertEquals("TEST-1", state.getPendingWorklogs()[0].issueKey)

        state.removePendingWorklog(worklog)
        assertTrue(state.getPendingWorklogs().isEmpty())
    }

    fun `test offline service registration`() {
        val service = myFixture.project.getService(JiraOfflineWorklogService::class.java)
        assertNotNull("Service should be registered", service)
    }
}
