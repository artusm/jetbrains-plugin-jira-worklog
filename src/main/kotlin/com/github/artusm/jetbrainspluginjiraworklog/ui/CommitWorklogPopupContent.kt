package com.github.artusm.jetbrainspluginjiraworklog.ui

import com.github.artusm.jetbrainspluginjiraworklog.config.JiraSettings
import com.github.artusm.jetbrainspluginjiraworklog.git.GitBranchParser
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraApiClient
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraIssue
import com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogPersistentState
import com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogTimerService
import com.github.artusm.jetbrainspluginjiraworklog.utils.MyBundle
import com.github.artusm.jetbrainspluginjiraworklog.utils.TimeFormatter
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import git4idea.repo.GitRepositoryManager
import java.awt.*
import javax.swing.*

/**
 * Beautiful inline popup for committing worklog to Jira.
 * Features clean layout, proper spacing, and professional appearance.
 */
class CommitWorklogPopupContent(private val project: Project) : JPanel(BorderLayout()) {
    
    var popup: JBPopup? = null
    
    private val settings = JiraSettings.getInstance()
    private val timerService = project.service<JiraWorklogTimerService>()
    private val persistentState = project.service<JiraWorklogPersistentState>()
    private val gitBranchParser = service<GitBranchParser>()
    private val jiraClient = JiraApiClient(settings)
    
    private val taskComboBox = ComboBox<JiraIssue>(DefaultComboBoxModel())
    private val timeField = JBTextField()
    private val commentArea = JTextArea(4, 40)
    
    private var currentTimeMs: Long = 0L
    
    init {
        currentTimeMs = timerService.getTotalTimeMs()
        
        // Main panel with proper padding
        border = JBUI.Borders.empty(16, 20)
        
        // Create form with proper alignment
        val formPanel = createFormPanel()
        add(formPanel, BorderLayout.CENTER)
        
        // Create action panel with submit button
        val actionPanel = createActionPanel()
        add(actionPanel, BorderLayout.SOUTH)
        
        // Load initial data
        loadInitialData()
        
        // Add selection listener to save selected issue per branch
        taskComboBox.addActionListener {
            val selectedIssue = taskComboBox.selectedItem as? JiraIssue
            if (selectedIssue != null) {
                saveSelectedIssueForCurrentBranch(selectedIssue.key)
            }
        }
    }
    
    private fun createFormPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        
        // Jira Issue selector
        panel.add(createLabeledRow(MyBundle.message("commit.jira.issue"), taskComboBox))
        panel.add(Box.createVerticalStrut(12))
        
        // Time spent field
        timeField.preferredSize = Dimension(200, timeField.preferredSize.height)
        updateTimeField()
        panel.add(createLabeledRow(MyBundle.message("commit.time.spent"), timeField))
        panel.add(Box.createVerticalStrut(12))
        
        // Quick adjust buttons
        val quickAdjustPanel = createQuickAdjustPanel()
        panel.add(createLabeledRow(MyBundle.message("commit.quick.adjust"), quickAdjustPanel))
        panel.add(Box.createVerticalStrut(12))
        
        // Comment field
        val commentPanel = createCommentPanel()
        panel.add(createLabeledRow(MyBundle.message("commit.comment"), commentPanel))
        
