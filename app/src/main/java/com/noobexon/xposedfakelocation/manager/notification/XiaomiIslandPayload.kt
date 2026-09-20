package com.noobexon.xposedfakelocation.manager.notification

import com.google.gson.JsonArray
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
 *
 * Template mapping (小米超级岛通知模板库):
 * - Walking → 模板4 (文本组件2 + 识别图形组件1 + 进度组件1): `baseInfo` carries the
 *   文本组件2 and `progressInfo` carries the 进度组件1 with forward/middle/end pictures.
 * - Virtual location → 模板13 (强调图文组件 + 按钮组件1): `highlightInfo` carries the
 *   强调图文组件 with a native `timerInfo` counting-up timer so the OS renders the elapsed
 *   duration itself, and `baseInfo` mirrors the same data for the notification centre.
 */
object XiaomiIslandPayload {

    const val EXTRA_FOCUS_PARAM = "miui.focus.param"
    const val EXTRA_FOCUS_PICS = "miui.focus.pics"
    /** Key inside [EXTRA_FOCUS_PICS] and the `pic` reference used by the island templates. */
    const val KEY_ISLAND_PICTURE = "miui.focus.pic_island"
    /** Function icon key referenced by `picFunction` in baseInfo/highlightInfo (模板库 附录). */
    const val KEY_PIC_FUNCTION = "miui.focus.pic_function"
    /** 进度组件1 picture keys (模板库 附录 §4.2). No middle-node keys: the bar runs straight
     * from the walker to the destination pin. */
    const val KEY_PIC_FORWARD = "miui.focus.pic_forward"
    const val KEY_PIC_END = "miui.focus.pic_end"
    const val KEY_PIC_END_UNSELECTED = "miui.focus.pic_end_unselected"

    /** Broadcast action the island stop button dispatches to [IslandStopReceiver]. */
    const val ACTION_ISLAND_STOP_FIXED = "io.github.souitou.mockx.action.ISLAND_STOP_FIXED"
    private const val STOP_RECEIVER_COMPONENT =
        "io.github.souitou.mockx/.manager.control.IslandStopReceiver"

