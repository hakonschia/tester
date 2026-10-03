package com.hakonschia.tester.backend

import com.hakonschia.tester.common.Device
import com.hakonschia.tester.common.SerializableTestEvent
import com.hakonschia.tester.common.SocketMessage
import com.hakonschia.tester.common.Type
import com.malinskiy.adam.AndroidDebugBridgeClientFactory
import com.malinskiy.adam.request.device.AsyncDeviceMonitorRequest
import com.malinskiy.adam.request.device.DeviceState
import com.malinskiy.adam.request.prop.GetPropRequest
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.handler.TextWebSocketHandler

@OptIn(DelicateCoroutinesApi::class)
class DeviceHandler : TextWebSocketHandler() {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val adb = AndroidDebugBridgeClientFactory().build()
    private val sessions = MutableStateFlow(emptySet<WebSocketSession>())

    private val onlineDevices = MutableStateFlow<List<Device>>(emptyList())
    private val takenDevices = MutableStateFlow<Map<WebSocketSession, Device>>(emptyMap())
    private val availableDevicesFlow = combine(onlineDevices, takenDevices) { online, taken ->
        online - taken.values.toSet()
    }
    private val sessionsRequestingDevice = Channel<WebSocketSession>()
    private val serialsSubscribedTo = MutableStateFlow<Map<String, List<WebSocketSession>>>(emptyMap())
    private val sessionsSubscribedToAllDevices = MutableStateFlow<List<WebSocketSession>>(emptyList())
    private val deviceStatus = MutableStateFlow<List<Pair<Device, DeviceStatus.CurrentTestStatus>>>(emptyList())

