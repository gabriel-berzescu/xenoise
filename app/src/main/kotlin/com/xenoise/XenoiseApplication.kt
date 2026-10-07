package com.xenoise

import android.app.Application
import com.xenoise.data.NoiseLibrary
import com.xenoise.playback.NoisePlayer

/** Holds the app-wide singletons. Small enough that no DI framework is needed. */
class XenoiseApplication : Application() {

    lateinit var library: NoiseLibrary
        private set

    lateinit var player: NoisePlayer
        private set

    override fun onCreate() {
        super.onCreate()
        library = NoiseLibrary(this)
        player = NoisePlayer(this, library)
    }
}
