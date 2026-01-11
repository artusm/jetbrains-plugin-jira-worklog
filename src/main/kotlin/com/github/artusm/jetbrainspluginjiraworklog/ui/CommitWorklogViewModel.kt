package com.github.artusm.jetbrainspluginjiraworklog.ui

import com.github.artusm.jetbrainspluginjiraworklog.data.JiraWorklogRepository
import com.github.artusm.jetbrainspluginjiraworklog.data.WorklogRepository
import com.github.artusm.jetbrainspluginjiraworklog.git.GitUtils
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraIssue
import com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogTimerService
import com.github.artusm.jetbrainspluginjiraworklog.services.TimerService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class CommitWorklogViewModel(
    private val repository: WorklogRepository,
    private val timerService: TimerService,
    private val branchProvider: () -> String?
) {
    // Secondary constructor for convenience/production use
    constructor(project: Project) : this(
        project.service<JiraWorklogRepository>(),
        project.service<JiraWorklogTimerService>(),
        {
            val repoManager = GitRepositoryManager.getInstance(project)
            val projectRoot = project.basePath?.let { com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(it) }
            val repository = projectRoot?.let { repoManager.getRepositoryForFile(it) }
            repository?.let { GitUtils.getBranchNameOrRev(it) }
        }
    )

    // Scope for ViewModel operations (could be linked to UI lifecycle)
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // UI State
    private val _uiState = MutableStateFlow(CommitWorklogUiState())
    val uiState: StateFlow<CommitWorklogUiState> = _uiState.asStateFlow()

    init {
        // Initialize time from timer service
        updateTime(timerService.getTotalTimeMs())
    }

    fun loadInitialData() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        
        viewModelScope.launch {
            val result = repository.getAssignedIssues()
            
            if (result.isSuccess) {
                val issues = result.getOrNull()?.issues ?: emptyList()
                val currentBranch = branchProvider()
                val savedIssueKey = repository.getSavedIssueKey(currentBranch)
                
                // Find selected issue
                var selectedIssue: JiraIssue? = null
                if (savedIssueKey != null) {
                    selectedIssue = issues.find { it.key == savedIssueKey }
                    
                    // If not in the list but we have a key, try to load it specifically
                    if (selectedIssue == null) {
                        val specificIssueResult = repository.getIssue(savedIssueKey)
                        selectedIssue = specificIssueResult.getOrNull()
                    }
                }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    issues = issues + (listOfNotNull(selectedIssue).filter { it !in issues }),
                    selectedIssue = selectedIssue
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()
                )
            }
        }
    }

    fun selectIssue(issue: JiraIssue?) {
        _uiState.value = _uiState.value.copy(selectedIssue = issue)
        if (issue != null) {
            val currentBranch = branchProvider()
            repository.saveSelectedIssue(issue.key, currentBranch)
        }
    }

    fun updateTime(timeMs: Long) {
        _uiState.value = _uiState.value.copy(timeSpentMs = timeMs)
    }
    
    fun adjustTime(deltaMs: Long) {
        val newTime = (_uiState.value.timeSpentMs + deltaMs).coerceAtLeast(0)
        updateTime(newTime)
    }
    
    fun multiplyTime(factor: Double) {
        val newTime = (_uiState.value.timeSpentMs * factor).toLong().coerceAtLeast(0)
        updateTime(newTime)
    }

    fun updateComment(comment: String) {
        _uiState.value = _uiState.value.copy(comment = comment)
    }

    fun submitWorklog(onSuccess: () -> Unit) {
        val state = uiState.value
        val issue = state.selectedIssue ?: return
        val timeSeconds = (state.timeSpentMs / 1000).toInt()
        
        if (timeSeconds <= 0) {
            return
        }

        _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)

        viewModelScope.launch {
            val result = repository.submitWorklog(issue.key, timeSeconds, state.comment)
            
            if (result.isSuccess) {
                // Reset timer
                timerService.reset()
                
                _uiState.value = _uiState.value.copy(isSubmitting = false, isSuccess = true)
                onSuccess()
            } else {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    error = result.exceptionOrNull()
                )
            }
        }
    }
    fun clearError() {
         _uiState.value = _uiState.value.copy(error = null)
    }

    fun dispose() {
        viewModelScope.cancel()
    }
}

data class CommitWorklogUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val issues: List<JiraIssue> = emptyList(),
    val selectedIssue: JiraIssue? = null,
    val timeSpentMs: Long = 0,
    val comment: String = "",
    val error: Throwable? = null,
    val isSuccess: Boolean = false
)
