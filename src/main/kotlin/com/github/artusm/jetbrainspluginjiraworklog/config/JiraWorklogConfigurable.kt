package com.github.artusm.jetbrainspluginjiraworklog.config

import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraApiClient
import com.github.artusm.jetbrainspluginjiraworklog.utils.MyBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.BrowserLink
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.runBlocking
import java.awt.*
import javax.swing.*

/**
 * Settings configuration page for Jira Worklog Timer.
 * Provides configuration for Jira connection and auto-pause features.
 */
class JiraWorklogConfigurable : Configurable {

    private val settings = JiraSettings.getInstance()

    // UI Components
    private lateinit var jiraUrlField: JBTextField
    private lateinit var tokenField: JBPasswordField
    private lateinit var pauseOnFocusCheckbox: JBCheckBox
    private lateinit var pauseOnBranchCheckbox: JBCheckBox
    private lateinit var pauseOnProjectCheckbox: JBCheckBox
    private lateinit var pauseOnSystemSleepCheckbox: JBCheckBox
    private lateinit var testConnectionButton: JButton
    private lateinit var connectionStatusLabel: JBLabel

    override fun getDisplayName(): String = MyBundle.message("settings.title")

    override fun createComponent(): JComponent {
        initializeComponents()
        
        val mainPanel = JPanel(BorderLayout())
        mainPanel.border = JBUI.Borders.empty(20)

        val contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(createHeaderSection())
            add(Box.createVerticalStrut(20))
            add(createJiraConfigSection())
            add(Box.createVerticalStrut(24))
            add(createAutoPauseSection())
            add(Box.createVerticalGlue())
        }

