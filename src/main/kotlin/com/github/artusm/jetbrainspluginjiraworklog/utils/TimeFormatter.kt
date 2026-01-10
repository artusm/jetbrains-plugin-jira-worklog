package com.github.artusm.jetbrainspluginjiraworklog.utils

/**
 * Utility for formatting time durations.
 */
object TimeFormatter {
    
    /**
     * Format milliseconds to human-readable string (e.g., "9 hrs 13 min").
     * Used for display in the status bar widget.
     */
    fun formatDisplay(totalMs: Long): String {
        val totalSeconds = totalMs / 1000
        
        if (totalSeconds < 60) {
            return "${totalSeconds}s"
        }
        
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return buildString {
            if (hours > 0) {
                append("$hours hr")
                if (hours > 1) append("s")
            }
            if (minutes > 0) {
                if (hours > 0) append(" ")
                append("$minutes min")
            }
            if (hours == 0L && minutes == 0L && seconds > 0) {
                append("${seconds}s")
            }
        }
    }
    
    /**
     * Format milliseconds to Jira time format (e.g., "9h 13m").
     * Used when submitting worklog to Jira API.
     */
    fun formatJira(totalMs: Long): String {
        val totalSeconds = totalMs / 1000
        
        if (totalSeconds < 60) {
            return "${totalSeconds}s"
        }
        
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        
        return buildString {
            if (hours > 0) {
                append("${hours}h")
            }
            if (minutes > 0) {
                if (hours > 0) append(" ")
                append("${minutes}m")
            }
        }
    }
    
    /**
     * Format time for detailed display (e.g., "09:13:42").
     * Shows hours:minutes:seconds with leading zeros.
     */
    fun formatDetailed(totalMs: Long): String {
        val totalSeconds = totalMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
    
    /**
     * Parse Jira time string (e.g., "2h 30m") to milliseconds.
     */
    fun parseJira(jiraTime: String): Long {
        var totalSeconds = 0L
        
        val hoursMatch = Regex("""(\d+)h""").find(jiraTime)
        if (hoursMatch != null) {
            totalSeconds += hoursMatch.groupValues[1].toLong() * 3600
        }
        
        val minutesMatch = Regex("""(\d+)m""").find(jiraTime)
        if (minutesMatch != null) {
            totalSeconds += minutesMatch.groupValues[1].toLong() * 60
        }
        
        val secondsMatch = Regex("""(\d+)s""").find(jiraTime)
        if (secondsMatch != null) {
            totalSeconds += secondsMatch.groupValues[1].toLong()
        }
        
        return totalSeconds * 1000
    }
}
