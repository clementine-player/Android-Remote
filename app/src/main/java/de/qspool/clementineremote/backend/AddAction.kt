package de.qspool.clementineremote.backend

import de.qspool.clementineremote.backend.pb.ClementineMessage
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.RequestInsertUrls

/** What's done with songs put on a playlist, besides adding them at its end. */
enum class AddAction {
    /** Nothing more. */
    APPEND,

    /**
     * Clementine plays the first of them unless it's playing already, as it does when you
     * double-click a song in it.
     */
    PLAY_IF_STOPPED,

    /** Clementine plays the first of them. */
    PLAY_NOW,

    /** They're queued to play after anything queued already. */
    QUEUE,

    /**
     * They're queued to play straight after the current song, in front of anything queued
     * already. Needs a Clementine that can ([RemoteRepository.canEnqueueNext]); others just add
     * them.
     */
    PLAY_NEXT,

    /** The playlist is emptied first, then Clementine plays the first of them. */
    REPLACE,

    /** They go on a new playlist instead, which Clementine creates. */
    NEW_PLAYLIST;

    /**
     * The INSERT_URLS that puts songs on playlist [playlistId], doing this. [isPlaying] says
     * whether Clementine is playing; [newPlaylistName] names the playlist [NEW_PLAYLIST] creates.
     * [songs] puts the songs, by URL or in full, in the request.
     */
    fun insert(
        playlistId: Int,
        isPlaying: Boolean,
        newPlaylistName: String,
        songs: RequestInsertUrls.Builder.() -> Unit,
    ): ClementineMessage {
        val request = RequestInsertUrls.newBuilder()
            .setPlaylistId(playlistId)
            .setPlayNow(
                when (this) {
                    PLAY_NOW, REPLACE -> true
                    PLAY_IF_STOPPED -> !isPlaying
                    APPEND, QUEUE, PLAY_NEXT, NEW_PLAYLIST -> false
                },
            )
            .setEnqueue(this == QUEUE)
            .setEnqueueNext(this == PLAY_NEXT)
            .apply(songs)
        if (this == NEW_PLAYLIST) {
            request.setNewPlaylistName(newPlaylistName)
        }
        return ClementineMessage(ClementineMessage.getMessageBuilder(MsgType.INSERT_URLS).setRequestInsertUrls(request))
    }
}
