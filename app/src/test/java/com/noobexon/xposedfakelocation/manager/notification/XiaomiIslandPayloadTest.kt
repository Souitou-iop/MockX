package com.noobexon.xposedfakelocation.manager.notification

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiIslandPayloadTest {

    private fun build(eta: String? = null, progressPercent: Int = 40) =
        XiaomiIslandPayload.build(
            title = "MockX is simulating a walk",
            status = "Simulating walk",
            distanceSummary = "420 m / 1.00 km",
            eta = eta,
            progressPercent = progressPercent,
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
        val low = JsonParser.parseString(build(progressPercent = -3).json).asJsonObject
            .getAsJsonObject("param_v2").getAsJsonObject("progressInfo")
        assertEquals(0, low.get("progress").asInt)
        val high = JsonParser.parseString(build(progressPercent = 150).json).asJsonObject
            .getAsJsonObject("param_v2").getAsJsonObject("progressInfo")
        assertEquals(100, high.get("progress").asInt)
    }

    @Test
    fun `baseInfo carries title plus status and distance content`() {
        val baseInfo = JsonParser.parseString(build(eta = "approx. 5 min").json).asJsonObject
            .getAsJsonObject("param_v2").getAsJsonObject("baseInfo")
        assertEquals(2, baseInfo.get("type").asInt)
        assertEquals("MockX is simulating a walk", baseInfo.get("title").asString)
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
        assertEquals("420 m / 1.00 km", big.getAsJsonObject("textInfo").get("title").asString)
        val small = island.getAsJsonObject("smallIslandArea").getAsJsonObject("picInfo")
        assertEquals(XiaomiIslandPayload.KEY_ISLAND_PICTURE, small.get("pic").asString)
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
        )!!
        val parsed = JsonParser.parseString(payload.json).asJsonObject
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
