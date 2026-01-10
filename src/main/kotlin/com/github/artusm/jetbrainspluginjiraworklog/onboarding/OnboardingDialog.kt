package com.github.artusm.jetbrainspluginjiraworklog.onboarding

import com.github.artusm.jetbrainspluginjiraworklog.config.JiraSettings
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraApiClient
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.event.ActionEvent
import javax.swing.*

/**
 * Onboarding dialog shown on first run to configure Jira credentials.
 */
class OnboardingDialog(project: Project) : DialogWrapper(project) {
    
    companion object {
        fun show(project: Project) {
            val dialog = OnboardingDialog(project)
            dialog.show()
        }
    }
    
    private val settings = JiraSettings.getInstance()
    
    private val jiraUrlField = JBTextField(40)
    private val tokenField = JBPasswordField()
    
    init {
        title = "Welcome to Jira Worklog Timer"
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = FormBuilder.createFormBuilder()
            .addComponent(JLabel("<html><h2>Welcome to Jira Worklog Timer</h2>" +
                    "<p>To get started, please configure your Jira credentials.</p></html>"))
            .addSeparator()
            .addLabeledComponent(JLabel("Jira Instance URL:"), jiraUrlField)
            .addComponentToRightColumn(JLabel("<html><i>Example: https://company.atlassian.net</i></html>"))
            .addSeparator()
            .addLabeledComponent(JLabel("Personal Access Token:"), tokenField)
            .addComponentToRightColumn(createTokenHelpLink())
            .addComponentFillVertically(JPanel(), 0)
            .panel
        
        return panel
    }
    
    private fun createTokenHelpLink(): JComponent {
        val linkLabel = JLabel("<html><a href=''>How to generate a Personal Access Token</a></html>")
        linkLabel.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                BrowserUtil.browse("https://id.atlassian.com/manage-profile/security/api-tokens")
            }
        })
        return linkLabel
    }
    
    override fun doOKAction() {
        val url = jiraUrlField.text.trim()
        val token = String(tokenField.password).trim()
        
        if (url.isBlank()) {
            Messages.showErrorDialog(
                "Please enter your Jira instance URL",
                "Missing URL"
            )
            return
        }
        
        if (token.isBlank()) {
            Messages.showErrorDialog(
                "Please enter your Personal Access Token",
                "Missing Token"
            )
            return
        }
        
        // Save credentials
        settings.setJiraUrl(url)
        settings.setPersonalAccessToken(token)
        
        // Test connection
        val client = JiraApiClient(settings)
        val result = client.testConnection()
        
        if (result.isSuccess) {
            Messages.showInfoMessage(
                "Successfully connected to Jira! You can now start tracking time.",
                "Connection Successful"
            )
            super.doOKAction()
        } else {
            Messages.showErrorDialog(
                "Failed to connect to Jira:\n${result.exceptionOrNull()?.message}\n\nPlease check your credentials and try again.",
                "Connection Failed"
            )
        }
    }
    
    override fun createActions(): Array<Action> {
        return arrayOf(okAction, cancelAction)
    }
}