    /** Brand-stable accent for the progress bar and timer; the OS renders the bar itself. */
    private const val PROGRESS_COLOR = "#1E88E5"
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
     * @param progressPercent 0..100, clamped (walking only; virtual-location passes -1 to hide
     * the bar and switch to 模板13 layout)
     * @param islandContent optional right-side text for the collapsed island (fixed virtual
     * location: the elapsed time). Follows 摘要态设计规范模版2: the left side is the 图文组件1
     * (mode name as large text) and the right side is a 文本组件 carrying [islandContent]. null
     * keeps the collapsed island at just the mode name.
     * @param expandedLines optional lines rendered in the expanded state / notification centre,
     * one per list entry (fixed virtual location: coordinates, then elapsed time). null keeps the
     * default single-line [distanceSummary]/[eta] content.
     * @param timerStartedAt epoch-millis when the virtual-location session started; when > 0 and
     * [progressPercent] < 0, emits a `highlightInfo` with `timerInfo` (timerType=1 正计时开始)
     * so the OS natively renders the counting-up elapsed timer in the island expanded state
     * (模板13 强调图文组件).
     * @param stopActionTitle localized title of the stop button (模板13 按钮组件1). When non-null
     * and [progressPercent] < 0, emits an `actions` array with one circular stop button
     * (`actionIntentType=2` broadcast → [ACTION_ISLAND_STOP_FIXED]). Passing null leaves the
     * expanded state without buttons (模板4 walking has none).
     */
    fun build(
        title: String,
        status: String,
        distanceSummary: String,
        eta: String?,
        progressPercent: Int,
        islandContent: String? = null,
        expandedLines: List<String>? = null,
        timerStartedAt: Long = 0L,
        stopActionTitle: String? = null,
    ): Payload? {
        if (title.isBlank() || status.isBlank() || distanceSummary.isBlank()) return null
        val progress = progressPercent.coerceIn(0, 100)
        val isVirtualLocation = progressPercent < 0
        return try {
            val paramV2 = JsonObject().apply {
                addProperty("protocol", 3) // OS3 Super Island; OS2 falls back to the focus capsule
                addProperty("business", BUSINESS)
                addProperty("updatable", true) // persistent notification updated by later notify() calls
                addProperty("enableFloat", false) // progress updates must not auto-expand the island
                addProperty("islandFirstFloat", false) // first appearance stays in the summary state
                addProperty("ticker", status) // OS2 status-bar capsule text
                addProperty("aodTitle", status)

                // --- Expanded-state components (模板4 / 模板13) ---

                // 文本组件2 (baseInfo): renders in the notification centre AND the island
                // expanded state. 模板4 (walking) and 模板13 (virtual location) both carry one.
                add("baseInfo", JsonObject().apply {
                    addProperty("type", 2) // 文本组件 2: 主要文本 1 + 次要文本 1 (模板库)
                    addProperty("title", title)
                    addProperty("picFunction", KEY_PIC_FUNCTION)
                    if (expandedLines.isNullOrEmpty()) {
                        addProperty(
                            "content",
                            listOf(status, distanceSummary, eta)
                                .filterNot { it.isNullOrBlank() }
                                .joinToString(" · "),
                        )
                    } else {
                        // One line per entry — "\n" in one property renders as multiple lines.
                        addProperty("content", expandedLines.joinToString("\n"))
                    }
                })

                // 进度组件1 (progressInfo): walking only (模板4). Virtual-location passes -1
                // to skip this entirely (模板13 has no progress bar).
                if (!isVirtualLocation) {
                    add("progressInfo", JsonObject().apply {
                        addProperty("progress", progress)
                        addProperty("colorProgress", PROGRESS_COLOR)
                        // 进度组件1 picture fields (模板库 附录 §4.2). When these are present
                        // the OS renders the full progress bar with forward/end markers;
                        // if the Icons are missing from the Bundle the OS degrades to 进度组件2.
                        addProperty("picForward", KEY_PIC_FORWARD)
                        addProperty("picEnd", KEY_PIC_END)
                        addProperty("picEndUnselected", KEY_PIC_END_UNSELECTED)
                    })
                }

                // 强调图文组件 (highlightInfo): virtual-location only (模板13). Carries the
                // native timerInfo so the OS itself renders the counting-up elapsed timer —
                // the manual per-second refresh still updates the baseInfo/notification centre copy.
                if (isVirtualLocation && timerStartedAt > 0L) {
                    add("highlightInfo", JsonObject().apply {
                        addProperty("title", "00:00") // initial; overridden by live timer
                        addProperty("content", expandedLines?.firstOrNull() ?: distanceSummary)
                        addProperty("subContent", status)
                        addProperty("picFunction", KEY_PIC_FUNCTION)
                        add("timerInfo", JsonObject().apply {
                            addProperty("timerType", 1) // 正计时开始 (counting up)
                            addProperty("timerWhen", timerStartedAt)
                            addProperty("timerTotal", 0)
                            addProperty("timerCurrent", 0)
                        })
                    })
                }

                // 按钮组件1 (actions): virtual-location only (模板13). One circular stop button
                // that dispatches a broadcast the island system starts via the intent URI
                // (模板库 §关于 actionInfo / 自定义构建 Action). The receiver must be exported.
                if (isVirtualLocation && !stopActionTitle.isNullOrBlank()) {
                    add("actions", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("type", 0) // 普通按钮 (圆形)
                            addProperty("actionIcon", KEY_ISLAND_PICTURE)
                            addProperty("actionTitle", stopActionTitle)
                            addProperty("actionIntentType", 2) // action to broadcast
                            addProperty("actionIntent", stopIntentUri())
                            addProperty("clickWithCollapse", true)
                        })
                    })
                }

                // --- Summary-state island (摘要态 模板2) ---

                add("param_island", JsonObject().apply {
                    addProperty("islandProperty", 1) // information-first island
                    addProperty("islandPriority", 2)
                    add("bigIslandArea", JsonObject().apply {
                        // A 区: 图文组件1 — app icon + the mode name as large text.
                        add("imageTextInfoLeft", JsonObject().apply {
                            addProperty("type", 1)
                            add("picInfo", JsonObject().apply {
                                addProperty("type", 1)
                                addProperty("pic", KEY_ISLAND_PICTURE)
                            })
                            add("textInfo", JsonObject().apply {
                                addProperty("title", status)
                            })
                        })
                        // B 区: 文本组件 (模版2) — right-side text (elapsed time / coordinates).
                        if (!islandContent.isNullOrBlank()) {
                            add("textInfo", JsonObject().apply {
                                addProperty("title", islandContent)
                            })
                        }
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

    /**
     * `intent:` URI the island system resolves against the exported [IslandStopReceiver]
     * (模板库 §关于 actionInfo). Format: `intent:#Intent;action=...;component=pkg/.Cls;end`.
     */
    private fun stopIntentUri(): String =
        "intent:#Intent;action=$ACTION_ISLAND_STOP_FIXED;component=$STOP_RECEIVER_COMPONENT;end"
}
