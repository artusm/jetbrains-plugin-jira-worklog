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
    
    /**
     * Pauses Jira worklog timers for all open projects when the IDE window loses focus and the corresponding setting is enabled.
     *
     * @param ideFrame The IDE frame that was deactivated. 
     */
    override fun applicationDeactivated(ideFrame: IdeFrame) {
        val settings = JiraSettings.getInstance()
        
        // Handle window focus loss auto-pause
        if (settings.isPauseOnFocusLoss()) {
            forEachOpenProject { timerService ->
                timerService.autoPauseByFocus()
            }
        }
    }
    
    /**
     * Reacts to IDE window activation by pausing or resuming Jira worklog timers according to user settings.
     *
     * If "pause on project switch" is enabled and the activated frame has a project, pauses the timer for the previously tracked project when it differs from the current one and is not disposed, then updates the tracked last active project. If "pause on focus loss" is enabled, resumes timers for all open projects.
     *
     * @param ideFrame The activated IDE frame (may contain the currently focused project).
     */
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
            forEachOpenProject { timerService ->
                timerService.autoResumeFromFocus()
            }
        }
    }
    
    /**
     * Clears internal last-active project tracking if the specified project was the last active one.
     *
     * @param project The project that was closed.
     */
    override fun projectClosed(project: Project) {
        // Clear tracking if the closed project was the last active one
        if (lastActiveProject == project) {
            lastActiveProject = null
        }
    }
    
    /**
     * Execute the given action for each open project's JiraWorklogTimerService.
     *
     * @param action Function invoked with the project's JiraWorklogTimerService for every open, non-disposed project that provides the service; projects without the service are skipped.
     */
    private fun forEachOpenProject(action: (JiraWorklogTimerService) -> Unit) {
        val projectManager = ProjectManager.getInstance()
        projectManager.openProjects.forEach { project ->
            if (!project.isDisposed) {
                val timerService = project.getService(JiraWorklogTimerService::class.java)
                timerService?.let(action)
            }
        }
    }
}