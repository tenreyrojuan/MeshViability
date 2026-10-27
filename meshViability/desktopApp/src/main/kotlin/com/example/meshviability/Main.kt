package com.example.meshviability

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.meshviability.data.transport.RadioLink
import com.example.meshviability.data.transport.SerialDiscovery
import com.example.meshviability.data.transport.SerialTransport

fun main() {
    val links = listOf(RadioLink(SerialTransport(), SerialDiscovery))
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "meshViability",
            state = rememberWindowState(width = 480.dp, height = 820.dp),
        ) {
            App(links)
        }
    }
}
