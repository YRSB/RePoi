package com.example.repoi

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.nobody.poishelf.di.initKoin

fun main() = application {
    initKoin()
    Window(
        onCloseRequest = ::exitApplication,
        title = "RePoi",
    ) {
        App()
    }
}