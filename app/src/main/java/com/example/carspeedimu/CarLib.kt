package com.example.carspeedimu

import android.content.Context
import android.os.Build
import dalvik.system.PathClassLoader
import java.io.File

/**
 * Finds the Android Automotive car library (android.car.*) at runtime.
 *
 * Normally it is on the class path already (boot class path, or `<uses-library android:name="android.car">`).
 * If a head unit doesn't expose it to third-party apps, we fall back to loading
 * the system's jar directly from the framework directories.
 */
object CarLib {

    class Result(val loader: ClassLoader?, val source: String, val log: String)

    private const val CAR_CLASS = "android.car.Car"

    private val SEARCH_DIRS = listOf("/system/framework", "/system_ext/framework", "/product/framework")

    fun find(context: Context): Result {
        val log = StringBuilder()
        val appLoader = context.classLoader

        // 1. Library already visible (boot class path or uses-library).
        try {
            appLoader.loadClass(CAR_CLASS)
            return Result(appLoader, "system class path", log.toString())
        } catch (e: ClassNotFoundException) {
            log.appendLine("not on app class path")
        }

        log.appendLine("shared libraries: " + sharedLibraries(context))

        // 2. Load the system jar ourselves.
        val candidates = candidateJars()
        if (candidates.isEmpty()) log.appendLine("no car jars found in ${SEARCH_DIRS + "/apex/*/javalib"}")
        val builtin = candidates.firstOrNull { it.name.contains("builtin") }
        for (jar in candidates.filterNot { it.name.contains("builtin") }) {
            val path = listOfNotNull(jar.path, builtin?.path).joinToString(File.pathSeparator)
            try {
                val loader = PathClassLoader(path, appLoader)
                loader.loadClass(CAR_CLASS)
                return Result(loader, jar.path, log.toString())
            } catch (t: Throwable) {
                log.appendLine("${jar.path}: ${t.javaClass.simpleName}: ${t.message?.take(120)}")
            }
        }
        return Result(null, "not found", log.toString())
    }

    private fun candidateJars(): List<File> {
        val dirs = SEARCH_DIRS.map(::File).toMutableList()
        File("/apex").listFiles()?.filter { it.name.contains("car") }?.forEach { dirs += File(it, "javalib") }
        return dirs.flatMap { dir ->
            dir.listFiles { f -> f.name.endsWith(".jar") && f.name.startsWith("android.car") }?.toList().orEmpty()
        }.sortedBy { if (it.name == "android.car.jar") 0 else 1 }
    }

    private fun sharedLibraries(context: Context): String {
        if (Build.VERSION.SDK_INT < 26) return "?"
        return try {
            context.packageManager.getSharedLibraries(0)
                .map { it.name }
                .filter { it.contains("car", ignoreCase = true) }
                .ifEmpty { listOf("(none with 'car')") }
                .joinToString()
        } catch (t: Throwable) {
            "error: ${t.message}"
        }
    }
}
