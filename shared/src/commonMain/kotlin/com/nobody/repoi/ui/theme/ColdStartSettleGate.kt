package com.nobody.repoi.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal class ColdStartSettleGate(
    private val settlingWindow: Duration = 2.seconds,
) {
    private val launchedAt = TimeSource.Monotonic.markNow()
    private var launchSignature: List<Color>? = null
    private var launchDarkTheme: Boolean? = null
    private var settled = false

    fun shouldSnap(scheme: ColorScheme, darkTheme: Boolean): Boolean {
        if (settled) return false

        val signature = scheme.settlingSignature()
        val launch = launchSignature
        if (launch == null || launchDarkTheme != darkTheme) {

            launchSignature = signature
            launchDarkTheme = darkTheme
            return true
        }
        if (launch != signature) {
            settled = true

            return launchedAt.elapsedNow() <= settlingWindow
        }
        if (launchedAt.elapsedNow() > settlingWindow) {
            settled = true
            return false
        }
        return true
    }
}

private fun ColorScheme.settlingSignature(): List<Color> = listOf(
    primary,
    secondary,
    tertiary,
    background,
    surface,
    surfaceContainer,
    onSurface,
    outline,
    error,
    surfaceVariant,
)
