package com.hakonschia.tester.common

import com.hakonschia.tester.common.SerializableTestIdentifier.Companion.toSerializable
import com.malinskiy.adam.request.testrunner.TestEvent
import com.malinskiy.adam.request.testrunner.TestIdentifier
import kotlinx.serialization.Serializable

// Surely a better way of sending these events back and forth..

@Serializable
data class SerializableTestIdentifier(
    val className: String,
    val testName: String
) {
    companion object {
        fun TestIdentifier.toSerializable() = SerializableTestIdentifier(
            className = className,
            testName = testName
        )
    }
}

@Serializable
sealed interface SerializableTestEvent {
    @Serializable
    data class TestRunStartedEvent(val testCount: Int) : SerializableTestEvent

    @Serializable
    data class TestStarted(val id: SerializableTestIdentifier) : SerializableTestEvent

    @Serializable
    data class TestFailed(val id: SerializableTestIdentifier, val stackTrace: String) : SerializableTestEvent

    @Serializable
    data class TestAssumptionFailed(val id: SerializableTestIdentifier, val stackTrace: String) : SerializableTestEvent

    @Serializable
    data class TestIgnored(val id: SerializableTestIdentifier) : SerializableTestEvent

    @Serializable
    data class TestEnded(val id: SerializableTestIdentifier, val metrics: Map<String, String>) : SerializableTestEvent

    @Serializable
    data class TestRunFailed(val error: String) : SerializableTestEvent

    @Serializable
    data class TestRunFailing(val error: String, val stackTrace: String) : SerializableTestEvent

    @Serializable
    data class TestRunStopped(val elapsedTimeMillis: Long) : SerializableTestEvent

    @Serializable
    data class TestRunEnded(val elapsedTimeMillis: Long, val metrics: Map<String, String>) : SerializableTestEvent

    companion object {
        fun TestEvent.toSerializable(): SerializableTestEvent {
            return when (this) {
                is com.malinskiy.adam.request.testrunner.TestAssumptionFailed -> TestAssumptionFailed(id.toSerializable(), stackTrace)
                is com.malinskiy.adam.request.testrunner.TestEnded -> TestEnded(id.toSerializable(), metrics)
                is com.malinskiy.adam.request.testrunner.TestFailed -> TestFailed(id.toSerializable(), stackTrace)
                is com.malinskiy.adam.request.testrunner.TestIgnored -> TestIgnored(id.toSerializable())
                is com.malinskiy.adam.request.testrunner.TestRunEnded -> TestRunEnded(elapsedTimeMillis, metrics)
                is com.malinskiy.adam.request.testrunner.TestRunFailed -> TestRunFailed(error)
                is com.malinskiy.adam.request.testrunner.TestRunFailing -> TestRunFailing(error, stackTrace)
                is com.malinskiy.adam.request.testrunner.TestRunStartedEvent -> TestRunStartedEvent(testCount)
                is com.malinskiy.adam.request.testrunner.TestRunStopped -> TestRunStopped(elapsedTimeMillis)
                is com.malinskiy.adam.request.testrunner.TestStarted -> TestStarted(id.toSerializable())
            }
        }
    }
}
