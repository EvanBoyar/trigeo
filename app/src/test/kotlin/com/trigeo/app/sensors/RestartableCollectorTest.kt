package com.trigeo.app.sensors

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RestartableCollectorTest {

    @Test
    fun resumesUpdatesAfterRestartWhenSourceWentStale() = runTest {
        var current = MutableSharedFlow<Int>(extraBufferCapacity = 8)
        var subscribeCount = 0
        val collector = RestartableCollector<Int>(backgroundScope) {
            subscribeCount++
            current
        }

        collector.start(); runCurrent()
        current.tryEmit(1); runCurrent()
        assertEquals(1, collector.latest.value)

        // Lock then unlock: the foreground subscription goes stale, and the OS
        // would only deliver to a freshly requested subscription.
        val stale = current
        current = MutableSharedFlow(extraBufferCapacity = 8)

        collector.start(); runCurrent() // returning to foreground must re-subscribe
        stale.tryEmit(99)               // the stale source must no longer feed us
        current.tryEmit(2); runCurrent()

        assertEquals(2, collector.latest.value)
        assertEquals(2, subscribeCount)
    }

    @Test
    fun nullSourceLeavesLatestNull() = runTest {
        val collector = RestartableCollector<Int>(backgroundScope) { null }

        collector.start(); runCurrent()

        assertNull(collector.latest.value)
    }
}
