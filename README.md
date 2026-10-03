# Tester

Tester is a project for running Android UI tests. It is split in three modules

## Techincal overview

- `backend` - This module orchestrates connected devices
- `runner` - This module runs the tests
- `frontend` - A frontend that shows currently connected devices and their status (which tests a device is currently running and previous runs)
- `android` - A bonus module with some UI tests

`backend` and `runner` are written in Kotlin and uses [Adam](https://github.com/Malinskiy/adam) for ADB interaction. `frontend` is written with Next.js 


The project uses websockets for communicating across the three modules. `backend` listens to which devices are connected, and when `runner` wants to run a test it sends a message through the websocket to `backend` asking for a device. `backend` queues up all requests for devices in a first-come-first-serve manner and hands out a device when it becomes available

`frontend` listens to all connected `devices` and has:
- An overview of all devices
- Details about a specific device


### Backend
The backend is written with Kotlin and SpringBoot, run `main` in `BackendApplication.kt`

### Runner
The runner is written in Kotlin and needs two arguments to run:
- `--app-apk` - The location on your machine to the APK of the app you want to run tests with
- `--test-apk` - THe location on your machine to the APK of the test you want to run

In an Android project you run `./gradlew assemble assembleAndroidTest` to generate these APKs. `--app-apk` is located in `<module>/build/outputs/apk/debug/app-debug.apk`, `--test-apk` is located in `<module>/build/outputs/apk/androidTest/app-debug-androidTest.apk
