package com.github.artusm.jetbrainspluginjiraworklog.git

import git4idea.repo.GitRepository

object GitUtils {
    /**
     * Gets the branch name or a descriptive string for detached HEAD state.
     * Consistent with BranchChangeListener logic.
     */
    fun getBranchNameOrRev(repo: GitRepository): String {
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
