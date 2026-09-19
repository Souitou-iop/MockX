package com.noobexon.xposedfakelocation.manager.walking

import com.noobexon.xposedfakelocation.manager.route.WalkingPhase

/**
 * Pure decision rules for walking-service commands (see 追踪.md P2-001), kept free of Android
 * dependencies so the phase transitions are unit-testable.
 */
object WalkingCommandPolicy {

    /**
     * Target phase for a PAUSE command: `PAUSED` only while the shared state is `WALKING`;
     * `null` for every other phase (`PAUSED`/`ARRIVED`/`IDLE`/`FAILED`/…), making repeated or
     * late pause commands idempotent no-ops that never clobber an unrelated state.
     */
    fun phaseAfterPause(currentPhase: WalkingPhase): WalkingPhase? =
        if (currentPhase == WalkingPhase.WALKING) WalkingPhase.PAUSED else null

    /**
     * Whether a RESUME command may start (or restart) the ticker. Only live sessions qualify:
     * `WALKING` (e.g. adopted after a process restart without a null-intent restore) and
     * `PAUSED`. `ARRIVED`/`IDLE`/`FAILED`/preview phases must never be resurrected by a late
     * or mis-tapped resume (MockX完整功能规划.md §5.4).
     */
    fun canResume(currentPhase: WalkingPhase): Boolean =
        currentPhase == WalkingPhase.WALKING || currentPhase == WalkingPhase.PAUSED
}
