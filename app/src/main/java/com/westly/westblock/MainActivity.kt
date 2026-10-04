package com.westly.westblock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.westly.westblock.ui.GameScreen
import com.westly.westblock.ui.WestBlockTheme
import com.westly.westblock.vm.GameViewModel

class MainActivity : ComponentActivity() {

    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            WestBlockTheme {
                GameScreen(vm)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        vm.pauseAudio()
    }
}
