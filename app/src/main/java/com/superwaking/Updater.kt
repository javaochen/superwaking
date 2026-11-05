package com.superwaking

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 从 GitHub Releases 检查并下载新版本。
 * 只做“下载 + 拉起安装器”，系统仍会要求用户确认安装。
 */
object Updater {

    private const val API =
        "https://api.github.com/repos/javaochen/superwaking/releases/latest"

    fun check(activity: Activity, silent: Boolean, onDone: (() -> Unit)? = null) {
        Thread {
            var handled = false
            try {
                val conn = URL(API).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "SuperWaking")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val json = JSONObject(body)
                val tag = json.getString("tag_name").removePrefix("v").trim()
                val assets = json.getJSONArray("assets")
                var url: String? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name").endsWith(".apk")) {
                        url = a.getString("browser_download_url")
                        break
                    }
                }
                if (url == null) {
                    if (!silent) say(activity, "这个版本没有附带 APK")
                    return@Thread
                }
                if (!isNewer(tag, BuildConfig.VERSION_NAME)) {
                    if (!silent) say(activity, "已是最新：${BuildConfig.VERSION_NAME}")
                    return@Thread
                }
                handled = true
                val apkUrl = url
                activity.runOnUiThread {
                    AlertDialog.Builder(activity)
                        .setTitle("发现新版本 $tag")
                        .setMessage("当前 ${BuildConfig.VERSION_NAME}，是否下载并安装？")
                        .setPositiveButton("下载") { _, _ -> download(activity, apkUrl) }
                        .setNegativeButton("以后", null)
                        .show()
                }
            } catch (e: Exception) {
                if (!silent) say(activity, "检查失败：${e.javaClass.simpleName}")
            } finally {
                if (!handled) activity.runOnUiThread { onDone?.invoke() }
            }
        }.start()
    }

    private fun download(activity: Activity, url: String) {
        say(activity, "开始下载…")
        Thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 60000
                conn.instanceFollowRedirects = true
                val file = File(activity.cacheDir, "update.apk")
                conn.inputStream.use { input ->
                    file.outputStream().use { out -> input.copyTo(out) }
                }
                if (file.length() < 50_000) throw IllegalStateException("文件不完整")
                activity.runOnUiThread { install(activity, file) }
            } catch (e: Exception) {
                say(activity, "下载失败：${e.message}")
            }
        }.start()
    }

    private fun install(activity: Activity, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            say(activity, "请先允许本应用「安装未知应用」")
            try {
                activity.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")
                    )
                )
            } catch (_: Exception) {
            }
            return
        }
        val uri = Uri.parse("content://${activity.packageName}.apk/update.apk")
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            activity.startActivity(i)
        } catch (e: Exception) {
            say(activity, "无法拉起安装器：${e.message}")
        }
    }

    fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun say(activity: Activity, msg: String) {
        activity.runOnUiThread {
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
