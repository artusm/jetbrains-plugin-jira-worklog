package com.github.artusm.jetbrainspluginjiraworklog.jira

interface JiraApi {
    suspend fun searchAssignedIssues(jql: String = "assignee=currentuser()", maxResults: Int = 50): Result<JiraSearchResult>
    suspend fun getIssueWithSubtasks(issueKey: String): Result<JiraIssue>
    suspend fun submitWorklog(issueKey: String, timeSpentSeconds: Int, comment: String? = null): Result<JiraWorklogResponse>
    suspend fun testConnection(): Result<Boolean>
}
