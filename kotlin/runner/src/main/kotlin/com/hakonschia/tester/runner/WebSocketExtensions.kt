package com.hakonschia.tester.runner

import com.hakonschia.tester.common.SocketMessage
import kotlinx.serialization.json.Json
import okhttp3.WebSocket

inline fun <reified T> WebSocket.send(message: SocketMessage<T>) {
    val message = Json.encodeToString(message)
    println("WebSocket sending: $message")
    send(message)
}