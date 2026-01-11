package com.github.artusm.jetbrainspluginjiraworklog.ui

import com.github.artusm.jetbrainspluginjiraworklog.data.WorklogRepository
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraIssue
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraSearchResult
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraWorklogResponse
import com.github.artusm.jetbrainspluginjiraworklog.model.TimeTrackingStatus
import com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogPersistentState
import com.github.artusm.jetbrainspluginjiraworklog.services.TimerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CommitWorklogViewModelTest {

    private lateinit var viewModel: CommitWorklogViewModel
    private lateinit var fakeTimerService: FakeTimerService

    @Before
    fun setup() {
        // Create instance-based state for isolation between tests
        val state = JiraWorklogPersistentState().apply {
            setStatus(TimeTrackingStatus.STOPPED)
            setTotalTimeMs(5000L)
        }
        
        fakeTimerService = FakeTimerService(state)
        
        val fakeApi = FakeJiraApi()
        
        // Use instance state for setup
        state.saveIssueForBranch("feature/test", "JIRA-1")
        state.setLastIssueKey("JIRA-1")

        // Create fake repository based on interface
        val realRepository = object : WorklogRepository {
             private val api = fakeApi
             private val persistentState = state
             
             override suspend fun getAssignedIssues(): Result<JiraSearchResult> = api.searchAssignedIssues()
             override suspend fun getIssue(issueKey: String): Result<JiraIssue> = api.getIssueWithSubtasks(issueKey)
             override suspend fun submitWorklog(issueKey: String, timeSpentSeconds: Int, comment: String?): Result<JiraWorklogResponse> =
                 api.submitWorklog(issueKey, timeSpentSeconds, comment)
                 
             override fun saveSelectedIssue(issueKey: String, branchName: String?) {
                 persistentState.setLastIssueKey(issueKey)
                 if (branchName != null) persistentState.saveIssueForBranch(branchName, issueKey)
             }
             
             override fun getSavedIssueKey(branchName: String?): String? {
                 return branchName?.let { persistentState.getIssueForBranch(it) } ?: persistentState.getLastIssueKey()
             }
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
            val issue = issuesToReturn.firstOrNull { it.key == issueKey }
            return if (issue != null) {
                Result.success(issue)
            } else {
                Result.failure(NoSuchElementException("Issue not found: $issueKey"))
            }
        }

        override suspend fun submitWorklog(issueKey: String, timeSpentSeconds: Int, comment: String?): Result<JiraWorklogResponse> {
            return Result.success(JiraWorklogResponse("100", issueKey, "${timeSpentSeconds / 3600}h"))
        }

        override suspend fun testConnection(): Result<Boolean> {
            return Result.success(true)
        }
    }

    class FakeTimerService(private val state: JiraWorklogPersistentState) : TimerService {
        
        private val _timeFlow = MutableStateFlow(state.getTotalTimeMs())
        override val timeFlow: StateFlow<Long> = _timeFlow.asStateFlow()
        
        private val _statusFlow = MutableStateFlow(state.getStatus())
        override val statusFlow: StateFlow<TimeTrackingStatus> = _statusFlow.asStateFlow()

        override fun getStatus(): TimeTrackingStatus = _statusFlow.value
        override fun getTotalTimeMs(): Long = _timeFlow.value
        override fun getTotalTimeSeconds(): Int = (_timeFlow.value / 1000).toInt()
        override fun toggleRunning() {}
        override fun setStatus(status: TimeTrackingStatus) { _statusFlow.value = status }
        override fun pause() {}
        override fun resume() {}
        override fun reset() { 
            _timeFlow.value = 0L
            state.setTotalTimeMs(0L)
        }
        override fun addTimeMs(timeMs: Long) {
            _timeFlow.value += timeMs
            state.setTotalTimeMs(_timeFlow.value)
        }
        override fun autoPauseByFocus() {}
        override fun autoResumeFromFocus() {}
        override fun autoPauseByProjectSwitch() {}
    }
}
