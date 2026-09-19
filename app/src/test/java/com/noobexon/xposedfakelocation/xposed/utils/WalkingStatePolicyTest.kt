package com.noobexon.xposedfakelocation.xposed.utils

import com.noobexon.xposedfakelocation.data.WALKING_STATE_STALE_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the staleness decision of the hook-visible walking state (追踪.md P1-001):
 * the persisted timestamp is Unix wall-clock time and must be evaluated against the same
 * time base — with a safe treatment of missing values and clock rollback.
 */
class WalkingStatePolicyTest {

    private val now = 1_000_000L

    @Test
    fun `age is now minus updated-at on the same wall-clock base`() {
        assertEquals(5_000L, WalkingStatePolicy.ageMillis(now - 5_000L, now))
    }

    @Test
    fun `missing timestamp yields null age and is stale`() {
        assertNull(WalkingStatePolicy.ageMillis(null, now))
        assertTrue(WalkingStatePolicy.isStale(null))
    }

    @Test
    fun `state within the threshold is fresh`() {
        assertFalse(WalkingStatePolicy.isStale(0L))
        assertFalse(WalkingStatePolicy.isStale(WALKING_STATE_STALE_MS - 1))
        assertFalse(WalkingStatePolicy.isStale(WALKING_STATE_STALE_MS))
    }

    @Test
    fun `state beyond the threshold is stale`() {
        assertTrue(WalkingStatePolicy.isStale(WALKING_STATE_STALE_MS + 1))
        assertTrue(WalkingStatePolicy.isStale(60_000L))
    }

    @Test
    fun `negative age from clock rollback is treated as just updated`() {
        assertFalse(WalkingStatePolicy.isStale(-1L))
        assertFalse(WalkingStatePolicy.isStale(-60_000L))
    }

    @Test
    fun `explicit threshold is honoured`() {
        val policy = WalkingStatePolicy
        assertTrue(policy.isStale(16_000L, staleThresholdMillis = 15_000L))
        assertFalse(policy.isStale(15_000L, staleThresholdMillis = 15_000L))
    }
}
