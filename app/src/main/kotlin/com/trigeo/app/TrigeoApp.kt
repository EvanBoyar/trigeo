package com.trigeo.app

import android.app.Application
import com.trigeo.app.data.BackupNotifier
import com.trigeo.app.data.OfflineRegionsRepository
import com.trigeo.app.data.OutingsRepository
import com.trigeo.app.data.ReadingsRepository
import com.trigeo.app.data.SettingsRepository
import com.trigeo.app.data.TrigeoDatabase
import com.trigeo.app.map.LocalStyleServer
import com.trigeo.app.sensors.CompassService
import com.trigeo.app.sensors.LocationService
import java.io.File

class TrigeoApp : Application() {

    val styleServer: LocalStyleServer by lazy {
        LocalStyleServer(File(filesDir, "styles"))
    }

    override fun onCreate() {
        super.onCreate()
        copyStyleAssets()
        styleServer.start()
    }

    private fun copyStyleAssets() {
        val dir = File(filesDir, "styles")
        if (!dir.exists()) dir.mkdirs()
        listOf("osm.json", "opentopo.json").forEach { filename ->
            val target = File(dir, filename)
            // Always refresh so style updates ship correctly with new app versions.
            assets.open("styles/$filename").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    val database: TrigeoDatabase by lazy { TrigeoDatabase.get(this) }
    val backupNotifier: BackupNotifier by lazy { BackupNotifier(this) }
    val outingsRepository: OutingsRepository by lazy {
        OutingsRepository(database.outingDao(), backupNotifier)
    }
    val readingsRepository: ReadingsRepository by lazy {
        ReadingsRepository(database.readingDao(), backupNotifier)
    }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val offlineRegionsRepository: OfflineRegionsRepository by lazy { OfflineRegionsRepository(this) }
    val locationService: LocationService by lazy { LocationService(this) }
    val compassService: CompassService by lazy { CompassService(this) }
}
