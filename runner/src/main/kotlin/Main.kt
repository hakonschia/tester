package com.hakonschia.tester

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.AndroidDebugBridgeClientFactory
import com.malinskiy.adam.interactor.StartAdbInteractor
import com.malinskiy.adam.request.device.FetchDeviceFeaturesRequest
import com.malinskiy.adam.request.pkg.StreamingPackageInstallRequest
import com.malinskiy.adam.request.pkg.UninstallRemotePackageRequest
import com.malinskiy.adam.request.shell.v2.ShellCommandRequest
import com.malinskiy.adam.request.testrunner.InstrumentOptions
import com.malinskiy.adam.request.testrunner.TestRunnerRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) = Main().main(args)

@Serializable
data class SocketMessage<T>(
    val type: String,
    val data: T,
)

@Serializable
data class Device(
    val serial: String,
    val manufacturer: String,
    val model: String,
)

@Serializable
data class Type(
    val type: String,
)

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
                    webSocket.send(Json.encodeToString(SocketMessage(type = "request-device", data = "")))
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    println("onFailure ${t.stackTraceToString()}")
                }
            }
        )

    override fun run(): Unit = runBlocking {
        StartAdbInteractor().execute()
        val adb = AndroidDebugBridgeClientFactory().build()

        println("Waiting for device")
        val serial = deviceFlow.filterNotNull().first().serial
        println("Retrieved $serial!")

        try {
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
                    webSocket.send(Json.encodeToString(SocketMessage(type = "msg-from-device", data = testEvent.toString())))
                }
            }
        } finally {
            webSocket.send(json.encodeToString(SocketMessage(type = "free-device", data = "")))
            exitProcess(0)
        }
    }
}

private suspend fun AndroidDebugBridgeClient.fetchInstrumentationPackages(serial: String): List<Instrumentation> {
    return execute(request = ShellCommandRequest("pm list instrumentation"), serial = serial)
        .output
        .split("\n")
        .filter { it.isNotEmpty() }
        // Each line will be in the form:
        // "instrumentation:com.hakonschia.tester.android.test/androidx.test.runner.AndroidJUnitRunner (target=com.hakonschia.tester.android)"
        .map { line ->
            val packageName = line.substringAfter("instrumentation:").substringBefore("/")
            val instrumentationRunner = line.substringAfter("/").substringBefore(" ")

            Instrumentation(
                packageName = packageName,
                runnerName = instrumentationRunner,
            )
        }
}

data class Instrumentation(
    val packageName: String,
    val runnerName: String,
)