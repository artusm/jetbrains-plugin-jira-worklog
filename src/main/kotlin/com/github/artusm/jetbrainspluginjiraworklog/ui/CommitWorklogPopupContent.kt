package com.github.artusm.jetbrainspluginjiraworklog.ui

import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraIssue
import com.github.artusm.jetbrainspluginjiraworklog.utils.MyBundle
import com.github.artusm.jetbrainspluginjiraworklog.utils.TimeFormatter
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Beautiful inline popup for committing worklog to Jira.
 * Features clean layout, proper spacing, and professional appearance.
 * Now acts as a View in MVVM, delegating logic to CommitWorklogViewModel.
 */
class CommitWorklogPopupContent(private val project: Project) : JPanel(BorderLayout()) {
    
    var popup: JBPopup? = null
    
    private val viewModel = CommitWorklogViewModel(project)
    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val taskComboBox = ComboBox<JiraIssue>(DefaultComboBoxModel())
    private val timeField = JBTextField()
    private val commentArea = JTextArea(4, 40)
    
    // UI components we need to update
    private lateinit var submitButton: JButton
    
    init {
        // Main panel with proper padding
        border = JBUI.Borders.empty(16, 20)
        
        // Create form with proper alignment
        val formPanel = createFormPanel()
        add(formPanel, BorderLayout.CENTER)
        
        // Create action panel with submit button
        val actionPanel = createActionPanel()
        add(actionPanel, BorderLayout.SOUTH)
        
        // Setup listeners
        setupListeners()
        
        // Start observing ViewModel state
        observeViewModel()
        
        // Load initial data
        viewModel.loadInitialData()
    }
    
    private fun setupListeners() {
        // Issue selection
        taskComboBox.addActionListener {
            if (taskComboBox.hasFocus() || taskComboBox.isPopupVisible) {
                // Only update if user interaction or explicitly set
                viewModel.selectIssue(taskComboBox.selectedItem as? JiraIssue)
            }
        }
        
        // Comment updates
        commentArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updateComment()
            override fun removeUpdate(e: DocumentEvent?) = updateComment()
            override fun changedUpdate(e: DocumentEvent?) = updateComment()
            
            private fun updateComment() {
                viewModel.updateComment(commentArea.text)
            }
        })
    }
    
    private fun observeViewModel() {
        uiScope.launch {
            viewModel.uiState.collectLatest { state ->
                // Update Issue List and Selection
                val model = taskComboBox.model as DefaultComboBoxModel<JiraIssue>
                
                // Update elements only if changed to avoid flickering
                if (model.size != state.issues.size || (0 until model.size).any { model.getElementAt(it) != state.issues[it] }) {
                    val currentSelection = model.selectedItem
                    model.removeAllElements()
                    state.issues.forEach { model.addElement(it) }
                    
                    // Restore selection if it's still valid, or use state selection
                    if (state.selectedIssue != null) {
                        model.selectedItem = state.selectedIssue
                    } else if (currentSelection in state.issues) {
                        model.selectedItem = currentSelection
                    }
                } else if (model.selectedItem != state.selectedIssue) {
                    model.selectedItem = state.selectedIssue
                }
                
                // Update Time Field
                timeField.text = TimeFormatter.formatJira(state.timeSpentMs)
                
                // Update Comment
                if (commentArea.text != state.comment) {
                     commentArea.text = state.comment
                }
                
                // Error Handling
                if (state.error != null) {
                    Messages.showErrorDialog(
                        this@CommitWorklogPopupContent,
                        state.error.message ?: MyBundle.message("error.general", "Unknown"),
                        MyBundle.message("error.general.title")
                    )
                    // We might want to clear error in VM after showing
                }
                
                // Success Handling
                if (state.isSuccess) {
                    Messages.showInfoMessage(
                        this@CommitWorklogPopupContent,
                        MyBundle.message("commit.success", TimeFormatter.formatJira(state.timeSpentMs), state.selectedIssue?.key ?: ""),
                        MyBundle.message("commit.success.title")
                    )
                    popup?.cancel()
                }
                
                // Loading State
                taskComboBox.isEnabled = !state.isLoading && !state.isSubmitting
                submitButton.isEnabled = !state.isLoading && !state.isSubmitting
                submitButton.text = if (state.isSubmitting) "Submitting..." else MyBundle.message("commit.button.submit")
            }
        }
    }
    
    private fun createFormPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        
        // Jira Issue selector
        setupComboBoxRenderer()
        panel.add(createLabeledRow(MyBundle.message("commit.jira.issue"), taskComboBox))
        panel.add(Box.createVerticalStrut(12))
        
        // Time spent field
        timeField.preferredSize = Dimension(200, timeField.preferredSize.height)
        timeField.isEditable = false // Read-only, driven by VM/Buttons for now
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
    
    private fun setupComboBoxRenderer() {
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
        panel.add(createStyledButton(MyBundle.message("commit.button.+1h")) { viewModel.adjustTime(3600 * 1000) })
        panel.add(createStyledButton(MyBundle.message("commit.button.-1h")) { viewModel.adjustTime(-3600 * 1000) })
        panel.add(createStyledButton(MyBundle.message("commit.button.+30m")) { viewModel.adjustTime(30 * 60 * 1000) })
        panel.add(createStyledButton(MyBundle.message("commit.button.x2")) { viewModel.multiplyTime(2.0) })
        panel.add(createStyledButton(MyBundle.message("commit.button.div2")) { viewModel.multiplyTime(0.5) })
        
        return panel
    }
    
    private fun createStyledButton(label: String, action: () -> Unit): JButton {
        val button = JButton(label)
        styleButton(button)
        button.addActionListener { action() }
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
        
        submitButton = JButton(MyBundle.message("commit.button.submit"))
        submitButton.preferredSize = Dimension(140, 32)
        submitButton.font = submitButton.font.deriveFont(Font.BOLD, 13f)
        submitButton.isFocusPainted = false
        submitButton.addActionListener {
            viewModel.submitWorklog {
                // Actions after success handled in observer
            }
        }
        
        panel.add(submitButton)
        return panel
    }
    
    override fun removeNotify() {
        super.removeNotify()
        uiScope.cancel() // Cancel coroutines when view is removed
    }
    
    override fun getPreferredSize(): Dimension {
        return Dimension(500, 280)
    }
}
