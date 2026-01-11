package com.github.artusm.jetbrainspluginjiraworklog.data

import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraIssue
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraSearchResult
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraWorklogResponse

interface WorklogRepository {
    suspend fun getAssignedIssues(): Result<JiraSearchResult>
    suspend fun getIssue(issueKey: String): Result<JiraIssue>
    suspend fun submitWorklog(
        issueKey: String,
        timeSpentSeconds: Int,
        comment: String?
    ): Result<JiraWorklogResponse>

    fun saveSelectedIssue(issueKey: String, branchName: String?)
    fun getSavedIssueKey(branchName: String?): String?
}
