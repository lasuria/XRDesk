package com.xrdesk

object SessionStore {
    @Volatile
    var lastLaunchFailure: String? = null

    @Volatile
    var lastInjectionResult: String? = null

    @Volatile
    var lastBackWarmupUptime: Long = 0L

    @Volatile
    var lastBackFailure: String? = null

    @Volatile
    var lastLaunchedPackage: String? = null

    @Volatile
    var capturedBrightness: Float = -1f

    @Volatile
    var capturedSystemBrightness: Float = 1f

    @Volatile
    var hasCapturedBrightness: Boolean = false

    fun clear() {
        lastLaunchFailure = null
        lastInjectionResult = null
        lastBackWarmupUptime = 0L
        lastBackFailure = null
        lastLaunchedPackage = null
        capturedBrightness = -1f
        capturedSystemBrightness = 1f
        hasCapturedBrightness = false
    }
}
