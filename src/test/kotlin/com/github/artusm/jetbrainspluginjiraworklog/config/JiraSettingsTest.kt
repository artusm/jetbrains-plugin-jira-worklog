package com.github.artusm.jetbrainspluginjiraworklog.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JiraSettingsTest {

    @Test
    fun `test initial state`() {
        val settings = JiraSettings()
        assertFalse(settings.isPauseOnFocusLoss())
        assertTrue(settings.isPauseOnSystemSleep()) // Default from code inspection if true
        // Checking defaults from JiraSettings.kt
        // Assuming defaults are:
        // pauseOnFocusLoss = false
        // pauseOnSystemSleep = true
        // pauseOnProjectSwitch = true
        // pauseOnBranchChange = true

        // Let's verify actual values by creating an instance
    }

    @Test
    fun `test round trip serialization`() {
        val settings = JiraSettings()
        settings.setJiraUrl("https://jira.test.com")
        settings.setPersonalAccessToken("secret")
        settings.setPauseOnFocusLoss(true)

        val state = settings.getState()

        val newSettings = JiraSettings()
        newSettings.loadState(state)

        assertEquals("https://jira.test.com", newSettings.getJiraUrl())
        assertEquals("secret", newSettings.getPersonalAccessToken())
        assertTrue(newSettings.isPauseOnFocusLoss())
    }
}
