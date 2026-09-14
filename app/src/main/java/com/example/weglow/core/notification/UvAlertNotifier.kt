package com.example.weglow.core.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.weglow.R
import com.example.weglow.domain.model.EnvironmentInfo

class UvAlertNotifier(context: Context) {
    private val application = context.applicationContext
    private val notificationManager = application.getSystemService(NotificationManager::class.java)

    fun notifyIfHigh(environment: EnvironmentInfo) {
        if (environment.uvIndex < HIGH_UV_INDEX) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(application, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "UV alerts", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(application, CHANNEL_ID)
        } else {
            Notification.Builder(application)
        }
        notificationManager.notify(
            NOTIFICATION_ID,
            builder
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("High UV index")
                .setContentText("Apply Sunscreen")
                .setPriority(Notification.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build(),
        )
    }

    private companion object {
        const val HIGH_UV_INDEX = 6.0
        const val CHANNEL_ID = "uv_alerts"
        const val NOTIFICATION_ID = 1001
    }
}
