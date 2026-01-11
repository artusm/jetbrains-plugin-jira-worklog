package com.github.artusm.jetbrainspluginjiraworklog.ui

import com.github.artusm.jetbrainspluginjiraworklog.services.JiraOfflineWorklogService
import com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogPersistentState
import com.github.artusm.jetbrainspluginjiraworklog.utils.MyBundle
import com.github.artusm.jetbrainspluginjiraworklog.utils.TimeFormatter
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.border.MatteBorder

/**
 * Popup to show pending worklogs waiting for network connection.
 */
class PendingWorklogsPopupContent(private val project: Project) : JPanel(BorderLayout()) {

    var popup: JBPopup? = null

    private val persistentState = project.service<JiraWorklogPersistentState>()
    private val offlineService = project.service<JiraOfflineWorklogService>()

    init {
        border = JBUI.Borders.empty(12)
        preferredSize = Dimension(400, 300)

        val headerPanel = createHeaderPanel()
        add(headerPanel, BorderLayout.NORTH)

        val contentPanel = createContentPanel()
        add(contentPanel, BorderLayout.CENTER)

        val footerPanel = createFooterPanel()
        add(footerPanel, BorderLayout.SOUTH)
    }

    private fun createHeaderPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.emptyBottom(10)

        val titleLabel = JBLabel("Pending Worklogs") // TODO: Localize
        titleLabel.font = UIUtil.getLabelFont().deriveFont(java.awt.Font.BOLD, 14f)

        val count = persistentState.getPendingWorklogs().size
        val subtitleLabel = JBLabel("$count waiting for connection") // TODO: Localize
        subtitleLabel.foreground = UIUtil.getContextHelpForeground()

        panel.add(titleLabel, BorderLayout.NORTH)
        panel.add(subtitleLabel, BorderLayout.SOUTH)

        return panel
    }

    private fun createContentPanel(): JComponent {
        val pendingLogs = persistentState.getPendingWorklogs()

        if (pendingLogs.isEmpty()) {
            val emptyLabel = JBLabel("No pending worklogs", SwingConstants.CENTER) // TODO: Localize
            emptyLabel.foreground = UIUtil.getLabelDisabledForeground()
            return emptyLabel
        }

        val listPanel = JPanel()
        listPanel.layout = BoxLayout(listPanel, BoxLayout.Y_AXIS)
        listPanel.background = UIUtil.getListBackground()

        pendingLogs.forEach { log ->
            listPanel.add(createWorklogItem(log))
        }

        val scrollPane = JBScrollPane(listPanel)
        scrollPane.border = JBUI.Borders.customLine(JBColor.border(), 1)

        return scrollPane
    }

    private fun createWorklogItem(log: JiraWorklogPersistentState.PendingWorklog): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = MatteBorder(0, 0, 1, 0, JBColor.border())
        panel.background = UIUtil.getListBackground()
        panel.maximumSize = Dimension(Int.MAX_VALUE, 60)

        val leftPanel = JPanel(BorderLayout())
        leftPanel.isOpaque = false
        leftPanel.border = JBUI.Borders.empty(8)

        val issueLabel = JBLabel(log.issueKey)
        issueLabel.font = UIUtil.getLabelFont().deriveFont(java.awt.Font.BOLD)

        val timeLabel = JBLabel(TimeFormatter.formatJira(log.timeSpentSeconds * 1000L))
        timeLabel.border = JBUI.Borders.emptyLeft(8)

        val infoPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        infoPanel.isOpaque = false
        infoPanel.add(issueLabel)
        infoPanel.add(timeLabel)

        val commentLabel = JBLabel(log.comment ?: "No comment") // TODO: Localize
        commentLabel.foreground = UIUtil.getContextHelpForeground()

        leftPanel.add(infoPanel, BorderLayout.NORTH)
        leftPanel.add(commentLabel, BorderLayout.CENTER)

        panel.add(leftPanel, BorderLayout.CENTER)

        // Delete button
        val deleteButton = JButton(AllIcons.Actions.Cancel)
        deleteButton.isBorderPainted = false
        deleteButton.isContentAreaFilled = false
        deleteButton.preferredSize = Dimension(30, 30)
        deleteButton.addActionListener {
            persistentState.removePendingWorklog(log)
            popup?.cancel() // Simple refresh by closing. Could be better.
        }
        panel.add(deleteButton, BorderLayout.EAST)

        return panel
    }

    private fun createFooterPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.RIGHT))
        panel.border = JBUI.Borders.emptyTop(10)

        val retryButton = JButton("Retry Now") // TODO: Localize
        retryButton.addActionListener {
            popup?.cancel()
            offlineService.retryPendingWorklogs()
        }

        panel.add(retryButton)
        return panel
    }
}
