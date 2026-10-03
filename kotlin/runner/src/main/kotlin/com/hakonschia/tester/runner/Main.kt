package com.hakonschia.tester.runner

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.hakonschia.tester.common.Device
import com.hakonschia.tester.common.SerializableTestEvent.Companion.toSerializable
import com.hakonschia.tester.common.SocketMessage
import com.hakonschia.tester.common.Type
import com.malinskiy.adam.AndroidDebugBridgeClientFactory
import com.malinskiy.adam.interactor.StartAdbInteractor
import com.malinskiy.adam.request.device.FetchDeviceFeaturesRequest
import com.malinskiy.adam.request.pkg.StreamingPackageInstallRequest
import com.malinskiy.adam.request.pkg.UninstallRemotePackageRequest
import com.malinskiy.adam.request.testrunner.InstrumentOptions
import com.malinskiy.adam.request.testrunner.TestRunnerRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.*
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.minutes

fun main(args: Array<String>) = Main().main(args)

class Main : CliktCommand() {
    private val appApk by option("--app-apk").required()
    private val testApk by option("--test-apk").required()

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val deviceFlow = MutableStateFlow<Device?>(null)
    private val webSocket = OkHttpClient.Builder()
        .build()
        .newWebSocket(
            request = Request.Builder()
                .url("ws://localhost:8080/socket/devices")
                .build(),
            listener = object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    println("onMessage: $text")

                    val messageType = json.decodeFromString<Type>(text).type

                    when (messageType) {
                        "device-given" -> {
                            deviceFlow.value = json.decodeFromString<SocketMessage<Device>>(text).data
                        }
                    }
                }

                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(SocketMessage(type = "request-device", data = ""))
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    println("onFailure ${t.stackTraceToString()}")
                }
            }
        )

    override fun run(): Unit = runBlocking {
        StartAdbInteractor().execute()
        val adb = AndroidDebugBridgeClientFactory().build()

        try {
            println("Waiting for device")
            val serial = withTimeout(5.minutes) {
                deviceFlow.filterNotNull().first().serial
            }
            println("Retrieved $serial!")

            val supportedFeatures = adb.execute(FetchDeviceFeaturesRequest(serial))

            adb.fetchInstrumentationPackages(serial).forEach { instrumentation ->
                println("Uninstalling $instrumentation")

                adb.execute(
                    request = UninstallRemotePackageRequest(
                        packageName = instrumentation.packageName,
                        keepData = false
                    ),
                    serial = serial
                )
            }

            adb.execute(
                StreamingPackageInstallRequest(
                    pkg = File(appApk),
                    supportedFeatures = supportedFeatures,
                    reinstall = false,
                ),
                serial = serial
            )
            println("appApk installed")

            adb.execute(
                StreamingPackageInstallRequest(
                    pkg = File(testApk),
                    supportedFeatures = supportedFeatures,
                    reinstall = false,
                ),
                serial = serial
            )
            println("testApk installed")

            val instrumentationPackage = adb.fetchInstrumentationPackages(serial).first()
            println("Running $instrumentationPackage")

            adb.execute(
                request = TestRunnerRequest(
                    testPackage = instrumentationPackage.packageName,
                    runnerClass = instrumentationPackage.runnerName,
                    instrumentOptions = InstrumentOptions(),
                    supportedFeatures = supportedFeatures,
                    coroutineScope = this,
                ),
                serial = serial
            ).consumeAsFlow().collect { testEvents ->
                testEvents.forEach { testEvent ->
                    println(testEvent)
                    webSocket.send(SocketMessage(type = "msg-from-device", data = testEvent.toSerializable()))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // Not really necessary to send this message, the backend will free the device automatically when the WebSocket is closed
            // But nice to be nice I guess :)
            webSocket.send(SocketMessage(type = "free-device", data = ""))
            exitProcess(0)
        }
    }
}
