package com.example.carspeedimu

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Vehicle data from Android Automotive's CarPropertyManager.
 *
 * The car library is called through reflection because it may be loaded at runtime from the
 * system (see [CarLib]); nothing here links against android.car.* at compile time.
 */
class CarSpeedSource(private val context: Context, private val loader: ClassLoader) {

    val speed = Channel("Vehicle speed  (PERF_VEHICLE_SPEED)")
    val displaySpeed = Channel("Dashboard speed  (PERF_VEHICLE_SPEED_DISPLAY)")
    val espAccel = Channel("ESP lat/long accel  (vendor 0x21703442)")
    val channels = listOf(speed, displaySpeed, espAccel)

    @Volatile
    var status: String = "connecting to car service…"
        private set

    private val carClass = loader.loadClass("android.car.Car")
    private val cpmClass = loader.loadClass("android.car.hardware.property.CarPropertyManager")
    // AOSP: CarPropertyEventCallback + registerCallback/unregisterCallback.
    // Tinnove's car-lib renames them to CarPropertyEventListener + registerListener/unregisterListener.
    private val callbackClass = loadFirst(
        "android.car.hardware.property.CarPropertyManager\$CarPropertyEventCallback",
        "android.car.hardware.property.CarPropertyManager\$CarPropertyEventListener",
    )
    private val registerMethod = findMethod(
        cpmClass, listOf("registerCallback", "registerListener"),
        callbackClass, Int::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!
    )
    private val unregisterMethod =
        findMethod(cpmClass, listOf("unregisterCallback", "unregisterListener"), callbackClass)
    private val lifecycleClass = loader.loadClass("android.car.Car\$CarServiceLifecycleListener")

    /** Which car-lib API flavour was found, for the status line. */
    val apiFlavour = "${callbackClass.simpleName}.${registerMethod.name}"

    private var car: Any? = null
    private var cpm: Any? = null

    private val callback: Any = proxy(callbackClass) { method, args ->
        when (method.name) {
            "onChangeEvent" -> onChange(args!![0])
            "onErrorEvent" -> Log.w(TAG, "error event for property 0x%x".format(args!![0] as Int))
        }
        null
    }

    private val lifecycle: Any = proxy(lifecycleClass) { method, args ->
        if (method.name == "onLifecycleChanged") {
            val ready = args!![1] as Boolean
            if (ready) onCarReady(args[0]!!) else {
                cpm = null
                status = "car service disconnected"
            }
        }
        null
    }

    fun start() {
        if (car != null) return
        car = carClass.getMethod(
            "createCar", Context::class.java, Handler::class.java, Long::class.javaPrimitiveType, lifecycleClass
        ).invoke(null, context, Handler(Looper.getMainLooper()), CAR_WAIT_TIMEOUT_WAIT_FOREVER, lifecycle)
    }

    fun stop() {
        cpm?.let { m ->
            runCatching { unregisterMethod.invoke(m, callback) }
        }
        cpm = null
        car?.let { c -> runCatching { carClass.getMethod("disconnect").invoke(c) } }
        car = null
    }

    private fun onCarReady(c: Any) {
        try {
            val manager = carClass.getMethod("getCarManager", String::class.java).invoke(c, PROPERTY_SERVICE)
                ?: error("getCarManager(\"$PROPERTY_SERVICE\") returned null")
            cpm = manager
            status = "connected"
            subscribe(manager, PERF_VEHICLE_SPEED, speed, "")
            subscribe(manager, PERF_VEHICLE_SPEED_DISPLAY, displaySpeed, "")
            subscribe(
                manager, VENDOR_ESP_LAT_LONG_ACCEL, espAccel,
                "not accessible: needs CAR_VENDOR_EXTENSION (system apps only)"
            )
        } catch (t: Throwable) {
            status = "car service error: ${describe(t)}"
            Log.e(TAG, "onCarReady failed", t)
        }
    }

