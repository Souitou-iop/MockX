package com.noobexon.xposedfakelocation.manager.notification

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiIslandPayloadTest {

    private fun build(
        eta: String? = null,
        progressPercent: Int = 40,
        islandContent: String? = null,
        expandedLines: List<String>? = null,
        timerStartedAt: Long = 0L,
    ) = XiaomiIslandPayload.build(
            title = "MockX is simulating a walk",
            status = "Simulating walk",
            distanceSummary = "420 m / 1.00 km",
            eta = eta,
            progressPercent = progressPercent,
            islandContent = islandContent,
            expandedLines = expandedLines,
            timerStartedAt = timerStartedAt,
        )!!

    @Test
    fun `payload wraps a param_v2 root with the OS3 protocol`() {
        val root = JsonParser.parseString(build().json).asJsonObject
        val paramV2 = root.getAsJsonObject("param_v2")
        assertNotNull(paramV2)
        assertEquals(3, paramV2.get("protocol").asInt)
        assertEquals("mockx.walking", paramV2.get("business").asString)
        assertEquals(true, paramV2.get("updatable").asBoolean)
    }

    @Test
    fun `island starts collapsed and never auto-expands on updates`() {
        val paramV2 = JsonParser.parseString(build().json).asJsonObject.getAsJsonObject("param_v2")
        assertEquals(false, paramV2.get("enableFloat").asBoolean)
        assertEquals(false, paramV2.get("islandFirstFloat").asBoolean)
    }

    @Test
    fun `progress bar is clamped to percent bounds`() {
        val low = JsonParser.parseString(build(progressPercent = 0).json).asJsonObject
            .getAsJsonObject("param_v2").getAsJsonObject("progressInfo")
        assertEquals(0, low.get("progress").asInt)
        val high = JsonParser.parseString(build(progressPercent = 150).json).asJsonObject
            .getAsJsonObject("param_v2").getAsJsonObject("progressInfo")
        assertEquals(100, high.get("progress").asInt)
    }

    @Test
    fun `walking progressInfo carries 进度组件1 picture keys without middle node (模板4)`() {
        val progressInfo = JsonParser.parseString(build().json).asJsonObject
            .getAsJsonObject("param_v2").getAsJsonObject("progressInfo")
        assertEquals(XiaomiIslandPayload.KEY_PIC_FORWARD, progressInfo.get("picForward").asString)
        // No middle node: the bar runs straight from the walker to the destination pin.
        assertFalse("picMiddle must be omitted", progressInfo.has("picMiddle"))
        assertFalse("picMiddleUnselected must be omitted", progressInfo.has("picMiddleUnselected"))
        assertEquals(XiaomiIslandPayload.KEY_PIC_END, progressInfo.get("picEnd").asString)
        assertEquals(XiaomiIslandPayload.KEY_PIC_END_UNSELECTED, progressInfo.get("picEndUnselected").asString)
    }

    @Test
    fun `baseInfo carries picFunction and title plus status content (模板4)`() {
        val baseInfo = JsonParser.parseString(build(eta = "approx. 5 min").json).asJsonObject
            .getAsJsonObject("param_v2").getAsJsonObject("baseInfo")
        assertEquals(2, baseInfo.get("type").asInt)
        assertEquals("MockX is simulating a walk", baseInfo.get("title").asString)
        assertEquals(XiaomiIslandPayload.KEY_PIC_FUNCTION, baseInfo.get("picFunction").asString)
        val content = baseInfo.get("content").asString
        assertTrue(content.contains("Simulating walk"))
        assertTrue(content.contains("420 m / 1.00 km"))
        assertTrue(content.contains("approx. 5 min"))
    }

    @Test
    fun `big island references the picture key and small island shows the picture`() {
        val island = JsonParser.parseString(build().json).asJsonObject
            .getAsJsonObject("param_v2").getAsJsonObject("param_island")
        assertEquals(1, island.get("islandProperty").asInt)
        val big = island.getAsJsonObject("bigIslandArea").getAsJsonObject("imageTextInfoLeft")
        assertEquals(
            XiaomiIslandPayload.KEY_ISLAND_PICTURE,
            big.getAsJsonObject("picInfo").get("pic").asString,
        )
        assertEquals("Simulating walk", big.getAsJsonObject("textInfo").get("title").asString)
        val small = island.getAsJsonObject("smallIslandArea").getAsJsonObject("picInfo")
        assertEquals(XiaomiIslandPayload.KEY_ISLAND_PICTURE, small.get("pic").asString)
    }

    @Test
    fun `virtual-location island renders mode on the left and elapsed time on the right`() {
        val island = JsonParser.parseString(build(islandContent = "04:12", expandedLines = listOf("23.1, 113.2", "Elapsed 04:12")).json)
            .asJsonObject.getAsJsonObject("param_v2").getAsJsonObject("param_island")
        val big = island.getAsJsonObject("bigIslandArea")
        val left = big.getAsJsonObject("imageTextInfoLeft").getAsJsonObject("textInfo")
        assertEquals("Simulating walk", left.get("title").asString)
        assertEquals("04:12", big.getAsJsonObject("textInfo").get("title").asString)
    }

    @Test
    fun `virtual-location payload has no progress bar and expanded lines are separated`() {
        val root = JsonParser.parseString(
            XiaomiIslandPayload.build(
                title = "MockX is simulating a walk",
                status = "Simulating walk",
                distanceSummary = "420 m / 1.00 km",
                eta = null,
                progressPercent = -1,
                islandContent = "04:12",
                expandedLines = listOf("23.1, 113.2", "Elapsed 04:12"),
            )!!.json,
        ).asJsonObject
        val paramV2 = root.getAsJsonObject("param_v2")
        // Virtual-location mode does not render a progress bar.
        assertFalse(paramV2.has("progressInfo"))
        val content = paramV2.getAsJsonObject("baseInfo").get("content").asString
        assertEquals("23.1, 113.2\nElapsed 04:12", content)
    }

    @Test
    fun `virtual-location payload carries highlightInfo with counting-up timer (模板13)`() {
        val startedAt = 1717470687604L
        val payload = XiaomiIslandPayload.build(
            title = "Virtual location",
            status = "虚拟定位",
            distanceSummary = "Virtual location active",
            eta = null,
            progressPercent = -1,
            islandContent = "04:12",
            expandedLines = listOf("23.1, 113.2", "Elapsed 04:12"),
            timerStartedAt = startedAt,
        )!!
        val paramV2 = JsonParser.parseString(payload.json).asJsonObject.getAsJsonObject("param_v2")
        assertTrue("highlightInfo must be present for 模板13", paramV2.has("highlightInfo"))
        val highlight = paramV2.getAsJsonObject("highlightInfo")
        assertEquals("00:00", highlight.get("title").asString)
        assertEquals("23.1, 113.2", highlight.get("content").asString)
        assertEquals("虚拟定位", highlight.get("subContent").asString)
        assertEquals(XiaomiIslandPayload.KEY_PIC_FUNCTION, highlight.get("picFunction").asString)
        val timer = highlight.getAsJsonObject("timerInfo")
        assertEquals(1, timer.get("timerType").asInt) // 正计时开始
        assertEquals(startedAt, timer.get("timerWhen").asLong)
    }

    @Test
    fun `virtual-location without timerStartedAt omits highlightInfo`() {
        val paramV2 = JsonParser.parseString(
            XiaomiIslandPayload.build(
                title = "Virtual location",
                status = "虚拟定位",
                distanceSummary = "Virtual location active",
                eta = null,
                progressPercent = -1,
            )!!.json,
        ).asJsonObject.getAsJsonObject("param_v2")
        assertFalse("highlightInfo should be absent when timerStartedAt is 0", paramV2.has("highlightInfo"))
    }

    @Test
    fun `virtual-location carries stop action button targeting the exported receiver (模板13)`() {
        val startedAt = 1717470687604L
        val payload = XiaomiIslandPayload.build(
            title = "Virtual location",
            status = "虚拟定位",
            distanceSummary = "Virtual location active",
            eta = null,
            progressPercent = -1,
            islandContent = "04:12",
            expandedLines = listOf("23.1, 113.2", "Elapsed 04:12"),
            timerStartedAt = startedAt,
            stopActionTitle = "停止",
        )!!
        val paramV2 = JsonParser.parseString(payload.json).asJsonObject.getAsJsonObject("param_v2")
        assertTrue("actions must be present when stopActionTitle is set", paramV2.has("actions"))
        val actions = paramV2.getAsJsonArray("actions")
        assertEquals(1, actions.size())
        val action = actions[0].asJsonObject
        assertEquals(0, action.get("type").asInt) // 普通按钮
        assertEquals(XiaomiIslandPayload.KEY_ISLAND_PICTURE, action.get("actionIcon").asString)
        assertEquals("停止", action.get("actionTitle").asString)
        assertEquals(2, action.get("actionIntentType").asInt) // action to broadcast
        val uri = action.get("actionIntent").asString
        assertTrue(uri.startsWith("intent:#Intent;"))
        assertTrue(uri.contains("action=${XiaomiIslandPayload.ACTION_ISLAND_STOP_FIXED}"))
        assertTrue(uri.contains("component=io.github.souitou.mockx/.manager.control.IslandStopReceiver"))
        assertEquals(true, action.get("clickWithCollapse").asBoolean)
    }

    @Test
    fun `walking payload has no actions and virtual-location without title omits them`() {
        val walking = JsonParser.parseString(build().json).asJsonObject
            .getAsJsonObject("param_v2")
        assertFalse("模板4 walking must not carry actions", walking.has("actions"))

        val virtual = JsonParser.parseString(
            XiaomiIslandPayload.build(
                title = "Virtual location",
                status = "虚拟定位",
                distanceSummary = "Virtual location active",
                eta = null,
                progressPercent = -1,
                timerStartedAt = 1717470687604L,
                stopActionTitle = null,
            )!!.json,
        ).asJsonObject.getAsJsonObject("param_v2")
        assertFalse("actions must be absent when stopActionTitle is null", virtual.has("actions"))
    }

    @Test
    fun `eta is omitted from the island text when unknown`() {
        val island = JsonParser.parseString(build(eta = null).json).asJsonObject
            .getAsJsonObject("param_v2").getAsJsonObject("param_island")
        val text = island.getAsJsonObject("bigIslandArea").getAsJsonObject("imageTextInfoLeft")
            .getAsJsonObject("textInfo")
        assertFalse(text.has("content"))
    }

    @Test
    fun `payload never carries coordinates or keys`() {
        val json = build(eta = "approx. 5 min").json
        for (forbidden in listOf("latitude", "longitude", "apiKey", "amap", "key=", "{lat")) {
            assertFalse("payload must not contain '$forbidden'", json.contains(forbidden, ignoreCase = true))
        }
    }

    @Test
    fun `text with quotes and newlines round-trips through the JSON`() {
        val payload = XiaomiIslandPayload.build(
            title = "MockX \"quote\" walk\nline2",
            status = "模拟\t步行",
            distanceSummary = "1 m / 2 m",
            eta = null,
            progressPercent = 0,
        )
        val parsed = JsonParser.parseString(payload!!.json).asJsonObject
        assertEquals(
            "MockX \"quote\" walk\nline2",
            parsed.getAsJsonObject("param_v2").getAsJsonObject("baseInfo").get("title").asString,
        )
        assertEquals("模拟\t步行", parsed.getAsJsonObject("param_v2").get("ticker").asString)
    }

    @Test
    fun `blank inputs produce no payload instead of a broken one`() {
        assertNull(
            XiaomiIslandPayload.build(
                title = "  ",
                status = "Simulating walk",
                distanceSummary = "0 m / 1 m",
                eta = null,
                progressPercent = 0,
            ),
        )
        assertNull(
            XiaomiIslandPayload.build(
                title = "MockX",
                status = "",
                distanceSummary = "0 m / 1 m",
                eta = null,
                progressPercent = 0,
            ),
        )
    }

    @Test
    fun `payload requests its island picture bundle`() {
        assertTrue(build().pictureNeeded)
    }
}
