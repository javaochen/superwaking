package com.superwaking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** 闹钟触发：拉起前台服务，并立刻排下一次“链条”。 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_RING) return

        val store = Store(context)
        if (!store.enabled) return

        // 预约关闭到点：此时才真正关闭，之前一律照常响
        val now = System.currentTimeMillis()
        if (store.pendingDisableAt > 0 && now >= store.pendingDisableAt) {
            store.enabled = false
            store.pendingDisableAt = 0
            store.chainActive = false
            AlarmScheduler.cancel(context)
            Diagnostics.log(context, "scheduled_disable_applied")
            return
        }

        store.ringCount += 1
        Diagnostics.log(context, "alarm_fire n=${store.ringCount}")
        store.chainActive = true
        val app = context.applicationContext
        val svc = Intent(app, AlarmService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(svc)
        } else {
            app.startService(svc)
        }

        val next = System.currentTimeMillis() + AlarmScheduler.CHAIN_GAP_MS
        store.nextAt = next
        AlarmScheduler.schedule(app, next)
    }
}
