package io.github.stanleychy.cleantubetv

import android.app.Application
import io.github.stanleychy.cleantubetv.auth.Auth
import io.github.stanleychy.cleantubetv.settings.Settings
import io.github.stanleychy.cleantubetv.yt.InnerTube
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Holds the few app-wide singletons. No dependency-injection library. */
class App : Application() {
    lateinit var auth: Auth private set
    lateinit var innerTube: InnerTube private set
    lateinit var settings: Settings private set
    val io: ExecutorService = Executors.newFixedThreadPool(4)

    override fun onCreate() {
        super.onCreate()
        auth = Auth(this)
        innerTube = InnerTube(this, auth)
        settings = Settings(this)
    }
}

val android.content.Context.app: App get() = applicationContext as App
