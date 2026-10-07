package com.xenoise

import android.graphics.Color
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xenoise.ui.XenoiseRoot
import com.xenoise.ui.theme.XenoiseTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // The hardware volume keys control the noise even before it starts playing.
        volumeControlStream = AudioManager.STREAM_MUSIC

        val app = application as XenoiseApplication
        setContent {
            XenoiseTheme {
                XenoiseRoot(player = app.player, library = app.library)
            }
        }
    }
}
