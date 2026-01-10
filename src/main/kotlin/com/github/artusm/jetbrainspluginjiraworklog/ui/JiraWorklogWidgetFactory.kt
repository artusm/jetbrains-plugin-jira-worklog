package com.github.artusm.jetbrainspluginjiraworklog.ui

import com.github.artusm.jetbrainspluginjiraworklog.services.JiraWorklogTimerService
import com.github.artusm.jetbrainspluginjiraworklog.utils.MyBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NotNull

/**
 * Factory for creating JiraWorklogWidget instances.
 */
class JiraWorklogWidgetFactory : StatusBarWidgetFactory {

    @NotNull
    override fun getId(): String {
        return JiraWorklogWidget.ID
    }

    @Nls
    @NotNull
    override fun getDisplayName(): String {
        return MyBundle.message("widget.display.name")
    }

    override fun isAvailable(@NotNull project: Project): Boolean {
        return project.getService(JiraWorklogTimerService::class.java) != null
    }

    @NotNull
    override fun createWidget(@NotNull project: Project): StatusBarWidget {
        return project.getService(JiraWorklogTimerService::class.java).widget()
    }

    override fun disposeWidget(@NotNull widget: StatusBarWidget) {
        // Widget disposal is handled by the service
    }

    override fun canBeEnabledOn(@NotNull statusBar: StatusBar): Boolean {
        return true
    }
}
