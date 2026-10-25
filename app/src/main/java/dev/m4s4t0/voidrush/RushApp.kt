package dev.m4s4t0.voidrush

import android.app.Application
import dev.m4s4t0.voidrush.audio.Audio
import dev.m4s4t0.voidrush.audio.Haptics
import dev.m4s4t0.voidrush.model.Game

class RushApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Themes.init(this)
        Game.init(this)
        Haptics.init(this)
        Audio.start()
    }
}
