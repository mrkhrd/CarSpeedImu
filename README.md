# Car Speed & IMU

Single-activity Android Automotive (AAOS 14) app that shows live vehicle speed and IMU data, each with its measured refresh rate.

Built for a Changan / Tinnove head unit, but uses standard AAOS APIs, so it should work on any Android Automotive car.

## What it shows

**Vehicle (`CarPropertyManager`)**
- `PERF_VEHICLE_SPEED` — speed in km/h
- `PERF_VEHICLE_SPEED_DISPLAY` — speed as shown on the instrument cluster
- Vendor `ESP_LAT_LONG_ACCEL` (`0x21703442`) — needs `CAR_VENDOR_EXTENSION`, so only readable by system apps; shown as raw hex

Each card shows the VHAL's declared min/max sample rate. Speed is requested at `SENSOR_RATE_FASTEST`; CarService clamps it to the VHAL max (10 Hz in the stock AAOS config).

**IMU (`SensorManager`)** — the head unit's own sensors
- Accelerometer, gyroscope, linear acceleration
- Automotive limited-axes accelerometer/gyroscope (API 33+), if present
- List of all sensors on the device

Every card shows the measured rate (Hz over a 2 s window), the last interval, and the event count.

On a non-automotive device (phone/emulator) the vehicle section is hidden and only the IMU is shown.

## Permissions

- `android.car.permission.CAR_SPEED` — runtime ("dangerous") permission, requested on first launch
- IMU sensors need no permission

## Build

Requires the Android SDK with platform 34 (provides `optional/android.car.jar`).

```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Notes

- Some VHALs only send speed events when the value changes, so the rate can drop to 0 while parked.
- The activity is marked `distractionOptimized`, but on stock AAOS this is only honoured for apps from allowed install sources; a sideloaded build may be blocked while driving.
- Some head units restrict sideloading (patched package manager, password-protected adb shell).
