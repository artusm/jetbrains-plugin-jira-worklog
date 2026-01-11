package com.github.artusm.jetbrainspluginjiraworklog.listeners

import com.github.artusm.jetbrainspluginjiraworklog.config.JiraSettings
import com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogTimerService
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.IdeFrame

/**
 * Listens for IDE window activation and deactivation events to manage timer auto-pause/resume.
 * Handles three scenarios:
 * 1. Window Focus Loss: Pauses all running timers (if configured).
 * 2. Window Focus Gain: Resumes timer for the newly active project (if configured).
 * 3. Project Switch: Pauses the timer of the previously active project (if configured).
 */
class WindowFocusListener : ApplicationActivationListener {

    private var lastActiveProjectRef: java.lang.ref.WeakReference<Project>? = null

    override fun applicationDeactivated(ideFrame: IdeFrame) {
        val settings = JiraSettings.getInstance()
        if (settings.isPauseOnFocusLoss()) {
            pauseAllTimers()
        }
    }

    override fun applicationActivated(ideFrame: IdeFrame) {
        val currentProject = ideFrame.project ?: return
        val settings = JiraSettings.getInstance()

        handleProjectSwitch(currentProject, settings)
        handleFocusGain(currentProject, settings)

        lastActiveProjectRef = java.lang.ref.WeakReference(currentProject)
    }

    private fun pauseAllTimers() {
        ProjectManager.getInstance().openProjects.forEach { project ->
            if (!project.isDisposed) {
                project.service<JiraWorklogTimerService>().autoPauseByFocus()
            }
        }
    }

    private fun handleProjectSwitch(currentProject: Project, settings: JiraSettings) {
        if (!settings.isPauseOnProjectSwitch()) return

        val previousProject = lastActiveProjectRef?.get()
        if (previousProject != null && previousProject != currentProject && !previousProject.isDisposed) {
            previousProject.service<JiraWorklogTimerService>().autoPauseByProjectSwitch()
        }
    }

    private fun handleFocusGain(currentProject: Project, settings: JiraSettings) {
        if (settings.isPauseOnFocusLoss() && !currentProject.isDisposed) {
            currentProject.service<JiraWorklogTimerService>().autoResumeFromFocus()
        }
    }
}
