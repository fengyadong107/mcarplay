package com.mcarplay.app

import android.app.Application
import android.util.Log
import com.mcarplay.app.diagnostics.ConnectionDiagnostics
import com.mcarplay.app.i18n.AppLanguage
import com.mcarplay.app.ui.ScreenDimmer
import com.mcarplay.app.util.DebugFileLog

/**
 * 进程级安全网：任一线程的未捕获异常只记录并让该线程结束，
 * 不再走默认处理器杀死整个进程（连接会话横跨多个后台线程，
 * 一个套接字异常不应毁掉全部链路与前台服务）。
 * 各线程自身仍应尽量用 try/catch 精确处理；这里是最后防线。
 */
open class SenderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 会话日志起点标记：日志文件跨进程累积（2MB 上限），导出默认只取本次启动
        // 之后的部分。必须在这里写（Application 是进程最早入口），晚于任何业务日志
        // 都会让它被算进「历史」。
        DebugFileLog.markSessionStart(this)
        ConnectionDiagnostics.initialize(this)
        // 界面语言：未选过时落到默认中文（不跟随系统语言）
        AppLanguage.applyAtStartup(this)
        // 后台自动连接不经过 MainActivity：暗幕/真息屏只在那里 attach 会静默失效
        // （2026-11-19 报告 5/5 会话「物理屏断电失败」、屏幕整程常亮）。
        // 常亮开关在这里先读一次，否则 Activity 没跑时默认值会盖过用户设置。
        ScreenDimmer.enabled =
            getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                .getBoolean("alwaysOn", true)
        ScreenDimmer.attach(this)
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { ConnectionDiagnostics.uncaught(thread, throwable) }
            Log.e(TAG, "未捕获异常（线程 ${thread.name}，已吞掉保进程）", throwable)
            // 不调用 previous：默认处理器会 killProcess 杀死整个 app
        }
    }

    companion object {
        private const val TAG = "CarPlaySender"
    }
}
