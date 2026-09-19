package com.noobexon.xposedfakelocation.manager.walking

import com.noobexon.xposedfakelocation.manager.route.WalkingPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the PAUSE/RESUME command phase transitions of the walking service (追踪.md P2-001,
 * MockX完整功能规划.md §5.4): pausing a live WALKING state must produce PAUSED; pausing or
 * resuming any other phase must be an idempotent no-op that never clobbers ARRIVED, IDLE,
 * FAILED, READY or PLANNING.
 */
class WalkingCommandPolicyTest {

    @Test
    fun `pausing a walking session transitions to paused`() {
        assertEquals(WalkingPhase.PAUSED, WalkingCommandPolicy.phaseAfterPause(WalkingPhase.WALKING))
    }

    @Test
    fun `pausing a paused session is a no-op`() {
        assertNull(WalkingCommandPolicy.phaseAfterPause(WalkingPhase.PAUSED))
    }

    @Test
    fun `pausing never changes terminal or preview phases`() {
        assertNull(WalkingCommandPolicy.phaseAfterPause(WalkingPhase.ARRIVED))
        assertNull(WalkingCommandPolicy.phaseAfterPause(WalkingPhase.IDLE))
        assertNull(WalkingCommandPolicy.phaseAfterPause(WalkingPhase.FAILED))
        assertNull(WalkingCommandPolicy.phaseAfterPause(WalkingPhase.READY))
        assertNull(WalkingCommandPolicy.phaseAfterPause(WalkingPhase.PLANNING))
        assertNull(WalkingCommandPolicy.phaseAfterPause(WalkingPhase.STOPPING))
    }

    @Test
    fun `resuming a live session is allowed`() {
        assertTrue(WalkingCommandPolicy.canResume(WalkingPhase.WALKING))
        assertTrue(WalkingCommandPolicy.canResume(WalkingPhase.PAUSED))
    }

    @Test
    fun `resuming never resurrects terminal or preview phases`() {
        assertFalse(WalkingCommandPolicy.canResume(WalkingPhase.ARRIVED))
        assertFalse(WalkingCommandPolicy.canResume(WalkingPhase.IDLE))
        assertFalse(WalkingCommandPolicy.canResume(WalkingPhase.FAILED))
        assertFalse(WalkingCommandPolicy.canResume(WalkingPhase.READY))
        assertFalse(WalkingCommandPolicy.canResume(WalkingPhase.PLANNING))
        assertFalse(WalkingCommandPolicy.canResume(WalkingPhase.STOPPING))
    }
}
