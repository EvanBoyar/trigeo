package com.trigeo.app

import android.app.Application
import com.trigeo.app.data.OutingsRepository
import com.trigeo.app.data.ReadingsRepository
import com.trigeo.app.data.TrigeoDatabase
import com.trigeo.app.sensors.CompassService
import com.trigeo.app.sensors.LocationService

class TrigeoApp : Application() {
    val database: TrigeoDatabase by lazy { TrigeoDatabase.get(this) }
    val outingsRepository: OutingsRepository by lazy { OutingsRepository(database.outingDao()) }
    val readingsRepository: ReadingsRepository by lazy { ReadingsRepository(database.readingDao()) }
    val locationService: LocationService by lazy { LocationService(this) }
    val compassService: CompassService by lazy { CompassService(this) }
}
