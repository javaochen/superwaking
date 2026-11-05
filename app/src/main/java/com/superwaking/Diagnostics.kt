package com.superwaking

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 极简事件日志，写到 filesDir/events.log，主界面可查看最近若干条。 */
object Diagnostics {

    private const val FILE = "events.log"
    private const val MAX_BYTES = 64 * 1024

    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    @Synchronized
    fun log(context: Context, msg: String) {
        try {
            val f = File(context.applicationContext.filesDir, FILE)
            if (f.exists() && f.length() > MAX_BYTES) f.writeText("")
            f.appendText("${fmt.format(Date())} $msg\n")
        } catch (_: Exception) {
            // 日志失败不能影响闹钟本身
        }
    }

    fun tail(context: Context, lines: Int = 12): String {
        return try {
            val f = File(context.applicationContext.filesDir, FILE)
            if (!f.exists()) "(暂无记录)" else f.readLines().takeLast(lines).joinToString("\n")
        } catch (e: Exception) {
            "(读取失败: ${e.javaClass.simpleName})"
        }
    }

    fun clear(context: Context) {
        try {
            File(context.applicationContext.filesDir, FILE).writeText("")
        } catch (_: Exception) {
        }
    }
}
