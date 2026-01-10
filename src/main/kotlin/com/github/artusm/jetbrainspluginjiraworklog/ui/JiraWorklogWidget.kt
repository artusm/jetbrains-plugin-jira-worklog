package com.github.artusm.jetbrainspluginjiraworklog.ui

import com.github.artusm.jetbrainspluginjiraworklog.model.TimeTrackingStatus
import com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogTimerService
import com.github.artusm.jetbrainspluginjiraworklog.utils.TimeFormatter
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NotNull
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JButton

/**
 * Status bar widget displaying the Jira worklog timer.
 * Adapted from the reference TimeTrackerWidget.
 */
class JiraWorklogWidget(
    @NotNull private val service: JiraWorklogTimerService,
    private val project: Project
) : JButton(), CustomStatusBarWidget {

    companion object {
        const val ID = "com.github.artusm.JiraWorklogWidget"
        
        private val SETTINGS_ICON: Icon = AllIcons.General.Settings
        private val START_ICON: Icon = AllIcons.Actions.Resume
        private val STOP_ICON: Icon = AllIcons.Actions.Pause
        private val COMMIT_ICON: Icon = AllIcons.Actions.Commit
        
        private val WIDGET_FONT = JBUI.Fonts.label(11f)
        
        // Status colors
        private val COLOR_OFF = JBColor(Color(189, 0, 16), Color(128, 0, 0))
        private val COLOR_ON = JBColor(Color(28, 152, 19), Color(56, 113, 41))
        private val COLOR_IDLE = JBColor(Color(200, 164, 23), Color(163, 112, 17))
        
        // Hover colors
        private val COLOR_MENU_OFF = JBColor(Color(198, 88, 97), Color(97, 38, 38))
        private val COLOR_MENU_ON = JBColor(Color(133, 194, 130), Color(55, 80, 48))
    }

    private var mouseInside = false

    init {
        // Register with service
        service.registerWidget(this)
        
        // Configure button
        border = JBUI.CurrentTheme.StatusBar.Widget.border()
        isOpaque = false
        isFocusable = false
        
        // Mouse listeners for hover effect
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                mouseInside = true
                repaint()
            }

            override fun mouseExited(e: MouseEvent) {
                mouseInside = false
                repaint()
            }
            
            override fun mouseClicked(e: MouseEvent) {
                handleClick(e)
            }
        })
    }

    private fun handleClick(e: MouseEvent) {
        val width = getWidth()
        val insets = getInsets()
        val totalBarLength = width - insets.left - insets.right
        
        // Divide the widget into three sections
        val sectionWidth = totalBarLength / 3
        val clickX = e.x - insets.left
        
        when {
            clickX <= sectionWidth -> {
                // Left section: Start/Stop
                service.toggleRunning()
            }
            clickX <= sectionWidth * 2 -> {
                // Middle section: Commit
                showCommitDialog()
            }
            else -> {
                // Right section: Settings
                showSettings()
            }
        }
    }

    private fun showCommitDialog() {
        ApplicationManager.getApplication().invokeLater {
            CommitWorklogDialog.show(project)
        }
    }

    private fun showSettings() {
        // TODO: Implement settings popup/dialog
        // For now, just a placeholder
        println("Settings - to be implemented")
    }

    override fun ID(): String = ID

    override fun install(@NotNull statusBar: StatusBar) {
        // No special installation needed
    }

    override fun dispose() {
        // Cleanup if needed
    }

    override fun getComponent(): JButton = this

    override fun paintComponent(g: Graphics) {
        val timeToShow = service.getTotalTimeMs()
        val info = TimeFormatter.formatDisplay(timeToShow)

        val size = getSize()
        val insets = getInsets()

        val totalBarLength = size.width - insets.left - insets.right
        val barHeight = maxOf(size.height, font.size + 2)
        val yOffset = (size.height - barHeight) / 2
        val xOffset = insets.left

        // Set background color based on status
        val status = service.getStatus()
        if (mouseInside) {
            g.color = if (status == TimeTrackingStatus.RUNNING) {
                COLOR_MENU_ON
            } else {
                COLOR_MENU_OFF
            }
        } else {
            g.color = when (status) {
                TimeTrackingStatus.RUNNING -> COLOR_ON
                TimeTrackingStatus.IDLE -> COLOR_IDLE
                TimeTrackingStatus.STOPPED -> COLOR_OFF
            }
        }
        
        g.fillRect(insets.left, insets.bottom, totalBarLength, size.height - insets.bottom - insets.top)
        UISettings.setupAntialiasing(g)

        if (mouseInside) {
            // Draw icons in three sections
            val sectionWidth = totalBarLength / 3
            
            // Draw dividers
            g.color = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
            g.drawLine(xOffset + sectionWidth, yOffset, xOffset + sectionWidth, yOffset + barHeight)
            g.drawLine(xOffset + sectionWidth * 2, yOffset, xOffset + sectionWidth * 2, yOffset + barHeight)
            
            // First icon: Start/Stop
            val firstIcon = if (status == TimeTrackingStatus.RUNNING) STOP_ICON else START_ICON
            firstIcon.paintIcon(
                this, g,
                xOffset + (sectionWidth - firstIcon.iconWidth) / 2,
                yOffset + (barHeight - firstIcon.iconHeight) / 2
            )
            
            // Second icon: Commit
            COMMIT_ICON.paintIcon(
                this, g,
                xOffset + sectionWidth + (sectionWidth - COMMIT_ICON.iconWidth) / 2,
                yOffset + (barHeight - COMMIT_ICON.iconHeight) / 2
            )
            
            // Third icon: Settings
            SETTINGS_ICON.paintIcon(
                this, g,
                xOffset + sectionWidth * 2 + (sectionWidth - SETTINGS_ICON.iconWidth) / 2,
                yOffset + (barHeight - SETTINGS_ICON.iconHeight) / 2
            )
        } else {
            // Draw time text
            val fg = if (model.isPressed) {
                UIUtil.getLabelDisabledForeground()
            } else {
                JBColor.foreground()
            }
            g.color = fg
            g.font = WIDGET_FONT
            
            val fontMetrics = g.fontMetrics
            val infoWidth = fontMetrics.stringWidth(info)
            val infoHeight = fontMetrics.ascent
            
            g.drawString(
                info,
                xOffset + (totalBarLength - infoWidth) / 2,
                yOffset + infoHeight + (barHeight - infoHeight) / 2 - 1
            )
        }
    }

    override fun getPreferredSize(): Dimension {
        val widgetFont = WIDGET_FONT
        val fontMetrics = getFontMetrics(widgetFont)
        
        // Use a sample time string to determine size
        val sampleTime = "99 hrs 59 min"
        val stringWidth = fontMetrics.stringWidth(sampleTime)

        val insets = getInsets()
        val width = stringWidth + insets.left + insets.right + JBUI.scale(2)
        val height = fontMetrics.height + insets.top + insets.bottom + JBUI.scale(2)
        
        return Dimension(width, height)
    }

    override fun getMinimumSize(): Dimension = preferredSize

    override fun getMaximumSize(): Dimension = preferredSize
}
