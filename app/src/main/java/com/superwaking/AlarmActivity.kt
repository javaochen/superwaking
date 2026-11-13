package com.superwaking

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/**
 * 响铃界面：覆盖锁屏、常亮、吞掉返回键。
 * 只有逐字输入正确句子才会停止振动并排下一次。
 */
class AlarmActivity : Activity() {

    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_alarm)

        Diagnostics.log(this, "activity_create")
        val store = Store(this)
        val input = findViewById<EditText>(R.id.input)
        val error = findViewById<TextView>(R.id.error)
        findViewById<TextView>(R.id.target).text = store.sentence
        val freshContract = store.contract.isNotBlank() &&
            System.currentTimeMillis() - store.contractAt < 36 * 60 * 60 * 1000L
        if (freshContract) {
            findViewById<TextView>(R.id.contract).text = "你昨晚写的：${store.contract}"
        }

        findViewById<TextView>(R.id.count).text = if (store.ringCount > 1) {
            "第 ${store.ringCount} 次响了。你已经拖了 ${(store.ringCount - 1) * 2} 分钟。"
        } else {
            "第一次响。把它当作今天的第一件事。"
        }

        findViewById<Button>(R.id.btnDone).setOnClickListener {
            if (normalize(input.text.toString()) == normalize(store.sentence)) {
                dismissAndClose(store)
            } else {
                error.text = "不对，重新输入"
                input.setText("")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Diagnostics.log(this, "activity_start")
        try {
            startService(Intent(this, AlarmService::class.java))
        } catch (e: Exception) {
            Diagnostics.log(this, "activity_start_service_failed ${e.javaClass.simpleName}")
        }
    }

    override fun onStop() {
        super.onStop()
        Diagnostics.log(this, "activity_stop done=$done")
    }

    override fun onDestroy() {
        Diagnostics.log(this, "activity_destroy done=$done")
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 故意什么都不做：返回键不能关闹钟
    }

    private fun dismissAndClose(store: Store) {
        Diagnostics.log(this, "dismiss_ok")
        done = true
        val rings = store.ringCount
        store.chainActive = false
        store.ringCount = 0
        store.streak = if (rings <= 1) store.streak + 1 else 0
        AlarmScheduler.cancel(this)

        val now = System.currentTimeMillis()
        if (store.pendingDisableAt > 0 && now >= store.pendingDisableAt) {
            // 预约时间已到 → 兑现关闭
            store.enabled = false
            store.pendingDisableAt = 0
            Diagnostics.log(this, "disabled_after_dismiss")
        } else if (store.pendingDisableAt > 0) {
            // 预约还没到（例如 6:55 申请、7:00 完成）→ 明天照常响
            store.nextAt = AlarmScheduler.nextTrigger(store.hour, store.minute)
            AlarmScheduler.schedule(this, store.nextAt)
            Diagnostics.log(this, "pending_disable_not_yet")
        } else {
            val next = AlarmScheduler.nextTrigger(store.hour, store.minute)
            store.nextAt = next
            AlarmScheduler.schedule(this, next)
        }

        getSystemService(NotificationManager::class.java).cancel(AlarmService.NOTIF_ID)
        stopService(Intent(this, AlarmService::class.java))
        startActivity(
            Intent(this, DoneActivity::class.java)
                .putExtra("ringCount", rings)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    private fun normalize(s: String): String =
        s.replace(Regex("\\s+"), "").trim()
}
