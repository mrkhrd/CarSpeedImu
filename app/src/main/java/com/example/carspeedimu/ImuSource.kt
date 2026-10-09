package com.example.carspeedimu

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread

/** Head-unit IMU (accelerometer/gyroscope) via the standard Android SensorManager. */
class ImuSource(context: Context) : SensorEventListener {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val byType = linkedMapOf<Int, Channel>()
    private var thread: HandlerThread? = null

    val channels: List<Channel>

    init {
        for ((type, label, unit) in WANTED) {
            val sensor = sm.getDefaultSensor(type) ?: continue
            byType[type] = Channel("$label  [$unit]").apply { info = describe(sensor) }
        }
        if (byType.isEmpty()) {
            byType[-1] = Channel("IMU").apply { info = "no accelerometer/gyroscope on this device" }
        }
        channels = byType.values.toList()
    }

    /** All sensors the device reports, for the info section. */
    fun allSensors(): String = sm.getSensorList(Sensor.TYPE_ALL)
        .joinToString("\n") { "• ${it.stringType.removePrefix("android.sensor.")}: ${describe(it)}" }

    fun start() {
        if (thread != null) return
        val t = HandlerThread("imu").also { it.start() }
        thread = t
        val handler = Handler(t.looper)
        for (type in byType.keys) {
            sm.getDefaultSensor(type)?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, handler)
            }
        }
    }

    fun stop() {
        sm.unregisterListener(this)
        thread?.quitSafely()
        thread = null
    }

    override fun onSensorChanged(e: SensorEvent) {
        val ch = byType[e.sensor.type] ?: return
        val v = e.values
        val text = when (e.sensor.type) {
            TYPE_ACCEL_LIMITED, TYPE_GYRO_LIMITED ->
                // values[3..5] say which axes are supported (1 = yes)
                "x %+7.3f  y %+7.3f  z %+7.3f".format(
                    if (v[3] != 0f) v[0] else Float.NaN,
                    if (v[4] != 0f) v[1] else Float.NaN,
                    if (v[5] != 0f) v[2] else Float.NaN,
                )
            else -> "x %+7.3f  y %+7.3f  z %+7.3f".format(v[0], v[1], v[2])
        }
        ch.update(text)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    private fun describe(s: Sensor): String {
        val max = if (s.minDelay > 0) "max %.0f Hz".format(1e6 / s.minDelay) else "on-change"
        return "${s.name} (${s.vendor}), $max"
    }

    private data class Wanted(val type: Int, val label: String, val unit: String)

    companion object {
        // Sensor.TYPE_*_LIMITED_AXES exist from API 33 (made for cars); use raw ids for minSdk 29.
        private const val TYPE_ACCEL_LIMITED = 38
        private const val TYPE_GYRO_LIMITED = 39

        private val WANTED = buildList {
            add(Wanted(Sensor.TYPE_ACCELEROMETER, "Accelerometer", "m/s²"))
            add(Wanted(Sensor.TYPE_GYROSCOPE, "Gyroscope", "rad/s"))
            add(Wanted(Sensor.TYPE_LINEAR_ACCELERATION, "Linear acceleration", "m/s²"))
            if (Build.VERSION.SDK_INT >= 33) {
                add(Wanted(TYPE_ACCEL_LIMITED, "Accelerometer (car, limited axes)", "m/s²"))
                add(Wanted(TYPE_GYRO_LIMITED, "Gyroscope (car, limited axes)", "rad/s"))
            }
        }
    }
}
