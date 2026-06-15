package com.fomokiller

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class FomoNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "FomoKiller"
        const val ACTION_STATE_CHANGED = "com.fomokiller.STATE_CHANGED"
        var instance: FomoNotificationService? = null
    }

    private val heldNotifications = mutableMapOf<String, HeldNotif>()

    data class HeldNotif(
        val packageName: String,
        val tag: String?,
        val id: Int,
        val notification: Notification,
        val key: String
    )

    override fun onCreate() {
        super.onCreate()
        AppState.init(applicationContext)
        Log.d(TAG, "onCreate — service créé")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
        Log.d(TAG, "onDestroy — service détruit")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.d(TAG, "onListenerConnected — prêt")
        sendBroadcast(Intent(ACTION_STATE_CHANGED))
        applyCurrentMode()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance == this) instance = null
        Log.d(TAG, "onListenerDisconnected")
        sendBroadcast(Intent(ACTION_STATE_CHANGED))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return

        val shouldBlock = AppState.shouldBlockNotification(pkg)
        Log.d(TAG, "onNotificationPosted: $pkg — bloquer: $shouldBlock — mode: ${AppState.currentMode}")

        if (shouldBlock) {
            heldNotifications[sbn.key] = HeldNotif(
                packageName = pkg,
                tag = sbn.tag,
                id = sbn.id,
                notification = sbn.notification,
                key = sbn.key
            )
            try {
                cancelNotification(sbn.key)
                Log.d(TAG, "Notification annulée: $pkg")
            } catch (e: Exception) {
                Log.e(TAG, "Erreur annulation: ${e.message}")
            }
        } else {
            heldNotifications.remove(sbn.key)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        if (!AppState.shouldBlockNotification(sbn.packageName ?: return)) {
            heldNotifications.remove(sbn.key)
        }
    }

    fun applyCurrentMode() {
        Log.d(TAG, "applyCurrentMode — mode: ${AppState.currentMode}")
        try {
            when (AppState.currentMode) {
                FomoMode.OFF -> releaseAllHeld()
                FomoMode.KILL_ALL, FomoMode.VIP_ONLY -> {
                    val active = try {
                        activeNotifications
                    } catch (e: Exception) {
                        Log.e(TAG, "Impossible de lire activeNotifications: ${e.message}")
                        null
                    } ?: return

                    Log.d(TAG, "Notifications actives trouvées: ${active.size}")
                    for (sbn in active) {
                        val pkg = sbn.packageName ?: continue
                        if (pkg == packageName) continue
                        if (AppState.shouldBlockNotification(pkg)) {
                            Log.d(TAG, "Interception notif existante: $pkg")
                            heldNotifications[sbn.key] = HeldNotif(
                                packageName = pkg,
                                tag = sbn.tag,
                                id = sbn.id,
                                notification = sbn.notification,
                                key = sbn.key
                            )
                            try {
                                cancelNotification(sbn.key)
                            } catch (e: Exception) {
                                Log.e(TAG, "Erreur interception: ${e.message}")
                            }
                        }
                    }
                    releaseNowAllowed()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur applyCurrentMode: ${e.message}")
        }
    }

    private fun releaseAllHeld() {
        Log.d(TAG, "Relâchement de ${heldNotifications.size} notifications")
        val toRelease = heldNotifications.values.toList()
        heldNotifications.clear()
        for (held in toRelease) {
            try {
                val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.notify(held.tag, held.id, held.notification)
                Log.d(TAG, "Relâché: ${held.packageName}")
            } catch (e: Exception) {
                Log.e(TAG, "Erreur relâchement: ${e.message}")
            }
        }
    }

    private fun releaseNowAllowed() {
        val toRelease = heldNotifications.values.filter {
            !AppState.shouldBlockNotification(it.packageName)
        }
        for (held in toRelease) {
            heldNotifications.remove(held.key)
            try {
                val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.notify(held.tag, held.id, held.notification)
            } catch (e: Exception) {
                Log.e(TAG, "Erreur relâchement partiel: ${e.message}")
            }
        }
    }
}