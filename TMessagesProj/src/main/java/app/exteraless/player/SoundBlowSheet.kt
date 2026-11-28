package app.exteraless.player

import android.content.Context
import android.net.Uri
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.view.Gravity
import android.text.TextUtils
import android.widget.LinearLayout
import android.view.View
import androidx.media3.common.Player
import com.xvare.soundblow.player.SoundBlowEngine
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
        val foreground = Theme.getColor(Theme.key_player_actionBarTitle, resources)
        val accent = Theme.getColor(Theme.key_player_buttonActive, resources)
        val title = TextView(context).apply {
            textSize = 22f
            setTextColor(foreground)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val artist = TextView(context).apply {
            textSize = 15f
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resources))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val seek = SeekBar(context).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            thumbTintList = progressTintList
        }
        var seeking = false
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar) { seeking = true }
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) {
                if (player.duration > 0L) engine.seek(player.duration * bar.progress / bar.max)
                seeking = false
            }
        })
        val controls = LinearLayout(context).apply { gravity = Gravity.CENTER }
        fun control(path: String, label: Int, size: Int, action: () -> Unit): ImageButton {
            return ImageButton(context).apply {
                setImageDrawable(PlayerIcon.stroke(path, 24))
                setColorFilter(foreground)
                contentDescription = org.telegram.messenger.LocaleController.getString(label)
                setBackground(Theme.createSelectorDrawable(Theme.multAlpha(accent, 0.2f)))
                setOnClickListener { action() }
                controls.addView(this, LinearLayout.LayoutParams(dp(size.toFloat()), dp(size.toFloat())))
            }
        }
        val previous = control(PlayerIcon.PREV, org.telegram.messenger.R.string.AccDescrPrevious, 56) { engine.previous() }
        val play = control(PlayerIcon.PLAY, org.telegram.messenger.R.string.AccActionPlay, 64) { engine.toggle() }
        val next = control(PlayerIcon.NEXT, org.telegram.messenger.R.string.Next, 56) { engine.next() }
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
                val duration = player.duration
                seek.isEnabled = duration > 0L
                if (!seeking) seek.progress = if (duration > 0L) {
                    (player.currentPosition * seek.max / duration).toInt().coerceIn(0, seek.max)
                } else 0
                lyrics.postDelayed(this, 100)
            }
        }
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                title.text = player.mediaMetadata.title
                artist.text = player.mediaMetadata.artist
                previous.isEnabled = player.hasPreviousMediaItem()
                next.isEnabled = player.hasNextMediaItem()
                play.setImageDrawable(PlayerIcon.stroke(if (player.isPlaying) PlayerIcon.PAUSE else PlayerIcon.PLAY, 28))
                play.contentDescription = org.telegram.messenger.LocaleController.getString(
                    if (player.isPlaying) org.telegram.messenger.R.string.AccActionPause else org.telegram.messenger.R.string.AccActionPlay
                )
            }
            override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) {
                ++request
                sourceOptions = emptyList()
                sourceButton.visibility = View.GONE
                lyrics.showState(LyricsView.STATE_OFFER)
            }
        })
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24f), dp(20f), dp(24f), dp(12f))
            setBackgroundColor(Theme.getColor(Theme.key_player_background, resources))
            addView(title)
            addView(artist)
            addView(seek)
            addView(controls)
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
        lyrics.showState(LyricsView.STATE_OFFER)
        lyrics.post(tick)
        if (wasPlaying) engine.play()
        return sheet
    }
}
