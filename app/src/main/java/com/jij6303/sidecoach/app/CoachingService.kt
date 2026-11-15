package com.jij6303.sidecoach.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.jij6303.sidecoach.R
import com.jij6303.sidecoach.app.di.ServiceLocator

/**
 * 코칭 세션 수명을 맡는 Foreground Service.
 * 앱 화면을 숨겨도(최상단 Back → moveTaskToBack) 신호 구독·판정·음성 코칭이 계속된다.
 */
class CoachingService : Service() {

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        ServiceLocator.startCoaching()
        Log.i(TAG, "started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "stop requested")
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ServiceLocator.stopCoaching()
        Log.i(TAG, "stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.coaching_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, CoachingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.coaching_notification_title))
            .setContentText(getString(R.string.coaching_notification_text))
            .setContentIntent(open)
            .addAction(0, getString(R.string.coaching_stop), stop)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val TAG = "CoachingService"
        private const val CHANNEL_ID = "coaching"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.jij6303.sidecoach.action.STOP_COACHING"

        /** 화면이 보이는 상태(포그라운드)에서 호출한다. 이미 돌고 있으면 아무 일도 없다. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, CoachingService::class.java))
        }
    }
}
