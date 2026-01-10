package com.github.artusm.jetbrainspluginjiraworklog.listeners

import com.github.artusm.jetbrainspluginjiraworklog.config.JiraSettings
import com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogTimerService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.wm.IdeFrame

/**
 * Listens for IDE window activation/deactivation and project switches.
 * Handles auto-pause for both window focus loss and project switching.
 */
class WindowFocusListener : ApplicationActivationListener, ProjectManagerListener {
    
    private var lastActiveProject: Project? = null
    
    override fun applicationDeactivated(ideFrame: IdeFrame) {
        val settings = JiraSettings.getInstance()
        
        // Handle window focus loss auto-pause
        if (settings.isPauseOnFocusLoss()) {
            val projectManager = ProjectManager.getInstance()
            projectManager.openProjects.forEach { project ->
                if (!project.isDisposed) {
                    val timerService = project.getService(JiraWorklogTimerService::class.java)
                    timerService?.autoPauseByFocus()
                }
            }
        }
    }
    
    override fun applicationActivated(ideFrame: IdeFrame) {
        val settings = JiraSettings.getInstance()
        val currentProject = ideFrame.project
        
        // Handle project switch auto-pause
        if (settings.isPauseOnProjectSwitch() && currentProject != null) {
            val previousProject = lastActiveProject
            
            // If switching to a different project, pause the previous one
            if (previousProject != null && 
                previousProject != currentProject && 
                !previousProject.isDisposed) {
                
                val timerService = previousProject.getService(JiraWorklogTimerService::class.java)
                timerService?.autoPauseByProjectSwitch()
            }
            
            lastActiveProject = currentProject
        }
        
        // Handle window focus gain auto-resume
        if (settings.isPauseOnFocusLoss()) {
            val projectManager = ProjectManager.getInstance()
            projectManager.openProjects.forEach { project ->
                if (!project.isDisposed) {
                    val timerService = project.getService(JiraWorklogTimerService::class.java)
                    timerService?.autoResumeFromFocus()
                }
            }
        }
    }
    
    override fun projectClosed(project: Project) {
        // Clear tracking if the closed project was the last active one
        if (lastActiveProject == project) {
            lastActiveProject = null
        }
    }
}
