package com.github.artusm.jetbrainspluginjiraworklog.config

import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraApiClient
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Settings page for Jira Worklog Timer configuration.
 */
class JiraWorklogConfigurable : Configurable {
    
    private val settings = JiraSettings.getInstance()
    
    private var jiraUrlField: JBTextField? = null
    private var tokenField: JBPasswordField? = null
    private var pauseOnFocusCheckbox: JCheckBox? = null
    private var pauseOnBranchCheckbox: JCheckBox? = null
    private var pauseOnProjectCheckbox: JCheckBox? = null
    
    override fun getDisplayName(): String = "Jira Worklog Timer"
    
    override fun createComponent(): JComponent {
        val panel = JPanel(BorderLayout())
        
        // Jira URL field
        jiraUrlField = JBTextField(settings.getJiraUrl(), 40)
        
        // Token field
        tokenField = JBPasswordField()
        settings.getPersonalAccessToken()?.let { tokenField?.text = it }
        
        // Auto-pause checkboxes
        pauseOnFocusCheckbox = JCheckBox("Pause timer when IDE window loses focus", settings.isPauseOnFocusLoss())
        pauseOnBranchCheckbox = JCheckBox("Pause timer when switching Git branches", settings.isPauseOnBranchChange())
        pauseOnProjectCheckbox = JCheckBox("Pause timer when switching projects", settings.isPauseOnProjectSwitch())
        
        val formBuilder = FormBuilder.createFormBuilder()
            .addLabeledComponent(JLabel("Jira Instance URL:"), jiraUrlField!!)
            .addComponentToRightColumn(JLabel("Example: https://company.atlassian.net"))
            .addSeparator()
            .addLabeledComponent(JLabel("Personal Access Token:"), tokenField!!)
            .addComponentToRightColumn(JLabel("<html><a href='https://id.atlassian.com/manage-profile/security/api-tokens'>Generate token</a></html>"))
            .addSeparator()
            .addComponent(JLabel("Auto-Pause Settings:"))
            .addComponent(pauseOnFocusCheckbox!!)
            .addComponent(pauseOnBranchCheckbox!!)
            .addComponent(pauseOnProjectCheckbox!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        
        panel.add(formBuilder, BorderLayout.NORTH)
        
        return panel
    }
    
    override fun isModified(): Boolean {
        return jiraUrlField?.text != settings.getJiraUrl() ||
                String(tokenField?.password ?: charArrayOf()) != (settings.getPersonalAccessToken() ?: "") ||
                pauseOnFocusCheckbox?.isSelected != settings.isPauseOnFocusLoss() ||
                pauseOnBranchCheckbox?.isSelected != settings.isPauseOnBranchChange() ||
                pauseOnProjectCheckbox?.isSelected != settings.isPauseOnProjectSwitch()
    }
    
    override fun apply() {
        settings.setJiraUrl(jiraUrlField?.text ?: "")
        settings.setPersonalAccessToken(String(tokenField?.password ?: charArrayOf()))
        settings.setPauseOnFocusLoss(pauseOnFocusCheckbox?.isSelected ?: true)
        settings.setPauseOnBranchChange(pauseOnBranchCheckbox?.isSelected ?: true)
        settings.setPauseOnProjectSwitch(pauseOnProjectCheckbox?.isSelected ?: true)
        
        // Test connection if credentials are provided
        if (settings.hasCredentials()) {
            testConnection()
        }
    }
    
    override fun reset() {
        jiraUrlField?.text = settings.getJiraUrl()
        tokenField?.text = settings.getPersonalAccessToken() ?: ""
        pauseOnFocusCheckbox?.isSelected = settings.isPauseOnFocusLoss()
        pauseOnBranchCheckbox?.isSelected = settings.isPauseOnBranchChange()
        pauseOnProjectCheckbox?.isSelected = settings.isPauseOnProjectSwitch()
    }
    
    private fun testConnection() {
        val client = JiraApiClient(settings)
        val result = client.testConnection()
        
        if (result.isSuccess) {
            Messages.showInfoMessage(
                "Successfully connected to Jira!",
                "Connection Test"
            )
        } else {
            Messages.showErrorDialog(
                "Failed to connect to Jira: ${result.exceptionOrNull()?.message}",
                "Connection Test Failed"
            )
        }
    }
}
