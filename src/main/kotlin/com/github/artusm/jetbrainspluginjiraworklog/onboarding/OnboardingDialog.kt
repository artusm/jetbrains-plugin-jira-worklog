package com.github.artusm.jetbrainspluginjiraworklog.onboarding

import com.github.artusm.jetbrainspluginjiraworklog.config.JiraSettings
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraApiClient
import com.github.artusm.jetbrainspluginjiraworklog.utils.MyBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.net.URL
import javax.swing.*

/**
 * Modern onboarding dialog with beautiful UI/UX for configuring Jira credentials.
 */
class OnboardingDialog(private val project: Project) : DialogWrapper(project) {
    
    companion object {
        fun show(project: Project) {
            val dialog = OnboardingDialog(project)
            dialog.show()
        }
    }
    
    private val settings = JiraSettings.getInstance()
    
    private val jiraUrlField = JBTextField(40)
    private val tokenField = JBPasswordField()
    private val urlValidationLabel = JBLabel()
    private val feedbackPanel = JPanel()
    
    init {
        title = MyBundle.message("onboarding.title")
        init()
        setupValidation()
    }
    
    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout(JBUI.scale(20), JBUI.scale(20)))
        mainPanel.border = JBUI.Borders.empty(16)
        mainPanel.background = UIUtil.getPanelBackground()
        
        // Header Panel
        mainPanel.add(createHeaderPanel(), BorderLayout.NORTH)
        
        // Content Panel
        mainPanel.add(createContentPanel(), BorderLayout.CENTER)
        
        // Feedback Panel
        mainPanel.add(feedbackPanel, BorderLayout.SOUTH)
        feedbackPanel.isVisible = false
        
        return mainPanel
    }
    
    private fun createHeaderPanel(): JPanel {
        val headerPanel = JPanel(BorderLayout(JBUI.scale(12), 0))
        headerPanel.border = JBUI.Borders.empty(0, 0, 20, 0)
        headerPanel.background = UIUtil.getPanelBackground()
        
        // Icon
        val iconLabel = JBLabel(AllIcons.Toolwindows.ToolWindowMessages)
        headerPanel.add(iconLabel, BorderLayout.WEST)
        
        // Text content
        val textPanel = JPanel()
        textPanel.layout = BoxLayout(textPanel, BoxLayout.Y_AXIS)
        textPanel.background = UIUtil.getPanelBackground()
        
        val titleLabel = JBLabel(MyBundle.message("onboarding.title"))
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 20f)
        titleLabel.foreground = UIUtil.getLabelForeground()
        
        val descLabel = JBLabel("<html>${MyBundle.message("onboarding.description")}</html>")
        descLabel.foreground = UIUtil.getLabelInfoForeground()
        descLabel.border = JBUI.Borders.empty(4, 0, 0, 0)
        
        textPanel.add(titleLabel)
        textPanel.add(descLabel)
        
        headerPanel.add(textPanel, BorderLayout.CENTER)
        
        return headerPanel
    }
    
    private fun createContentPanel(): JPanel {
        val contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        contentPanel.background = UIUtil.getPanelBackground()
        
        // Jira URL Section
        contentPanel.add(createSection(
            MyBundle.message("onboarding.url.label"),
            MyBundle.message("onboarding.url.description"),
            jiraUrlField,
            urlValidationLabel
        ))
        
        contentPanel.add(Box.createVerticalStrut(JBUI.scale(24)))
        
        // Token Section
        contentPanel.add(createSection(
            MyBundle.message("onboarding.token.label"),
            MyBundle.message("onboarding.token.description"),
            tokenField,
            null
        ))
        
        // Help link
        contentPanel.add(Box.createVerticalStrut(JBUI.scale(8)))
        contentPanel.add(createTokenHelpPanel())
        
        contentPanel.add(Box.createVerticalStrut(JBUI.scale(16)))
        
        // Info panel
        contentPanel.add(createInfoPanel())
        
        return contentPanel
    }
    
    private fun createSection(
        title: String, 
        description: String, 
        field: JTextField,
        validationLabel: JBLabel?
    ): JPanel {
        val section = JPanel()
        section.layout = BoxLayout(section, BoxLayout.Y_AXIS)
        section.background = UIUtil.getPanelBackground()
        section.alignmentX = Component.LEFT_ALIGNMENT
        
        // Title
        val titleLabel = JBLabel(title)
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD)
        titleLabel.foreground = UIUtil.getLabelForeground()
        titleLabel.alignmentX = Component.LEFT_ALIGNMENT
        section.add(titleLabel)
        
        section.add(Box.createVerticalStrut(JBUI.scale(4)))
        
        // Description
        val descLabel = JBLabel("<html>$description</html>")
        descLabel.foreground = UIUtil.getLabelInfoForeground()
        descLabel.font = descLabel.font.deriveFont(11f)
        descLabel.alignmentX = Component.LEFT_ALIGNMENT
        section.add(descLabel)
        
        section.add(Box.createVerticalStrut(JBUI.scale(6)))
        
        // Field with validation
        val fieldPanel = JPanel(BorderLayout(JBUI.scale(4), 0))
        fieldPanel.background = UIUtil.getPanelBackground()
        fieldPanel.alignmentX = Component.LEFT_ALIGNMENT
        fieldPanel.maximumSize = Dimension(Int.MAX_VALUE, field.preferredSize.height)
        
        field.putClientProperty("JTextField.placeholderText", if (field == jiraUrlField) {
            MyBundle.message("onboarding.url.placeholder")
        } else {
            MyBundle.message("onboarding.token.placeholder")
        })
        
        fieldPanel.add(field, BorderLayout.CENTER)
        
        if (validationLabel != null) {
            validationLabel.border = JBUI.Borders.empty(0, 4, 0, 0)
            fieldPanel.add(validationLabel, BorderLayout.EAST)
        }
        
        section.add(fieldPanel)
        
        return section
    }
    
    private fun createTokenHelpPanel(): JPanel {
        val helpPanel = JPanel(HorizontalLayout(JBUI.scale(6)))
        helpPanel.background = UIUtil.getPanelBackground()
        helpPanel.alignmentX = Component.LEFT_ALIGNMENT
        
        val iconLabel = JBLabel(AllIcons.General.ContextHelp)
        helpPanel.add(iconLabel)
        
        val linkLabel = JBLabel("<html><a href=''>${MyBundle.message("onboarding.help.link")}</a></html>")
        linkLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        linkLabel.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                BrowserUtil.browse("https://id.atlassian.com/manage-profile/security/api-tokens")
            }
        })
        helpPanel.add(linkLabel)
        
        return helpPanel
    }
    
    private fun createInfoPanel(): JPanel {
        val infoPanel = JPanel(BorderLayout(JBUI.scale(12), 0))
        infoPanel.background = JBColor.lazy { 
            if (UIUtil.isUnderDarcula()) JBColor(0x2D3748, 0x2D3748) 
            else JBColor(0xEBF5FF, 0xEBF5FF)
        }
        infoPanel.border = JBUI.Borders.empty(12)
        infoPanel.alignmentX = Component.LEFT_ALIGNMENT
        
        val iconLabel = JBLabel(AllIcons.General.BalloonInformation)
        infoPanel.add(iconLabel, BorderLayout.WEST)
        
        val textLabel = JBLabel("<html><b>Privacy:</b> ${MyBundle.message("onboarding.privacy.message")}</html>")
        textLabel.foreground = UIUtil.getLabelForeground()
        textLabel.font = textLabel.font.deriveFont(11f)
        infoPanel.add(textLabel, BorderLayout.CENTER)
        
        return infoPanel
    }
    
    private fun setupValidation() {
        jiraUrlField.addKeyListener(object : KeyAdapter() {
            override fun keyReleased(e: KeyEvent) {
                validateUrl()
            }
        })
        
        jiraUrlField.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                validateUrl()
            }
        })
    }
    
    private fun validateUrl() {
        val url = jiraUrlField.text.trim()
        
        if (url.isEmpty()) {
            urlValidationLabel.icon = null
            urlValidationLabel.text = ""
            return
        }
        
        try {
            val parsedUrl = URL(url)
            
            when {
                !parsedUrl.protocol.equals("https", ignoreCase = true) -> {
                    urlValidationLabel.icon = AllIcons.General.Warning
                    urlValidationLabel.foreground = JBColor.ORANGE
                    urlValidationLabel.toolTipText = MyBundle.message("validation.url.https.warning")
                }
                !url.contains("atlassian.net") && !url.contains("jira") -> {
                    urlValidationLabel.icon = AllIcons.General.Warning
                    urlValidationLabel.foreground = JBColor.ORANGE
                    urlValidationLabel.toolTipText = MyBundle.message("validation.url.format.warning")
                }
                else -> {
                    urlValidationLabel.icon = AllIcons.General.InspectionsOK
                    urlValidationLabel.foreground = JBColor.GREEN
                    urlValidationLabel.toolTipText = MyBundle.message("validation.url.format.ok")
                }
            }
        } catch (e: Exception) {
            urlValidationLabel.icon = AllIcons.General.Error
            urlValidationLabel.foreground = JBColor.RED
            urlValidationLabel.toolTipText = MyBundle.message("validation.url.invalid")
        }
    }
    
    private fun showFeedback(message: String, isError: Boolean) {
        feedbackPanel.removeAll()
        feedbackPanel.layout = BorderLayout(JBUI.scale(8), 0)
        feedbackPanel.background = if (isError) {
            JBColor.lazy { 
                if (UIUtil.isUnderDarcula()) JBColor(0x5C2626, 0x5C2626)
                else JBColor(0xFFF5F5, 0xFFF5F5)
            }
        } else {
            JBColor.lazy { 
                if (UIUtil.isUnderDarcula()) JBColor(0x1E4620, 0x1E4620)
                else JBColor(0xF0FFF4, 0xF0FFF4)
            }
        }
        feedbackPanel.border = JBUI.Borders.empty(12)
        
        val icon = if (isError) AllIcons.General.Error else AllIcons.General.InspectionsOK
        feedbackPanel.add(JBLabel(icon), BorderLayout.WEST)
        
        val textLabel = JBLabel("<html>$message</html>")
        textLabel.foreground = if (isError) JBColor.RED else JBColor.GREEN
        feedbackPanel.add(textLabel, BorderLayout.CENTER)
        
        feedbackPanel.isVisible = true
        feedbackPanel.revalidate()
        feedbackPanel.repaint()
    }
    
    private fun hideFeedback() {
        feedbackPanel.isVisible = false
    }
    
    override fun doOKAction() {
        val url = jiraUrlField.text.trim()
        val token = String(tokenField.password).trim()
        
        hideFeedback()
        
        if (url.isBlank()) {
            showFeedback(MyBundle.message("validation.url.missing"), true)
            jiraUrlField.requestFocus()
            return
        }
        
        if (token.isBlank()) {
            showFeedback(MyBundle.message("validation.token.missing"), true)
            tokenField.requestFocus()
            return
        }
        
        // Save credentials
        settings.setJiraUrl(url)
        settings.setPersonalAccessToken(token)
        
        // Test connection asynchronously
        ProgressManager.getInstance().run(object : Task.Modal(project, MyBundle.message("connection.testing"), true) {
            private var result: Result<Boolean>? = null
            
            override fun run(indicator: ProgressIndicator) {
                indicator.text = MyBundle.message("connection.connecting", url)
                indicator.isIndeterminate = true
                
                val client = JiraApiClient(settings)
                result = client.testConnection()
            }
            
            override fun onSuccess() {
                result?.let { r ->
                    if (r.isSuccess) {
                        showFeedback(MyBundle.message("connection.success"), false)
                        // Close dialog after a brief delay
                        Timer(1000) { 
                            SwingUtilities.invokeLater {
                                close(OK_EXIT_CODE)
                            }
                        }.apply { 
                            isRepeats = false 
                            start() 
                        }
                    } else {
                        val errorMsg = r.exceptionOrNull()?.message ?: "Unknown error"
                        val troubleshooting = when {
                            errorMsg.contains("401") || errorMsg.contains("Unauthorized") -> 
                                MyBundle.message("onboarding.error.tip.401")
                            errorMsg.contains("404") || errorMsg.contains("Not Found") -> 
                                MyBundle.message("onboarding.error.tip.404")
                            errorMsg.contains("timeout") || errorMsg.contains("connect") -> 
                                MyBundle.message("onboarding.error.tip.timeout")
                            else -> ""
                        }
                        showFeedback(MyBundle.message("onboarding.error.failed", errorMsg) + troubleshooting, true)
                    }
                }
            }
            
            override fun onThrowable(error: Throwable) {
                showFeedback(MyBundle.message("onboarding.error.unexpected", error.message ?: ""), true)
            }
        })
    }
    
    override fun createActions(): Array<Action> {
        return arrayOf(okAction, cancelAction)
    }
    
    override fun getPreferredFocusedComponent(): JComponent {
        return jiraUrlField
    }
}
