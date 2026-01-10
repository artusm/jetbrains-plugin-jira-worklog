package com.github.artusm.jetbrainspluginjiraworklog.model

/**
 * Represents the current state of the time tracker.
 */
enum class TimeTrackingStatus {
    /** Timer is actively running */
    RUNNING,
    
    /** Timer is paused due to inactivity */
    IDLE,
    
    /** Timer is stopped */
    STOPPED
}
