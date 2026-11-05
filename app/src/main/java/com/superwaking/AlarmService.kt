package com.superwaking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * 响铃期间的前台服务：
 * 1. 用 Vibrator 直接驱动马达（不依赖通知渠道/铃声音量，音量键无效）
 * 2. 常驻全屏 Intent 通知
 * 3. 拿 PARTIAL_WAKE_LOCK，防止 CPU 睡下去
 */
class AlarmService : Service() {

    private var vibrator: Vibrator? = null
    private var wake: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        startVibrating()
        wake = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "superwaking:ring")
            .apply {
                setReferenceCounted(false)
                acquire(30 * 60 * 1000L)
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        bringToFront()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        vibrator?.cancel()
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun bringToFront() {
        handler.removeCallbacksAndMessages(null)
        launchUi()
        // 用户把界面划走后，3 秒再拉一次（后台启动可能被系统拦，但振动一直在）
        handler.postDelayed({ launchUi() }, 3000L)
    }

    private fun launchUi() {
        try {
            startActivity(
                Intent(this, AlarmActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (_: Exception) {
            // MagicOS 可能拦截后台启动，全屏通知是主要通道
        }
    }

    private fun startVibrating() {
        val v = getSystemService(Vibrator::class.java) ?: return
        if (!v.hasVibrator()) return
        vibrator = v
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()
        val timings = longArrayOf(0, 600, 350, 600, 350, 1100)
        val effect = if (v.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, intArrayOf(0, 255, 0, 255, 0, 255), 0)
        } else {
            VibrationEffect.createWaveform(timings, 0)
        }
        v.vibrate(effect, attrs)
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(CHANNEL_ID, "闹钟响铃", NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(null, null)
            ch.enableVibration(false)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this,
            2002,
            Intent(this, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("闹钟")
            .setContentText("输入句子才能关闭")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .build()
    }

    companion object {
        const val NOTIF_ID = 9001
        const val CHANNEL_ID = "alarm_ring"
        const val ACTION_STOP = "com.superwaking.action.STOP"
    }
}
