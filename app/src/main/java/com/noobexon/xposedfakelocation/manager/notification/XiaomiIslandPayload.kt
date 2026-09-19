package com.noobexon.xposedfakelocation.manager.notification

import com.google.gson.JsonObject

/**
 * Minimal, officially documented `miui.focus.param` payload for the HyperOS 3 Super Island
 * (MockX完整功能规划.md §9.4). Only fields confirmed by the 澎湃OS 岛通知开发指南
 * (dev.mi.com pId=2131) and its 模板库 are emitted — no experimental keys, no coordinates,
 * no API key, no route geometry.
 *
 * `sequence` is deliberately omitted: the official field table marks it MIPUSH-only, local
 * `notify()` updates on one notification id are ordered by the system, and the service-side
 * [WalkingNotificationState.sequence] guard already drops out-of-order refreshes.
 */
object XiaomiIslandPayload {

    const val EXTRA_FOCUS_PARAM = "miui.focus.param"
    const val EXTRA_FOCUS_PICS = "miui.focus.pics"
    /** Key inside [EXTRA_FOCUS_PICS] and the `pic` reference used by the island templates. */
    const val KEY_ISLAND_PICTURE = "miui.focus.pic_island"

    /** Brand-stable accent for the progress bar; the OS renders the bar itself. */
    private const val PROGRESS_COLOR = "#0F766E"
    private const val BUSINESS = "mockx.walking"

    /** [pictureNeeded] is false when there was no app icon to reference from the island surfaces. */
    data class Payload(val json: String, val pictureNeeded: Boolean)

    /**
     * Builds the payload or returns null when the inputs are unusable. Never throws.
     *
     * @param title notification title, e.g. "MockX is simulating a walk"
     * @param status short phase label, e.g. "Simulating walk"
     * @param distanceSummary "travelled / total" text
     * @param eta localized ETA line, or null
     * @param progressPercent 0..100, clamped
     */
    fun build(
        title: String,
        status: String,
        distanceSummary: String,
        eta: String?,
        progressPercent: Int,
    ): Payload? {
        if (title.isBlank() || status.isBlank() || distanceSummary.isBlank()) return null
        val progress = progressPercent.coerceIn(0, 100)
        return try {
            val bigIslandText = JsonObject().apply {
                addProperty("frontTitle", status)
                addProperty("title", distanceSummary)
                if (!eta.isNullOrBlank()) addProperty("content", eta)
            }
            val paramV2 = JsonObject().apply {
                addProperty("protocol", 3) // OS3 Super Island; OS2 falls back to the focus capsule
                addProperty("business", BUSINESS)
                addProperty("updatable", true) // persistent notification updated by later notify() calls
                addProperty("enableFloat", false) // progress updates must not auto-expand the island
                addProperty("islandFirstFloat", false) // first appearance stays in the summary state
                addProperty("ticker", status) // OS2 status-bar capsule text
                addProperty("aodTitle", status)
                add("progressInfo", JsonObject().apply {
                    addProperty("progress", progress)
                    addProperty("colorProgress", PROGRESS_COLOR)
                })
                add("baseInfo", JsonObject().apply {
                    addProperty("type", 2) // 文本组件 2: 主要文本 1 + 次要文本 1 (模板库)
                    addProperty("title", title)
                    addProperty(
                        "content",
                        listOf(status, distanceSummary, eta)
                            .filterNot { it.isNullOrBlank() }
                            .joinToString(" · "),
                    )
                })
                add("param_island", JsonObject().apply {
                    addProperty("islandProperty", 1) // information-first island
                    addProperty("islandPriority", 2)
                    add("bigIslandArea", JsonObject().apply {
                        add("imageTextInfoLeft", JsonObject().apply {
                            addProperty("type", 1)
                            add("picInfo", JsonObject().apply {
                                addProperty("type", 1)
                                addProperty("pic", KEY_ISLAND_PICTURE)
                            })
                            add("textInfo", bigIslandText)
                        })
                    })
                    add("smallIslandArea", JsonObject().apply {
                        add("picInfo", JsonObject().apply {
                            addProperty("type", 1)
                            addProperty("pic", KEY_ISLAND_PICTURE)
                        })
                    })
                })
            }
            val json = JsonObject().apply { add("param_v2", paramV2) }.toString()
            Payload(json, pictureNeeded = true)
        } catch (_: Exception) {
            null
        }
    }
}
