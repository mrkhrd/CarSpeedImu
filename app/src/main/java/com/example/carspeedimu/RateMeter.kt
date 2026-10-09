package com.example.carspeedimu

import android.os.SystemClock

/** Measures how often events arrive, over a sliding time window. Thread-safe. */
class RateMeter(private val windowNs: Long = 2_000_000_000L) {
    private val stamps = ArrayDeque<Long>()
    private var lastIntervalNs = 0L
    var count = 0L
        private set

    @Synchronized
    fun tick(nowNs: Long = SystemClock.elapsedRealtimeNanos()) {
        stamps.lastOrNull()?.let { lastIntervalNs = nowNs - it }
        stamps.addLast(nowNs)
        count++
        trim(nowNs)
    }

    /** Events per second over the window; 0 if nothing arrived recently. */
    @Synchronized
    fun hz(nowNs: Long = SystemClock.elapsedRealtimeNanos()): Double {
        trim(nowNs)
        if (stamps.size < 2) return 0.0
        return (stamps.size - 1) * 1e9 / (stamps.last() - stamps.first())
    }

    @Synchronized
    fun lastIntervalMs(): Double = lastIntervalNs / 1e6

    fun summary(): String =
        if (count == 0L) "no events yet"
        else "%.1f Hz  (last Δ %.0f ms, total %d)".format(hz(), lastIntervalMs(), count)

    private fun trim(nowNs: Long) {
        while (stamps.isNotEmpty() && nowNs - stamps.first() > windowNs) stamps.removeFirst()
    }
}
