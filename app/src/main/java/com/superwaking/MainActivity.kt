package com.superwaking

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TWO_HOURS = 2 * 60 * 60 * 1000L
private const val TWELVE_HOURS = 12 * 60 * 60 * 1000L

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
        findViewById<Button>(R.id.btnCancel).setOnClickListener { requestDisableToggle() }
        findViewById<Button>(R.id.btnTest).setOnClickListener { testVibration() }
        findViewById<Button>(R.id.btnNow).setOnClickListener { ringInFiveSeconds() }
        findViewById<Button>(R.id.btnSoundTest).setOnClickListener { testSound() }
        findViewById<CheckBox>(R.id.soundEnabled).isChecked = store.soundEnabled
        findViewById<EditText>(R.id.soundMinutes).setText(store.soundAfterMin.toString())
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
        val h = picker.hour
        val m = picker.minute
        val changed = text != store.sentence || h != store.hour || m != store.minute
        val hoursLeft = store.nextAt - System.currentTimeMillis()
        val soon = store.enabled && hoursLeft in 1L..TWELVE_HOURS

        if (changed && soon) {
            requireSentence(
                "修改承诺",
                "距离下次响铃不到 12 小时。改时间或改句子，要先输入当前句子。"
            ) { applySave(text, h, m) }
        } else {
            applySave(text, h, m)
        }
    }

    private fun applySave(text: String, h: Int, m: Int) {
        store.sentence = text
        store.hour = h
        store.minute = m
        store.pendingDisableAt = 0
        store.ringCount = 0
        store.soundEnabled = findViewById<CheckBox>(R.id.soundEnabled).isChecked
        store.soundAfterMin = findViewById<EditText>(R.id.soundMinutes)
            .text.toString().toIntOrNull()?.coerceIn(1, 60) ?: 5
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

    /**
     * 关闭闹钟不再是开关，而是一次“申请”：
     * 1. 必须输入当前句子；
     * 2. 预约 2 小时后生效 —— 6:55 想关掉 7:00 的闹钟，来不及。
     */
    private fun requestDisableToggle() {
        if (store.pendingDisableAt > 0) {
            store.pendingDisableAt = 0
            toast("已撤销关闭申请，闹钟继续")
            refresh()
            return
        }
        if (!store.enabled) {
            toast("当前没有启用的闹钟")
            return
        }
        requireSentence(
            "申请关闭",
            "关闭不会立刻生效：会预约在 2 小时后。这期间下一次响铃仍会照常。"
        ) {
            store.pendingDisableAt = System.currentTimeMillis() + TWO_HOURS
            toast("已预约 2 小时后关闭")
            refresh()
        }
    }

    private fun requireSentence(title: String, message: String, onOk: () -> Unit) {
        val input = EditText(this).apply { hint = "输入当前句子" }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setView(input)
            .setPositiveButton("确认") { _, _ ->
                if (norm(input.text.toString()) == norm(store.sentence)) onOk()
                else toast("句子不对，未生效")
            }
            .setNegativeButton("算了", null)
            .show()
    }

    private fun norm(s: String) = s.replace(Regex("\\s+"), "").trim()

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

    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun testSound() {
        SoundAlert.play(this)
        toast("用闹钟音量播放 10 秒；听不到就去查系统「闹钟音量」")
        uiHandler.postDelayed({ SoundAlert.stop() }, 10_000L)
    }

    override fun onDestroy() {
        uiHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
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
        status.text = buildString {
            if (store.enabled) {
                val chain = if (store.chainActive) "（正在响，未完成）" else ""
                append("已启用 · 每天 ${"%02d:%02d".format(store.hour, store.minute)}$chain")
                append("\n下次：${fmt.format(Date(store.nextAt))}")
            } else {
                append("未启用")
            }
            if (store.pendingDisableAt > 0) {
                append("\n已预约关闭：${fmt.format(Date(store.pendingDisableAt))} 生效")
            }
        }

        findViewById<Button>(R.id.btnCancel).text =
            if (store.pendingDisableAt > 0) "撤销关闭申请" else "申请关闭闹钟（需验证）"

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
            val (av, amax) = SoundAlert.alarmVolume(this@MainActivity)
            appendLine("闹钟音量：$av/$amax")
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
