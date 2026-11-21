package com.thecirclestriangle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thecirclestriangle.audio.Sound
import com.thecirclestriangle.ui.CirclesTheme
import com.thecirclestriangle.ui.Root

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Sound.init(this)
        enableEdgeToEdge()
        setContent {
            CirclesTheme {
                Root(viewModel<GameViewModel>())
            }
        }
    }

    // Music only plays while the game is on screen.
    override fun onStart() { super.onStart(); Sound.setForeground(true) }
    override fun onStop() { Sound.setForeground(false); super.onStop() }
}
