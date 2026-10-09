package com.example.carspeedimu

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log

/**
 * Vehicle data from Android Automotive's CarPropertyManager.
 * Only touch this class on devices with FEATURE_AUTOMOTIVE (android.car.* is missing elsewhere).
 */
class CarSpeedSource(private val context: Context) {

    val speed = Channel("Vehicle speed  (PERF_VEHICLE_SPEED)")
    val displaySpeed = Channel("Dashboard speed  (PERF_VEHICLE_SPEED_DISPLAY)")
    val espAccel = Channel("ESP lat/long accel  (vendor 0x21703442)")
    val channels = listOf(speed, displaySpeed, espAccel)

    private var car: Car? = null
    private var cpm: CarPropertyManager? = null

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(v: CarPropertyValue<*>) {
            when (v.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> speed.update(formatSpeed(v))
                VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY -> displaySpeed.update(formatSpeed(v))
                VENDOR_ESP_LAT_LONG_ACCEL -> espAccel.update(formatRaw(v.value))
            }
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.w(TAG, "error event for property 0x%x".format(propId))
        }
    }

    fun start() {
        if (car != null) return
        car = Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER) { c, ready ->
            if (ready) onCarReady(c) else cpm = null
        }
    }

    fun stop() {
        cpm?.unregisterCallback(callback)
        cpm = null
        car?.disconnect()
        car = null
    }

    private fun onCarReady(c: Car) {
        val manager = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        cpm = manager
        subscribe(manager, VehiclePropertyIds.PERF_VEHICLE_SPEED, speed, "")
        subscribe(manager, VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY, displaySpeed, "")
        subscribe(
            manager, VENDOR_ESP_LAT_LONG_ACCEL, espAccel,
            "not accessible: needs CAR_VENDOR_EXTENSION (system apps only)"
        )
    }

    private fun subscribe(manager: CarPropertyManager, propId: Int, ch: Channel, unavailableMsg: String) {
        val config = try {
            manager.getCarPropertyConfig(propId)
        } catch (e: Exception) {
            null
        }
        if (config == null) {
            ch.info = unavailableMsg.ifEmpty { "not exposed by this car (or permission missing)" }
            return
        }
        ch.info = "VHAL sample rate %.1f–%.1f Hz".format(config.minSampleRate, config.maxSampleRate)
        try {
            // Requests the fastest rate; CarService clamps it to the VHAL's maxSampleRate.
            manager.registerCallback(callback, propId, CarPropertyManager.SENSOR_RATE_FASTEST)
        } catch (e: Exception) {
            ch.info = "subscribe failed: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    private fun formatSpeed(v: CarPropertyValue<*>): String {
        val mps = (v.value as? Float) ?: return "n/a"
        return "%.1f km/h".format(mps * 3.6f)
    }

    private fun formatRaw(value: Any?): String = when (value) {
        is ByteArray -> value.joinToString(" ") { "%02X".format(it) }
        is Array<*> -> value.joinToString(", ")
        else -> value.toString()
    }

    companion object {
        private const val TAG = "CarSpeedSource"

        /** Changan/Tinnove vendor property (CarDvrManager.ID_ESP_LAT_LONG_ACCEL), BYTES type. */
        const val VENDOR_ESP_LAT_LONG_ACCEL = 0x21703442
    }
}