    private fun subscribe(manager: Any, propId: Int, ch: Channel, unavailableMsg: String) {
        val config = try {
            cpmClass.getMethod("getCarPropertyConfig", Int::class.javaPrimitiveType).invoke(manager, propId)
        } catch (t: Throwable) {
            null
        }
        if (config == null) {
            ch.info = unavailableMsg.ifEmpty { "not exposed by this car (or permission missing)" }
            return
        }
        val min = config.javaClass.getMethod("getMinSampleRate").invoke(config) as Float
        val max = config.javaClass.getMethod("getMaxSampleRate").invoke(config) as Float
        ch.info = "VHAL sample rate %.1f–%.1f Hz".format(min, max)
        try {
            // Requests the fastest rate; CarService clamps it to the VHAL's maxSampleRate.
            registerMethod.invoke(manager, callback, propId, SENSOR_RATE_FASTEST)
        } catch (t: Throwable) {
            ch.info = "subscribe failed: ${describe(t)}"
        }
    }

    private fun onChange(v: Any?) {
        v ?: return
        val propId = v.javaClass.getMethod("getPropertyId").invoke(v) as Int
        val value = v.javaClass.getMethod("getValue").invoke(v)
        when (propId) {
            PERF_VEHICLE_SPEED -> speed.update(formatSpeed(value))
            PERF_VEHICLE_SPEED_DISPLAY -> displaySpeed.update(formatSpeed(value))
            VENDOR_ESP_LAT_LONG_ACCEL -> espAccel.update(formatRaw(value))
        }
    }

    private fun formatSpeed(value: Any?): String {
        val mps = (value as? Float) ?: return "n/a"
        return "%.1f km/h".format(mps * 3.6f)
    }

    private fun formatRaw(value: Any?): String = when (value) {
        is ByteArray -> value.joinToString(" ") { "%02X".format(it) }
        is Array<*> -> value.joinToString(", ")
        else -> value.toString()
    }

    private fun loadFirst(vararg names: String): Class<*> {
        for (n in names) {
            try {
                return loader.loadClass(n)
            } catch (_: ClassNotFoundException) {
            }
        }
        throw ClassNotFoundException("none of: ${names.joinToString()}")
    }

    private fun findMethod(cls: Class<*>, names: List<String>, vararg params: Class<*>): Method {
        for (n in names) {
            try {
                return cls.getMethod(n, *params)
            } catch (_: NoSuchMethodException) {
            }
        }
        throw NoSuchMethodException("${cls.simpleName}: none of $names(${params.joinToString { it.simpleName }})")
    }

    /** Implements a car-library interface; also answers equals/hashCode so it works as a map key. */
    private fun proxy(iface: Class<*>, body: (Method, Array<Any?>?) -> Any?): Any {
        lateinit var self: Any
        val handler = InvocationHandler { _, method, args ->
            when (method.name) {
                "equals" -> self === args?.get(0)
                "hashCode" -> System.identityHashCode(self)
                "toString" -> "CarSpeedSource\$${iface.simpleName}"
                else -> body(method, args) ?: defaultFor(method.returnType)
            }
        }
        self = Proxy.newProxyInstance(iface.classLoader, arrayOf(iface), handler)
        return self
    }

    private fun defaultFor(type: Class<*>): Any? = when (type) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        else -> null
    }

    companion object {
        private const val TAG = "CarSpeedSource"

        // Values of android.car constants (Car, VehiclePropertyIds, CarPropertyManager).
        private const val PROPERTY_SERVICE = "property"
        private const val CAR_WAIT_TIMEOUT_WAIT_FOREVER = -1L
        private const val SENSOR_RATE_FASTEST = 100f
        const val PERF_VEHICLE_SPEED = 0x11600207
        const val PERF_VEHICLE_SPEED_DISPLAY = 0x11600208

        /** Changan/Tinnove vendor property (CarDvrManager.ID_ESP_LAT_LONG_ACCEL), BYTES type. */
        const val VENDOR_ESP_LAT_LONG_ACCEL = 0x21703442

        fun describe(t: Throwable): String {
            val root = generateSequence(t) { it.cause }.last()
            return "${root.javaClass.simpleName}: ${root.message}"
        }
    }
}
