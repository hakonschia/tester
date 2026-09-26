package com.hakonschia.tester.backend

import com.malinskiy.adam.AndroidDebugBridgeClientFactory
import com.malinskiy.adam.request.device.AsyncDeviceMonitorRequest
import com.malinskiy.adam.request.device.DeviceState
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.handler.TextWebSocketHandler
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper

data class SocketMessage<T>(
    val type: String,
    val data: T
)

class DeviceHandler : TextWebSocketHandler() {

    private val adb = AndroidDebugBridgeClientFactory().build()
    private val sessions = mutableSetOf<WebSocketSession>()

    private var onlineDevices = emptyList<String>()
    private val takenDevices = mutableMapOf<WebSocketSession, String>()

    init {
        GlobalScope.launch {
            adb.execute(request = AsyncDeviceMonitorRequest(), scope = this).consumeEach { devices ->
                onlineDevices = devices.filter { it.state == DeviceState.DEVICE }.map { it.serial }

                sessions.forEach { session ->
                    session.sendOnlineDevices()
                }
            }
        }
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        println("Session added: ${session.id}")
        sessions += session
        session.sendOnlineDevices()
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        println("Session removed: ${session.id} - $status")
        sessions -= session
        takenDevices -= session
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        println("Message recieved from ${session.id}: ${message.payload}")

        val json = ObjectMapper().readTree(message.payload)
        when (json.get("type").asString()) {
            "msg" -> {
                val data = json.get("data")

                when (data.asString()) {
                    "request-device" -> {
                        val currentDevice = takenDevices[session]
                        if (currentDevice != null) {
                            session.send(SocketMessage("request-device", "already-connected-$currentDevice"))
                        } else {
                            val availableDevices = onlineDevices - takenDevices.values

                            if (availableDevices.isNotEmpty()) {
                                val device = availableDevices.random()
                                takenDevices[session] = device
                                session.send(SocketMessage("request-device", device))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun WebSocketSession.sendOnlineDevices() {
        send(SocketMessage("all-devices", onlineDevices))
    }

    private fun WebSocketSession.send(message: SocketMessage<Any>) {
        val json = jacksonObjectMapper().writeValueAsString(message)
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

