package com.github.artusm.jetbrainspluginjiraworklog.git

import com.github.artusm.jetbrainspluginjiraworklog.config.JiraSettings
import com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogPersistentState
import com.github.artusm.jetbrainspluginjiraworklog.data.JiraWorklogRepository
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

    private val lastBranchByRepo = java.util.concurrent.ConcurrentHashMap<String, String>()

    override fun repositoryChanged(@NotNull repository: GitRepository) {
        val project: Project = repository.project
        if (project.isDisposed) {
            return
        }

        val branchName = GitUtils.getBranchNameOrRev(repository)
        val repoPath = repository.root.path
        val lastBranchName = lastBranchByRepo[repoPath]
        
        // Check if branch actually changed
        if (lastBranchName != null && lastBranchName != branchName) {
            onBranchChanged(project, repository, branchName)
        }
        
        lastBranchByRepo[repoPath] = branchName
    }

    private fun onBranchChanged(project: Project, repository: GitRepository, newBranchName: String?) {
        val settings = JiraSettings.getInstance()
        
        // Auto-pause timer if enabled in settings
        if (settings.isPauseOnBranchChange()) {
            val timerService = project.service<JiraWorklogTimerService>()
            timerService.pause()
        }
        
        // Restore saved ticket for current branch, or use fallback
        // We use the Repository to handle this logic centrally
        newBranchName?.let { branch ->
            val worklogRepo = project.service<JiraWorklogRepository>()
            
            // This will look up the saved issue for the branch, or fallback to global last used
            val issueKeysToRestore = worklogRepo.getSavedIssueKey(branch)
            
            if (issueKeysToRestore != null) {
                // Ensure the global "last used" is updated to match the branch's selection
                worklogRepo.saveSelectedIssue(issueKeysToRestore, branch)
            }
        }
        
        // Periodically clean up deleted branches (every 10th branch change)
        if (shouldCleanupDeletedBranches()) {
            cleanupDeletedBranches(project)
        }
    }
    
    private val branchChangeCount = AtomicInteger(0)
    
    private fun shouldCleanupDeletedBranches(): Boolean {
        return branchChangeCount.incrementAndGet() % CLEANUP_INTERVAL == 0
    }
    
    private fun cleanupDeletedBranches(project: Project) {
        val persistentState = project.service<JiraWorklogPersistentState>()
        val activeBranches = git4idea.repo.GitRepositoryManager.getInstance(project).repositories
            .flatMap { it.branches.localBranches + it.branches.remoteBranches }
            .map { it.name }
            .toSet()

        persistentState.cleanupDeletedBranches(activeBranches)
    }
}