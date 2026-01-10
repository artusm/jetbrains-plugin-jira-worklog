package com.github.artusm.jetbrainspluginjiraworklog.config

import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraApiClient
import com.github.artusm.jetbrainspluginjiraworklog.utils.MyBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import javax.swing.*

/**
 * Beautiful settings page for Jira Worklog Timer configuration.
 * Accessible via Settings → Tools → Jira Worklog Timer
 */
class JiraWorklogConfigurable : Configurable {
    
    private val settings = JiraSettings.getInstance()
    
    private var jiraUrlField: JBTextField? = null
    private var tokenField: JBPasswordField? = null
    private var pauseOnFocusCheckbox: JBCheckBox? = null
    private var pauseOnBranchCheckbox: JBCheckBox? = null
    private var pauseOnProjectCheckbox: JBCheckBox? = null
    private var pauseOnSystemSleepCheckbox: JBCheckBox? = null
    private var testConnectionButton: JButton? = null
    private var connectionStatusLabel: JBLabel? = null
    
    private var mainPanel: JPanel? = null
    
    override fun getDisplayName(): String = MyBundle.message("settings.title")
    
    override fun createComponent(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(20)
        
        // Create main content panel
        val contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        
        // Header section
        contentPanel.add(createHeaderSection())
        contentPanel.add(Box.createVerticalStrut(20))
        
        // Jira configuration section
        contentPanel.add(createSectionLabel(MyBundle.message("settings.section.jira")))
        contentPanel.add(Box.createVerticalStrut(8))
        contentPanel.add(createJiraConfigSection())
        contentPanel.add(Box.createVerticalStrut(24))
        
        // Auto-pause section
        contentPanel.add(createSectionLabel(MyBundle.message("settings.section.autopause")))
        contentPanel.add(Box.createVerticalStrut(8))
        contentPanel.add(createAutoPauseSection())
        
        // Add vertical glue to push content to top
        contentPanel.add(Box.createVerticalGlue())
        
        panel.add(contentPanel, BorderLayout.NORTH)
        
        mainPanel = panel
        return panel
    }
    
    private fun createHeaderSection(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.alignmentX = Component.LEFT_ALIGNMENT
        
        val titleLabel = JBLabel(MyBundle.message("settings.title"))
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 18f)
        titleLabel.alignmentX = Component.LEFT_ALIGNMENT
        
        val descLabel = JBLabel(MyBundle.message("settings.description"))
        descLabel.foreground = UIUtil.getContextHelpForeground()
        descLabel.alignmentX = Component.LEFT_ALIGNMENT
        
        panel.add(titleLabel)
        panel.add(Box.createVerticalStrut(4))
        panel.add(descLabel)
        
