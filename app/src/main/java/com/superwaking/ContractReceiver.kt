package com.superwaking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 每晚 22:30 提醒写下“明天起床后第一件事”。 */
class ContractReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_CONTRACT) return
        val store = Store(context)
        if (!store.contractReminder) return

        Diagnostics.log(context, "contract_reminder")
        notifyNow(context)
        AlarmScheduler.scheduleContract(context)
    }

    private fun notifyNow(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "睡前契约", NotificationManager.IMPORTANCE_DEFAULT)
            ch.setSound(null, null)
            nm.createNotificationChannel(ch)
        }
        val pi = PendingIntent.getActivity(
            context,
            2003,
            Intent(context, MainActivity::class.java)
                .putExtra("open_contract", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("写一句明天起床后要做的事")
            .setContentText("明早闹钟响时会显示这句话。")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(3001, n)
    }

    companion object {
        private const val CHANNEL = "contract"
    }
}
