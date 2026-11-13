package com.superwaking

import android.content.Context

/** 极简状态存储：不引 DataStore/Room，避免依赖与初始化成本。 */
class Store(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("superwaking", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    var hour: Int
        get() = sp.getInt("hour", 7)
        set(v) = sp.edit().putInt("hour", v).apply()

    var minute: Int
        get() = sp.getInt("minute", 0)
        set(v) = sp.edit().putInt("minute", v).apply()

    var sentence: String
        get() = sp.getString("sentence", "") ?: ""
        set(v) = sp.edit().putString("sentence", v).apply()

    /** 闹钟链是否处于“已响未完成”状态 */
    var chainActive: Boolean
        get() = sp.getBoolean("chain", false)
        set(v) = sp.edit().putBoolean("chain", v).apply()

    var nextAt: Long
        get() = sp.getLong("nextAt", 0L)
        set(v) = sp.edit().putLong("nextAt", v).apply()

    /** 是否允许“响太久就出声”升级 */
    var soundEnabled: Boolean
        get() = sp.getBoolean("soundEnabled", true)
        set(v) = sp.edit().putBoolean("soundEnabled", v).apply()

    /** 响铃多少分钟后开始出声 */
    var soundAfterMin: Int
        get() = sp.getInt("soundAfterMin", 5)
        set(v) = sp.edit().putInt("soundAfterMin", v).apply()

    /**
     * 预约关闭时间戳。>0 表示已申请关闭但尚未生效；
     * 到点之前闹钟照常响，避免半睡状态下把自己“关掉”。
     */
    var pendingDisableAt: Long
        get() = sp.getLong("pendingDisableAt", 0L)
        set(v) = sp.edit().putLong("pendingDisableAt", v).apply()

    /** 本次响铃已经是第几次（未完成就续响一次 +1） */
    var ringCount: Int
        get() = sp.getInt("ringCount", 0)
        set(v) = sp.edit().putInt("ringCount", v).apply()

    /** 睡前写的“明天起床后第一件事” */
    var contract: String
        get() = sp.getString("contract", "") ?: ""
        set(v) = sp.edit().putString("contract", v).apply()

    var contractAt: Long
        get() = sp.getLong("contractAt", 0L)
        set(v) = sp.edit().putLong("contractAt", v).apply()

    /** 连续“第一次响铃就完成”的天数 */
    var streak: Int
        get() = sp.getInt("streak", 0)
        set(v) = sp.edit().putInt("streak", v).apply()

    var contractReminder: Boolean
        get() = sp.getBoolean("contractReminder", true)
        set(v) = sp.edit().putBoolean("contractReminder", v).apply()

    /** 上次检查更新的时间戳，用于 24 小时节流 */
    var lastCheck: Long
        get() = sp.getLong("lastCheck", 0L)
        set(v) = sp.edit().putLong("lastCheck", v).apply()
}
