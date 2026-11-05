package com.superwaking

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Vibrator
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var store: Store
    private lateinit var status: TextView
    private lateinit var perms: TextView
    private lateinit var picker: TimePicker
    private lateinit var sentence: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = Store(this)
        status = findViewById(R.id.status)
        perms = findViewById(R.id.perms)
        picker = findViewById(R.id.timePicker)
        sentence = findViewById(R.id.sentence)

        picker.setIs24HourView(true)
        picker.hour = store.hour
        picker.minute = store.minute
        sentence.setText(
            if (store.sentence.isBlank()) getString(R.string.default_sentence) else store.sentence
        )

        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnCancel).setOnClickListener { cancelAll() }
        findViewById<Button>(R.id.btnTest).setOnClickListener { testVibration() }
        findViewById<Button>(R.id.btnNow).setOnClickListener { ringInFiveSeconds() }
        findViewById<Button>(R.id.btnPerms).setOnClickListener { permissionsFlow() }

        findViewById<Button>(R.id.btnUpdate).apply {
            text = "检查更新（当前 ${BuildConfig.VERSION_NAME}）"
            setOnClickListener { Updater.check(this@MainActivity, silent = false) }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        autoCheckUpdate()
    }

    /** 每 24 小时最多静默检查一次；发现新版仍会弹窗 */
    private fun autoCheckUpdate() {
        val now = System.currentTimeMillis()
        if (now - store.lastCheck < 24 * 60 * 60 * 1000L) return
        store.lastCheck = now
        Updater.check(this, silent = true)
    }

    private fun save() {
        val text = sentence.text.toString().trim()
        if (text.length < 4) {
            toast("句子太短，至少 4 个字")
            return
        }
        store.sentence = text
        store.hour = picker.hour
        store.minute = picker.minute
        store.enabled = true
        store.chainActive = false

        val t = AlarmScheduler.nextTrigger(store.hour, store.minute)
        store.nextAt = t
        AlarmScheduler.schedule(this, t)

        if (!AlarmScheduler.canScheduleExact(this)) {
            toast("未授予「精确闹钟」权限，可能被延迟，请点权限按钮")
        } else {
            toast("已设定")
        }
        refresh()
    }

    private fun cancelAll() {
        store.enabled = false
        store.chainActive = false
        AlarmScheduler.cancel(this)
        stopService(Intent(this, AlarmService::class.java))
        getSystemService(NotificationManager::class.java).cancel(AlarmService.NOTIF_ID)
        toast("已取消")
        refresh()
    }

    /** 不等到点，直接把整条流程跑一遍：响铃 → 全屏界面 → 输句子。 */
    private fun ringInFiveSeconds() {
        store.sentence = sentence.text.toString().trim().ifBlank {
            getString(R.string.default_sentence)
        }
        store.hour = picker.hour
        store.minute = picker.minute
        store.enabled = true
        store.chainActive = false
        val t = System.currentTimeMillis() + 5000L
        store.nextAt = t
        AlarmScheduler.schedule(this, t)
        toast("5 秒后响铃，可以直接锁屏观察")
        refresh()
    }

    private fun testVibration() {
        val v = getSystemService(Vibrator::class.java)
        if (v == null || !v.hasVibrator()) {
            toast("本机没有可用的振动器")
            return
        }
        val timings = longArrayOf(0, 700, 300, 700, 300, 700, 300, 700, 300, 700)
        v.vibrate(VibrationEffectCompat.make(timings, v.hasAmplitudeControl()))
        toast("振动 8 秒；此时可按音量键试试是否有效")
    }

    private fun permissionsFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !AlarmScheduler.canScheduleExact(this)) {
            openSafely(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
            )
            return
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = getSystemService(NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) {
                openSafely(
                    Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:$packageName")
                    )
                )
                return
            }
        }
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            openSafely(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        openSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        toast("系统级权限都开了；这里再手动确认「自启动」「后台活动」")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun refresh() {
        val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        status.text = if (store.enabled) {
            val chain = if (store.chainActive) "（正在响，未完成）" else ""
            "已启用 · 每天 ${"%02d:%02d".format(store.hour, store.minute)}$chain\n下次：${fmt.format(Date(store.nextAt))}"
        } else {
            "未启用"
        }

        val nm = getSystemService(NotificationManager::class.java)
        val exact = AlarmScheduler.canScheduleExact(this)
        val fsi = if (Build.VERSION.SDK_INT >= 34) nm.canUseFullScreenIntent() else true
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        val battery = getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(packageName)
        val vibOn = Settings.System.getInt(contentResolver, Settings.System.VIBRATE_ON, 1) == 1

        perms.text = buildString {
            appendLine("精确闹钟：${mark(exact)}")
            appendLine("全屏通知：${mark(fsi)}")
            appendLine("通知权限：${mark(notif)}")
            appendLine("电池优化：${if (battery) "已忽略 ✓" else "受限 ✗"}")
            appendLine("系统振动开关：${mark(vibOn)}")
        }

        findViewById<TextView>(R.id.diag).text = Diagnostics.tail(this, 12)
    }

    private fun mark(ok: Boolean) = if (ok) "✓" else "✗ 需处理"

    private fun openSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
            toast("打不开对应设置页，请手动到系统设置里找")
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}

/** 振幅控制只在部分机型可用，做个兼容壳。 */
object VibrationEffectCompat {
    fun make(timings: LongArray, amplitude: Boolean): android.os.VibrationEffect =
        if (amplitude) {
            android.os.VibrationEffect.createWaveform(
                timings,
                intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255),
                0
            )
        } else {
            android.os.VibrationEffect.createWaveform(timings, 0)
        }
}
