package com.fomokiller

import android.content.Context
import android.content.SharedPreferences

enum class FomoMode {
    OFF,
    KILL_ALL,
    VIP_ONLY
}

object AppState {
    private const val PREFS_NAME = "fomokiller_prefs"
    private const val KEY_MODE = "mode"
    private const val KEY_BLOCKED_APPS = "blocked_apps"
    private const val KEY_VIP_APPS = "vip_apps"
    private const val KEY_OPEN_COUNT = "open_count"
    private const val KEY_REDISPLAY = "redisplay_notifications"

    val ALWAYS_ALLOWED_PACKAGES = setOf(
        "com.android.phone",
        "com.android.incallui",
        "com.android.server.telecom",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.incallui",
        "com.android.mms",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.deskclock",
        "com.google.android.deskclock",
        "com.samsung.android.app.clockpackage",
        "android",
        "com.android.systemui",
        "com.android.settings"
    )

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var openCount: Int
        get() = prefs.getInt(KEY_OPEN_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_OPEN_COUNT, value).apply()

    var reDisplayNotifications: Boolean
        get() = prefs.getBoolean(KEY_REDISPLAY, true)
        set(value) = prefs.edit().putBoolean(KEY_REDISPLAY, value).apply()

    var currentMode: FomoMode
        get() = FomoMode.valueOf(prefs.getString(KEY_MODE, FomoMode.OFF.name) ?: FomoMode.OFF.name)
        set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()

    var blockedApps: Set<String>
        get() = prefs.getStringSet(KEY_BLOCKED_APPS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_BLOCKED_APPS, value).apply()

    var vipApps: Set<String>
        get() = prefs.getStringSet(KEY_VIP_APPS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_VIP_APPS, value).apply()

    fun shouldBlockNotification(packageName: String): Boolean {
        if (ALWAYS_ALLOWED_PACKAGES.contains(packageName)) return false
        
        return when (currentMode) {
            FomoMode.OFF -> false
            FomoMode.KILL_ALL -> {
                blockedApps.contains(packageName)
            }
            FomoMode.VIP_ONLY -> {
                !vipApps.contains(packageName)
            }
        }
    }
}