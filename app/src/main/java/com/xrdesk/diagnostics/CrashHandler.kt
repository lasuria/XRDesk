package com.xrdesk.diagnostics

import android.util.Log

class CrashHandler : Thread.UncaughtExceptionHandler {
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    companion object {
        fun init() {
            val handler = CrashHandler()
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            DiagnosticsManager.fatal("Crash", "Uncaught exception in thread ${thread.name}: ${throwable.message}", throwable)
        } catch (e: Exception) {
            Log.e("XRDesk-Crash", "Failed to log uncaught exception", e)
        } finally {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
