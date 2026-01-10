package com.github.artusm.jetbrainspluginjiraworklog.ui

import com.github.artusm.jetbrainspluginjiraworklog.config.JiraSettings
import com.github.artusm.jetbrainspluginjiraworklog.git.GitBranchParser
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraApiClient
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraIssue
import com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogTimerService
import com.github.artusm.jetbrainspluginjiraworklog.utils.TimeFormatter
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.ui.components.JBTextField
import git4idea.repo.GitRepositoryManager
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import javax.swing.*

/**
 * Inline popup content for committing worklog to Jira.
 * Adapted from TimeTrackerPopupContent to use JBPopup instead of DialogWrapper.
 */
class CommitWorklogPopupContent(private val project: Project) : Box(BoxLayout.Y_AXIS) {
    
    var popup: JBPopup? = null
    
    private val settings = JiraSettings.getInstance()
    private val timerService = project.service<JiraWorklogTimerService>()
    private val gitBranchParser = service<GitBranchParser>()
    private val jiraClient = JiraApiClient(settings)
    
    private val taskComboBox = ComboBox<JiraIssue>(DefaultComboBoxModel())
    private val timeField = JBTextField()
    private val commentArea = JTextArea(3, 30)
    
    private var currentTimeMs: Long = 0L
    
    init {
        currentTimeMs = timerService.getTotalTimeMs()
        
        // Add padding border
        border = BorderFactory.createEmptyBorder(5, 10, 5, 10)
        
        // Create form panel
        val formPanel = JPanel(GridLayout(0, 2, 4, 4))
        add(formPanel)
        
        // Jira Issue selector
        formPanel.add(JLabel("Jira Issue:", JLabel.RIGHT))
        formPanel.add(taskComboBox)
        
        // Time spent field
        formPanel.add(JLabel("Time Spent:", JLabel.RIGHT))
        timeField.preferredSize = Dimension(150, timeField.preferredSize.height)
        updateTimeField()
        formPanel.add(timeField)
        
        // Create time adjustment buttons
        val timeButtons = Box.createHorizontalBox()
        add(timeButtons)
        
        timeButtons.add(JLabel("Quick adjust: "))
        timeButtons.add(createTimeButton("+1h", 3600 * 1000))
        timeButtons.add(createTimeButton("-1h", -3600 * 1000))
        timeButtons.add(createTimeButton("+30m", 30 * 60 * 1000))
        timeButtons.add(createTimeButton("×2") { currentTimeMs *= 2 })
        timeButtons.add(createTimeButton("÷2") { currentTimeMs /= 2 })
        
        // Comment field
        val commentPanel = JPanel()
        commentPanel.layout = BoxLayout(commentPanel, BoxLayout.Y_AXIS)
        commentPanel.add(JLabel("Comment:"))
        commentArea.lineWrap = true
        commentArea.wrapStyleWord = true
        val scrollPane = JScrollPane(commentArea)
        scrollPane.preferredSize = Dimension(300, 60)
        commentPanel.add(scrollPane)
        add(commentPanel)
        
        // Submit button
        val submitPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        val submitButton = JButton("Submit Worklog")
        submitButton.addActionListener {
            submitWorklog()
        }
        submitPanel.add(submitButton)
        add(submitPanel)
        
        // Load initial data
        loadInitialData()
    }
    
    private fun createTimeButton(label: String, deltaMs: Long): JButton {
        val button = JButton(label)
        button.addActionListener {
            currentTimeMs = maxOf(0, currentTimeMs + deltaMs)
            updateTimeField()
        }
        return button
    }
    
    private fun createTimeButton(label: String, action: () -> Unit): JButton {
        val button = JButton(label)
        button.addActionListener {
            action()
            currentTimeMs = maxOf(0, currentTimeMs)
            updateTimeField()
        }
        return button
    }
    
    private fun updateTimeField() {
        timeField.text = TimeFormatter.formatJira(currentTimeMs)
    }
    
    private fun loadInitialData() {
        // Get current Git branch and parse Jira key
        val gitRepoManager = GitRepositoryManager.getInstance(project)
        val repositories = gitRepoManager.repositories
        
        if (repositories.isNotEmpty()) {
            val currentBranch = repositories.first().currentBranch?.name
            val parseResult = gitBranchParser.parseBranchName(currentBranch)
            val primaryKey = parseResult.getPrimaryKey()
            
            // Load issues
            loadIssues(primaryKey)
        } else {
            loadIssues(null)
        }
    }
    
    private fun loadIssues(preselectedKey: String?) {
        // Load in background
        Thread {
            val result = jiraClient.searchAssignedIssues()
            
            SwingUtilities.invokeLater {
                if (!project.isDisposed && result.isSuccess) {
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
                }
            }
        }.start()
    }
    
    private fun loadSpecificIssue(issueKey: String) {
        Thread {
            val result = jiraClient.getIssueWithSubtasks(issueKey)
            
            SwingUtilities.invokeLater {
                if (!project.isDisposed && result.isSuccess) {
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
                }
            }
        }.start()
    }
    
    private fun submitWorklog() {
        val selectedIssue = taskComboBox.selectedItem as? JiraIssue
        
        if (selectedIssue == null) {
            Messages.showErrorDialog(project, "Please select a Jira issue", "No Issue Selected")
            return
        }
        
        val timeSpentSeconds = (currentTimeMs / 1000).toInt()
        if (timeSpentSeconds <= 0) {
            Messages.showErrorDialog(project, "Time spent must be greater than zero", "Invalid Time")
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
                            "Successfully logged ${TimeFormatter.formatJira(currentTimeMs)} to ${selectedIssue.key}",
                            "Worklog Submitted"
                        )
                        
                        // Reset timer
                        timerService.reset()
                        
                        // Close popup
                        popup?.cancel()
                    } else {
                        Messages.showErrorDialog(
                            project,
                            "Failed to submit worklog: ${result.exceptionOrNull()?.message}",
                            "Submission Failed"
                        )
                    }
                }
            }
        }.start()
    }
}
