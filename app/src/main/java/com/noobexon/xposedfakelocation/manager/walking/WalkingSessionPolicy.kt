package com.noobexon.xposedfakelocation.manager.walking

/**
 * Pure decision rules for walking-session generation isolation (see MockX完整功能规划.md §5.1).
 *
 * Every session-scoped write captures the session id it belongs to when it is enqueued and
 * must validate it against the id currently stored in the shared preferences before mutating
 * state. This is what keeps a delayed stop/fail/tick from a previous generation from
 * clobbering a session the user already restarted.
 *
 * Pure Kotlin so both boundaries are unit-testable without Android framework stubs.
 */
object WalkingSessionPolicy {

    /**
     * Whether a queued session-scoped write captured in generation [writerSessionId] may still
     * mutate the shared state. Only an exact match against the current id passes; a `null`
     * current id (session cleared, or remote preferences unreachable) also rejects the write —
     * the repository no-ops such writes anyway, so dropping early keeps both sides consistent.
     */
    fun mayWrite(writerSessionId: String?, currentSessionId: String?): Boolean =
        writerSessionId != null && writerSessionId == currentSessionId

    /**
     * Whether a terminal stop/fail cleanup captured in generation [writerSessionId] has been
     * superseded by a newer session. Only a positive mismatch counts: a `null` on either side
     * must NOT be treated as supersession, otherwise a momentarily unreachable remote
     * preferences service would make the finalizer skip both the cleanup and the shutdown and
     * leave the foreground notification stuck.
     */
    fun isSuperseded(writerSessionId: String?, currentSessionId: String?): Boolean =
        writerSessionId != null && currentSessionId != null && writerSessionId != currentSessionId
}
