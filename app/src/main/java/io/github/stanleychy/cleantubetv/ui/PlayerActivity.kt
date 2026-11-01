package io.github.stanleychy.cleantubetv.ui

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import io.github.stanleychy.cleantubetv.R
import io.github.stanleychy.cleantubetv.app
import io.github.stanleychy.cleantubetv.sponsor.Segment
import io.github.stanleychy.cleantubetv.sponsor.SponsorBlock
import io.github.stanleychy.cleantubetv.yt.Clients
import io.github.stanleychy.cleantubetv.yt.DashManifest
import io.github.stanleychy.cleantubetv.yt.PlayerInfo
import io.github.stanleychy.cleantubetv.yt.PlayerParser
import io.github.stanleychy.cleantubetv.yt.WatchReporter
import java.io.ByteArrayInputStream

@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {
    private lateinit var playerView: PlayerView
    private lateinit var message: TextView
    private lateinit var qualityButton: Button
    private var player: ExoPlayer? = null
    private var reporter: WatchReporter? = null
    private var segments: List<Segment> = emptyList()
    private val skipped = HashSet<Segment>()
    private val handler = Handler(Looper.getMainLooper())
    private var lastReportMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.player_view)
        message = findViewById(R.id.player_message)
        qualityButton = findViewById(R.id.quality_button)

        playerView.setShowSubtitleButton(true)
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
            qualityButton.visibility = v
        })
        qualityButton.setOnClickListener { showQualityDialog() }

        val id = intent.getStringExtra(EXTRA_ID) ?: return finish()
        showMessage(intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.loading))
        app.io.execute {
            val result = runCatching { PlayerParser.parse(id, app.innerTube.player(id)) }
            val segs = SponsorBlock.segments(id)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                segments = segs
                result.onSuccess { start(it) }.onFailure { e ->
                    Log.w(TAG, "player request failed", e)
                    showMessage(getString(R.string.play_error, e.message))
                }
            }
        }
    }

    private fun start(info: PlayerInfo) {
        val source = buildSource(info) ?: return showMessage(getString(R.string.play_error, "no playable formats"))
        val selector = DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    // VP9 first: the Bravia 8 decodes it in hardware. AV1 support is not verified.
                    .setPreferredVideoMimeTypes(MimeTypes.VIDEO_VP9, MimeTypes.VIDEO_AV1, MimeTypes.VIDEO_H264)
                    .setPreferredAudioLanguage(null)
                    .setPreferredTextLanguage(null),
            )
        }
        val p = ExoPlayer.Builder(this).setTrackSelector(selector).build()
        player = p
        playerView.player = p
        reporter = WatchReporter(app.auth, info)
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "playback error", error)
                showMessage(getString(R.string.play_error, error.errorCodeName))
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    message.visibility = View.GONE
                    app.io.execute { reporter?.onStart() }
                } else {
                    report()
                }
            }
        })
        p.setMediaSource(source)
        p.prepare()
        p.playWhenReady = true
        handler.post(tick)
    }

    private fun buildSource(info: PlayerInfo): MediaSource? {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Clients.VISIONOS.userAgent)
            .setDefaultRequestProperties(mapOf("Origin" to "https://www.youtube.com", "Referer" to "https://www.youtube.com/"))
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        val hls = info.hlsUrl
        if (info.isLive || (info.formats.isEmpty() && hls != null)) {
            hls ?: return null
            return HlsMediaSource.Factory(http).createMediaSource(MediaItem.fromUri(hls))
        }
        if (info.formats.isEmpty()) return null
        val mpd = DashManifest.build(info)
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
        app.io.execute { r.onProgress(pos) }
    }

    private fun showQualityDialog() {
        val p = player ?: return
        TrackSelectionDialogBuilder(this, getString(R.string.quality), p, C.TRACK_TYPE_VIDEO)
            .setShowDisableOption(false)
            .setAllowAdaptiveSelections(false)
            .build()
            .show()
    }

    private fun showMessage(text: String) {
        message.text = text
        message.visibility = View.VISIBLE
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        if (qualityButton.hasFocus()) return super.dispatchKeyEvent(event)
        return playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        report()
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ID = "video_id"
        const val EXTRA_TITLE = "title"
        private const val TAG = "PlayerActivity"
        private const val REPORT_INTERVAL_MS = 30_000L
    }
}
