package com.superwaking

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object AlarmScheduler {

    const val ACTION_RING = "com.superwaking.action.RING"

    /** 未完成任务时，每隔这么久再响一次 */
    const val CHAIN_GAP_MS = 2 * 60 * 1000L

    private const val REQ_RING = 1001

    fun nextTrigger(hour: Int, minute: Int, now: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val i = Intent(context, AlarmReceiver::class.java).setAction(ACTION_RING)
        return PendingIntent.getBroadcast(
            context,
            REQ_RING,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun schedule(context: Context, triggerAt: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context)
        try {
            // setAlarmClock：系统状态栏显示闹钟图标，优先级最高，且给应用临时后台豁免
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, pi), pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }
}
