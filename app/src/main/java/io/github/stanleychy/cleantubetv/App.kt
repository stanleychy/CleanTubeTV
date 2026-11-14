package io.github.stanleychy.cleantubetv

import android.app.Application
import android.content.pm.ApplicationInfo
import io.github.stanleychy.cleantubetv.auth.Auth
import io.github.stanleychy.cleantubetv.cast.CastController
import io.github.stanleychy.cleantubetv.settings.Settings
import io.github.stanleychy.cleantubetv.yt.InnerTube
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Holds the few app-wide singletons. No dependency-injection library. */
class App : Application() {
    lateinit var auth: Auth private set
    lateinit var innerTube: InnerTube private set
    lateinit var settings: Settings private set
    /** Cast from a phone ("Link with TV code"). It has no access to [auth]: the Lounge session is signed out. */
    lateinit var cast: CastController private set
    val io: ExecutorService = Executors.newFixedThreadPool(4)

    /** True for a debug build. Only a debug build can write diagnostic data to the log. */
    val debuggable: Boolean get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    override fun onCreate() {
        super.onCreate()
        auth = Auth(this)
        innerTube = InnerTube(this, auth)
        settings = Settings(this)
        cast = CastController(this)
    }
}

val android.content.Context.app: App get() = applicationContext as App
