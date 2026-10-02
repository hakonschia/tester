package com.hakonschia.tester.runner

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.shell.v2.ShellCommandRequest

suspend fun AndroidDebugBridgeClient.fetchInstrumentationPackages(serial: String): List<Instrumentation> {
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
