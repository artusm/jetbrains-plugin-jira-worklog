package com.github.artusm.jetbrainspluginjiraworklog.git

import com.github.artusm.jetbrainspluginjiraworklog.config.JiraSettings
import com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogPersistentState
import com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogTimerService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import org.jetbrains.annotations.NotNull

/**
 * Listens for Git repository changes (including branch switches) and
 * triggers auto-pause if enabled in settings.
 */
class BranchChangeListener : GitRepositoryChangeListener {

    private var lastBranchName: String? = null

    override fun repositoryChanged(@NotNull repository: GitRepository) {
        val project: Project = repository.project
        if (project.isDisposed) {
            return
        }

        val branchName = getBranchName(repository)
        
        // Check if branch actually changed
        if (lastBranchName != null && lastBranchName != branchName) {
            onBranchChanged(project)
        }
        
        lastBranchName = branchName
    }

    private fun onBranchChanged(project: Project) {
        val settings = JiraSettings.getInstance()
        val persistentState = project.service<JiraWorklogPersistentState>()
        val currentBranch = lastBranchName
        
        // Auto-pause timer if enabled in settings
        if (settings.isPauseOnBranchChange()) {
            val timerService = project.service<JiraWorklogTimerService>()
            timerService.pause()
        }
        
        // Restore saved ticket for current branch, or use fallback
        if (currentBranch != null) {
            val savedIssue = persistentState.getIssueForBranch(currentBranch)
            
            if (savedIssue != null) {
                // Branch has saved ticket - use it
                persistentState.setLastIssueKey(savedIssue)
            } else {
                // No saved ticket - use fallback from last issue
                val lastIssue = persistentState.getLastIssueKey()
                if (lastIssue != null) {
                    // Save the fallback issue for this new branch
                    persistentState.saveIssueForBranch(currentBranch, lastIssue)
                }
            }
        }
    }

    private fun getBranchName(repo: GitRepository): String? {
        val currentBranch = repo.currentBranch
        if (currentBranch != null) {
            return currentBranch.name
        }
        
        // Detached HEAD state - show short hash
        val revision = repo.currentRevision
        if (revision != null && revision.length > 7) {
            return "detached:${revision.substring(0, 7)}"
        }
        return "detached"
    }
}