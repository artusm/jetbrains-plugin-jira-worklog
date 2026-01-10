package com.github.artusm.jetbrainspluginjiraworklog.listeners

import com.github.artusm.jetbrainspluginjiraworklog.config.JiraSettings
import com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogTimerService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

/**
 * Listens for project switches and auto-pauses the timer in the previously active project.
 */
class ProjectSwitchListener : ProjectManagerListener {
    
    private var lastActiveProject: Project? = null
    
    override fun projectOpened(project: Project) {
        // Track newly opened project
        handleProjectSwitch(project)
    }
    
    override fun projectClosed(project: Project) {
        // Clear tracking if this was the last active project
        if (lastActiveProject == project) {
            lastActiveProject = null
        }
    }
    
    /**
     * Called when a different project becomes active.
     * Note: IntelliJ doesn't have a direct "project activated" event,
     * so we use frame activation as a proxy.
     */
    private fun handleProjectSwitch(newProject: Project) {
        val settings = JiraSettings.getInstance()
        
        // Only auto-pause if setting is enabled
        if (!settings.isPauseOnProjectSwitch()) {
            return
        }
        
        // If there was a previous project and it's different, pause its timer
        val previousProject = lastActiveProject
        if (previousProject != null && 
            previousProject != newProject && 
            !previousProject.isDisposed) {
            
            val timerService = previousProject.getService(JiraWorklogTimerService::class.java)
            timerService?.autoPauseByProjectSwitch()
        }
        
        lastActiveProject = newProject
    }
}