        mainPanel.add(contentPanel, BorderLayout.NORTH)
        return mainPanel
    }

    private fun initializeComponents() {
        jiraUrlField = JBTextField(settings.getJiraUrl(), 40)
        tokenField = JBPasswordField().apply {
            settings.getPersonalAccessToken()?.let { text = it }
        }
        
        pauseOnFocusCheckbox = JBCheckBox(MyBundle.message("settings.autopause.focus"), settings.isPauseOnFocusLoss())
        pauseOnBranchCheckbox = JBCheckBox(MyBundle.message("settings.autopause.branch"), settings.isPauseOnBranchChange())
        pauseOnProjectCheckbox = JBCheckBox(MyBundle.message("settings.autopause.project"), settings.isPauseOnProjectSwitch())
        pauseOnSystemSleepCheckbox = JBCheckBox(MyBundle.message("settings.autopause.sleep"), settings.isPauseOnSystemSleep())
        
        testConnectionButton = JButton(MyBundle.message("settings.jira.test")).apply {
            addActionListener { testConnection() }
        }
        
        connectionStatusLabel = JBLabel("").apply {
            isVisible = false
        }
    }

    override fun isModified(): Boolean {
        return jiraUrlField.text != settings.getJiraUrl() ||
                String(tokenField.password) != (settings.getPersonalAccessToken() ?: "") ||
                pauseOnFocusCheckbox.isSelected != settings.isPauseOnFocusLoss() ||
                pauseOnBranchCheckbox.isSelected != settings.isPauseOnBranchChange() ||
                pauseOnProjectCheckbox.isSelected != settings.isPauseOnProjectSwitch() ||
                pauseOnSystemSleepCheckbox.isSelected != settings.isPauseOnSystemSleep()
    }

    override fun apply() {
        settings.setJiraUrl(jiraUrlField.text)
        settings.setPersonalAccessToken(String(tokenField.password))
        settings.setPauseOnFocusLoss(pauseOnFocusCheckbox.isSelected)
        settings.setPauseOnBranchChange(pauseOnBranchCheckbox.isSelected)
        settings.setPauseOnProjectSwitch(pauseOnProjectCheckbox.isSelected)
        settings.setPauseOnSystemSleep(pauseOnSystemSleepCheckbox.isSelected)

        connectionStatusLabel.isVisible = false
    }

    override fun reset() {
        jiraUrlField.text = settings.getJiraUrl()
        tokenField.text = settings.getPersonalAccessToken() ?: ""
        pauseOnFocusCheckbox.isSelected = settings.isPauseOnFocusLoss()
        pauseOnBranchCheckbox.isSelected = settings.isPauseOnBranchChange()
        pauseOnProjectCheckbox.isSelected = settings.isPauseOnProjectSwitch()
        pauseOnSystemSleepCheckbox.isSelected = settings.isPauseOnSystemSleep()

        connectionStatusLabel.isVisible = false
    }

    // --- UI Construction Helpers ---

    private fun createHeaderSection() = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
        
        add(JBLabel(MyBundle.message("settings.title")).apply {
            font = font.deriveFont(Font.BOLD, 18f)
            alignmentX = Component.LEFT_ALIGNMENT
        })
        add(Box.createVerticalStrut(4))
        add(JBLabel(MyBundle.message("settings.description")).apply {
            foreground = UIUtil.getContextHelpForeground()
            alignmentX = Component.LEFT_ALIGNMENT
        })
    }

    private fun createJiraConfigSection() = createSectionPanel(MyBundle.message("settings.section.jira")).apply {
        add(createFieldRow(MyBundle.message("settings.jira.url.label"), jiraUrlField, MyBundle.message("settings.jira.url.hint")))
        add(Box.createVerticalStrut(12))
        add(createFieldRow(MyBundle.message("settings.jira.token.label"), tokenField, MyBundle.message("settings.jira.token.hint")))
        add(Box.createVerticalStrut(8))
        
        // Token Link
        add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false
            add(BrowserLink(AllIcons.General.Web, MyBundle.message("settings.jira.token.link"), null, "https://id.atlassian.com/manage-profile/security/api-tokens"))
        })
        add(Box.createVerticalStrut(12))
        
        // Test Connection
        add(JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false
            add(testConnectionButton)
            add(connectionStatusLabel)
        })
    }

    private fun createAutoPauseSection() = createSectionPanel(MyBundle.message("settings.section.autopause")).apply {
        add(createCheckboxRow(pauseOnFocusCheckbox))
        add(createCheckboxRow(pauseOnBranchCheckbox))
        add(createCheckboxRow(pauseOnProjectCheckbox))
        add(createCheckboxRow(pauseOnSystemSleepCheckbox)) // Last one doesn't need strut strictly but consistenly fine
    }

    private fun createSectionPanel(title: String): JPanel {
        val container = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        
        container.add(JBLabel(title).apply {
            font = font.deriveFont(Font.BOLD, 14f)
            alignmentX = Component.LEFT_ALIGNMENT
        })
        container.add(Box.createVerticalStrut(8))
        
        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.empty(12, 16)
            background = UIUtil.getPanelBackground()
        }
        container.add(content)
        return content
    }

    private fun createFieldRow(label: String, component: JComponent, hint: String? = null) = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
        isOpaque = false
        
        add(JBLabel(label).apply { alignmentX = Component.LEFT_ALIGNMENT })
        add(Box.createVerticalStrut(4))
        
        component.alignmentX = Component.LEFT_ALIGNMENT
        if (component is JTextField) component.maximumSize = Dimension(400, component.preferredSize.height)
        add(component)
        
        hint?.let {
            add(Box.createVerticalStrut(2))
            add(JBLabel(it).apply {
                foreground = UIUtil.getContextHelpForeground()
                font = font.deriveFont(11f)
                alignmentX = Component.LEFT_ALIGNMENT
            })
        }
    }
    
    private fun createCheckboxRow(checkbox: JBCheckBox) = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
        isOpaque = false
        
        checkbox.alignmentX = Component.LEFT_ALIGNMENT
        add(checkbox)
        add(Box.createVerticalStrut(8))
    }

    // --- Logic ---

    private fun testConnection() {
        val url = jiraUrlField.text.trim()
        val token = String(tokenField.password).trim()

        if (url.isBlank() || token.isBlank()) {
            showConnectionStatus(false, MyBundle.message(if (url.isBlank()) "settings.connection.enter.url" else "settings.connection.enter.token"))
            return
        }

        testConnectionButton.isEnabled = false
        showConnectionStatus(true, MyBundle.message("settings.jira.testing"), isProcessing = true)

        ProgressManager.getInstance().run(object : Task.Backgroundable(null, "Testing Jira Connection", false) {
            override fun run(indicator: ProgressIndicator) {
                // Create restricted config just for this test
                val tempConfig = object : JiraConfig {
                    override fun getJiraUrl(): String = url
                    override fun getPersonalAccessToken(): String = token
                }

                val result = runBlocking {
                    JiraApiClient(tempConfig).testConnection()
                }

                SwingUtilities.invokeLater {
                    testConnectionButton.isEnabled = true
                    if (result.isSuccess) {
                         showConnectionStatus(true, MyBundle.message("settings.connection.success"))
                    } else {
                         showConnectionStatus(false, MyBundle.message("settings.connection.failed", result.exceptionOrNull()?.message ?: ""))
                    }
                }
            }
        })
    }

    private fun showConnectionStatus(success: Boolean, message: String, isProcessing: Boolean = false) {
        connectionStatusLabel.isVisible = true
        connectionStatusLabel.text = message
        connectionStatusLabel.icon = when {
            isProcessing -> AllIcons.Process.Step_1
            success -> AllIcons.General.InspectionsOK
            else -> AllIcons.General.Error
        }
        connectionStatusLabel.foreground = if (success && !isProcessing) JBColor(Color(0, 128, 0), Color(0, 200, 0)) else if (isProcessing) UIUtil.getLabelForeground() else JBColor.RED
    }
}
