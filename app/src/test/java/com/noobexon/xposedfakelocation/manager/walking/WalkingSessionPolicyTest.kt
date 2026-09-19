package com.noobexon.xposedfakelocation.manager.walking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers walking-session generation isolation (MockX完整功能规划.md §5.1): every session-scoped
 * shared-state write must be dropped once the shared state has moved to a different session
 * generation, and a terminal cleanup must recognise when a newer session has superseded it.
 */
class WalkingSessionPolicyTest {

    // region mayWrite — guard for session-scoped writes (tick / pause / resume / arrival)

    @Test
    fun `a write whose generation matches the shared state is applied`() {
        assertTrue(WalkingSessionPolicy.mayWrite("gen-a", "gen-a"))
    }

    @Test
    fun `a write from a previous generation is dropped`() {
        assertFalse(WalkingSessionPolicy.mayWrite("gen-a", "gen-b"))
    }

    @Test
    fun `a write with no captured generation is dropped`() {
        assertFalse(WalkingSessionPolicy.mayWrite(null, "gen-a"))
    }

    @Test
    fun `a write against a cleared or unreachable session state is dropped`() {
        // Terminal cleanup clears the id; remote preferences may also be briefly unreachable.
        assertFalse(WalkingSessionPolicy.mayWrite("gen-a", null))
        assertFalse(WalkingSessionPolicy.mayWrite(null, null))
    }

    // endregion

    // region isSuperseded — guard for the terminal stop/fail finalizer

    @Test
    fun `a finalizer whose generation was replaced is superseded`() {
        assertTrue(WalkingSessionPolicy.isSuperseded("gen-a", "gen-b"))
    }

    @Test
    fun `a finalizer for the live generation is not superseded`() {
        assertFalse(WalkingSessionPolicy.isSuperseded("gen-a", "gen-a"))
    }

    @Test
    fun `a null current id never counts as superseded`() {
        // The id is null after the generation was cleared OR while remote preferences are
        // unreachable; the finalizer must fall through to the legacy cleanup either way,
        // otherwise the foreground notification and the service itself would be stuck.
        assertFalse(WalkingSessionPolicy.isSuperseded("gen-a", null))
        assertFalse(WalkingSessionPolicy.isSuperseded(null, "gen-a"))
        assertFalse(WalkingSessionPolicy.isSuperseded(null, null))
    }

    // endregion
}
