package com.mcarplay.app.carlife

import android.content.Context

/**
 * 记录「CarWith 侧可能还留着上一轮 CarLife 会话」。
 *
 * mcarplay 被强杀或崩溃时 CarWith（com.miui.carlink）不会跟着清会话，下一次握手会在
 * 命令通道解密失败（日志：七路连接完成 → 命令通道解密失败 → CarLife 握手失败），
 * 表现为「连上了但 CarWith 不出来」。真机实测：把 com.miui.carlink 也 force-stop
 * 掉再连即恢复。
 *
 * 只在**建会话时置位、正常收尾时清除**：进程被杀时来不及清除，标记就留下了，
 * 下一次连接据此判断「上次是异常结束」。
 */
internal object CarWithSessionFlag {
    private const val PREFS = "carlife_session"
    private const val KEY_OPEN = "carlife_open"

    fun markOpen(context: Context) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_OPEN, true).apply()
        }
    }

    fun clear(context: Context) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_OPEN).apply()
        }
    }

    /** 取一次即清：返回 true = 上次进程没走正常收尾。 */
    fun takeAbnormal(context: Context): Boolean =
        runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val open = prefs.getBoolean(KEY_OPEN, false)
            if (open) prefs.edit().remove(KEY_OPEN).apply()
            open
        }.getOrDefault(false)
}
