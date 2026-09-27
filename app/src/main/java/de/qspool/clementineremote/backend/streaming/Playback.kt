package de.qspool.clementineremote.backend.streaming

/**
 * The audio player a [Renderer] drives: one item playing, and optionally the next one queued to
 * follow it without a gap. Called on the main thread, and calls its [Listener] there.
 */
interface Playback {

    enum class State { IDLE, BUFFERING, PLAYING, PAUSED }

    interface Listener {
        /** [state] changed. */
        fun onStateChanged()

        /** The playing item finished, and the queued one started. */
        fun onAdvanced()

        /** The playing item finished, with nothing queued. */
        fun onEnded()

        /**
         * The playing item failed.
         *
         * @param transient a network problem, worth reloading, rather than an item this can't play
         */
        fun onError(message: String, transient: Boolean)
    }

    var listener: Listener?

    val state: State

    /** Within the url playing. */
    val positionMs: Long

    val bufferedPercent: Int

    /** Replaces what's playing and queued with [url], from [startMs] within it. */
    fun load(url: String, startMs: Long, playing: Boolean)

    /** Queues [url] to follow what's playing, replacing anything queued; null clears the queue. */
    fun queue(url: String?)

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    /** From 0 to 1. */
    fun setVolume(volume: Float)

    fun stop()

    fun release()
}
