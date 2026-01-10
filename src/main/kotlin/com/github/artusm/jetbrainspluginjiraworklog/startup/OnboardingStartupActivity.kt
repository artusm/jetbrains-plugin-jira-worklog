package com.github.artusm.jetbrainspluginjiraworklog.startup

import com.github.artusm.jetbrainspluginjiraworklog.config.JiraSettings
import com.github.artusm.jetbrainspluginjiraworklog.onboarding.OnboardingDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Startup activity to check if onboarding is needed.
 * Shows onboarding dialog if Jira credentials are not configured.
 */
class OnboardingStartupActivity : ProjectActivity {
    
    override suspend fun execute(project: Project) {
        val settings = JiraSettings.getInstance()
        
        // Show onboarding dialog if credentials are not configured
        if (!settings.hasCredentials()) {
            OnboardingDialog.show(project)
        }
    }
}
