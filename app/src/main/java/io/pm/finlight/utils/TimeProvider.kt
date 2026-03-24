package io.pm.finlight.utils

import java.util.Calendar

/**
 * Interface to provide the current time.
 * This abstraction allows for easier testing by enabling the injection of mock time providers.
 */
interface TimeProvider {
    fun now(): Calendar
}

/**
 * Default implementation of TimeProvider that returns the current system time.
 */
class SystemTimeProvider : TimeProvider {
    override fun now(): Calendar {
        return Calendar.getInstance()
    }
}
