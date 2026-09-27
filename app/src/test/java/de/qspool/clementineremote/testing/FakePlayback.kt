package de.qspool.clementineremote.testing

import de.qspool.clementineremote.backend.streaming.Playback

/** Plays nothing: records what it's asked, and moves when the test says. */
class FakePlayback : Playback {
    override var listener: Playback.Listener? = null
    override var state = Playback.State.IDLE
    override var positionMs = 0L
    override var bufferedPercent = 0
    var url: String? = null
    var queued: String? = null
    var level = 1f

    override fun load(url: String, startMs: Long, playing: Boolean) {
        this.url = url
        queued = null
        positionMs = startMs
        state = Playback.State.BUFFERING
    }

    override fun queue(url: String?) {
        queued = url
    }

    override fun play() = become(Playback.State.PLAYING)

    override fun pause() = become(Playback.State.PAUSED)

    override fun seekTo(positionMs: Long) {
        this.positionMs = positionMs
    }

    override fun setVolume(volume: Float) {
        level = volume
    }

    override fun stop() {
        url = null
        queued = null
        state = Playback.State.IDLE
    }

    override fun release() = stop()

    fun become(state: Playback.State) {
        this.state = state
        listener?.onStateChanged()
    }

    /** The playing item ends: the queued one follows, or playback stops. */
    fun finish() {
        if (queued != null) {
            url = queued
            queued = null
            positionMs = 0
            listener?.onAdvanced()
        } else {
            state = Playback.State.IDLE
            listener?.onEnded()
            listener?.onStateChanged()
        }
    }
}
