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
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) = Main().main(args)

class Main : CliktCommand() {
    private val appApk by option("--app-apk").required()
    private val testApk by option("--test-apk").required()

    override fun run(): Unit = runBlocking {
        println("Running with $appApk and $testApk")
        StartAdbInteractor().execute()

        val adb = AndroidDebugBridgeClientFactory().build()

        val serial = "emulator-5554"
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
            }
        }

        exitProcess(0)
    }
}

private suspend fun AndroidDebugBridgeClient.fetchInstrumentationPackages(serial: String): List<Instrumentation> {
    return execute(
        request = ShellCommandRequest(
            "pm list instrumentation"
        ),
        serial = serial
    )
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