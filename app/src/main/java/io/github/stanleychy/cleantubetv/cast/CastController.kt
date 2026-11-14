package io.github.stanleychy.cleantubetv.cast

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import io.github.stanleychy.cleantubetv.ui.PlayerActivity

/**
 * "Link with TV code": a phone with the official YouTube app sends a video to this app.
 * This class connects the three parts: the stored screen ([ScreenStore]), the connection to YouTube
 * ([LoungeSession]) and the player screen ([PlayerActivity]).
 *
 * Rules, and their reasons:
 * - The session runs only while an activity of this app is visible. The app has no service and no boot receiver.
 * - The session is signed out. It never gets the OAuth token or a visitor ID (see [LoungeApi]).
 * - A cast video plays through the normal player screen, as a playback that a link started
 *   ([PlayerActivity.EXTRA_FROM_LINK]). Thus the watch reports to the account start only at the first key
 *   press on the TV remote: a different device started the playback.
 * - The app reports only cast videos to the phone. A video that the user starts on the TV is not reported:
 *   the Lounge session belongs to the account on the phone, and playback in this app is signed out on purpose.
 * - One video at a time. The app plays the selected video of a phone queue and ignores the other queue commands.
 *
 * Call the public functions on the main thread, but [requestPairingCode] on a worker thread.
 */
class CastController(private val app: Application) : Application.ActivityLifecycleCallbacks {
    private val store = ScreenStore(app)
    private val main = Handler(Looper.getMainLooper())

    /** The screen of the session. It is not in the store until a phone links to it. */
    @Volatile private var screen: LoungeScreen? = store.load()

    /** True when a phone linked to [screen] and the screen is in the store. */
    @Volatile var isLinked: Boolean = screen != null
        private set

    private var session: LoungeSession? = null
    private var visibleActivities = 0
    private var playerActivity: Activity? = null
    private var playback: CastPlayback? = null
    private var sequence = 0
    private val reportPlayback = Runnable { session?.send(playbackEvents()) }

    /** Runs on the main thread when the first phone links to the screen. The link screen uses it. */
    var onLinked: (() -> Unit)? = null

    init {
        app.registerActivityLifecycleCallbacks(this)
    }

    /**
     * Gets a pairing code for the link screen. Without a screen, it makes one and starts the session,
     * so that the app sees the phone when the user enters the code. Network work: call it off the main thread.
     */
    fun requestPairingCode(): String {
        val s = screen ?: adopt(LoungeScreen.create(LoungeApi.generateScreenId()))
        return LoungeApi.pairingCode(s, LoungeApi.loungeToken(s.screenId))
    }

    /** Forgets the screen. The phone must link again with a new code. */
    @Synchronized
    fun unlink() {
        stopSession()
        store.clear()
        screen = null
        isLinked = false
    }

    /** [PlayerActivity] calls this when its player is ready. A player of a video that no phone started is ignored. */
    fun onPlayerReady(activity: Activity, player: Player) {
        val p = playback ?: return
        if (sequenceOf(activity) != p.sequence) return
        p.attach(player) { if (playback === p) reportSoon() }
    }

    /** Uses [created] as the screen if there is none. Two requests at the same time get the same screen. */
    @Synchronized
    private fun adopt(created: LoungeScreen): LoungeScreen {
        screen?.let { return it }
        screen = created
        main.post { if (visibleActivities > 0) startSession() }
        return created
    }

    @Synchronized
    private fun startSession() {
        val s = screen ?: return
        if (session != null) return
        lateinit var created: LoungeSession
        created = LoungeSession(s, object : LoungeSession.Listener {
            override fun onConnected() = onMain(created) { reportAll() }

            override fun onCommand(command: CastCommand) = onMain(created) { obey(command) }
        })
        session = created
        created.start()
    }

    @Synchronized
    private fun stopSession() {
        session?.stop()
        session = null
    }

