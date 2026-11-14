package io.github.stanleychy.cleantubetv.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.window.OnBackInvokedDispatcher
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import io.github.stanleychy.cleantubetv.R
import io.github.stanleychy.cleantubetv.app
import io.github.stanleychy.cleantubetv.settings.Settings
import io.github.stanleychy.cleantubetv.sponsor.Segment
import io.github.stanleychy.cleantubetv.sponsor.SponsorBlock
import io.github.stanleychy.cleantubetv.yt.DashManifest
import io.github.stanleychy.cleantubetv.yt.PlayerInfo
import io.github.stanleychy.cleantubetv.yt.PlayerParser
import io.github.stanleychy.cleantubetv.yt.SignInRequiredException
import io.github.stanleychy.cleantubetv.yt.WatchReporter
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {
    private lateinit var playerView: PlayerView
    private lateinit var message: TextView
    private lateinit var qualityButton: Button
    private lateinit var speedButton: TextView
    private var player: ExoPlayer? = null
    private var reporter: WatchReporter? = null
    /** The reporter of a playback that a link started. It becomes [reporter] at the first key press. */
    private var pendingReporter: WatchReporter? = null
    private var segments: List<Segment> = emptyList()
    private val skipped = HashSet<Segment>()
    private val handler = Handler(Looper.getMainLooper())
    private var lastReportMs = 0L
    /** One thread for watch reports, so that YouTube gets the pings in order. */
    private val reportQueue = Executors.newSingleThreadExecutor()
    private var speed = 1f
    /** True from the Back key-down that hid the controls to its key-up. That push does not also close the player. */
    private var backHidControls = false
    /** The short side of the resolution that the user selected in the quality menu. Null is "Auto". */
    private var qualitySide: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.player_view)
        message = findViewById(R.id.player_message)
        qualityButton = findViewById(R.id.quality_button)

        playerView.setShowSubtitleButton(true)
        // The controls stay hidden while the video loads: the message shows the title. See onIsPlayingChanged.
        playerView.controllerAutoShow = false
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
            qualityButton.visibility = v
        })
        qualityButton.setOnClickListener { showQualityDialog() }
        speedButton = addSpeedButton()
        registerBackCallback()

        val id = intent.getStringExtra(EXTRA_ID) ?: return finish()
        showMessage(intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.loading))
        val subtitleLanguage = app.settings.subtitleLanguage
        app.io.execute {
            val result = runCatching { app.innerTube.playback(id).let { PlayerParser.parse(id, it.reply, it.client) } }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                result.onSuccess { start(it, subtitleLanguage) }.onFailure { e ->
                    Log.w(TAG, "player request failed", e)
                    val text = if (e is SignInRequiredException) R.string.play_error_sign_in_required else R.string.play_error
                    showMessage(getString(text, e.message))
                }
            }
        }
        // The SponsorBlock reply can take long. Playback does not wait for it: the segments apply when they come,
        // and a segment that already plays is skipped at that moment (see tick). The lookup stops when the player closes.
        SponsorBlock.executor.execute {
            val segs = SponsorBlock.segments(id) { !isFinishing && !isDestroyed }
            runOnUiThread { if (!isFinishing) segments = segs }
        }
    }

    private fun start(info: PlayerInfo, subtitleLanguage: String) {
        val source = buildSource(info, subtitleLanguage) ?: return showMessage(getString(R.string.play_error, "no playable formats"))
        val settings = app.settings
        val selector = DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    // VP9 first: it has the widest HDR support on YouTube. The Bravia decodes VP9 and AV1 in hardware.
                    .setPreferredVideoMimeTypes(MimeTypes.VIDEO_VP9, MimeTypes.VIDEO_AV1, MimeTypes.VIDEO_H264)
                    // Null selects the track with the "main" role, which is the original audio.
                    .setPreferredAudioLanguage(settings.audioLanguage.ifEmpty { null })
                    .setPreferredTextLanguage(subtitleLanguage.ifEmpty { null })
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitleLanguage.isEmpty()),
            )
        }
        val p = ExoPlayer.Builder(this).setTrackSelector(selector).build()
        player = p
        playerView.player = withoutSpeedSetting(p)
        speed = settings.speeds.let { if (1f in it) 1f else it.first() }
        applySpeed()
        if (settings.historyEnabled && app.auth.isSignedIn) {
            val r = WatchReporter(app.innerTube, app.auth, info)
            // A link from another app starts playback without the user. Such a watch goes to the history only
            // after the user pushes a key (see dispatchKeyEvent), so that an app cannot fill the Watch History.
            if (intent.getBooleanExtra(EXTRA_FROM_LINK, false)) pendingReporter = r else reporter = r
        }
        p.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?,
            ) {
                qualityButton.text = qualityLabel(format)
            }
        })
        p.addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason != Player.DISCONTINUITY_REASON_SEEK) return
                val r = reporter ?: return
                val from = oldPosition.positionMs
                val to = newPosition.positionMs
                reportQueue.execute { r.onSeek(from, to) }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "playback error", error)
                showMessage(getString(R.string.play_error, error.errorCodeName))
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    message.visibility = View.GONE
                    // The video started. From now on, a pause and the end of the video show the controls.
                    playerView.controllerAutoShow = true
                    val r = reporter ?: return
                    val pos = p.currentPosition
                    reportQueue.execute { r.onStart(pos) }
                } else {
                    report()
                }
            }
        })
        // A start time from a link (for example youtu.be/ID?t=90). 0 starts at the beginning.
        p.setMediaSource(source, intent.getLongExtra(EXTRA_START_MS, 0).coerceAtLeast(0))
        p.prepare()
        p.playWhenReady = true
        handler.post(tick)
    }

    private fun buildSource(info: PlayerInfo, subtitleLanguage: String): MediaSource? {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(info.client.userAgent)
            .setDefaultRequestProperties(mapOf("Origin" to "https://www.youtube.com", "Referer" to "https://www.youtube.com/"))
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        val hls = info.hlsUrl
        if (info.isLive || (info.formats.isEmpty() && hls != null)) {
            hls ?: return null
            return HlsMediaSource.Factory(http).createMediaSource(MediaItem.fromUri(hls))
        }
        if (info.formats.isEmpty()) return null
        val mpd = DashManifest.build(info, subtitleLanguage)
        val uri = Uri.parse("https://www.youtube.com/watch?v=${info.videoId}")
        val manifest = DashManifestParser().parse(uri, ByteArrayInputStream(mpd.toByteArray()))
        return DashMediaSource.Factory(http).createMediaSource(manifest, MediaItem.Builder().setUri(uri).setMediaId(info.videoId).build())
    }

    private val tick = object : Runnable {
        override fun run() {
            val p = player ?: return
            val pos = p.currentPosition
            for (s in segments) {
                if (s !in skipped && pos >= s.startMs && pos < s.endMs - 500) {
                    skipped += s
                    p.seekTo(s.endMs)
                    Toast.makeText(this@PlayerActivity, getString(R.string.skipped_segment, s.category), Toast.LENGTH_SHORT).show()
                    break
                }
            }
            if (p.isPlaying && System.currentTimeMillis() - lastReportMs > REPORT_INTERVAL_MS) report()
            handler.postDelayed(this, 250)
        }
    }

    private fun report() {
        val p = player ?: return
        val r = reporter ?: return
        lastReportMs = System.currentTimeMillis()
        val pos = p.currentPosition
        reportQueue.execute { r.onProgress(pos) }
    }

    /** The speed button replaces the speed row of the settings menu. It cycles through the speeds from Settings. */
    private fun addSpeedButton(): TextView {
        val bar = playerView.findViewById<LinearLayout>(androidx.media3.ui.R.id.exo_basic_controls)
        val pad = (12 * resources.displayMetrics.density).toInt()
        val button = TextView(this).apply {
            setBackgroundResource(R.drawable.tab_bg)
            setTextColor(getColorStateList(R.color.tab_text))
            textSize = 16f
            gravity = Gravity.CENTER
            minWidth = (64 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
            isFocusable = true
            contentDescription = getString(R.string.speed)
            setOnClickListener { nextSpeed() }
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.CENTER_VERTICAL
        lp.marginEnd = pad / 2
        bar.addView(button, 0, lp)
        button.text = Settings.speedLabel(speed)
        return button
    }

    private fun nextSpeed() {
        val list = app.settings.speeds
        speed = list.firstOrNull { it > speed + 0.001f } ?: list.first()
        applySpeed()
    }

    private fun applySpeed() {
        player?.setPlaybackSpeed(speed)
        speedButton.text = Settings.speedLabel(speed)
    }

    /** Hides the speed row in the Media3 settings menu. The player itself still accepts speed changes. */
    private fun withoutSpeedSetting(p: Player): Player = object : ForwardingPlayer(p) {
        override fun isCommandAvailable(command: Int) =
            command != Player.COMMAND_SET_SPEED_AND_PITCH && super.isCommandAvailable(command)

        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon().remove(Player.COMMAND_SET_SPEED_AND_PITCH).build()
    }

    /** For example "2160p60 HDR". YouTube names a quality by the short side of the frame. */
    private fun qualityLabel(f: Format): String {
        val side = listOf(f.width, f.height).filter { it > 0 }.minOrNull() ?: return getString(R.string.quality)
        val fps = if (f.frameRate > 30.5f) f.frameRate.roundToInt().toString() else ""
        val transfer = f.colorInfo?.colorTransfer
        val codecs = f.codecs.orEmpty()
        // Transfer 16 is PQ and 18 is HLG in the VP9 and AV1 codec strings.
        val hdr = transfer == C.COLOR_TRANSFER_ST2084 || transfer == C.COLOR_TRANSFER_HLG ||
            codecs.contains(".09.16.") || codecs.contains(".09.18.")
        return "${side}p$fps" + if (hdr) " HDR" else ""
    }

    /** A short list as in the YouTube quality menu: "Auto", then one row for each resolution, highest first. */
    private fun showQualityDialog() {
        val p = player ?: return
        val options = QualityOptions.from(p.currentTracks, p.trackSelectionParameters.preferredVideoMimeTypes)
        val labels = listOf(getString(R.string.quality_auto)) + options.map { "${qualityLabel(it.best)} ${it.hint}".trim() }
        // Index 0 is "Auto". It is also the mark when the selected resolution is not in the list.
        val checked = options.indexOfFirst { it.side == qualitySide } + 1
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.quality)
            .setSingleChoiceItems(labels.toTypedArray(), checked) { d, i ->
                selectQuality(p, options.getOrNull(i - 1))
                d.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Limits the video to the frame size of [option]. Null is "Auto": it removes the limit.
     * A size limit, not a track override, keeps the selection in the track selector: the selector selects the
     * best codec variant of that size that the device can play, and it uses a different size if no variant plays.
     */
    private fun selectQuality(p: Player, option: QualityOption?) {
        qualitySide = option?.side
        val params = p.trackSelectionParameters.buildUpon()
        if (option == null) {
            params.clearVideoSizeConstraints().setMinVideoSize(0, 0).setViewportSizeToPhysicalDisplaySize(true)
        } else {
            // The display size does not limit a resolution that the user selected.
            params.setMaxVideoSize(option.maxWidth, option.maxHeight)
                .setMinVideoSize(option.minWidth, option.minHeight)
                .clearViewportSizeConstraints()
        }
        p.trackSelectionParameters = params.build()
    }

    private fun showMessage(text: String) {
        message.text = text
        message.visibility = View.VISIBLE
    }

    /** Hides the controls if they show. Returns true if it hid them. */
    private fun hideControls(): Boolean {
        if (!playerView.isControllerFullyVisible) return false
        playerView.hideController()
        return true
    }

    /**
     * Back as a key event. The return value is true for each event of the push that hid the controls (key-down,
     * repeats and key-up), so that a long push does not also close the player.
     */
    private fun hideControlsOnBack(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) backHidControls = hideControls()
        return backHidControls
    }

    /**
     * Back as a callback. Android 16 and later send Back to this callback and not as a key event. Earlier
     * versions ignore the callback and send the key event (see dispatchKeyEvent).
     */
    private fun registerBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
            if (!hideControls()) finish()
        }
    }

    // Back first hides the controls. With the controls hidden, it goes to super unchanged and closes the player.
    // A dialog or a menu has its own window and gets Back before this activity. TVs have no predictive back gesture.
    @SuppressLint("GestureBackNavigation")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return hideControlsOnBack(event) || super.dispatchKeyEvent(event)
        // Any other key shows that the user watches: the watch reports of a link-started playback can start.
        pendingReporter?.let {
            pendingReporter = null
            reporter = it
            report()
        }
        if (qualityButton.hasFocus()) {
            // The quality button is not in the control bar. Start the hide timeout again, as the bar does for its keys.
            playerView.showController()
            return super.dispatchKeyEvent(event)
        }
        return playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        report()
        // The queued pings still go out. The queue then stops.
        reportQueue.shutdown()
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ID = "video_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_START_MS = "start_ms"
        /** True when another app started the playback with a link. */
        const val EXTRA_FROM_LINK = "from_link"
        private const val TAG = "PlayerActivity"
        private const val REPORT_INTERVAL_MS = 30_000L
    }
}
