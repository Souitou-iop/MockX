package com.noobexon.xposedfakelocation.manager.notification

import com.noobexon.xposedfakelocation.manager.route.WalkingPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidLiveUpdateAdapterTest {

    @Test
    fun `walking spec keeps the shared per-mille progress`() {
        val spec = AndroidLiveUpdateAdapter.styleSpec(
            WalkingNotificationState(WalkingPhase.WALKING, 420, 420.0, 1000.0, 300, 1),
        )
        assertEquals(420, spec!!.progressPerMille)
        assertEquals(LiveUpdateStyleSpec.TrackerMarker.WALKING, spec.trackerMarker)
    }

    @Test
    fun `paused spec keeps the current position instead of advancing`() {
        val spec = AndroidLiveUpdateAdapter.styleSpec(
            WalkingNotificationState(WalkingPhase.PAUSED, 420, 420.0, 1000.0, null, 2),
        )
        assertEquals(420, spec!!.progressPerMille)
        assertEquals(LiveUpdateStyleSpec.TrackerMarker.PAUSED, spec.trackerMarker)
    }

    @Test
    fun `arrived spec reports the full bar`() {
        val spec = AndroidLiveUpdateAdapter.styleSpec(
            WalkingNotificationState(WalkingPhase.ARRIVED, 1000, 1000.0, 1000.0, null, 3),
        )
        assertEquals(1000, spec!!.progressPerMille)
        assertEquals(LiveUpdateStyleSpec.TrackerMarker.ARRIVED, spec.trackerMarker)
    }

    @Test
    fun `non-session phases produce no Live Update spec`() {
        for (phase in listOf(WalkingPhase.IDLE, WalkingPhase.PLANNING, WalkingPhase.READY, WalkingPhase.STOPPING, WalkingPhase.FAILED)) {
            assertNull("phase $phase must not produce a spec", AndroidLiveUpdateAdapter.styleSpec(
                WalkingNotificationState(phase, 0, 0.0, 100.0, null, 1),
            ))
        }
    }

    @Test
    fun `live update spec and standard bar share one progress source`() {
        val state = WalkingNotificationState.fromSession(WalkingPhase.WALKING, 333.0, 1000.0, 1.4f, 1)
        val spec = AndroidLiveUpdateAdapter.styleSpec(state)!!
        assertEquals(state.progressPerMille, spec.progressPerMille)
        assertTrue(spec.progressPerMille in 0..WalkingNotificationState.PROGRESS_MAX)
    }
}
