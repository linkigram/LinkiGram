package com.xvare.soundblow.player

import android.content.Context
import android.media.audiofx.Equalizer
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/** Независимый от аккаунта движок; все методы вызываются на главном потоке. */
data class Track(val uri: String, val title: String, val artist: String = "", val artworkUri: String? = null)
@UnstableApi
class SoundBlowEngine(context: Context) {
    private val appContext = context.applicationContext
    private val attributes = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()
    private val crossfade = CrossfadePlayer(appContext, { ExoPlayer.Builder(appContext).build() }, attributes)
    val player: Player = crossfade.player
    private var equalizer: Equalizer? = null
    fun setQueue(tracks: List<Track>, index: Int = 0) {
        require(tracks.isNotEmpty() && index in tracks.indices)
        val items = tracks.map { t -> MediaItem.Builder().setUri(t.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).apply { t.artworkUri?.let { setArtworkUri(Uri.parse(it)) } }.build()).build() }
        player.setMediaItems(items, index, 0); player.prepare()
    }
    fun add(track: Track) { player.addMediaItem(MediaItem.Builder().setUri(track.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).build()).build()) }
    fun play() = player.play()
    fun pause() = player.pause()
    fun toggle() { if (player.isPlaying) pause() else play() }
    fun next() = player.seekToNextMediaItem()
    fun previous() = player.seekToPreviousMediaItem()
    fun seek(positionMs: Long) = player.seekTo(positionMs.coerceAtLeast(0))
    fun setVolume(volume: Float) { player.volume = volume.coerceIn(0f, 1f) }
    fun setSpeed(speed: Float) = player.setPlaybackSpeed(speed.coerceIn(0.5f, 2.5f))
    fun setShuffle(enabled: Boolean) { player.shuffleModeEnabled = enabled }
    fun setRepeat(mode: Int) { require(mode in Player.REPEAT_MODE_OFF..Player.REPEAT_MODE_ALL); player.repeatMode = mode }
    fun setCrossfade(seconds: Int) { crossfade.crossfadeSeconds = seconds }
    /** Android EQ support and available bands vary by device; caller supplies active audio session ID. */
    fun attachEqualizer(audioSessionId: Int): Short { equalizer?.release(); equalizer = Equalizer(0, audioSessionId).apply { enabled = true }; return equalizer!!.numberOfBands }
    fun setEqualizerBand(band: Short, levelMillibels: Short) { val eq = requireNotNull(equalizer); require(band >= 0 && band < eq.numberOfBands); val bounds = eq.bandLevelRange; eq.setBandLevel(band, levelMillibels.coerceIn(bounds[0], bounds[1])) }
    fun release() { equalizer?.release(); equalizer = null; player.release() }
}
