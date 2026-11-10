package com.mcarplay.app.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteRetryPolicyTest {
    @Test
    fun quotaStopsAtMax() {
        assertTrue(InviteRetryPolicy.hasQuota(usedCount = 0, max = 2))
        assertTrue(InviteRetryPolicy.hasQuota(usedCount = 1, max = 2))
        assertFalse("用满额度不能再等邀请", InviteRetryPolicy.hasQuota(usedCount = 2, max = 2))
        assertFalse(InviteRetryPolicy.hasQuota(usedCount = 3, max = 2))
    }

    @Test
    fun onlyNewerInviteTriggersReconnect() {
        // 必须比基线新——首次收到（baseline=0, latest=1000）算新邀请
        assertTrue(
            InviteRetryPolicy.shouldReconnect(
                latestInviteAtMillis = 1_000,
                baselineMillis = 0,
                cancelled = false,
                sessionStarting = false,
            ),
        )
        // 与基线相同 = 还是上一次那条邀请，不能重连（否则会原地打转）
        assertFalse(
            InviteRetryPolicy.shouldReconnect(
                latestInviteAtMillis = 1_000,
                baselineMillis = 1_000,
                cancelled = false,
                sessionStarting = false,
            ),
        )
        assertFalse(
            InviteRetryPolicy.shouldReconnect(
                latestInviteAtMillis = 0,
                baselineMillis = 0,
                cancelled = false,
                sessionStarting = false,
            ),
        )
    }

    @Test
    fun cancelledOrStartingSuppressesReconnect() {
        assertFalse(
            "用户已取消",
            InviteRetryPolicy.shouldReconnect(2_000, 1_000, cancelled = true, sessionStarting = false),
        )
        assertFalse(
            "新一轮已经在建",
            InviteRetryPolicy.shouldReconnect(2_000, 1_000, cancelled = false, sessionStarting = true),
        )
    }
}
