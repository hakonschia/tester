package com.hakonschia.tester.common

import kotlinx.serialization.Serializable

@Serializable
data class SocketMessage<T>(
    val type: String,
    val data: T
)

@Serializable
data class Type(
    val type: String
)
