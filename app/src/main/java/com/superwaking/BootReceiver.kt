package com.superwaking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机 / 改时间 / 改时区后重排闹钟，否则重启一次就失效。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = Store(context)
        if (!store.enabled) return

        val now = System.currentTimeMillis()
        val triggerAt = when {
            store.chainActive && store.nextAt > now -> store.nextAt
            store.chainActive -> now + 1000L
            else -> AlarmScheduler.nextTrigger(store.hour, store.minute, now)
        }
        AlarmScheduler.schedule(context, triggerAt)
        store.nextAt = triggerAt
        if (store.contractReminder) AlarmScheduler.scheduleContract(context)
    }
}
