package com.github.artusm.jetbrainspluginjiraworklog.listeners

import com.github.artusm.jetbrainspluginjiraworklog.config.JiraSettings
import com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogTimerService
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.IdeFrame

/**
 * Listens for IDE window activation/deactivation and auto-pauses/resumes timers.
 */
class WindowFocusListener : ApplicationActivationListener {
    
    override fun applicationDeactivated(ideFrame: IdeFrame) {
        val settings = JiraSettings.getInstance()
        
        // Only auto-pause if setting is enabled
        if (!settings.isPauseOnFocusLoss()) {
            return
        }
        
        // Pause timers in all open projects
        val projectManager = ProjectManager.getInstance()
        projectManager.openProjects.forEach { project ->
            if (!project.isDisposed) {
                val timerService = project.getService(JiraWorklogTimerService::class.java)
                timerService?.autoPauseByFocus()
            }
        }
    }
    
    override fun applicationActivated(ideFrame: IdeFrame) {
        val settings = JiraSettings.getInstance()
        
        // Only auto-resume if setting is enabled
        if (!settings.isPauseOnFocusLoss()) {
            return
        }
        
        // Resume timers that were auto-paused in all open projects
        val projectManager = ProjectManager.getInstance()
        projectManager.openProjects.forEach { project ->
            if (!project.isDisposed) {
                val timerService = project.getService(JiraWorklogTimerService::class.java)
                timerService?.autoResumeFromFocus()
            }
        }
    }
}
