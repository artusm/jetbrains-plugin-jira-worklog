package com.github.artusm.jetbrainspluginjiraworklog.data

import com.github.artusm.jetbrainspluginjiraworklog.config.JiraSettings
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraApi
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraApiClient
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraIssue
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraSearchResult
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraWorklogResponse
import com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogPersistentState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Repository for managing Jira worklog data and issue mappings.
 * Acts as a single source of truth for the UI and other services.
 */
@Service(Service.Level.PROJECT)
open class JiraWorklogRepository(private val project: Project?) {

    protected open val settings: JiraSettings get() = JiraSettings.getInstance()
    
    // In a real DI system we'd inject this, but for now we instantiate it.
    // We could also make JiraApiClient a service.
    protected open val api: JiraApi by lazy { JiraApiClient(settings) }
    
    protected open val persistentState: JiraWorklogPersistentState get() = project!!.service()

    /**
     * Search for issues assigned to the current user.
     */
    open suspend fun getAssignedIssues(): Result<JiraSearchResult> {
        return api.searchAssignedIssues()
    }

    /**
     * Get details for a specific issue.
     */
    suspend fun getIssue(issueKey: String): Result<JiraIssue> {
        return api.getIssueWithSubtasks(issueKey)
    }

    /**
     * Submit a worklog entry.
     */
    open suspend fun submitWorklog(
        issueKey: String, 
        timeSpentSeconds: Int, 
        comment: String?
    ): Result<JiraWorklogResponse> {
        return api.submitWorklog(issueKey, timeSpentSeconds, comment)
    }

    /**
     * Save the selected issue key for a specific branch (and globally as fallback).
     */
    open fun saveSelectedIssue(issueKey: String, branchName: String?) {
        // Always update global fallback
        persistentState.setLastIssueKey(issueKey)
        
        // Update per-branch if applicable
        if (branchName != null) {
            persistentState.saveIssueForBranch(branchName, issueKey)
        }
    }

    /**
     * Get the saved issue key for a branch, falling back to the last used global key.
     */
    open fun getSavedIssueKey(branchName: String?): String? {
        val branchKey = branchName?.let { persistentState.getIssueForBranch(it) }
        return branchKey ?: persistentState.getLastIssueKey()
    }
}
