package com.trigeo.app

import android.app.Application
import com.trigeo.app.data.OfflineRegionsRepository
import com.trigeo.app.data.OutingsRepository
import com.trigeo.app.data.ReadingsRepository
import com.trigeo.app.data.SettingsRepository
import com.trigeo.app.data.TrigeoDatabase
import com.trigeo.app.sensors.CompassService
import com.trigeo.app.sensors.LocationService

class TrigeoApp : Application() {
    val database: TrigeoDatabase by lazy { TrigeoDatabase.get(this) }
    val outingsRepository: OutingsRepository by lazy { OutingsRepository(database.outingDao()) }
    val readingsRepository: ReadingsRepository by lazy { ReadingsRepository(database.readingDao()) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val offlineRegionsRepository: OfflineRegionsRepository by lazy { OfflineRegionsRepository(this) }
    val locationService: LocationService by lazy { LocationService(this) }
    val compassService: CompassService by lazy { CompassService(this) }
}
