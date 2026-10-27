package com.nobody.repoi

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.nobody.repoi.di.initKoin

fun main() {
    initKoin()
    application {
        val windowState = rememberWindowState(
            size = DpSize(1280.dp, 800.dp)
        )
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "RePoi",
        ) {
            App()
        }
    }
}
