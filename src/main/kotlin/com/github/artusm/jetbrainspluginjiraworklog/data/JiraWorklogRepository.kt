package com.github.artusm.jetbrainspluginjiraworklog.data

import com.github.artusm.jetbrainspluginjiraworklog.config.JiraSettings
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraApi
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraApiClient
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraIssue
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraSearchResult
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraWorklogResponse
import com.github.artusm.jetbrainspluginjiraworklog.services.JiraOfflineWorklogService
import com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogPersistentState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Service(Service.Level.PROJECT)
class JiraWorklogRepository(
    private val project: Project
) : WorklogRepository {

    private val settings: JiraSettings get() = JiraSettings.getInstance()

    // Use injected API if present, otherwise create new client
    private val api: JiraApi by lazy { JiraApiClient(settings) }

    private val persistentState: JiraWorklogPersistentState
        get() = project.service()

    private val offlineService: JiraOfflineWorklogService
        get() = project.service()

    /**
     * Search for issues assigned to the current user.
     */
    override suspend fun getAssignedIssues(): Result<JiraSearchResult> {
        return api.searchAssignedIssues()
    }

    /**
     * Get details for a specific issue.
     */
    override suspend fun getIssue(issueKey: String): Result<JiraIssue> {
        return api.getIssueWithSubtasks(issueKey)
    }

    /**
     * Submit a worklog entry.
     */
    override suspend fun submitWorklog(
        issueKey: String,
        timeSpentSeconds: Int,
        comment: String?
    ): Result<JiraWorklogResponse> {
        // Use offline service to handle network errors
        val started = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        // JiraOfflineWorklogService.submitWorklog returns Result<JiraWorklogResponse?>
        // We need to map it to Result<JiraWorklogResponse> and handle nulls if necessary
        // Actually, if it returns success with null, it might be the queued state?
        // Let's check JiraOfflineWorklogService signature.
        // It returns Result<JiraWorklogResponse?>.

        val result = offlineService.submitWorklog(issueKey, timeSpentSeconds, comment, started)

        return result.map { response ->
            response ?: JiraWorklogResponse("0", "0", "0") // Dummy response for queued items
        }
    }

    /**
     * Save the selected issue key for a specific branch (and globally as fallback).
     */
    override fun saveSelectedIssue(issueKey: String, branchName: String?) {
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
    override fun getSavedIssueKey(branchName: String?): String? {
        val branchKey = branchName?.let { persistentState.getIssueForBranch(it) }
        return branchKey ?: persistentState.getLastIssueKey()
    }
}
