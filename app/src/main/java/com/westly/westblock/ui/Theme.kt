package com.westly.westblock.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val Scheme = darkColorScheme(
    primary = Amber,
    background = BgBottom,
    surface = PanelBottom,
    onPrimary = BtnAmberText,
    onBackground = Cream,
    onSurface = Cream,
)

@Composable
fun WestBlockTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
