package com.example.repoi

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.nobody.poishelf.di.initKoin
import com.nobody.poishelf.ui.navigation.PoiShelfApp

fun main() = application {
    initKoin()
    val mainWindowState = rememberWindowState(
        position = WindowPosition.Aligned(alignment = Alignment.Center),
        size = DpSize(1200.dp, 800.dp),
    )
    Window(
        onCloseRequest = ::exitApplication,
        state = mainWindowState,
        title = "RePoi",
    ) {
        PoiShelfApp()
    }
}