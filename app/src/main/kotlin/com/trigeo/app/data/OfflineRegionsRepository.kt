package com.trigeo.app.data

import android.content.Context
import com.trigeo.app.map.MapTileStyle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Serializable
private data class RegionMetadata(
    @SerialName("n") val name: String,
    @SerialName("s") val styleName: String,
)

data class OfflineRegionInfo(
    val id: Long,
    val name: String,
    val styleName: String,
    val minZoom: Double,
    val maxZoom: Double,
    val bounds: LatLngBounds,
    val downloadedTiles: Long,
    val requiredTiles: Long,
    val downloadedBytes: Long,
    val isComplete: Boolean,
)

class OfflineRegionsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val manager: OfflineManager by lazy {
        MapLibre.getInstance(appContext)
        OfflineManager.getInstance(appContext)
    }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun list(): List<OfflineRegionInfo> = suspendCoroutine { cont ->
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(regions: Array<OfflineRegion>?) {
                val arr = regions
                if (arr == null || arr.isEmpty()) {
                    cont.resume(emptyList())
                    return
                }
                val results = mutableListOf<OfflineRegionInfo>()
                var remaining = arr.size
                arr.forEach { region ->
                    region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) {
                            status?.let { results.add(region.toInfo(it)) }
                            if (--remaining == 0) cont.resume(results.sortedBy { it.id })
                        }

                        override fun onError(error: String?) {
                            if (--remaining == 0) cont.resume(results.sortedBy { it.id })
                        }
                    })
                }
            }

            override fun onError(error: String) {
                cont.resume(emptyList())
            }
        })
    }

    fun observeAll(intervalMs: Long = 1000L): Flow<List<OfflineRegionInfo>> = flow {
        while (true) {
            emit(list())
            delay(intervalMs)
        }
    }

    fun downloadRegion(
        name: String,
        tileStyle: MapTileStyle,
        bounds: LatLngBounds,
        minZoom: Double,
        maxZoom: Double,
    ): Flow<RegionProgress> = callbackFlow {
        val definition = OfflineTilePyramidRegionDefinition(
            tileStyle.styleUri,
            bounds,
            minZoom,
            maxZoom,
            appContext.resources.displayMetrics.density,
        )
        val metadata = json.encodeToString(
            RegionMetadata.serializer(),
            RegionMetadata(name = name, styleName = tileStyle.name),
        ).toByteArray(Charsets.UTF_8)

        manager.createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(region: OfflineRegion) {
                    region.setDeliverInactiveMessages(true)
                    region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            trySend(
                                RegionProgress.InFlight(
                                    completedTiles = status.completedTileCount,
                                    requiredTiles = status.requiredResourceCount,
                                    bytes = status.completedTileSize,
                                ),
                            )
                            if (status.isComplete) {
                                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                trySend(
                                    RegionProgress.Complete(
                                        tiles = status.completedTileCount,
                                        bytes = status.completedTileSize,
                                    ),
                                )
                                close()
                            }
                        }

                        override fun onError(error: OfflineRegionError) {
                            trySend(RegionProgress.Failed(error.reason + ": " + error.message))
                            close()
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            trySend(RegionProgress.Failed("Tile count limit exceeded ($limit)"))
                            close()
                        }
                    })
                    region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    trySend(RegionProgress.Started(region.id))
                }

                override fun onError(error: String) {
                    trySend(RegionProgress.Failed(error))
                    close()
                }
            },
        )
        awaitClose { /* observer naturally tears down with the region */ }
    }

    suspend fun delete(regionId: Long) = suspendCoroutine<Unit> { cont ->
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(regions: Array<OfflineRegion>?) {
                val region = regions?.firstOrNull { it.id == regionId }
                if (region == null) {
                    cont.resume(Unit)
                    return
                }
                region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() { cont.resume(Unit) }
                    override fun onError(error: String) {
                        cont.resumeWithException(IllegalStateException(error))
                    }
                })
            }

            override fun onError(error: String) {
                cont.resumeWithException(IllegalStateException(error))
            }
        })
    }

    private fun OfflineRegion.toInfo(status: OfflineRegionStatus): OfflineRegionInfo {
        val meta = runCatching {
            json.decodeFromString(
                RegionMetadata.serializer(),
                String(metadata, Charsets.UTF_8),
            )
        }.getOrNull()
        val def = definition as OfflineTilePyramidRegionDefinition
        return OfflineRegionInfo(
            id = id,
            name = meta?.name ?: "Region $id",
            styleName = meta?.styleName ?: "OSM",
            minZoom = def.minZoom,
            maxZoom = def.maxZoom,
            bounds = def.bounds!!,
            downloadedTiles = status.completedTileCount,
            requiredTiles = status.requiredResourceCount,
            downloadedBytes = status.completedTileSize,
            isComplete = status.isComplete,
        )
    }
}

sealed class RegionProgress {
    data class Started(val regionId: Long) : RegionProgress()
    data class InFlight(
        val completedTiles: Long,
        val requiredTiles: Long,
        val bytes: Long,
    ) : RegionProgress()
    data class Complete(val tiles: Long, val bytes: Long) : RegionProgress()
    data class Failed(val reason: String) : RegionProgress()
}
