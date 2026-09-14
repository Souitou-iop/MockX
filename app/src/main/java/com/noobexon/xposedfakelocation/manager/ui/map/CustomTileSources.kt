package com.noobexon.xposedfakelocation.manager.ui.map

import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex

/**
 * Tile source factory for domestic and international map providers.
 *
 * Each tile source specifies a unique name so that osmdroid caches their tiles in separate
 * cache directories without conflicts.
 */
object CustomTileSources {
    val AMAP_VECTOR: ITileSource = object : OnlineTileSourceBase(
        "AutoNavi-Vector",
        3,
        18,
        256,
        ".png",
        arrayOf(
            "https://wprd01.is.autonavi.com/appmaptile?",
            "https://wprd02.is.autonavi.com/appmaptile?",
            "https://wprd03.is.autonavi.com/appmaptile?",
            "https://wprd04.is.autonavi.com/appmaptile?"
        )
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "${baseUrl}lang=zh_cn&size=1&scale=1&style=7&x=$x&y=$y&z=$zoom"
        }
    }

    val AMAP_SATELLITE: ITileSource = object : OnlineTileSourceBase(
        "AutoNavi-Satellite",
        3,
        18,
        256,
        ".png",
        arrayOf(
            "https://wprd01.is.autonavi.com/appmaptile?",
            "https://wprd02.is.autonavi.com/appmaptile?",
            "https://wprd03.is.autonavi.com/appmaptile?",
            "https://wprd04.is.autonavi.com/appmaptile?"
        )
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "${baseUrl}lang=zh_cn&size=1&scale=1&style=6&x=$x&y=$y&z=$zoom"
        }
    }

    fun createTianDiTuVector(token: String): ITileSource = object : OnlineTileSourceBase(
        "TianDiTu-Vector",
        1,
        18,
        256,
        ".png",
        arrayOf(
            "https://t0.tianditu.gov.cn/DataServer?",
            "https://t1.tianditu.gov.cn/DataServer?",
            "https://t2.tianditu.gov.cn/DataServer?",
            "https://t3.tianditu.gov.cn/DataServer?",
            "https://t4.tianditu.gov.cn/DataServer?",
            "https://t5.tianditu.gov.cn/DataServer?",
            "https://t6.tianditu.gov.cn/DataServer?",
            "https://t7.tianditu.gov.cn/DataServer?"
        )
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "${baseUrl}T=vec_w&x=$x&y=$y&l=$zoom&tk=$token"
        }
    }

    /**
     * Resolves an [ITileSource] for the specified [option] and optional [tiandituToken].
     */
    fun getTileSource(option: MapSourceOption, tiandituToken: String): ITileSource = when (option) {
        MapSourceOption.AMAP_VECTOR -> AMAP_VECTOR
        MapSourceOption.AMAP_SATELLITE -> AMAP_SATELLITE
        MapSourceOption.TIANDITU_VECTOR -> createTianDiTuVector(tiandituToken)
        MapSourceOption.OPEN_STREET_MAP -> TileSourceFactory.MAPNIK
    }
}