    /** Runs [block] on the main thread, but not when [from] is an old session that was stopped. */
    private fun onMain(from: LoungeSession, block: () -> Unit) {
        main.post { if (session === from) block() }
    }

    private fun obey(command: CastCommand) {
        when (command) {
            is CastCommand.Play -> play(command)
            CastCommand.Resume -> playback?.player?.play()
            CastCommand.Pause -> playback?.player?.pause()
            is CastCommand.Seek -> playback?.player?.seekTo(command.positionMs)
            CastCommand.Stop -> stop()
            is CastCommand.ReportState -> {
                if (command.remoteLinked) keepScreen()
                reportAll()
            }
        }
    }

    /** Opens the player screen for the video. A video that plays now is replaced: the app has no queue. */
    private fun play(command: CastCommand.Play) {
        val current = playback
        // The phone sends the same video again when it connects again. Do not start the video again.
        if (current != null && current.request.videoId == command.videoId && playerActivity != null) {
            if (current.ended) current.player?.let { it.seekTo(command.startMs); it.play() }
            return reportSoon()
        }
        playerActivity?.finish()
        val next = CastPlayback(++sequence, command)
        playback = next
        app.startActivity(
            Intent(app, PlayerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(PlayerActivity.EXTRA_ID, command.videoId)
                .putExtra(PlayerActivity.EXTRA_START_MS, command.startMs)
                .putExtra(PlayerActivity.EXTRA_FROM_LINK, true)
                .putExtra(EXTRA_CAST_SEQUENCE, next.sequence),
        )
        reportSoon()
    }

    /** Closes the player screen of the cast video. A video that the user started on the TV continues. */
    private fun stop() {
        val p = playback ?: return
        playback = null
        playerActivity?.takeIf { sequenceOf(it) == p.sequence }?.finish()
        reportSoon()
    }

    /** A phone is linked: keep the screen, so that the link stays after the app stops. */
    private fun keepScreen() {
        val s = screen ?: return
        if (isLinked) return
        store.save(s)
        isLinked = true
        onLinked?.invoke()
    }

    /** The state, and the functions that this screen does not have. The phone then hides those buttons. */
    private fun reportAll() {
        val abilities = listOf(
            LoungeEvent("onHasPreviousNextChanged", mapOf("hasPrevious" to "false", "hasNext" to "false")),
            LoungeEvent("onAutoplayModeChanged", mapOf("autoplayMode" to "UNSUPPORTED")),
        )
        session?.send(abilities + playbackEvents())
    }

    /** One report for a group of player events that come together (for example "play" and "ready"). */
    private fun reportSoon() {
        main.removeCallbacks(reportPlayback)
        main.post(reportPlayback)
    }

    /** With no cast video, an empty "nowPlaying" tells the phone that nothing plays. */
    private fun playbackEvents(): List<LoungeEvent> = playback?.events() ?: listOf(LoungeEvent("nowPlaying"))

    private fun sequenceOf(activity: Activity) = activity.intent.getIntExtra(EXTRA_CAST_SEQUENCE, 0)

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is PlayerActivity) playerActivity = activity
    }

    override fun onActivityStarted(activity: Activity) {
        if (visibleActivities++ == 0) startSession()
    }

    override fun onActivityStopped(activity: Activity) {
        if (--visibleActivities > 0) return
        stopSession()
        // A screen that no phone linked to is not kept. The link screen makes a new one.
        if (!isLinked) screen = null
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity === playerActivity) playerActivity = null
        val p = playback ?: return
        if (activity !is PlayerActivity || sequenceOf(activity) != p.sequence) return
        // The user closed the player of the cast video.
        playback = null
        reportSoon()
    }

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private companion object {
        /** The [CastPlayback.sequence] of the video that a player screen was opened for. 0 means "not a cast video". */
        const val EXTRA_CAST_SEQUENCE = "cast_sequence"
    }
}