        return panel
    }
    
    private fun createLabeledRow(labelText: String, component: JComponent): JPanel {
        val row = JPanel(BorderLayout())
        row.maximumSize = Dimension(Int.MAX_VALUE, component.preferredSize.height)
        
        // Label with fixed width for alignment
        val label = JBLabel(labelText)
        label.preferredSize = Dimension(100, label.preferredSize.height)
        label.horizontalAlignment = SwingConstants.RIGHT
        label.border = JBUI.Borders.emptyRight(8)
        
        row.add(label, BorderLayout.WEST)
        row.add(component, BorderLayout.CENTER)
        
        return row
    }
    
    private fun createQuickAdjustPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        panel.background = UIUtil.getPanelBackground()
        
        // Create styled buttons
        panel.add(createStyledButton(MyBundle.message("commit.button.+1h"), 3600 * 1000))
        panel.add(createStyledButton(MyBundle.message("commit.button.-1h"), -3600 * 1000))
        panel.add(createStyledButton(MyBundle.message("commit.button.+30m"), 30 * 60 * 1000))
        panel.add(createStyledButton(MyBundle.message("commit.button.x2")) { currentTimeMs *= 2 })
        panel.add(createStyledButton(MyBundle.message("commit.button.div2")) { currentTimeMs /= 2 })
        
        return panel
    }
    
    private fun createStyledButton(label: String, deltaMs: Long): JButton {
        val button = JButton(label)
        styleButton(button)
        button.addActionListener {
            currentTimeMs = maxOf(0, currentTimeMs + deltaMs)
            updateTimeField()
        }
        return button
    }
    
    private fun createStyledButton(label: String, action: () -> Unit): JButton {
        val button = JButton(label)
        styleButton(button)
        button.addActionListener {
            action()
            currentTimeMs = maxOf(0, currentTimeMs)
            updateTimeField()
        }
        return button
    }
    
    private fun styleButton(button: JButton) {
        button.preferredSize = Dimension(70, 28)
        button.font = button.font.deriveFont(12f)
        button.isFocusPainted = false
    }
    
    private fun createCommentPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        commentArea.lineWrap = true
        commentArea.wrapStyleWord = true
        commentArea.border = JBUI.Borders.empty(4)
        
        val scrollPane = JScrollPane(commentArea)
        scrollPane.preferredSize = Dimension(400, 80)
        scrollPane.border = JBUI.Borders.customLine(JBColor.border(), 1)
        
        panel.add(scrollPane, BorderLayout.CENTER)
        return panel
    }
    
    private fun createActionPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        panel.border = JBUI.Borders.emptyTop(16)
        
        val submitButton = JButton(MyBundle.message("commit.button.submit"))
        submitButton.preferredSize = Dimension(140, 32)
        submitButton.font = submitButton.font.deriveFont(Font.BOLD, 13f)
        submitButton.isFocusPainted = false
        submitButton.addActionListener {
            submitWorklog()
        }
        
        panel.add(submitButton)
        return panel
    }
    
    private fun updateTimeField() {
        timeField.text = TimeFormatter.formatJira(currentTimeMs)
    }
    
    private fun loadInitialData() {
        // Get saved issue for current branch (or last issue as fallback)
        val savedIssueKey = getSavedIssueForCurrentBranch()
        
        // Load issues with the saved/fallback key
        loadIssues(savedIssueKey)
    }
    
    private fun loadIssues(preselectedKey: String?) {
        // Load in background
        Thread {
            val result = jiraClient.searchAssignedIssues()
            
            SwingUtilities.invokeLater {
                if (!project.isDisposed) {
                    if (result.isSuccess) {
                        val issues = result.getOrNull()?.issues ?: emptyList()
                        val model = taskComboBox.model as DefaultComboBoxModel<JiraIssue>
                        
                        issues.forEach { model.addElement(it) }
                        
                        // Set renderer to show key and summary
                        taskComboBox.renderer = object : DefaultListCellRenderer() {
                            override fun getListCellRendererComponent(
                                list: JList<*>?,
                                value: Any?,
                                index: Int,
                                isSelected: Boolean,
                                cellHasFocus: Boolean
                            ): java.awt.Component {
                                val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                                if (value is JiraIssue) {
                                    text = "${value.key} - ${value.summary}"
                                }
                                return component
                            }
                        }
                        
                        // Preselect the issue from branch if found
                        if (preselectedKey != null) {
                            val matchingIssue = issues.find { it.key == preselectedKey }
                            if (matchingIssue != null) {
                                taskComboBox.selectedItem = matchingIssue
                            } else {
                                // Try to load the issue directly
                                loadSpecificIssue(preselectedKey)
                            }
                        }
                    } else {
                        // Show friendly error message
                        val error = result.exceptionOrNull()
                        val errorMessage = getErrorMessage(error)
                        val title = getErrorTitle(error)
                        
                        Messages.showErrorDialog(
                            project,
                            errorMessage,
                            title
                        )
                    }
                }
            }
        }.start()
    }
    
    private fun loadSpecificIssue(issueKey: String) {
        Thread {
            val result = jiraClient.getIssueWithSubtasks(issueKey)
            
            SwingUtilities.invokeLater {
                if (!project.isDisposed) {
                    if (result.isSuccess) {
                        val issue = result.getOrNull()
                        if (issue != null) {
                            val model = taskComboBox.model as DefaultComboBoxModel<JiraIssue>
                            model.addElement(issue)
                            taskComboBox.selectedItem = issue
                            
                            // Also add subtasks if available
                            issue.fields.subtasks?.forEach { subtask ->
                                model.addElement(subtask)
                            }
                        }
                    } else {
                        // Show error for specific issue lookup
                        val error = result.exceptionOrNull()
                        println("Failed to load issue $issueKey: ${error?.message}")
                    }
                }
            }
        }.start()
    }
    
    /**
     * Get user-friendly error message based on exception type.
     */
    private fun getErrorMessage(error: Throwable?): String {
        val host = (error as? java.net.UnknownHostException)?.message ?: "unknown"
        return when (error) {
            is java.net.UnknownHostException -> MyBundle.message("error.unknown.host", host)
            is java.net.ConnectException -> MyBundle.message("error.connect")
            is javax.net.ssl.SSLHandshakeException -> MyBundle.message("error.ssl")
            is java.net.SocketTimeoutException -> MyBundle.message("error.timeout")
            is IllegalStateException -> MyBundle.message("error.config", error.message ?:"")
            else -> MyBundle.message("error.general", error?.message ?: "Unknown error")
        }
    }
    
    /**
     * Get error dialog title based on exception type.
     */
    private fun getErrorTitle(error: Throwable?): String {
        return when (error) {
            is java.net.UnknownHostException -> MyBundle.message("error.unknown.host.title")
            is java.net.ConnectException -> MyBundle.message("error.connect.title")
            is javax.net.ssl.SSLException -> MyBundle.message("error.ssl.title")
            is java.net.SocketTimeoutException -> MyBundle.message("error.timeout.title")
            is IllegalStateException -> MyBundle.message("error.config.title")
            else -> MyBundle.message("error.general.title")
        }
    }
    
    /**
     * Save selected issue for current branch
     */
    private fun saveSelectedIssueForCurrentBranch(issueKey: String) {
        val repoManager = GitRepositoryManager.getInstance(project)
        val repository = repoManager.repositories.firstOrNull() ?: return
        
        val branchName = repository.currentBranch?.name ?: return
        persistentState.saveIssueForBranch(branchName, issueKey)
        persistentState.setLastIssueKey(issueKey)
    }
    
    /**
     * Get saved issue for current branch (with fallback to last issue)
     */
    private fun getSavedIssueForCurrentBranch(): String? {
        val repoManager = GitRepositoryManager.getInstance(project)
        val repository = repoManager.repositories.firstOrNull() ?: return persistentState.getLastIssueKey()
        
        val branchName = repository.currentBranch?.name ?: return persistentState.getLastIssueKey()
        return persistentState.getIssueForBranch(branchName) ?: persistentState.getLastIssueKey()
    }
    
    private fun submitWorklog() {
        val selectedIssue = taskComboBox.selectedItem as? JiraIssue
        
        if (selectedIssue == null) {
            Messages.showErrorDialog(
                project, 
                MyBundle.message("commit.error.no.issue"), 
                MyBundle.message("commit.error.no.issue.title")
            )
            return
        }
        
        val timeSpentSeconds = (currentTimeMs / 1000).toInt()
        if (timeSpentSeconds <= 0) {
            Messages.showErrorDialog(
                project, 
                MyBundle.message("commit.error.invalid.time"), 
                MyBundle.message("commit.error.invalid.time.title")
            )
            return
        }
        
        val comment = commentArea.text.trim()
        
        // Submit worklog in background
        Thread {
            val result = jiraClient.submitWorklog(selectedIssue.key, timeSpentSeconds, comment.ifBlank { null })
            
            SwingUtilities.invokeLater {
                if (!project.isDisposed) {
                    if (result.isSuccess) {
                        Messages.showInfoMessage(
                            project,
                            MyBundle.message("commit.success", TimeFormatter.formatJira(currentTimeMs), selectedIssue.key),
                            MyBundle.message("commit.success.title")
                        )
                        
                        // Reset timer
                        timerService.reset()
                        
                        // Close popup
                        popup?.cancel()
                    } else {
                        Messages.showErrorDialog(
                            project,
                            MyBundle.message("commit.error.submit", result.exceptionOrNull()?.message ?: ""),
                            MyBundle.message("commit.error.submit.title")
                        )
                    }
                }
            }
        }.start()
    }
    
    override fun getPreferredSize(): Dimension {
        return Dimension(500, 280)
    }
}
