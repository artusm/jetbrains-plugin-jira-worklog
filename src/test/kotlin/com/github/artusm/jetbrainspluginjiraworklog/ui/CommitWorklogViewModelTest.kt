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
    private lateinit var fakeRepository: FakeRepository
    private lateinit var fakeTimerService: FakeTimerService

    @Before
    fun setup() {
        // Stub project interaction by passing null as we used open classes and won't call super methods that need project
        // But referencing Project in constructor of FakeRepository might be tricky if it's not nullable.
        // Solution: Create a mock Project or pass null if possible? 
        // Kotlin won't allow null for non-null type.
        // We need to subclass the open classes.
        // But the constructor of JiraWorklogRepository takes Project.
        // We can create a version of FakeRepository that doesn't call super init if possible? No.
        // We mocked them as 'open class JiraWorklogRepository(project: Project)'.
        // We need a dummy project.
        // Or we can just use Mockito if available.
        // Assuming no Mockito available, we need to pass *something*.
        // Since we are running in unit test context, usually we don't have a real project.
        // But maybe we can pass Mockito.mock(Project::class.java)?
        // If Mockito is not available, we are stuck.
        // Let's assume we can create a dummy object using Proxy or just pass null and suppress check (risky).
        
        // Better approach: Test with interface if we had one.
        // Since we don't, let's try to trust the existing test infrastructure.
        // However, I will define the Fakes inside the test file and instantiate them.
        // I will try to pass null !! as Project.
        
        fakeRepository = FakeRepository()
        fakeTimerService = FakeTimerService()
        
        viewModel = CommitWorklogViewModel(
            fakeRepository,
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
    class FakeRepository : JiraWorklogRepository(nullAs()) {
        private val taskType = com.github.artusm.jetbrainspluginjiraworklog.jira.JiraIssueType("Task", false)
        private val bugType = com.github.artusm.jetbrainspluginjiraworklog.jira.JiraIssueType("Bug", false)
        
        var issuesToReturn = listOf(
            JiraIssue("JIRA-1", com.github.artusm.jetbrainspluginjiraworklog.jira.JiraIssueFields("Task 1", taskType)),
            JiraIssue("JIRA-2", com.github.artusm.jetbrainspluginjiraworklog.jira.JiraIssueFields("Task 2", bugType))
        )
        
        // Override dependencies to avoid NPE from super
        override val settings: com.github.artusm.jetbrainspluginjiraworklog.config.JiraSettings get() = nullAs()
        override val api: com.github.artusm.jetbrainspluginjiraworklog.jira.JiraApi get() = nullAs()
        override val persistentState: com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogPersistentState get() = nullAs()
        
        override suspend fun getAssignedIssues(): Result<JiraSearchResult> {
            return Result.success(JiraSearchResult(issuesToReturn, issuesToReturn.size))
        }
        
        override fun getSavedIssueKey(branchName: String?): String? {
            return if (branchName == "feature/test") "JIRA-1" else null
        }
        
        override suspend fun submitWorklog(issueKey: String, timeSpentSeconds: Int, comment: String?): Result<JiraWorklogResponse> {
             return Result.success(JiraWorklogResponse("100", "ISSUE-100", "1h"))
        }

        override fun saveSelectedIssue(issueKey: String, branchName: String?) {
            // no-op
        }
    }
    
    class FakeTimerService : JiraWorklogTimerService(nullAs(), 
        object : com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogPersistentState() {
            override fun getStatus() = com.github.artusm.jetbrainspluginjiraworklog.model.TimeTrackingStatus.STOPPED
            override fun getTotalTimeMs() = 5000L
        }
    ) {


        override fun getTotalTimeMs(): Long = 5000L
        override fun reset() {}
    }
}

// Helper to bypass non-null check for test
@Suppress("UNCHECKED_CAST")
private fun <T> nullAs(): T = null as T
