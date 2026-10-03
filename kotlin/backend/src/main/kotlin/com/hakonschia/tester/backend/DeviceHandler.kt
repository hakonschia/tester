package com.hakonschia.tester.backend

import com.hakonschia.tester.common.*
import com.malinskiy.adam.AndroidDebugBridgeClientFactory
import com.malinskiy.adam.request.device.AsyncDeviceMonitorRequest
import com.malinskiy.adam.request.device.DeviceState
import com.malinskiy.adam.request.prop.GetPropRequest
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
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

    /**
     * Currently connected websockets
     */
    private val sessions = MutableStateFlow(emptySet<WebSocketSession>())

    /**
     * All devices currently connected, which might include devices currently running tests
     */
    private val connectedDevicesFlow = MutableStateFlow<List<Device>>(emptyList())

    /**
     * Devices taken by a client, these devices are currently running tests
     */
    private val takenDevicesFlow = MutableStateFlow<Map<WebSocketSession, Device>>(emptyMap())

    /**
     * Devices currently available for clients that want to run tests
     */
    private val availableDevicesFlow = combine(connectedDevicesFlow, takenDevicesFlow) { online, taken ->
        online - taken.values.toSet()
    }

    /**
     * The current test status of each device, i.e. if a device is not running tests or which test it is currently running
     */
    private val deviceStatus = MutableStateFlow<List<Pair<Device, DeviceStatus.CurrentTestStatus>>>(emptyList())

    /**
     * A map of where the key is a [Device.serial] and the values are every websocket that wants updates on the device
     */
    private val serialsSubscribedTo = MutableStateFlow<Map<String, List<WebSocketSession>>>(emptyMap())

    /**
     * Websockets that want update on every connected device
     */
    private val sessionsSubscribedToAllDevices = MutableStateFlow<List<WebSocketSession>>(emptyList())

    /**
     * The queue of websockets requesting a device
     */
    private val deviceRequestQueue = Channel<WebSocketSession>()

    init {
        listenForConnectedDevices()
        launchDeviceQueue()
        listenForDeviceUpdatesAndNotifyWebsockets()
    }

    private fun listenForConnectedDevices() {
        GlobalScope.launch {
            adb.execute(request = AsyncDeviceMonitorRequest(), scope = this).consumeEach { devices ->
                connectedDevicesFlow.update {
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
    }

    private fun launchDeviceQueue() {
        GlobalScope.launch {
            availableDevicesFlow.collect {
                println("\tAvailable devices: $it")
            }
        }

        GlobalScope.launch {
            deviceRequestQueue.receiveAsFlow().collect { session ->
                println("\t${session.id} is waiting for a device...")

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
                        takenDevicesFlow.update {
                            it.toMutableMap().apply {
                                this[session] = device
                            }
                        }

                        session.send(SocketMessage("device-given", device))
                    }

                println("\t${session.id} is no longer waiting for a device")
            }
        }
    }

    private fun listenForDeviceUpdatesAndNotifyWebsockets() {
        GlobalScope.launch {
            combine(connectedDevicesFlow, takenDevicesFlow, deviceStatus) { online, taken, deviceStatus ->
                online.map { onlineDevice ->
                    DeviceStatus(
                        device = onlineDevice,
                        taken = taken.any { it.value.serial == onlineDevice.serial },
                        currentTestStatus = deviceStatus.find { it.first == onlineDevice }?.second ?: DeviceStatus.CurrentTestStatus.NotRunningTests(
                            emptyList()
                        ),
                    )
                }
            }.collectLatest { devices ->
                coroutineScope {
                    // Notify the websockets interested in specific devices
                    serialsSubscribedTo.onEach { serials ->
                        serials.forEach { (serial, sessions) ->
                            devices.forEach { device ->
                                if (device.device.serial == serial) {
                                    sessions.forEach { session ->
                                        session.send(SocketMessage("device-status", device))
                                    }
                                }
                            }
                        }
                    }.launchIn(this)

                    // Notify the websockets interested in all devices
                    sessionsSubscribedToAllDevices.onEach { sessions ->
                        sessions.forEach { session ->
                            session.send(SocketMessage("all-devices", devices))
                        }
                    }.launchIn(this)
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
        takenDevicesFlow.update { it - session }

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
                val currentDevice = takenDevicesFlow.value[session]
                if (currentDevice != null) {
                    session.send(SocketMessage("device-given", "already-connected-$currentDevice"))
                } else {
                    GlobalScope.launch {
                        deviceRequestQueue.send(session)
                    }
                }
            }

            "free-device" -> {
                takenDevicesFlow.update { it - session }
            }

            "subscribe-to-device-updates" -> {
                val serial = json.decodeFromString<SocketMessage<String>>(message.payload).data
                serialsSubscribedTo.update { currentSerials ->
                    currentSerials.toMutableMap().apply {
                        this[serial] = getOrDefault(serial, emptyList()) + sessions.value
                    }
                }
            }

            "test-event-from-device" -> {
                val message = json.decodeFromString<SocketMessage<SerializableTestEvent>>(message.payload).data
                val serialForDevice = takenDevicesFlow.value[session]
                if (serialForDevice != null) {
                    deviceStatus.update { currentStatuses ->
                        val currentEvent = currentStatuses.firstOrNull { it.first == serialForDevice }?.second
                            ?: DeviceStatus.CurrentTestStatus.NotRunningTests(emptyList())

                        val event = when (message) {
                            is SerializableTestEvent.TestRunStartedEvent -> {
                                DeviceStatus.CurrentTestStatus.RunningTests(
                                    totalTests = message.testCount,
                                    finishedTests = emptyList(),
                                    startTimestamp = System.currentTimeMillis(),
                                    currentlyRunningTest = null,
                                    previousRuns = (currentEvent as DeviceStatus.CurrentTestStatus.NotRunningTests).previousRuns,
                                )
                            }

                            is SerializableTestEvent.TestStarted -> {
                                (currentEvent as DeviceStatus.CurrentTestStatus.RunningTests).copy(
                                    currentlyRunningTest = message.id.prettyName()
                                )
                            }

                            is SerializableTestEvent.TestEnded -> {
                                (currentEvent as DeviceStatus.CurrentTestStatus.RunningTests)

                                // When a test fails it will first send TestFailed which means that event will already be in the list
                                // So if the last test finish is the same as this one don't add it again
                                if (currentEvent.finishedTests.lastOrNull()?.name == message.id.prettyName()) {
                                    currentEvent
                                } else {
                                    currentEvent.copy(
                                        currentlyRunningTest = null,
                                        finishedTests = currentEvent.finishedTests + DeviceStatus.CurrentTestStatus.RunningTests.FinishedTest.Success(
                                            name = message.id.prettyName(),
                                            timestamp = System.currentTimeMillis(),
                                        )
                                    )
                                }

                            }

                            is SerializableTestEvent.TestFailed -> {
                                (currentEvent as DeviceStatus.CurrentTestStatus.RunningTests).copy(
                                    currentlyRunningTest = null,
                                    finishedTests = currentEvent.finishedTests + DeviceStatus.CurrentTestStatus.RunningTests.FinishedTest.Failed(
                                        name = message.id.prettyName(),
                                        stackTrace = message.stackTrace,
                                        timestamp = System.currentTimeMillis(),
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
        if (!isOpen) {
            println("Trying to send message to closed socket: $id")
            sessions.update { it - this }
            return
        }

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

private fun SerializableTestIdentifier.prettyName() = className.split(".").last() + " - " + testName
