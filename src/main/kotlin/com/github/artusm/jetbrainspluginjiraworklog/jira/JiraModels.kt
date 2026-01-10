package com.github.artusm.jetbrainspluginjiraworklog.jira

import kotlinx.serialization.Serializable

/**
 * Data classes for Jira API responses and requests.
 */

@Serializable
data class JiraIssue(
    val key: String,
    val fields: JiraIssueFields
) {
    val summary: String
        get() = fields.summary
    
    val isSubtask: Boolean
        get() = fields.issuetype.subtask
}

@Serializable
data class JiraIssueFields(
    val summary: String,
    val issuetype: JiraIssueType,
    val subtasks: List<JiraIssue>? = null
)

@Serializable
data class JiraIssueType(
    val name: String,
    val subtask: Boolean = false
)

@Serializable
data class JiraSearchResult(
    val issues: List<JiraIssue>,
    val total: Int
)

@Serializable
data class JiraWorklogRequest(
    val timeSpent: String, // Format: "2h 30m"
    val comment: String? = null,
    val started: String? = null // ISO 8601 format
)

@Serializable
data class JiraWorklogResponse(
    val id: String,
    val issueId: String,
    val timeSpent: String
)
