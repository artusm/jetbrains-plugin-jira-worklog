package com.github.artusm.jetbrainspluginjiraworklog.ui

import com.github.artusm.jetbrainspluginjiraworklog.data.JiraWorklogRepository
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraIssue
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraSearchResult
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraWorklogResponse
import com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogTimerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CommitWorklogViewModelTest {

    private lateinit var viewModel: CommitWorklogViewModel
    // private lateinit var repository: JiraWorklogRepository // Not strictly needed if only used in setup
    private lateinit var fakeTimerService: FakeTimerService

    @Before
    fun setup() {
        fakeTimerService = FakeTimerService()
        
        val fakeApi = FakeJiraApi()
        // Pre-fill fake repository state via the persistent state
        FakeTimerService.fakeState.saveIssueForBranch("feature/test", "JIRA-1")

        // Inject fakeState into repository for robust testing
        // Inject fakeState into repository for robust testing via subclass
        val realRepository = object : JiraWorklogRepository(nullAs()) {
             override val api = fakeApi
             override val persistentState = FakeTimerService.fakeState
        }
        
        viewModel = CommitWorklogViewModel(
            realRepository,
            fakeTimerService,
            { "feature/test" }
        )
    }

    @Test
    fun testInitialTimeLoad() {
        assertEquals(5000L, viewModel.uiState.value.timeSpentMs)
    }
    
    @Test
    fun testTimeAdjustment() {
        viewModel.adjustTime(1000)
        assertEquals(6000L, viewModel.uiState.value.timeSpentMs)
        
        viewModel.adjustTime(-2000)
        assertEquals(4000L, viewModel.uiState.value.timeSpentMs)
    }

    // Fakes
    // We suppress "CAST_NEVER_SUCCEEDS" etc by passing null as any
    // Fake API implementation for testing main logic
    class FakeJiraApi : com.github.artusm.jetbrainspluginjiraworklog.jira.JiraApi {
        private val taskType = com.github.artusm.jetbrainspluginjiraworklog.jira.JiraIssueType("Task", false)
        private val bugType = com.github.artusm.jetbrainspluginjiraworklog.jira.JiraIssueType("Bug", false)
        
        var issuesToReturn = listOf(
            JiraIssue("JIRA-1", com.github.artusm.jetbrainspluginjiraworklog.jira.JiraIssueFields("Task 1", taskType)),
            JiraIssue("JIRA-2", com.github.artusm.jetbrainspluginjiraworklog.jira.JiraIssueFields("Task 2", bugType))
        )

        override suspend fun searchAssignedIssues(jql: String, maxResults: Int): Result<JiraSearchResult> {
            return Result.success(JiraSearchResult(issuesToReturn, issuesToReturn.size))
        }

        override suspend fun getIssueWithSubtasks(issueKey: String): Result<JiraIssue> {
           return Result.success(issuesToReturn.firstOrNull { it.key == issueKey } ?: issuesToReturn.first())
        }

        override suspend fun submitWorklog(issueKey: String, timeSpentSeconds: Int, comment: String?): Result<JiraWorklogResponse> {
            return Result.success(JiraWorklogResponse("100", issueKey, "${timeSpentSeconds / 3600}h"))
        }

        override suspend fun testConnection(): Result<Boolean> {
            return Result.success(true)
        }
    }

    class FakeTimerService : JiraWorklogTimerService(nullAs(), kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)) {
        
        companion object {
            // Static instance to ensure availability during super init
            val fakeState = com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogPersistentState().apply {
                setStatus(com.github.artusm.jetbrainspluginjiraworklog.model.TimeTrackingStatus.STOPPED)
                setTotalTimeMs(5000L)
            }
        }

        // Use custom getter to avoid initialization order issues with backing fields
        override val persistentState: com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogPersistentState
            get() = fakeState

        override fun getTotalTimeMs(): Long = 5000L
        override fun reset() {}
    }
}

// Helper to bypass non-null check for test
@Suppress("UNCHECKED_CAST")
private fun <T> nullAs(): T = null as T
