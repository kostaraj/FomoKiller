package com.fomokiller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class FomoNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "FomoKiller"
        private const val CHANNEL_ID = "fomo_recovered"
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
        createNotificationChannel()
        Log.d(TAG, "onCreate — service créé")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Notifications restaurées"
            val descriptionText = "Notifications qui ont été temporairement bloquées"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
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
        // Délai pour laisser le système se synchroniser
        Handler(Looper.getMainLooper()).postDelayed({
            applyCurrentMode()
        }, 500)
        sendBroadcast(Intent(ACTION_STATE_CHANGED))
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
            holdAndCancel(sbn)
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
                else -> {
                    val active = try {
                        activeNotifications
                    } catch (e: Exception) {
                        Log.e(TAG, "Erreur lecture activeNotifications: ${e.message}")
                        null
                    } ?: return

                    Log.d(TAG, "Analyse de ${active.size} notifications actives")
                    for (sbn in active) {
                        val pkg = sbn.packageName ?: continue
                        if (pkg == packageName) continue
                        
                        // Si déjà en mémoire, on ne traite pas à nouveau
                        if (heldNotifications.containsKey(sbn.key)) continue

                        if (AppState.shouldBlockNotification(pkg)) {
                            Log.d(TAG, "Interception notif: $pkg")
                            holdAndCancel(sbn)
                        }
                    }
                    // Relâche ce qui n'est plus censé être bloqué suite au changement de mode
                    releaseNowAllowed()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur applyCurrentMode: ${e.message}")
        }
    }

    private fun holdAndCancel(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        heldNotifications[sbn.key] = HeldNotif(
            packageName = pkg,
            tag = sbn.tag,
            id = sbn.id,
            notification = sbn.notification,
            key = sbn.key
        )
        try {
            cancelNotification(sbn.key)
            Log.d(TAG, "Notification supprimée de la barre d'état: $pkg")
        } catch (e: Exception) {
            Log.e(TAG, "Echec suppression: ${e.message}")
        }
    }

    private fun releaseAllHeld() {
        if (heldNotifications.isEmpty()) return
        Log.d(TAG, "Relâchement total: ${heldNotifications.size} notifications")
        val toRelease = heldNotifications.values.toList()
        heldNotifications.clear()
        
        if (AppState.reDisplayNotifications) {
            for (held in toRelease) {
                repostNotification(held)
            }
        } else {
            Log.d(TAG, "Restauration désactivée par les paramètres")
        }
    }

    private fun releaseNowAllowed() {
        val keysToRemove = heldNotifications.filter { !AppState.shouldBlockNotification(it.value.packageName) }.keys
        if (keysToRemove.isEmpty()) return
        
        Log.d(TAG, "Relâchement partiel: ${keysToRemove.size} notifications")
        for (key in keysToRemove) {
            val held = heldNotifications.remove(key)
            if (held != null && AppState.reDisplayNotifications) {
                repostNotification(held)
            }
        }
    }

    private fun repostNotification(held: HeldNotif) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val pm = packageManager
            
            // Récupération des infos de l'app d'origine pour l'identité
            val appLabel: String
            val appIcon: Drawable
            try {
                val appInfo = pm.getApplicationInfo(held.packageName, 0)
                appLabel = pm.getApplicationLabel(appInfo).toString()
                appIcon = pm.getApplicationIcon(appInfo)
            } catch (e: Exception) {
                repostSimple(held) // Fallback si l'app a été désinstallée entre temps
                return
            }

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }

            val extras = held.notification.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE) ?: appLabel
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT) ?: ""

            builder.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(drawableToBitmap(appIcon)) // Icône de l'app d'origine
                .setSubText(appLabel) // Affiche le nom de l'app source en haut
                .setContentIntent(held.notification.contentIntent)
                .setDeleteIntent(held.notification.deleteIntent)
                .setWhen(held.notification.`when`)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setGroup("fomo_group_${held.packageName}") // Grouper par app d'origine

            // Transfert des boutons d'actions (Répondre, Like, etc.)
            held.notification.actions?.forEach { action ->
                builder.addAction(action)
            }

            nm.notify(held.tag, held.id, builder.build())
            Log.d(TAG, "Notification relâchée avec identité: ${held.packageName}")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du renvoi complet: ${e.message}")
            repostSimple(held)
        }
    }

    private fun repostSimple(held: HeldNotif) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }
            builder.setContentTitle(held.packageName)
                .setContentText("Notification restaurée")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(true)
            nm.notify(held.tag, held.id, builder.build())
        } catch (e: Exception) { /* Echec total */ }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) return drawable.bitmap
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