    init {
        GlobalScope.launch {
            adb.execute(request = AsyncDeviceMonitorRequest(), scope = this).consumeEach { devices ->
                onlineDevices.update {
                    devices.filter { it.state == DeviceState.DEVICE }.map { device ->
                        val features = adb.execute(request = GetPropRequest(), serial = device.serial)

                        Device(
                            serial = device.serial,
                            manufacturer = features.getValue("ro.product.manufacturer"),
                            model = features.getValue("ro.product.model"),
                        )
                    }
                }
            }
        }

        GlobalScope.launch {
            sessionsRequestingDevice.receiveAsFlow().collect { session ->
                println("\tWaiting for device: $session")

                availableDevicesFlow
                    .combine(sessions) { devices, sessions -> devices to sessions }
                    // Continue in the queue of sessions waiting if the current session is removed
                    .takeWhile { (_, sessions) -> sessions.contains(session) }
                    // Wait for the devices to not be empty
                    .filter { (devices) -> devices.isNotEmpty() }
                    // Collect non-empty devices once
                    // Collection stops after a device is taken, and the outer session flow collect proceeds to the next session
                    .take(1)
                    .collect { (devices) ->
                        val device = devices.random()
                        takenDevices.update {
                            it.toMutableMap().apply {
                                this[session] = device
                            }
                        }

                        session.send(SocketMessage("device-given", device))
                    }

                println("\tNot waiting anymore for: $session")
            }
        }

        GlobalScope.launch {
            availableDevicesFlow.collect {
                println("\tAvailable devices: $it")
            }
        }

        GlobalScope.launch {
            combine(onlineDevices, takenDevices, deviceStatus) { online, taken, deviceStatus ->
                online.map { onlineDevice ->
                    DeviceStatus(
                        device = onlineDevice,
                        taken = taken.any { it.value.serial == onlineDevice.serial },
                        currentTestStatus = deviceStatus.find { it.first == onlineDevice }?.second ?: DeviceStatus.CurrentTestStatus.NotRunningTests(emptyList()),
                    )
                }
            }.combine(serialsSubscribedTo) { devices, serials ->
                devices to serials
            }.collectLatest { (devices, serials) ->
                serials.forEach { (serial, sessions) ->
                    devices.forEach { device ->
                        if (device.device.serial == serial) {
                            sessions.filter { it.isOpen }.forEach { session ->
                                session.send(SocketMessage("device-status", device))
                            }
                        }
                    }
                }

                sessionsSubscribedToAllDevices.collect { sessions ->
                    sessions.filter { it.isOpen }.forEach { session ->
                        session.send(SocketMessage("all-devices", devices))
                    }
                }
            }
        }
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        println("Session added: ${session.id}")
        sessions.update { it + session }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        println("Session removed: ${session.id} - $status")
        sessions.update { it - session }
        takenDevices.update { it - session }

        serialsSubscribedTo.update {
            it.toMutableMap().apply {
                replaceAll { _, sessions ->
                    sessions - session
                }
            }
        }
        sessionsSubscribedToAllDevices.update { it - session }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        println("Message received from ${session.id}: ${message.payload}")

        when (json.decodeFromString<Type>(message.payload).type) {
            "fetch-online-devices" -> {
                sessionsSubscribedToAllDevices.update { it + session }
            }

            "request-device" -> {
                val currentDevice = takenDevices.value[session]
                if (currentDevice != null) {
                    session.send(SocketMessage("device-given", "already-connected-$currentDevice"))
                } else {
                    GlobalScope.launch {
                        sessionsRequestingDevice.send(session)
                    }
                }
            }

            "free-device" -> {
                takenDevices.update { it - session }
            }

            "subscribe-to-device-updates" -> {
                val serial = json.decodeFromString<SocketMessage<String>>(message.payload).data
                serialsSubscribedTo.update { currentSerials ->
                    val currentSubscribersForDevice = currentSerials.getOrDefault(serial, emptyList())

                    currentSerials.toMutableMap().apply {
                        this[serial] = currentSubscribersForDevice + sessions.value
                    }
                }
            }

            "test-event-from-device" -> {
                val message = json.decodeFromString<SocketMessage<SerializableTestEvent>>(message.payload).data
                val serialForDevice = takenDevices.value[session]
                if (serialForDevice != null) {
                    deviceStatus.update { currentStatuses ->
                        val currentEvent = currentStatuses.firstOrNull { it.first == serialForDevice }?.second
                            ?: DeviceStatus.CurrentTestStatus.NotRunningTests(emptyList())

                        val event = when (message) {
                            is SerializableTestEvent.TestRunStartedEvent -> {
                                DeviceStatus.CurrentTestStatus.RunningTests(
                                    totalTests = message.testCount,
                                    finishedTests = emptyList(),
                                    currentlyRunningTest = null,
                                    previousRuns = (currentEvent as DeviceStatus.CurrentTestStatus.NotRunningTests).previousRuns,
                                )
                            }

                            is SerializableTestEvent.TestStarted -> {
                                (currentEvent as DeviceStatus.CurrentTestStatus.RunningTests).copy(
                                    currentlyRunningTest = message.id.testName
                                )
                            }

                            is SerializableTestEvent.TestEnded -> {
                                (currentEvent as DeviceStatus.CurrentTestStatus.RunningTests)

                                // When a test fails it will first send TestFailed which means that event will already be in the list
                                // So if the last test finish is the same as this one don't add it again
                                if (currentEvent.finishedTests.lastOrNull()?.name == message.id.testName) {
                                    currentEvent
                                } else {
                                    currentEvent.copy(
                                        currentlyRunningTest = null,
                                        finishedTests = currentEvent.finishedTests + DeviceStatus.CurrentTestStatus.RunningTests.FinishedTest.Success(
                                            name = message.id.testName,
                                        )
                                    )
                                }

                            }

                            is SerializableTestEvent.TestFailed -> {
                                (currentEvent as DeviceStatus.CurrentTestStatus.RunningTests).copy(
                                    currentlyRunningTest = null,
                                    finishedTests = currentEvent.finishedTests + DeviceStatus.CurrentTestStatus.RunningTests.FinishedTest.Failed(
                                        name = message.id.testName,
                                        stackTrace = message.stackTrace
                                    )
                                )
                            }

                            is SerializableTestEvent.TestRunEnded -> {
                                DeviceStatus.CurrentTestStatus.NotRunningTests(
                                    previousRuns = (currentEvent as DeviceStatus.CurrentTestStatus.RunningTests).previousRuns + currentEvent
                                )
                            }

                            is SerializableTestEvent.TestAssumptionFailed,
                            is SerializableTestEvent.TestIgnored,
                            is SerializableTestEvent.TestRunFailed,
                            is SerializableTestEvent.TestRunFailing,
                            is SerializableTestEvent.TestRunStopped -> {
                                currentEvent
                            }
                        }

                        currentStatuses.toMutableList().apply {
                            removeAll { it.first == serialForDevice }
                            add(serialForDevice to event)
                        }
                    }
                } else {
                    println("$session sent a test event message while it has no device!")
                }
            }
        }
    }

    private inline fun <reified T> WebSocketSession.send(message: SocketMessage<T>) {
        val json = json.encodeToString(message)
        println("Sending message to $id: $json")
        sendMessage(TextMessage(json))
    }
}

@Configuration
@EnableWebSocket
class WSConfig : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(DeviceHandler(), "/socket/devices")
            .setAllowedOrigins("*")
    }
}
