package com.example.autoglmagent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.autoglmagent.MainActivity
import com.example.autoglmagent.R
import com.example.autoglmagent.agent.AgentOrchestrator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class AgentForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        showForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                AgentOrchestrator.getInstance(applicationContext).stop()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> showForegroundNotification()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isForeground.value = false
        Log.i(TAG, "foreground service destroyed")
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AgentForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("AutoGLM Agent 运行中")
            .setContentText("正在执行手机自动化任务，可随时停止。")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(R.drawable.ic_launcher, "停止", stopIntent)
            .build()
    }

    private fun showForegroundNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isForeground.value = true
        Log.i(TAG, "foreground service entered foreground")
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AutoGLM Agent",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "AutoGLM 手机 Agent 任务运行状态"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "AutoGLMForeground"
        const val CHANNEL_ID = "autoglm_agent_running"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.example.autoglmagent.action.STOP_AGENT"
        private val isForeground = MutableStateFlow(false)

        suspend fun startAndWait(context: Context): Boolean {
            val intent = Intent(context, AgentForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            return isForeground.value || withTimeoutOrNull(4_000) {
                isForeground.first { it }
            } == true
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AgentForegroundService::class.java))
        }
    }
}
