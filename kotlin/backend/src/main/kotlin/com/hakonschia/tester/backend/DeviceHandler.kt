package com.hakonschia.tester.backend

import com.hakonschia.tester.common.Device
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
    val availableDevicesFlow = combine(onlineDevices, takenDevices) { online, taken ->
        online - taken.values.toSet()
    }
    private val sessionsRequestingDevice = Channel<WebSocketSession>()

    @all:Synchronized
    private val serialsSubscribedTo = mutableMapOf<String, List<WebSocketSession>>()

    init {
        GlobalScope.launch {
            adb.execute(request = AsyncDeviceMonitorRequest(), scope = this).consumeEach { devices ->
                onlineDevices.update {
                    devices.filter { it.state == DeviceState.DEVICE }.map { device ->
                        val features = adb.execute(request = GetPropRequest(), serial = device.serial)

                        Device(
                            serial = device.serial,
                            manufacturer = features.getValue("ro.product.manufacturer"),
                            model = features.getValue("ro.product.model")
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
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        println("Session added: ${session.id}")
        sessions.update { it + session }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        println("Session removed: ${session.id} - $status")
        sessions.update { it - session }
        takenDevices.update { it - session }
        serialsSubscribedTo.replaceAll { _, sessions ->
            sessions - session
        }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        println("Message received from ${session.id}: ${message.payload}")

        when (json.decodeFromString<Type>(message.payload).type) {
            "fetch-online-devices" -> {
                session.sendOnlineDevices()
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
                val currentSubscribersForDevice = serialsSubscribedTo.getOrDefault(serial, emptyList())
                serialsSubscribedTo[serial] = currentSubscribersForDevice + sessions.value
            }

            "msg-from-device" -> {
                val message = json.decodeFromString<SocketMessage<String>>(message.payload).data
                val serialForDevice = takenDevices.value[session]
                if (serialForDevice != null) {
                    serialsSubscribedTo[serialForDevice.serial]?.forEach { webSocketSession ->
                        webSocketSession.send(SocketMessage(type = "new-msg-from-device", data = message))
                    }
                } else {
                    println("$session sent a message while it has no device")
                }
            }
        }
    }

    private fun WebSocketSession.sendOnlineDevices() {
        send(SocketMessage("all-devices", onlineDevices.value))
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
