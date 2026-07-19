package com.xrdesk

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import java.util.Collections
import java.util.WeakHashMap

class XRDeskApp : Application() {
    companion object {
        private val activeActivities = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())

        fun recreateAllActivities() {
            for (activity in activeActivities) {
                activity.recreate()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        ThemeEngine.init(this)
        DiagnosticsLog.init(resources)
        SettingsStore.applyAppLanguage()
        
        val mode = if (SettingsStore.nightMode == SettingsStore.THEME_AMOLED) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            SettingsStore.nightMode
        }
        AppCompatDelegate.setDefaultNightMode(mode)
        
        DisplaySessionManager.init(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                activeActivities.add(activity)
                if (SettingsStore.nightMode == SettingsStore.THEME_AMOLED) {
                    activity.setTheme(R.style.Theme_XRDesk_Amoled)
                }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                activeActivities.remove(activity)
            }
        })
    }
}
