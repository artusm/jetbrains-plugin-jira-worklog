package com.github.artusm.jetbrainspluginjiraworklog.utils

interface TimeProvider {
    fun currentTimeMillis(): Long
}

class SystemTimeProvider : TimeProvider {
    override fun currentTimeMillis(): Long {
        return System.currentTimeMillis()
    }
}
