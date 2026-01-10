package com.github.artusm.jetbrainspluginjiraworklog.git

import com.github.artusm.jetbrainspluginjiraworklog.config.JiraSettings
import com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogPersistentState
import com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogTimerService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import org.jetbrains.annotations.NotNull
import java.util.concurrent.atomic.AtomicInteger

/**
 * Listens for Git repository changes (including branch switches) and
 * triggers auto-pause if enabled in settings.
 */
class BranchChangeListener : GitRepositoryChangeListener {

    companion object {
        private const val CLEANUP_INTERVAL = 10
    }

    private var lastBranchName: String? = null

    override fun repositoryChanged(@NotNull repository: GitRepository) {
        val project: Project = repository.project
        if (project.isDisposed) {
            return
        }

        val branchName = getBranchName(repository)
        
        // Check if branch actually changed
        if (lastBranchName != null && lastBranchName != branchName) {
            onBranchChanged(project, repository)
        }
        
        lastBranchName = branchName
    }

    private fun onBranchChanged(project: Project, repository: GitRepository) {
        val settings = JiraSettings.getInstance()
        val persistentState = project.service<JiraWorklogPersistentState>()
        
        // Get the NEW branch name (not the old one!)
        val newBranchName = getBranchName(repository)
        
        // Auto-pause timer if enabled in settings
        if (settings.isPauseOnBranchChange()) {
            val timerService = project.service<JiraWorklogTimerService>()
            timerService.pause()
        }
        
        // Restore saved ticket for current branch, or use fallback
        newBranchName?.let { branch ->
            val savedIssue = persistentState.getIssueForBranch(branch)
            
            if (savedIssue != null) {
                // Branch has saved ticket - use it
                persistentState.setLastIssueKey(savedIssue)
            } else {
                // No saved ticket - use fallback from last issue
                persistentState.getLastIssueKey()?.let { lastIssue ->
                    // Save the fallback issue for this new branch
                    persistentState.saveIssueForBranch(branch, lastIssue)
                }
            }
        }
        
        // Periodically clean up deleted branches (every 10th branch change)
        if (shouldCleanupDeletedBranches()) {
            cleanupDeletedBranches(persistentState, project, repository)
        }
    }
    
    private val branchChangeCount = AtomicInteger(0)
    
    private fun shouldCleanupDeletedBranches(): Boolean {
        return branchChangeCount.incrementAndGet() % CLEANUP_INTERVAL == 0
    }
    
    private fun cleanupDeletedBranches(
        persistentState: JiraWorklogPersistentState,
        project: Project,
        repository: GitRepository
    ) {
        val activeBranches = git4idea.repo.GitRepositoryManager.getInstance(project).repositories
            .flatMap { it.branches.localBranches + it.branches.remoteBranches }
            .map { it.name }
            .toSet()

        persistentState.cleanupDeletedBranches(activeBranches)
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