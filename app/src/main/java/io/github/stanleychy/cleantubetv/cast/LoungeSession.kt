package io.github.stanleychy.cleantubetv.cast

import android.util.Log
import io.github.stanleychy.cleantubetv.net.Http
import java.io.IOException
import java.util.concurrent.Executors

/**
 * One connection of the screen to the Lounge server. It lives from [start] to [stop] and cannot start again.
 *
 * Threads:
 * - "lounge-poll" gets a lounge token, opens the session and reads commands from the long poll.
 *   After an error it waits (2 seconds, then 4, up to 60) and opens a new session.
 * - "lounge-send" sends the player state. One thread keeps the events and the request counter in order.
 *
 * The log (tag "Lounge") shows command names and HTTP status codes only. It never shows the lounge token,
 * the screen ID, the session IDs or the values of a command.
 */
class LoungeSession(private val screen: LoungeScreen, private val listener: Listener) {
    /** The session calls these functions on the "lounge-poll" thread. */
    interface Listener {
        /** A new session is open. The phone does not know the player state yet. */
        fun onConnected()

        fun onCommand(command: CastCommand)
    }

    /** The values of one open session. Only the "lounge-send" thread changes [rid] and [ofs]. */
    private class Link(val token: String, val channel: LoungeApi.Channel, var rid: Int) {
        var ofs = 0
        @Volatile var aid = 0
    }

    @Volatile private var running = false
    @Volatile private var link: Link? = null
    @Volatile private var poll: Http.Stream? = null
    private val pollThread = Thread({ run() }, "lounge-poll")
    private val sender = Executors.newSingleThreadExecutor { r -> Thread(r, "lounge-send") }

    fun start() {
        running = true
        pollThread.start()
    }

    /** Sends events to the phone. Without an open session the events are dropped: [Listener.onConnected] comes later. */
    fun send(events: List<LoungeEvent>) {
        if (!running || events.isEmpty()) return
        sender.execute { post(events) }
    }

    /**
     * Stops the session. It tells the phone that the screen is gone, then closes the long poll.
     * The network work is on the send thread, so the caller can be the main thread.
     */
    fun stop() {
        if (!running) return
        running = false
        pollThread.interrupt()
        sender.execute {
            post(listOf(LoungeEvent("loungeScreenDisconnected")))
            poll?.close()
        }
        sender.shutdown()
    }

    private fun run() {
        var failures = 0
        while (running) {
            try {
                val opened = connect()
                if (!running) return
                link = opened
                opened.channel.first.forEach { handle(opened, it) }
                Log.i(TAG, "session open")
                listener.onConnected()
                while (running) {
                    readPoll(opened)
                    failures = 0
                }
            } catch (e: Exception) {
                if (!running) return
                failures++
                Log.w(TAG, "connection lost (${describe(e)}), failure $failures")
            }
            link = null
            try {
                Thread.sleep(backoffMs(failures))
            } catch (e: InterruptedException) {
                return
            }
        }
    }

    /** A new token for each session: the stored screen ID is sufficient to connect again, also after the token expires. */
    private fun connect(): Link {
        val token = LoungeApi.loungeToken(screen.screenId)
        val rid = LoungeApi.newRid()
        return Link(token, LoungeApi.bind(screen, token, rid), rid + 1)
    }

    /**
     * Reads one long-poll reply to its end. The server ends a reply after some time: the caller then opens the next.
     * Throws when the server refuses the poll or sends no chunk, so that the caller opens a new session.
     */
    private fun readPoll(l: Link) {
        val stream = LoungeApi.openPoll(screen, l.token, l.channel, l.aid)
        poll = stream
        try {
            if (!running) return
            val body = stream.body ?: throw LoungeHttpException("poll", stream.code)
            val reader = body.bufferedReader(Charsets.UTF_8)
            var chunks = 0
            while (running) {
                val messages = LoungeChunks.next(reader) ?: break
                chunks++
                messages.forEach { handle(l, it) }
            }
            if (running && chunks == 0) throw IOException("empty poll reply")
        } finally {
            poll = null
            stream.close()
        }
    }

    private fun handle(l: Link, m: LoungeMessage) {
        if (m.id > l.aid) l.aid = m.id
        val command = CastCommand.from(m) ?: return
        Log.i(TAG, "command ${command.javaClass.simpleName}")
        if (running) listener.onCommand(command)
    }

    private fun post(events: List<LoungeEvent>) {
        val l = link ?: return
        try {
            val code = LoungeApi.send(screen, l.token, l.channel, l.rid++, l.aid, l.ofs, events)
            l.ofs += events.size
            if (code !in 200..299) Log.w(TAG, "send HTTP $code")
        } catch (e: Exception) {
            Log.w(TAG, "send failed (${describe(e)})")
        }
    }

    /** The class name, and the status of a Lounge HTTP error. Not the message: a platform message can contain a URL. */
    private fun describe(e: Exception) =
        if (e is LoungeHttpException) "${e.what} HTTP ${e.code}" else e.javaClass.simpleName

    private fun backoffMs(failures: Int): Long =
        if (failures <= 0) 0 else (2_000L shl (failures - 1).coerceAtMost(5)).coerceAtMost(60_000L)

    private companion object {
        const val TAG = "Lounge"
    }
}
