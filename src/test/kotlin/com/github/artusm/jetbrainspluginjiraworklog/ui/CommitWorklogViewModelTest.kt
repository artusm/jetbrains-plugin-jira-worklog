package com.github.artusm.jetbrainspluginjiraworklog.ui

import com.github.artusm.jetbrainspluginjiraworklog.data.WorklogRepository
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraIssue
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraIssueFields
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraIssueType
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraSearchResult
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraWorklogResponse
import com.github.artusm.jetbrainspluginjiraworklog.services.TimerService
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommitWorklogViewModelTest {

    private lateinit var viewModel: CommitWorklogViewModel
    private lateinit var mockRepository: WorklogRepository
    private lateinit var mockTimerService: TimerService
    private val branchProvider: () -> String? = { "feature/branch" }
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        
        mockRepository = mockk(relaxed = true)
        mockTimerService = mockk(relaxed = true)
        
        every { mockTimerService.getTotalTimeMs() } returns 3600000L // 1 hour
        
        viewModel = CommitWorklogViewModel(mockRepository, mockTimerService, branchProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test initial data load`() {
        val issue = JiraIssue("PROJ-1", JiraIssueFields("Summary", JiraIssueType("Task", false), emptyList()))
        coEvery { mockRepository.getAssignedIssues() } returns Result.success(JiraSearchResult(listOf(issue), 1))
        coEvery { mockRepository.getSavedIssueKey(any()) } returns "PROJ-1"
        coEvery { mockRepository.getIssue("PROJ-1") } returns Result.success(issue)

        viewModel.loadInitialData()

        assertEquals(issue, viewModel.uiState.value.selectedIssue)
        assertEquals(3600000L, viewModel.uiState.value.timeSpentMs)
    }

    @Test
    fun `test adjust time`() {
        viewModel.updateTime(3600000L)
        assertEquals(3600000L, viewModel.uiState.value.timeSpentMs)

        viewModel.adjustTime(60000L) // Add 1 min
        assertEquals(3660000L, viewModel.uiState.value.timeSpentMs)
        
        viewModel.adjustTime(-60000L) // Remove 1 min
        assertEquals(3600000L, viewModel.uiState.value.timeSpentMs)
    }

    @Test
    fun `test submit worklog success`() {
        val issue = JiraIssue("PROJ-1", JiraIssueFields("Summary", JiraIssueType("Task", false), emptyList()))
        
        // Setup initial state
        viewModel.selectIssue(issue)
        viewModel.updateTime(3600000L)
        viewModel.updateComment("Done")

        coEvery { mockRepository.submitWorklog("PROJ-1", 3600, "Done") } returns Result.success(
            JiraWorklogResponse("http://self", "user", "user")
        )

        var successCallbackCalled = false
        viewModel.submitWorklog { successCallbackCalled = true }

        verify { mockTimerService.reset() }
        assertTrue(successCallbackCalled)
        coVerify { mockRepository.saveSelectedIssue("PROJ-1", any()) }
    }

    @Test
    fun `test submit worklog failure`() {
        val issue = JiraIssue("PROJ-1", JiraIssueFields("Summary", JiraIssueType("Task", false), emptyList()))
        
        // Setup initial state
        viewModel.selectIssue(issue)
        
        coEvery { mockRepository.submitWorklog(any(), any(), any()) } returns Result.failure(Exception("Failed"))

        var successCallbackCalled = false
        viewModel.submitWorklog { successCallbackCalled = true }

        assertNotNull(viewModel.uiState.value.error)
        verify(exactly = 0) { mockTimerService.reset() }
        assertFalse(successCallbackCalled)
    }
}
