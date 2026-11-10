package com.mcarplay.app.net

/**
 * 「SETUP 被车机关通道后等下一次 ctrl 邀请再重连」的纯决策。
 *
 * 真流程在 SessionController.scheduleInviteRetry 里（60s 轮询 + 进程级可变状态），
 * 这段抽出来是为了能被 JVM 单测覆盖：额度上限与「邀请是否比基线新」两条分支
 * 出错的表现都是「该重连的不重连 / 不该重连的反复重连」，真机上看不出来。
 */
internal object InviteRetryPolicy {
    /** 还有重试额度吗。 */
    fun hasQuota(
        usedCount: Int,
        max: Int,
    ): Boolean = usedCount < max

    /**
     * 轮询的这一拍该不该重连。
     *
     * @param latestInviteAtMillis 车机最近一次 ctrl 邀请的 uptime；0 = 从未收到
     * @param baselineMillis 本次等待开始前的邀请时刻（必须**比它更新**才算新邀请）
     */
    fun shouldReconnect(
        latestInviteAtMillis: Long,
        baselineMillis: Long,
        cancelled: Boolean,
        sessionStarting: Boolean,
    ): Boolean = !cancelled && !sessionStarting && latestInviteAtMillis > baselineMillis
}
