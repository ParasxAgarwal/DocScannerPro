package com.rebelroot.docscannerpro.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.rebelroot.docscannerpro.MainActivity
import com.rebelroot.docscannerpro.R

/**
 * A dismissible quick-access notification so the user can jump straight into
 * document scanning, QR scanning or photo-to-PDF capture without navigating
 * the app. Local only; no data leaves the device.
 */
object QuickAccessNotification {

    private const val CHANNEL_ID = "quick_access"
    private const val NOTIFICATION_ID = 1001

    const val EXTRA_QUICK_MODE = "quick_mode"
    const val MODE_DOCUMENT = "DOCUMENT"
    const val MODE_QR = "QR_BARCODE"
    const val MODE_PHOTO = "PHOTO"

    fun show(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Quick access",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shortcuts for scanning and QR tools"
                setShowBadge(false)
            }
        )

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun action(label: String, mode: String, requestCode: Int): NotificationCompat.Action {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_QUICK_MODE, mode)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pending = PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return NotificationCompat.Action.Builder(null, label, pending).build()
        }

        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Doc Scanner Pro")
            .setContentText("Quick actions: scan a document, a code, or photos to PDF")
            .setContentIntent(contentIntent)
            .setOngoing(false)
            .setAutoCancel(true)
            .addAction(action("Scan document", MODE_DOCUMENT, 1))
            .addAction(action("QR scan", MODE_QR, 2))
            .addAction(action("Photos to PDF", MODE_PHOTO, 3))
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }
}
