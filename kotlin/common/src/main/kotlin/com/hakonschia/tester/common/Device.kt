package com.hakonschia.tester.common

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val serial: String,
    val manufacturer: String,
    val model: String,
)
