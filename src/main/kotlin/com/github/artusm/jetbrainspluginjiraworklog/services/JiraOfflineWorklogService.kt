package com.github.artusm.jetbrainspluginjiraworklog.services

import com.github.artusm.jetbrainspluginjiraworklog.config.JiraSettings
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraApiClient
import com.github.artusm.jetbrainspluginjiraworklog.jira.JiraWorklogResponse
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.runBlocking
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

@Service(Service.Level.PROJECT)
class JiraOfflineWorklogService(private val project: Project) : Disposable {

    companion object {
        private val LOG = Logger.getInstance(JiraOfflineWorklogService::class.java)
        private const val RETRY_INTERVAL_MINUTES = 1L
    }

    private val persistentState: JiraWorklogPersistentState = project.service()
    private val settings: JiraSettings = JiraSettings.getInstance()
    private val jiraClient: JiraApiClient = JiraApiClient(settings)

    private var retryFuture: ScheduledFuture<*>? = null

    init {
        startRetryScheduler()
    }

    /**
     * Submit worklog. If network error occurs, queue it for later.
     */
    suspend fun submitWorklog(
        issueKey: String,
        timeSpentSeconds: Int,
        comment: String?,
        started: String
    ): Result<JiraWorklogResponse?> {

        // Try direct submission
        // jiraClient.submitWorklog is now likely suspend, based on compilation error
        // But if it wasn't updated in my file, it means it was updated in the conflict resolution
        // or upstream.
        // The error said: "Suspend function ... can only be called from a coroutine..."
        // which implies jiraClient.submitWorklog IS suspend or calls something suspend.
        // Wait, NO. The error was about THIS function `submitWorklog` in THIS class.
        // "Suspend function 'suspend fun submitWorklog ...' can only be called from a coroutine..."
        // This error was likely from JiraWorklogRepository calling THIS function, OR
        // from inside THIS function calling another suspend function?
        // Let's re-read the error:
        // `e: .../JiraOfflineWorklogService.kt:50:33 Suspend function 'suspend fun submitWorklog...' can only be called from a coroutine...`
        // Line 50 is likely inside `submitWorklog`? No, 50 is the declaration?
        // Ah, if I look at my previous `write_file`, the function was NOT `suspend`.
        // So `JiraWorklogRepository` called `offlineService.submitWorklog(...)`.
        // If `JiraWorklogRepository.submitWorklog` IS suspend, it can call non-suspend functions.
        // BUT if `JiraApiClient.submitWorklog` became suspend, then `JiraOfflineWorklogService` (which calls it) must also be suspend or launch a coroutine.
        // The error `e: .../JiraOfflineWorklogService.kt:50:33` suggests the call TO `jiraClient.submitWorklog` failed because `jiraClient.submitWorklog` IS suspend, but `JiraOfflineWorklogService.submitWorklog` WAS NOT.

        val result = jiraClient.submitWorklog(issueKey, timeSpentSeconds, comment, started)

        if (result.isSuccess) {
            return Result.success(result.getOrNull())
        }

        val error = result.exceptionOrNull()

        // Check if error is network/DNS related
        if (isNetworkError(error)) {
            queueWorklog(issueKey, timeSpentSeconds, comment, started)
            return Result.failure(Exception("OFFLINE_QUEUED"))
        }

        return Result.failure(error ?: Exception("Unknown error"))
    }

    private fun isNetworkError(error: Throwable?): Boolean {
        return when (error) {
            is UnknownHostException,
            is ConnectException,
            is SocketTimeoutException,
            is java.net.NoRouteToHostException,
            is SSLHandshakeException -> true
            else -> false
        }
    }

    private fun queueWorklog(issueKey: String, timeSpentSeconds: Int, comment: String?, started: String) {
        val worklog = JiraWorklogPersistentState.PendingWorklog(
            issueKey = issueKey,
            timeSpentSeconds = timeSpentSeconds,
            comment = comment,
            started = started,
            timestamp = System.currentTimeMillis()
        )
        persistentState.addPendingWorklog(worklog)

        notifyUser("Worklog queued for offline submission", NotificationType.INFORMATION)

        // Trigger repaint - service no longer exposes widget directly
        // We can just rely on state change if the widget observes state,
        // but JiraWorklogWidget observes JiraWorklogTimerService.
        // We need to notify the timer service that something changed, OR the widget checks the persistent state directly.
        // In my resolution of JiraWorklogWidget, I check persistentState in `paintComponent`.
        // So calling `repaint` on the widget is needed.
        // But how to get the widget?
        // `JiraWorklogTimerService` no longer has `widget()`.
        // However, the widget registers itself to the service?
        // `JiraWorklogTimerService` in upstream does NOT seem to have `registerWidget` anymore (based on `read_file` output).
        // It uses Flow.
        // So the widget observes the service.
        // Does the service have a way to force update?
        // `JiraWorklogTimerService` exposes `timeFlow` and `statusFlow`.
        // If I update persistent state, the widget won't know unless `timeFlow` or `statusFlow` emits.
        // BUT `JiraWorklogWidget` in my merged version checks `persistentState.getPendingWorklogs().isNotEmpty()` inside `paintComponent`.
        // So if I can trigger a repaint, it will update.
        // But I don't have a reference to the widget.
        // Maybe I can trigger a dummy status update? No, that has side effects.
        // Ideally, `JiraOfflineWorklogService` should expose a Flow or Listener.
        // But to keep it simple and fix the compilation error, I will skip the explicit repaint for now.
        // The widget repaints on timer tick (every second) or mouse hover.
        // So the icon will appear within 1 second. That is acceptable.
    }

    private fun startRetryScheduler() {
        retryFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            { retryPendingWorklogs() },
            RETRY_INTERVAL_MINUTES,
            RETRY_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
    }

    @Synchronized
    fun retryPendingWorklogs() {
        // Since testConnection and submitWorklog are likely suspend, we need runBlocking here
        // Note: runBlocking in a scheduled executor thread is generally fine as it blocks that thread.
        runBlocking {
            val pending = persistentState.getPendingWorklogs()
            if (pending.isEmpty()) return@runBlocking

            // Check connection first to avoid unnecessary attempts
            if (jiraClient.testConnection().isFailure) {
                return@runBlocking
            }

            val toRemove = mutableListOf<JiraWorklogPersistentState.PendingWorklog>()
            var successCount = 0

            for (worklog in pending) {
                val result = jiraClient.submitWorklog(worklog.issueKey, worklog.timeSpentSeconds, worklog.comment, worklog.started)

                if (result.isSuccess) {
                    toRemove.add(worklog)
                    successCount++
                } else {
                    val error = result.exceptionOrNull()
                    if (!isNetworkError(error)) {
                        LOG.warn("Failed to retry worklog for ${worklog.issueKey}: ${error?.message}")
                        toRemove.add(worklog)
                        notifyUser("Failed to submit offline worklog for ${worklog.issueKey}: ${error?.message}", NotificationType.ERROR)
                    }
                }
            }

            if (toRemove.isNotEmpty()) {
                toRemove.forEach { persistentState.removePendingWorklog(it) }
                if (successCount > 0) {
                    notifyUser("Successfully submitted $successCount offline worklogs", NotificationType.INFORMATION)
                }
            }
        }
    }

    private fun notifyUser(content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Jira Worklog Timer")
            .createNotification(content, type)
            .notify(project)
    }

    override fun dispose() {
        retryFuture?.cancel(false)
    }
}
