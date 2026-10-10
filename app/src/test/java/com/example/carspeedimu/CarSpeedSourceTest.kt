package com.example.carspeedimu

import android.car.Car
import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs CarSpeedSource's reflection against a fake car-lib with Tinnove's renamed API. */
class CarSpeedSourceTest {

    @Test
    fun tinnoveFlavour_subscribesAndReceivesSpeed() {
        val src = CarSpeedSource(object : ContextWrapper(null) {}, javaClass.classLoader!!)
        assertEquals("CarPropertyEventListener.registerListener", src.apiFlavour)

        src.start()
        val cpm = Car.last.propertyManager
        assertEquals("connected", src.status)

        val subscribed = cpm.listeners.values.single()
        assertEquals(mapOf(0x11600207 to 100f, 0x11600208 to 100f), subscribed)
        assertEquals("VHAL sample rate 1.0–10.0 Hz", src.speed.info)
        assertTrue(src.espAccel.info.startsWith("not accessible"))

        cpm.fire(0x11600207, 25f) // 25 m/s
        assertEquals("90.0 km/h", src.speed.value)
        assertEquals(1L, src.speed.rate.count)

        src.stop()
        assertTrue(cpm.listeners.isEmpty())
        assertTrue(Car.last.disconnected)
    }
}
