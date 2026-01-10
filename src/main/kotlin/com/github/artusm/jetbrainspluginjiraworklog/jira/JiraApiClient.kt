package com.github.artusm.jetbrainspluginjiraworklog.jira

import com.github.artusm.jetbrainspluginjiraworklog.config.JiraConfig
import com.github.artusm.jetbrainspluginjiraworklog.config.JiraSettings
import com.github.artusm.jetbrainspluginjiraworklog.utils.MyBundle
import com.github.artusm.jetbrainspluginjiraworklog.utils.TimeFormatter
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * HTTP client for Jira REST API v2.
 * Handles authentication and API requests.
 */
class JiraApiClient(private val settings: JiraConfig) : JiraApi {
    
    companion object {
        private val LOG = Logger.getInstance(JiraApiClient::class.java)
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
    
    /**
     * Search for issues assigned to the current user.
     * 
     * @param jql Optional JQL query. Default is "assignee=currentuser()"
     * @param maxResults Maximum number of results to return
     * @return Search result with matching issues
     */
    override suspend fun searchAssignedIssues(
        jql: String,
        maxResults: Int
    ): Result<JiraSearchResult> = withContext(Dispatchers.IO) {
        val baseUrl = settings.getJiraUrl()
        if (baseUrl.isBlank()) {
            return@withContext Result.failure(IllegalStateException(MyBundle.message("api.error.url.not.configured")))
        }
        
        val token = settings.getPersonalAccessToken()
        if (token.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException(MyBundle.message("api.error.token.not.configured")))
        }
        
        val encodedJql = java.net.URLEncoder.encode(jql, "UTF-8")
        val url = "$baseUrl/rest/api/2/search?jql=$encodedJql&maxResults=$maxResults&fields=key,summary,issuetype"
        
        try {
            val response = executeGet(url, token)
            val result = json.decodeFromString<JiraSearchResult>(response)
            Result.success(result)
        } catch (e: Exception) {
            LOG.error("Failed to search issues", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get issue details including subtasks.
     * 
     * @param issueKey The issue key (e.g., "PROJ-123")
     * @return Issue details with subtasks
     */
    override suspend fun getIssueWithSubtasks(issueKey: String): Result<JiraIssue> = withContext(Dispatchers.IO) {
        val baseUrl = settings.getJiraUrl()
        if (baseUrl.isBlank()) {
            return@withContext Result.failure(IllegalStateException(MyBundle.message("api.error.url.not.configured")))
        }
        
        val token = settings.getPersonalAccessToken()
        if (token.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException(MyBundle.message("api.error.token.not.configured")))
        }
        
        val url = "$baseUrl/rest/api/2/issue/$issueKey?fields=key,summary,issuetype,subtasks"
        
        try {
            val response = executeGet(url, token)
            val issue = json.decodeFromString<JiraIssue>(response)
            Result.success(issue)
        } catch (e: Exception) {
            LOG.error("Failed to get issue details for $issueKey", e)
            Result.failure(e)
        }
    }
    
    /**
     * Submit a worklog to a Jira issue.
     * 
     * @param issueKey The issue key (e.g., "PROJ-123")
     * @param timeSpentSeconds Time spent in seconds
     * @param comment Optional worklog comment
     * @return Worklog response if successful
     */
    override suspend fun submitWorklog(
        issueKey: String,
        timeSpentSeconds: Int,
        comment: String?
    ): Result<JiraWorklogResponse> = withContext(Dispatchers.IO) {
        val baseUrl = settings.getJiraUrl()
        if (baseUrl.isBlank()) {
            return@withContext Result.failure(IllegalStateException(MyBundle.message("api.error.url.not.configured")))
        }
        
        val token = settings.getPersonalAccessToken()
        if (token.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException(MyBundle.message("api.error.token.not.configured")))
        }
        
        // Convert seconds to Jira time format
        val timeSpent = TimeFormatter.formatJira(timeSpentSeconds * 1000L)
        
        // Use current time as worklog start time
        val started = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        
        val request = JiraWorklogRequest(
            timeSpent = timeSpent,
            comment = comment,
            started = started
        )
        
        val url = "$baseUrl/rest/api/2/issue/$issueKey/worklog"
        val requestBody = json.encodeToString(request)
        
        try {
            val response = executePost(url, token, requestBody)
            val worklogResponse = json.decodeFromString<JiraWorklogResponse>(response)
            Result.success(worklogResponse)
        } catch (e: Exception) {
            LOG.error("Failed to submit worklog to $issueKey", e)
            Result.failure(e)
        }
    }
    
    /**
     * Test the connection to Jira with current credentials.
     * 
     * @return true if connection is successful
     */
    override suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        val baseUrl = settings.getJiraUrl()
        if (baseUrl.isBlank()) {
            return@withContext Result.failure(IllegalStateException(MyBundle.message("api.error.url.not.configured")))
        }
        
        val token = settings.getPersonalAccessToken()
        if (token.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException(MyBundle.message("api.error.token.not.configured")))
        }
        
        val url = "$baseUrl/rest/api/2/myself"
        
        try {
            executeGet(url, token)
            Result.success(true)
        } catch (e: Exception) {
            LOG.error("Failed to test Jira connection", e)
            Result.failure(e)
        }
    }
    
    private fun executeGet(url: String, token: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw IOException("HTTP $responseCode: $errorStream")
            }
        } finally {
            connection.disconnect()
        }
    }
    
    private fun executePost(url: String, token: String, requestBody: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            // Write request body
            connection.outputStream.use { os ->
                val input = requestBody.toByteArray(StandardCharsets.UTF_8)
                os.write(input, 0, input.size)
            }
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK) {
                return connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw IOException("HTTP $responseCode: $errorStream")
            }
        } finally {
            connection.disconnect()
        }
    }
}
