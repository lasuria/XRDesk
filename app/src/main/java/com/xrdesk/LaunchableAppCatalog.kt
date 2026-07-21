package com.xrdesk

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log

data class LaunchableApp(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

/**
 * Centralized catalog for launchable applications.
 * Implements a background-initialized cache to avoid main-thread UI stutters.
 */
object LaunchableAppCatalog {
    private const val TAG = "AppCatalog"
    
    @Volatile
    private var cachedApps: List<LaunchableApp>? = null
    private var isInitializationStarted = false

    /**
     * Triggers background loading of the application catalog.
     * Call this early (e.g., in MainActivity) to warm up the cache.
     */
    fun preLoad(context: Context) {
        if (cachedApps != null || isInitializationStarted) return
        
        isInitializationStarted = true
        Thread {
            try {
                Log.d(TAG, "Starting background app scan...")
                val startTime = System.currentTimeMillis()
                val apps = loadInternal(context.applicationContext)
                cachedApps = apps
                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "Background app scan complete in ${duration}ms. Found ${apps.size} apps.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pre-load app catalog", e)
                isInitializationStarted = false
            }
        }.start()
    }

    /**
     * Returns the list of launchable apps. 
     * Performs a one-time synchronous load if the cache is empty.
     */
    fun get(context: Context): List<LaunchableApp> {
        return cachedApps ?: synchronized(this) {
            cachedApps ?: run {
                Log.w(TAG, "Cache miss! Performing synchronous load...")
                val apps = loadInternal(context.applicationContext)
                cachedApps = apps
                apps
            }
        }
    }

    private fun loadInternal(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        
        val apps = if (android.os.Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        
        return apps.map { resolveInfo ->
            val appInfo = resolveInfo.activityInfo.applicationInfo
            LaunchableApp(
                label = resolveInfo.loadLabel(pm).toString(),
                packageName = appInfo.packageName,
                icon = resolveInfo.loadIcon(pm)
            )
        }.distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Legacy method for backward compatibility, now delegates to get().
     */
    fun load(context: Context): List<LaunchableApp> = get(context)
}
