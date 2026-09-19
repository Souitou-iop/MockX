package com.noobexon.xposedfakelocation.xposed.utils

import com.noobexon.xposedfakelocation.data.WALKING_STATE_STALE_MS

/**
 * Time-base policy for the hook-visible walking state (see 追踪.md P1-001).
 *
 * `walking_updated_at` is persisted as Unix wall-clock milliseconds
 * ([System.currentTimeMillis]). The hook side must therefore evaluate staleness against
 * wall-clock time as well — mixing in `SystemClock.elapsedRealtime()` (monotonic since boot)
 * produces absurd ages and silently disables the staleness guard.
 *
 * Pure Kotlin so the boundary behaviour is unit-testable without Android framework stubs.
 */
object WalkingStatePolicy {

    /**
     * Age of the walking state in milliseconds, or `null` when no timestamp exists.
     * A negative result means the device clock moved backwards after the write.
     */
    fun ageMillis(updatedAtEpochMillis: Long?, nowEpochMillis: Long): Long? =
        updatedAtEpochMillis?.let { nowEpochMillis - it }

    /**
     * Whether a live `WALKING` state with the given [ageMillis] must be treated as stale:
     *
     * - missing timestamp            → stale (never trust what was never written);
     * - age < 0 (clock rolled back)  → treated as just updated, NOT stale;
     * - 0 <= age <= [WALKING_STATE_STALE_MS] → fresh;
     * - age > threshold              → stale; the hook must fall back instead of replaying
     *   the last dynamic coordinate forever.
     */
    fun isStale(ageMillis: Long?, staleThresholdMillis: Long = WALKING_STATE_STALE_MS): Boolean =
        ageMillis == null || ageMillis > staleThresholdMillis
}
