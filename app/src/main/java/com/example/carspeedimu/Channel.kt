package com.example.carspeedimu

/** One data stream shown on screen: latest value, static info and measured rate. */
class Channel(val title: String) {
    val rate = RateMeter()

    @Volatile
    var value: String = "—"

    @Volatile
    var info: String = ""

    fun update(newValue: String) {
        value = newValue
        rate.tick()
    }
}
