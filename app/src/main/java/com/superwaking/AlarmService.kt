package com.superwaking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
 * 2. 短振动脉冲循环，每 3.2 秒重新发起 —— 避免系统在窗口不可见时取消长振动
 * 3. 熄屏瞬间重发振动 + 重发全屏通知（会把屏幕重新点亮）
 * 4. 常驻全屏 Intent 通知 + PARTIAL_WAKE_LOCK
 */
class AlarmService : Service() {

    private var vibrator: Vibrator? = null
    private var wake: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var effect: VibrationEffect? = null

    private val burst = object : Runnable {
        override fun run() {
            val v = vibrator ?: return
            val e = effect ?: return
            try {
                v.vibrate(e, alarmAttrs())
            } catch (ex: Exception) {
                Diagnostics.log(this@AlarmService, "vibrate_error ${ex.javaClass.simpleName}")
            }
            handler.postDelayed(this, BURST_GAP_MS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_OFF) return
            Diagnostics.log(this@AlarmService, "screen_off -> re-vibrate")
            // 系统会把不可见窗口的振动掐掉，这里立刻重发一次
            handler.removeCallbacks(burst)
            burst.run()
            repostNotification()
            launchUi()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Diagnostics.log(this, "service_create pid=${android.os.Process.myPid()}")
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        prepareVibration()
        burst.run()

        wake = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "superwaking:ring")
            .apply {
                setReferenceCounted(false)
                acquire(30 * 60 * 1000L)
            }

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Diagnostics.log(this, "service_cmd action=${intent?.action ?: "null"} startId=$startId")
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        bringToFront()
        return START_STICKY
    }

    override fun onDestroy() {
        Diagnostics.log(this, "service_destroy")
        handler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        vibrator?.cancel()
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun alarmAttrs() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .build()

    private fun prepareVibration() {
        val v = getSystemService(Vibrator::class.java) ?: return
        if (!v.hasVibrator()) {
            Diagnostics.log(this, "no_vibrator")
            return
        }
        vibrator = v
        val timings = longArrayOf(0, 600, 350, 600, 350, 1100)
        effect = if (v.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, intArrayOf(0, 255, 0, 255, 0, 255), -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
        Diagnostics.log(this, "vibrator_ready amp=${v.hasAmplitudeControl()}")
    }

    private fun bringToFront() {
        handler.removeCallbacksAndMessages(null)
        launchUi()
        handler.postDelayed({ launchUi() }, 3000L)
        // 注意：removeCallbacksAndMessages 会清掉 burst，这里补回来
        burst.run()
    }

    private fun repostNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        try {
            nm.cancel(NOTIF_ID)
            nm.notify(NOTIF_ID, buildNotification())
        } catch (_: Exception) {
        }
    }

    private fun launchUi() {
        try {
            startActivity(
                Intent(this, AlarmActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (_: Exception) {
            Diagnostics.log(this, "launch_ui_failed")
        }
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

        /** 一次脉冲 3.0 秒，间隔 3.2 秒后重发 */
        private const val BURST_GAP_MS = 3200L
    }
}
