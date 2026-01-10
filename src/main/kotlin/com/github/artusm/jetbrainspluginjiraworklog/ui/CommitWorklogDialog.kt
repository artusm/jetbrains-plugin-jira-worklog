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
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import git4idea.repo.GitRepositoryManager
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*

/**
 * Dialog for committing worklog to Jira.
 * Includes task selector, time editor with fast actions, and comment field.
 */
class CommitWorklogDialog(private val project: Project) : DialogWrapper(project) {
    
    companion object {
        fun show(project: Project) {
            val dialog = CommitWorklogDialog(project)
            dialog.show()
        }
    }
    
    private val settings = JiraSettings.getInstance()
    private val timerService = project.service<JiraWorklogTimerService>()
    private val gitBranchParser = service<GitBranchParser>()
    private val jiraClient = JiraApiClient(settings)
    
    private val taskComboBox = ComboBox<JiraIssue>(DefaultComboBoxModel())
    private val timeField = JBTextField()
    private val commentArea = JTextArea(5, 40)
    
    private var currentTimeMs: Long = 0L
    
    init {
        title = "Commit Worklog to Jira"
        currentTimeMs = timerService.getTotalTimeMs()
        init()
        loadInitialData()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        
        // Update time field
        updateTimeField()
        
        // Create time editor buttons
        val timeButtonPanel = createTimeButtonPanel()
        
        // Create form
        val formPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Jira Issue:", taskComboBox)
            .addSeparator()
            .addLabeledComponent("Time Spent:", createTimePanel())
            .addComponent(timeButtonPanel)
            .addSeparator()
            .addLabeledComponent("Comment:", JScrollPane(commentArea))
            .panel
        
        panel.add(formPanel, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createTimePanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT))
        timeField.preferredSize = Dimension(150, timeField.preferredSize.height)
        panel.add(timeField)
        panel.add(JLabel("(format: 2h 30m)"))
        return panel
    }
    
    private fun createTimeButtonPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT))
        
        panel.add(JLabel("Quick adjust: "))
        panel.add(createTimeButton("+1h", 3600 * 1000))
        panel.add(createTimeButton("-1h", -3600 * 1000))
        panel.add(createTimeButton("+30m", 30 * 60 * 1000))
        panel.add(createTimeButton("×2") { currentTimeMs *= 2 })
        panel.add(createTimeButton("÷2") { currentTimeMs /= 2 })
        
        return panel
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
                    Messages.showErrorDialog(
                        "Failed to load Jira issues: ${result.exceptionOrNull()?.message}",
                        "Error Loading Issues"
                    )
                }
            }
        }.start()
    }
    
    private fun loadSpecificIssue(issueKey: String) {
        Thread {
            val result = jiraClient.getIssueWithSubtasks(issueKey)
            
            SwingUtilities.invokeLater {
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
                }
            }
        }.start()
    }
    
    override fun doOKAction() {
        val selectedIssue = taskComboBox.selectedItem as? JiraIssue
        
        if (selectedIssue == null) {
            Messages.showErrorDialog("Please select a Jira issue", "No Issue Selected")
            return
        }
        
        val timeSpentSeconds = (currentTimeMs / 1000).toInt()
        if (timeSpentSeconds <= 0) {
            Messages.showErrorDialog("Time spent must be greater than zero", "Invalid Time")
            return
        }
        
        val comment = commentArea.text.trim()
        
        // Submit worklog in background
        Thread {
            val result = jiraClient.submitWorklog(selectedIssue.key, timeSpentSeconds, comment.ifBlank { null })
            
            SwingUtilities.invokeLater {
                if (result.isSuccess) {
                    Messages.showInfoMessage(
                        "Successfully logged ${TimeFormatter.formatJira(currentTimeMs)} to ${selectedIssue.key}",
                        "Worklog Submitted"
                    )
                    
                    // Reset timer
                    timerService.reset()
                    
                    super.doOKAction()
                } else {
                    Messages.showErrorDialog(
                        "Failed to submit worklog: ${result.exceptionOrNull()?.message}",
                        "Submission Failed"
                    )
                }
            }
        }.start()
    }
}
