package com.trigeo.app.sensors

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Collects [source] into [latest], re-subscribing fresh on every [start]. */
class RestartableCollector<T>(
    private val scope: CoroutineScope,
    private val source: () -> Flow<T>?,
) {
    private val _latest = MutableStateFlow<T?>(null)
    val latest: StateFlow<T?> = _latest.asStateFlow()

    private var job: Job? = null

    fun start() {
        job?.cancel()
        val flow = source()
        job = if (flow == null) null else scope.launch { flow.collect { _latest.value = it } }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
