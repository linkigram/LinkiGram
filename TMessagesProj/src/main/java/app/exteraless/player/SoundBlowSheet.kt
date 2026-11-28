package app.exteraless.player

import android.content.Context
import android.net.Uri
import android.widget.Button
import android.widget.LinearLayout
import android.view.View
import androidx.media3.common.Player
import com.xvare.soundblow.player.SoundBlowEngine
import com.xvare.soundblow.player.SoundBlowPlayerView
import com.xvare.soundblow.player.Track
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.FileLoader
import org.telegram.messenger.MediaController
import org.telegram.messenger.MessageObject
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import java.io.File

/** Runs the alternate player while its sheet is open, using fully downloaded Telegram tracks. */
object SoundBlowSheet {
    @JvmStatic
    fun create(context: Context, resources: Theme.ResourcesProvider?, current: MessageObject): BottomSheet? {
        val media = MediaController.getInstance()
        val queue = media.playlist.toMutableList()
        if (queue.none { it === current }) queue.add(0, current)
        val playable = queue.mapNotNull { message ->
            if (!message.isMusic || message.currentAccount != current.currentAccount) return@mapNotNull null
            val file = message.messageOwner.attachPath?.takeIf { it.isNotEmpty() }
                ?.let(::File)?.takeIf { it.isFile }
                ?: FileLoader.getInstance(message.currentAccount).getPathToMessage(message.messageOwner)
            if (!file.isFile || file.length() == 0L) return@mapNotNull null
            message to Track(Uri.fromFile(file).toString(), message.musicTitle, message.musicAuthor, null)
        }
        val index = playable.indexOfFirst { it.first === current }
        if (index < 0) return null

        val sheet = BottomSheet(context, false, resources)
        val engine = SoundBlowEngine(context)
        val player = engine.player
        val wasPlaying = !media.isMessagePaused
        val view = SoundBlowPlayerView(context)
        val lyrics = LyricsView(context)
        lyrics.setColors(PlayerColors.fromSeed(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), Theme.isCurrentThemeDark()))
        var closed = false
        var request = 0
        var sourceOptions = emptyList<Lyrics>()
        fun showLyrics(source: Lyrics) {
            if (source.instrumental) lyrics.showState(LyricsView.STATE_INSTRUMENTAL)
            else lyrics.setLyrics(source, player.currentPosition)
        }
        val sourceButton = Button(context).apply {
            visibility = View.GONE
            setOnClickListener {
                val options = sourceOptions
                val generation = request
                val labels = Array<CharSequence>(options.size) { options[it].provider }
                AlertDialog.Builder(context, resources).setItems(labels) { _, which ->
                    if (!closed && request == generation) {
                        val selected = options[which]
                        text = selected.provider
                        showLyrics(selected)
                    }
                }.show()
            }
        }
        fun loadLyrics() {
            val message = playable.getOrNull(player.currentMediaItemIndex)?.first ?: return
            val generation = ++request
            lyrics.showState(LyricsView.STATE_LOADING)
            OnlineLyrics.fetch(
                OnlineLyrics.Query(message.getMusicAuthor(false), message.getMusicTitle(false), null, message.duration.toInt())
            ) { sources, status ->
                if (!closed && generation == request) {
                    sourceOptions = sources.orEmpty()
                    val result = sourceOptions.firstOrNull()
                    sourceButton.text = result?.provider
                    sourceButton.visibility = if (sourceOptions.size > 1) View.VISIBLE else View.GONE
                    when {
                        result != null -> showLyrics(result)
                        status == OnlineLyrics.ERROR -> lyrics.showState(LyricsView.STATE_ERROR)
                        else -> lyrics.showState(LyricsView.STATE_NOT_FOUND)
                    }
                }
            }
        }
        lyrics.setDelegate(object : LyricsView.Delegate {
            override fun onAction(state: Int) = loadLyrics()
            override fun onSeek(ms: Long) = engine.seek(ms)
        })
        val tick = object : Runnable {
            override fun run() {
                if (closed) return
                lyrics.setPosition(player.currentPosition)
                lyrics.postDelayed(this, 100)
            }
        }
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) {
                ++request
                sourceOptions = emptyList()
                sourceButton.visibility = View.GONE
                lyrics.showState(LyricsView.STATE_OFFER)
            }
        })
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(view)
            addView(sourceButton)
            addView(lyrics, LinearLayout.LayoutParams(-1, dp(230f)))
        }
        sheet.setCustomView(body)
        sheet.setOnDismissListener(Runnable {
            if (!closed) {
                closed = true
                lyrics.removeCallbacks(tick)
                val position = player.currentPosition
                val selected = playable.getOrNull(player.currentMediaItemIndex)?.first
                val resume = player.playWhenReady
                engine.release()
                if (media.playingMessageObject === current && media.isMessagePaused) {
                    if (selected === current) {
                        media.seekToProgressMs(current, position)
                        if (resume) media.playMessage(current)
                    } else if (resume && selected != null && media.playMessage(selected)) {
                        media.seekToProgressMs(selected, position)
                    }
                }
            }
        })

        if (wasPlaying && !media.pauseMessage(current)) {
            engine.release()
            return null
        }
        engine.setQueue(playable.map { it.second }, index)
        engine.seek(current.audioProgressMs.toLong())
        view.bind(engine)
        lyrics.showState(LyricsView.STATE_OFFER)
        lyrics.post(tick)
        if (wasPlaying) engine.play()
        return sheet
    }
}
