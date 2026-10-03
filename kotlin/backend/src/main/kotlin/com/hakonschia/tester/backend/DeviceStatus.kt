package com.hakonschia.tester.backend

import com.hakonschia.tester.common.Device
import kotlinx.serialization.Serializable

@Serializable
data class DeviceStatus(
    val device: Device,
    val taken: Boolean,
    val currentTestStatus: CurrentTestStatus,
) {
    @Serializable
    sealed interface CurrentTestStatus {
        @Serializable
        data class NotRunningTests(
            val previousRuns: List<RunningTests>
        ) : CurrentTestStatus

        @Serializable
        data class RunningTests(
            val totalTests: Int,
            val startTimestamp: Long,
            val finishedTests: List<FinishedTest>,
            val currentlyRunningTest: String?,
            val previousRuns: List<RunningTests>
        ) : CurrentTestStatus {

            @Serializable
            sealed interface FinishedTest {
                val name: String
                val timestamp: Long

                @Serializable
                data class Success(
                    override val name: String,
                    override val timestamp: Long,
                ) : FinishedTest

                @Serializable
                data class Failed(
                    override val name: String,
                    val stackTrace: String,
                    override val timestamp: Long,
                ) : FinishedTest
            }
        }
    }
}