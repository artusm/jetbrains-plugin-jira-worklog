package com.github.artusm.jetbrainspluginjiraworklog.jira

import com.github.artusm.jetbrainspluginjiraworklog.config.JiraConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

class JiraApiClientTest {

    private lateinit var jiraApiClient: JiraApiClient
    private lateinit var mockConfig: JiraConfig
    private lateinit var mockHttpClient: HttpClient

    @Before
    fun setUp() {
        mockConfig = mockk()
        mockHttpClient = mockk()
        jiraApiClient = JiraApiClient(mockConfig, mockHttpClient)
    }

    @Test
    fun `test searchAssignedIssues success`() = runBlocking {
        every { mockConfig.getJiraUrl() } returns "https://jira.example.com"
        every { mockConfig.getPersonalAccessToken() } returns "token"

        val jsonResponse = """
            {
                "issues": [
                    {
                        "key": "PROJ-1",
                        "fields": {
                            "summary": "Test Issue",
                            "issuetype": {
                                "name": "Task",
                                "subtask": false
                            }
                        }
                    }
                ],
                "total": 1
            }
        """.trimIndent()

        every { mockHttpClient.executeGet(any(), any()) } returns jsonResponse

        val result = jiraApiClient.searchAssignedIssues("assignee=currentUser()", 50)

        assertTrue(result.isSuccess)
        val searchResult = result.getOrNull()
        assertNotNull(searchResult)
        assertEquals(1, searchResult?.issues?.size)
        assertEquals("PROJ-1", searchResult?.issues?.first()?.key)

        verify { mockHttpClient.executeGet(any(), "token") }
    }

    @Test
    fun `test searchAssignedIssues failure when url missing`() = runBlocking {
        every { mockConfig.getJiraUrl() } returns ""

        val result = jiraApiClient.searchAssignedIssues("query", 10)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `test getIssueWithSubtasks success`() = runBlocking {
        every { mockConfig.getJiraUrl() } returns "https://jira.example.com"
        every { mockConfig.getPersonalAccessToken() } returns "token"

        val jsonResponse = """
            {
                "key": "PROJ-1",
                "fields": {
                    "summary": "Parent Issue",
                    "issuetype": { "name": "Story", "subtask": false },
                    "subtasks": [
                        {
                            "key": "PROJ-2",
                            "fields": {
                                "summary": "Subtask 1",
                                "issuetype": { "name": "Sub-task", "subtask": true }
                            }
                        }
                    ]
                }
            }
        """.trimIndent()

        every { mockHttpClient.executeGet(any(), any()) } returns jsonResponse

        val result = jiraApiClient.getIssueWithSubtasks("PROJ-1")

        assertTrue(result.isSuccess)
        val issue = result.getOrNull()
        assertEquals("PROJ-1", issue?.key)
        assertEquals(1, issue?.fields?.subtasks?.size)
        assertEquals("PROJ-2", issue?.fields?.subtasks?.first()?.key)
    }

    @Test
    fun `test submitWorklog success`() = runBlocking {
        every { mockConfig.getJiraUrl() } returns "https://jira.example.com"
        every { mockConfig.getPersonalAccessToken() } returns "token"

        val jsonResponse = """
            {
                "self": "https://jira.example.com/rest/api/2/issue/10000/worklog/10000",
                "author": { "name": "user" },
                "updateAuthor": { "name": "user" }
            }
        """.trimIndent()

        every { mockHttpClient.executePost(any(), any(), any()) } returns jsonResponse

        val result = jiraApiClient.submitWorklog("PROJ-1", 3600, "Done work")

        assertTrue(result.isSuccess)
        verify { mockHttpClient.executePost(any(), "token", any()) }
    }

    @Test
    fun `test testConnection success`() = runBlocking {
        every { mockConfig.getJiraUrl() } returns "https://jira.example.com"
        every { mockConfig.getPersonalAccessToken() } returns "token"
        every { mockHttpClient.executeGet(any(), any()) } returns "{}"

        val result = jiraApiClient.testConnection()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrDefault(false))
    }

    @Test
    fun `test testConnection failure`() = runBlocking {
        every { mockConfig.getJiraUrl() } returns "https://jira.example.com"
        every { mockConfig.getPersonalAccessToken() } returns "token"
        every { mockHttpClient.executeGet(any(), any()) } throws IOException("Unauthorized")

        val result = jiraApiClient.testConnection()

        assertTrue(result.isFailure)
    }
}