        return panel
    }
    
    private fun createSectionLabel(text: String): JBLabel {
        val label = JBLabel(text)
        label.font = label.font.deriveFont(Font.BOLD, 14f)
        label.alignmentX = Component.LEFT_ALIGNMENT
        return label
    }
    
    private fun createJiraConfigSection(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.alignmentX = Component.LEFT_ALIGNMENT
        panel.border = JBUI.Borders.empty(12, 16)
        panel.background = UIUtil.getPanelBackground()
        
        // Jira URL field
        jiraUrlField = JBTextField(settings.getJiraUrl(), 40)
        panel.add(createFieldRow(
            MyBundle.message("settings.jira.url.label"), 
            jiraUrlField!!,
            MyBundle.message("settings.jira.url.hint")
        ))
        panel.add(Box.createVerticalStrut(12))
        
        // Token field
        tokenField = JBPasswordField()
        settings.getPersonalAccessToken()?.let { tokenField?.text = it }
        panel.add(createFieldRow(
            MyBundle.message("settings.jira.token.label"), 
            tokenField!!,
            MyBundle.message("settings.jira.token.hint")
        ))
        panel.add(Box.createVerticalStrut(8))
        
        // Token generation link
        val linkPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        linkPanel.alignmentX = Component.LEFT_ALIGNMENT
        linkPanel.isOpaque = false
        
        val linkLabel = LinkLabel.create(MyBundle.message("settings.jira.token.link")) {
            BrowserUtil.browse("https://id.atlassian.com/manage-profile/security/api-tokens")
        }
        linkLabel.icon = AllIcons.General.Web
        linkPanel.add(linkLabel)
        panel.add(linkPanel)
        panel.add(Box.createVerticalStrut(12))
        
        // Connection test section
        val testPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        testPanel.alignmentX = Component.LEFT_ALIGNMENT
        testPanel.isOpaque = false
        
        testConnectionButton = JButton(MyBundle.message("settings.jira.test"))
        testConnectionButton?.addActionListener { testConnection() }
        testPanel.add(testConnectionButton)
        
        connectionStatusLabel = JBLabel("")
        connectionStatusLabel?.isVisible = false
        testPanel.add(connectionStatusLabel)
        
        panel.add(testPanel)
        
        return panel
    }
    
    private fun createAutoPauseSection(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.alignmentX = Component.LEFT_ALIGNMENT
        panel.border = JBUI.Borders.empty(12, 16)
        panel.background = UIUtil.getPanelBackground()
        
        pauseOnFocusCheckbox = JBCheckBox(MyBundle.message("settings.autopause.focus"), settings.isPauseOnFocusLoss())
        pauseOnFocusCheckbox?.alignmentX = Component.LEFT_ALIGNMENT
        panel.add(pauseOnFocusCheckbox)
        panel.add(Box.createVerticalStrut(8))
        
        pauseOnBranchCheckbox = JBCheckBox(MyBundle.message("settings.autopause.branch"), settings.isPauseOnBranchChange())
        pauseOnBranchCheckbox?.alignmentX = Component.LEFT_ALIGNMENT
        panel.add(pauseOnBranchCheckbox)
        panel.add(Box.createVerticalStrut(8))
        
        pauseOnProjectCheckbox = JBCheckBox(MyBundle.message("settings.autopause.project"), settings.isPauseOnProjectSwitch())
        pauseOnProjectCheckbox?.alignmentX = Component.LEFT_ALIGNMENT
        panel.add(pauseOnProjectCheckbox)
        panel.add(Box.createVerticalStrut(8))
        
        pauseOnSystemSleepCheckbox = JBCheckBox(MyBundle.message("settings.autopause.sleep"), settings.isPauseOnSystemSleep())
        pauseOnSystemSleepCheckbox?.alignmentX = Component.LEFT_ALIGNMENT
        panel.add(pauseOnSystemSleepCheckbox)
        
        return panel
    }
    
    private fun createFieldRow(labelText: String, field: JComponent, hint: String? = null): JPanel {
        val row = JPanel()
        row.layout = BoxLayout(row, BoxLayout.Y_AXIS)
        row.alignmentX = Component.LEFT_ALIGNMENT
        row.isOpaque = false
        
        val label = JBLabel(labelText)
        label.alignmentX = Component.LEFT_ALIGNMENT
        row.add(label)
        row.add(Box.createVerticalStrut(4))
        
        field.alignmentX = Component.LEFT_ALIGNMENT
        if (field is JBTextField || field is JBPasswordField) {
            field.maximumSize = Dimension(400, field.preferredSize.height)
        }
        row.add(field)
        
        if (hint != null) {
            row.add(Box.createVerticalStrut(2))
            val hintLabel = JBLabel(hint)
            hintLabel.foreground = UIUtil.getContextHelpForeground()
            hintLabel.font = hintLabel.font.deriveFont(11f)
            hintLabel.alignmentX = Component.LEFT_ALIGNMENT
            row.add(hintLabel)
        }
        
        return row
    }
    
    override fun isModified(): Boolean {
        return jiraUrlField?.text != settings.getJiraUrl() ||
                String(tokenField?.password ?: charArrayOf()) != (settings.getPersonalAccessToken() ?: "") ||
                pauseOnFocusCheckbox?.isSelected != settings.isPauseOnFocusLoss() ||
                pauseOnBranchCheckbox?.isSelected != settings.isPauseOnBranchChange() ||
                pauseOnProjectCheckbox?.isSelected != settings.isPauseOnProjectSwitch() ||
                pauseOnSystemSleepCheckbox?.isSelected != settings.isPauseOnSystemSleep()
    }
    
    override fun apply() {
        settings.setJiraUrl(jiraUrlField?.text ?: "")
        settings.setPersonalAccessToken(String(tokenField?.password ?: charArrayOf()))
        settings.setPauseOnFocusLoss(pauseOnFocusCheckbox?.isSelected ?: true)
        settings.setPauseOnBranchChange(pauseOnBranchCheckbox?.isSelected ?: true)
        settings.setPauseOnProjectSwitch(pauseOnProjectCheckbox?.isSelected ?: true)
        settings.setPauseOnSystemSleep(pauseOnSystemSleepCheckbox?.isSelected ?: true)
        
        // Hide status label after applying
        connectionStatusLabel?.isVisible = false
    }
    
    override fun reset() {
        jiraUrlField?.text = settings.getJiraUrl()
        tokenField?.text = settings.getPersonalAccessToken() ?: ""
        pauseOnFocusCheckbox?.isSelected = settings.isPauseOnFocusLoss()
        pauseOnBranchCheckbox?.isSelected = settings.isPauseOnBranchChange()
        pauseOnProjectCheckbox?.isSelected = settings.isPauseOnProjectSwitch()
        pauseOnSystemSleepCheckbox?.isSelected = settings.isPauseOnSystemSleep()
        
        // Hide status label on reset
        connectionStatusLabel?.isVisible = false
    }
    
    private fun testConnection() {
        // Validate inputs first
        val url = jiraUrlField?.text?.trim() ?: ""
        val token = String(tokenField?.password ?: charArrayOf()).trim()
        
        if (url.isBlank()) {
            showConnectionStatus(false, MyBundle.message("settings.connection.enter.url"))
            return
        }
        
        if (token.isBlank()) {
            showConnectionStatus(false, MyBundle.message("settings.connection.enter.token"))
            return
        }
        
        // Disable button during test
        testConnectionButton?.isEnabled = false
        connectionStatusLabel?.isVisible = true
        connectionStatusLabel?.text = MyBundle.message("settings.jira.testing")
        connectionStatusLabel?.icon = AllIcons.Process.Step_1
        connectionStatusLabel?.foreground = UIUtil.getLabelForeground()
        
        // Test connection in background
        ProgressManager.getInstance().run(object : Task.Backgroundable(null, "Testing Jira Connection", false) {
            override fun run(indicator: ProgressIndicator) {
                // Temporarily update settings for test
                val originalUrl = settings.getJiraUrl()
                val originalToken = settings.getPersonalAccessToken()
                
                settings.setJiraUrl(url)
                settings.setPersonalAccessToken(token)
                
                val client = JiraApiClient(settings)
                val result = client.testConnection()
                
                // Restore original settings if test failed
                if (!result.isSuccess) {
                    settings.setJiraUrl(originalUrl)
                    settings.setPersonalAccessToken(originalToken ?: "")
                }
                
                SwingUtilities.invokeLater {
                    testConnectionButton?.isEnabled = true
                    if (result.isSuccess) {
                        showConnectionStatus(true, MyBundle.message("settings.connection.success"))
                    } else {
                        showConnectionStatus(false, MyBundle.message("settings.connection.failed", result.exceptionOrNull()?.message ?: ""))
                    }
                }
            }
        })
    }
    
    private fun showConnectionStatus(success: Boolean, message: String) {
        connectionStatusLabel?.isVisible = true
        connectionStatusLabel?.text = message
        connectionStatusLabel?.icon = if (success) AllIcons.General.InspectionsOK else AllIcons.General.Error
        connectionStatusLabel?.foreground = if (success) {
            JBColor(Color(0, 128, 0), Color(0, 200, 0))
        } else {
            JBColor.RED
        }
    }
}
