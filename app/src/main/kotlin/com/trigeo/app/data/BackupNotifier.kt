package com.trigeo.app.data

import android.app.backup.BackupManager
import android.content.Context

class BackupNotifier(context: Context) {
    private val manager = BackupManager(context.applicationContext)

    fun markStale() {
        manager.dataChanged()
    }
}
