package com.superwaking

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 输对句子后的 10 秒：不给立刻回去躺下的机会。
 * 前 10 秒按钮不可用，返回键无效；屏幕常亮。
 */
class DoneActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var action: Button
    private var readyAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_done)

        val store = Store(this)
        findViewById<TextView>(R.id.date).text =
            SimpleDateFormat("M 月 d 日 EEEE", Locale.CHINA).format(Date())

        val freshContract = store.contract.isNotBlank() &&
            System.currentTimeMillis() - store.contractAt < 36 * 60 * 60 * 1000L
        findViewById<TextView>(R.id.contract).text = if (freshContract) {
            store.contract
        } else {
            "（昨晚没有写契约）\n今天先从起床开始"
        }

        val n = intent.getIntExtra("ringCount", 1)
        findViewById<TextView>(R.id.stats).text = buildString {
            if (n <= 1) append("今天第一次就起来了。") else append("今天响了 $n 次。")
            if (store.streak > 1) append("\n连续 ${store.streak} 天第一次就起。")
        }

        action = findViewById(R.id.action)
        action.isEnabled = false
        action.setOnClickListener { finish() }

        readyAt = System.currentTimeMillis() + 10_000L
        Diagnostics.log(this, "done_screen n=$n")
        tick()
    }

    private fun tick() {
        val left = (readyAt - System.currentTimeMillis() + 999) / 1000
        if (left > 0) {
            action.text = "还有 $left 秒"
            handler.postDelayed({ tick() }, 250L)
        } else {
            action.text = "开始今天"
            action.isEnabled = true
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 10 秒内不给退
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
