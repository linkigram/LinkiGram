package com.xvare.soundblow.player

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.FlagSet
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Один стабильный [Player] для медиасессии поверх двух ExoPlayer-дек (одна активная, другая запасная).
 *
 * Всё, кроме перехода между треками, — прямой проброс в активную деку, поэтому при выключенном перекрытии
 * поведение совпадает с обычным ExoPlayer. При включённом: запасная дека заранее получает ту же очередь (и тот же порядок
 * перемешивания) и готовит естественного преемника; у конца трека он стартует тихо, обе деки плавно меняют громкость
 * ([CrossfadeLogic]), и в конце запасная дека без перемотки и повторной подготовки становится активной — сессия видит один
 * автопереход с уже ненулевой позицией.
 *
 * Фокус и «наушники выдернули» держит только активная дека. Любое ручное действие (перемотка, переключение, правка очереди,
 * пауза, смена режимов), потеря фокуса, ошибка и буферизация отменяют перекрытие: уходящий трек доигрывает штатно.
 *
 * Ограничение: пока идёт перекрытие, эквалайзер зависит от аудиосессии устройства и может оставаться от уходящего трека,
 * после перехода подтягиваются настройки нового. Скорость/тональность у каждой деки свои, поэтому у нового трека они верны сразу.
 */
@UnstableApi
class CrossfadePlayer(
    context: Context,
    /** Строит деку без фокуса и без реакции на наушники; обе должны быть на главном потоке. */
    newDeck: () -> ExoPlayer,
    audioAttributes: AudioAttributes,
) {
    private companion object {
        const val DUCK_GAIN = 0.2f
        const val TAG = "XMCrossfade"
    }

    private fun log(msg: String) { }

    var crossfadeSeconds: Int = 0
        set(value) { field = value.coerceIn(0, 12) }

    private enum class Phase { IDLE, ARMED, FADING }

    private val handler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<Player.Listener>()
    private var audioAttributes = audioAttributes
    private var callerHandlesFocus = true
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var active: ExoPlayer = newDeck()
    private var standby: ExoPlayer = newDeck()
    private var phase = Phase.IDLE
    private var released = false
    private var userVolume = 1f
    private var surfaceCall: Pair<Method, Array<Any?>>? = null
    private var latched: Pair<Int, MediaItem?>? = null

    // состояние перекрытия
    private var audible = false
    private var startedAt = 0L
    private var fadeStartOutPos = 0L
    private var lastInPos = -1L
    private var lastInAdvanceAt = 0L
    private var lastOut: Player.PositionInfo? = null
    private var fadeSteps = 0
    private var lastOutGain = 1f
    private var lastInGain = 0f

    // Фокус. Пока перекрытие включено, им владеет фасад (а не активная дека): иначе «приглушение» от другого приложения
    // доходило бы только до одной деки и звук второй играл бы на полной громкости. Выключено — штатный фокус ExoPlayer.
    private var ownFocus = false
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false
    private var duck = 1f
    private var resumeOnGain = false
    private var expectedFocusPause = false
    private val focusListener = AudioManager.OnAudioFocusChangeListener { onFocusChange(it) }

    /** Стабильный плеер для сессии, виджетов и т.п. */
    val player: Player

    /** «Пауза на границе треков» нужна сервису для смены скорости; относится к активной деке. */
    var pauseAtEndOfMediaItems: Boolean
        get() = active.pauseAtEndOfMediaItems
        set(v) { if (active.pauseAtEndOfMediaItems != v) active.pauseAtEndOfMediaItems = v }

    init {
        active.setAudioAttributes(audioAttributes, true)
        active.setHandleAudioBecomingNoisy(true)
        standby.setAudioAttributes(audioAttributes, false)
        standby.setHandleAudioBecomingNoisy(false)
        for (d in listOf(active, standby)) d.addListener(forwarder(d))
        player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { proxy, m, a ->
            try { handleCall(proxy as Player, m, a ?: emptyArray()) } catch (e: InvocationTargetException) { throw e.targetException }
        } as Player
    }

    // ───────── проброс вызовов ─────────
    private fun handleCall(self: Player, m: Method, args: Array<Any?>): Any? {
        if (ProxyObjectMethods.isObjectMethod(m)) return ProxyObjectMethods.invoke(self, m, args, "CrossfadePlayer")
        when (m.name) {
            "addListener" -> { listeners.add(args[0] as Player.Listener); return null }
            "removeListener" -> { listeners.remove(args[0] as Player.Listener); return null }
            "release" -> { release(); return null }
            "getVolume" -> return userVolume
            "setVolume" -> {
                val v = (args[0] as Float).coerceIn(0f, 1f)
                if (v != userVolume) {
                    userVolume = v
                    applyGains(lastOutGain, lastInGain)
                    emit { onVolumeChanged(v) }
                    emit { onEvents(player, Player.Events(FlagSet.Builder().add(Player.EVENT_VOLUME_CHANGED).build())) }
                }
                return null
            }
            "setAudioAttributes" -> {
                audioAttributes = args[0] as AudioAttributes
                callerHandlesFocus = args[1] as Boolean
                standby.setAudioAttributes(audioAttributes, false)
                if (!callerHandlesFocus && ownFocus) setOwnFocus(false, force = true)
                active.setAudioAttributes(audioAttributes, callerHandlesFocus && !ownFocus)
                return null
            }
            "setPlaybackParameters", "setPlaybackSpeed" -> {
                val r = m.invoke(active, *args)
                if (phase != Phase.IDLE && true) m.invoke(standby, *args)
                return r
            }
            "setTrackSelectionParameters" -> {
                val r = m.invoke(active, *args)
                if (phase != Phase.IDLE) m.invoke(standby, *args)
                return r
            }
        }
        if (m.name.startsWith("setVideo")) surfaceCall = m to args
        else if (m.name.startsWith("clearVideo")) surfaceCall = null
        if (phase != Phase.IDLE && CrossfadeLogic.callCancelsOverlap(m.name, args)) cancel(latch = phase == Phase.FADING)
        if (CrossfadeLogic.callClearsLatch(m.name)) latched = null
        return m.invoke(active, *args)
    }

    private fun applyGains(outGain: Float, inGain: Float) {
        lastOutGain = outGain
        lastInGain = inGain
        active.volume = userVolume * duck * outGain
        if (phase == Phase.FADING) standby.volume = userVolume * duck * inGain
    }

    private fun resetGains() = applyGains(1f, 0f)

    // ───────── события ─────────
    private fun forwarder(deck: ExoPlayer): Player.Listener =
        Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player.Listener::class.java)) { self, m, a ->
            if (ProxyObjectMethods.isObjectMethod(m)) return@newProxyInstance ProxyObjectMethods.invoke(self, m, a, "CrossfadePlayer.Forwarder")
            val args = a ?: emptyArray()
            if (deck === active && !released && onActiveEvent(m.name, args) != null) {
                // громкость колеблется от наших плавных изменений: наружу её сообщает только setVolume фасада
                if (m.name == "onVolumeChanged") return@newProxyInstance null
                if (m.name == "onEvents") {
                    val ev = args[1] as Player.Events
                    if (!ev.contains(Player.EVENT_VOLUME_CHANGED)) dispatch(m, args, deck)
                    else {
                        val b = FlagSet.Builder()
                        for (i in 0 until ev.size()) if (ev.get(i) != Player.EVENT_VOLUME_CHANGED) b.add(ev.get(i))
                        val rest = b.build()
                        if (rest.size() > 0) dispatch(m, arrayOf(args[0], Player.Events(rest)), deck)
                    }
                } else dispatch(m, args, deck)
            }
            null
        } as Player.Listener

    /** Реакция на событие активной деки; null — событие поглощено (его заменит синтетический переход). */
    private fun onActiveEvent(name: String, args: Array<Any?>): Unit? {
        if (ownFocus) focusEvent(name, args)
        if (phase == Phase.FADING) {
            when (name) {
                "onPositionDiscontinuity" -> if (args.size == 3) {
                    if (args[2] == Player.DISCONTINUITY_REASON_AUTO_TRANSITION && audible) { commit(); return null }
                    cancel(latch = true)
                }
                "onPlaybackStateChanged" -> {
                    val s = args[0] as Int
                    if (s == Player.STATE_ENDED && audible) { commit(); return null }
                    if (CrossfadeLogic.playbackStateCancels(s)) cancel(latch = true)
                }
                "onPlayWhenReadyChanged" -> if (CrossfadeLogic.playWhenReadyCancels(args[0] as Boolean)) cancel(latch = true)
                "onPlayerError", "onPlayerErrorChanged" -> if (args[0] != null) cancel(latch = true)
            }
        }
        when (name) {
            "onTimelineChanged" -> if (phase != Phase.IDLE && args[1] == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) cancel(latch = phase == Phase.FADING)
            "onRepeatModeChanged", "onShuffleModeEnabledChanged" -> if (phase != Phase.IDLE) cancel(latch = phase == Phase.FADING)
            "onPlayWhenReadyChanged" -> if (phase == Phase.ARMED && !(args[0] as Boolean)) cancel(latch = false)
            "onMediaItemTransition", "onIsPlayingChanged" -> if (phase != Phase.FADING) kick()
        }
        return Unit
    }

    private fun dispatch(m: Method, args: Array<Any?>, from: Player) {
        val out = Array(args.size) { if (args[it] === from) player else args[it] }
        for (l in listeners) try { m.invoke(l, *out) } catch (e: InvocationTargetException) { throw e.targetException }
    }

    private inline fun emit(block: Player.Listener.() -> Unit) { for (l in listeners) l.block() }

    // ───────── планировщик ─────────
    private val tick = Runnable { onTick() }

    private fun kick() { handler.removeCallbacks(tick); handler.post(tick) }

    private fun onTick() {
        if (released) return
        log("tick phase=$phase pos=${active.currentPosition} dur=${active.duration} st=${active.playbackState} s=${crossfadeSeconds}")
        val a = active
        val delay = if (phase == Phase.FADING) stepFade(a) else evaluate(a)
        if (delay > 0 && !released) handler.postDelayed(tick, delay)
    }

    private fun resetOverlapState() { audible = false; lastInPos = -1; lastOut = null }

    /** Возвращает задержку до следующего тика; 0 — тики не нужны до следующего события. */
    private fun evaluate(a: ExoPlayer): Long {
        val seconds = crossfadeSeconds
        if (phase == Phase.IDLE) setOwnFocus(seconds > 0 && callerHandlesFocus)
        if (seconds <= 0) { if (phase != Phase.IDLE) cancel(latch = false); return if (a.isPlaying) 1000 else 0 }
        if (!a.playWhenReady || a.playbackState != Player.STATE_READY) { if (phase != Phase.IDLE) cancel(latch = false); return if (a.isPlaying) 500 else 0 }
        val tl = a.currentTimeline
        val cur = a.currentMediaItemIndex
        val key = cur to a.currentMediaItem
        if (latched != null && latched != key) latched = null
        if (latched != null) return 1000
        if (tl.isEmpty) return 1000
        val next = tl.getNextWindowIndex(cur, a.repeatMode, a.shuffleModeEnabled)
        val dur = a.duration
        val ctx = CrossfadeLogic.Context(
            seconds, a.repeatMode, isVideo(a.currentMediaItem), next != C.INDEX_UNSET && isVideo(a.getMediaItemAt(next)),
            next, cur, dur,
        )
        if (!CrossfadeLogic.eligible(ctx)) { log("not eligible dur=$dur next=$next"); if (phase != Phase.IDLE) cancel(latch = false); return 1000 }
        val fade = CrossfadeLogic.fadeMs(seconds, dur)
        val remaining = dur - a.currentPosition
        if (!CrossfadeLogic.shouldPrepare(remaining, fade)) {
            if (phase != Phase.IDLE) cancel(latch = false)
            return (remaining - fade - CrossfadeLogic.PRELOAD_LEAD_MS).coerceIn(100, 1000)
        }
        if (phase == Phase.IDLE && !arm(a, tl, next)) { latched = key; return 1000 }
        if (standby.playbackState == Player.STATE_IDLE && phase == Phase.ARMED) return 200
        if (remaining <= CrossfadeLogic.MIN_LATE_START_MS) { cancel(latch = true); return 1000 }
        if (CrossfadeLogic.shouldStart(remaining, fade, standby.playbackState == Player.STATE_READY)) { startFade(a); return 40 }
        return 100
    }

    /**
     * Готовит запасную деку. Она хранит зеркало очереди: после перехода (и после отмены) очередь в ней остаётся
     * (только stop, без очистки), поэтому в штатном ходе дека лишь перематывается на преемника и готовится, а
     * createMediaSource на всю очередь повторяется только после правки очереди/порядка. Очередь длиннее
     * [CrossfadeLogic.MAX_MIRRORED_QUEUE] не зеркалим — для неё остаётся штатный переход.
     * После повышения активная дека сразу имеет полную очередь, порядок и режимы без всяких перестроений.
     */
    private fun arm(a: ExoPlayer, tl: Timeline, next: Int): Boolean {
        val b = standby
        val order = shuffleOrderOf(tl)
        val same = b.mediaItemCount == a.mediaItemCount && (0 until a.mediaItemCount).all { b.getMediaItemAt(it) == a.getMediaItemAt(it) } &&
            orderOf(b.currentTimeline).contentEquals(order)
        if (!same && !CrossfadeLogic.mirrorAllowed(a.mediaItemCount)) return false
        b.playWhenReady = false
        b.volume = 0f
        b.pauseAtEndOfMediaItems = false
        b.repeatMode = a.repeatMode
        b.shuffleModeEnabled = a.shuffleModeEnabled
        if (same) b.seekTo(next, 0)
        else {
            b.setMediaItems(List(a.mediaItemCount) { a.getMediaItemAt(it) }, next, 0)
            b.setShuffleOrder(DefaultShuffleOrder(order, 0L))
        }
        b.trackSelectionParameters = a.trackSelectionParameters
        b.playbackParameters = paramsFor(a, a.getMediaItemAt(next))
        b.prepare()
        phase = Phase.ARMED
        log("armed next=$next same=$same")
        return true
    }

    /** Порядок перемешивания деки: список окон от первого к последнему при «вперемешку». */
    private fun orderOf(tl: Timeline): IntArray {
        val order = IntArray(tl.windowCount)
        var i = tl.getFirstWindowIndex(true)
        var n = 0
        while (i != C.INDEX_UNSET && n < order.size) { order[n++] = i; i = tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, true) }
        return if (n == order.size) order else order.copyOf(n)
    }

    private fun shuffleOrderOf(tl: Timeline) = orderOf(tl)

    private fun paramsFor(a: ExoPlayer, item: MediaItem): PlaybackParameters = a.playbackParameters

    private fun isVideo(item: MediaItem?) = item?.mediaMetadata?.extras?.getBoolean("video") == true

    private fun startFade(a: ExoPlayer) {
        log("startFade outPos=${a.currentPosition}")
        phase = Phase.FADING
        audible = false
        startedAt = SystemClock.elapsedRealtime()
        lastInPos = -1
        standby.volume = 0f
        standby.playWhenReady = true // дека без фокуса: просто играет тихо; громкость уходящего пока не трогаем
        recordOut(a)
    }

    private fun recordOut(a: ExoPlayer) { lastOut = info(a, a.currentPosition) }

    /** Один шаг перекрытия; возвращает задержку до следующего. */
    private fun stepFade(a: ExoPlayer): Long {
        val b = standby
        val now = SystemClock.elapsedRealtime()
        val outPos = a.currentPosition
        val dur = a.duration
        recordOut(a)
        if (!a.isPlaying) { cancel(latch = true); return 0 }
        val inPos = b.currentPosition
        if (inPos != lastInPos) { lastInPos = inPos; lastInAdvanceAt = now }
        val advancing = b.isPlaying && b.playbackState == Player.STATE_READY && inPos > 0
        if (!audible) {
            if (advancing) { audible = true; fadeStartOutPos = outPos }
            else if (now - startedAt > 1500 || dur - outPos <= CrossfadeLogic.MIN_LATE_START_MS) { cancel(latch = true); return 0 }
            else return 40
        } else if (!advancing && now - lastInAdvanceAt > 300) { cancel(latch = true); return 0 }
        val p = CrossfadeLogic.progress(outPos, fadeStartOutPos, dur)
        if (CrossfadeLogic.shouldCommit(p)) { commit(); return 100 }
        applyGains(CrossfadeLogic.outgoingGain(p), CrossfadeLogic.incomingGain(p))
        if (false && (++fadeSteps % 10 == 0)) log("fade p=${"%.2f".format(p)} outVol=${a.volume} inVol=${b.volume} outPos=$outPos inPos=$inPos outPlaying=${a.isPlaying} inPlaying=${b.isPlaying}")
        return 40
    }

    // ───────── завершение / отмена ─────────
    /** Запасная дека, уже играющая, становится активной: без перемотки, подготовки и провала. */
    private fun commit() {
        log("commit inPos=${standby.currentPosition} outPos=${active.currentPosition}")
        val old = active
        val nw = standby
        val oldInfo = lastOut ?: info(old, old.currentPosition)
        active = nw
        standby = old
        phase = Phase.IDLE
        resetOverlapState()
        latched = null
        // «наушники» (и штатный фокус, если фасад им не владеет) переезжают вместе с ролью; новая дека берёт фокус раньше, чем старая отпускает
        nw.setAudioAttributes(audioAttributes, callerHandlesFocus && !ownFocus)
        nw.setHandleAudioBecomingNoisy(true)
        old.playWhenReady = false
        old.setAudioAttributes(audioAttributes, false)
        old.setHandleAudioBecomingNoisy(false)
        old.stop() // очередь в деке остаётся как зеркало для следующего перехода
        lastOutGain = 1f
        lastInGain = 0f
        nw.volume = userVolume * duck
        nw.pauseAtEndOfMediaItems = false
        surfaceCall?.let { (m, args) -> runCatching { old.clearVideoSurface(); m.invoke(nw, *args) } }
        val newInfo = info(nw, nw.currentPosition)
        val paramsChanged = old.playbackParameters != nw.playbackParameters
        // обе деки держат одну очередь и один порядок, но если зеркало разошлось (не должно), сообщаем и о новой временной шкале
        val timelineChanged = !sameQueue(old.currentTimeline, nw.currentTimeline)
        val fb = FlagSet.Builder()
            .addAll(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_TRACKS_CHANGED)
        if (paramsChanged) fb.add(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)
        if (timelineChanged) fb.add(Player.EVENT_TIMELINE_CHANGED)
        val flags = Player.Events(fb.build())
        emit {
            if (timelineChanged) onTimelineChanged(nw.currentTimeline, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
            if (paramsChanged) onPlaybackParametersChanged(nw.playbackParameters)
            onPositionDiscontinuity(oldInfo, newInfo, Player.DISCONTINUITY_REASON_AUTO_TRANSITION)
            onMediaItemTransition(nw.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
            onMediaMetadataChanged(nw.mediaMetadata)
            onTracksChanged(nw.currentTracks)
            onEvents(player, flags)
        }
        kick()
    }

    /** Прекращает перекрытие: уходящий трек остаётся как есть и доигрывает штатно. [latch] — не пытаться заново на этом же треке. */
    private fun cancel(latch: Boolean) {
        log("cancel latch=$latch phase=$phase")
        val wasFading = phase == Phase.FADING
        val a = active
        phase = Phase.IDLE
        resetOverlapState()
        if (latch) latched = a.currentMediaItemIndex to a.currentMediaItem
        standby.playWhenReady = false
        standby.volume = 0f
        standby.stop() // очередь остаётся как зеркало
        if (wasFading) resetGains()
    }

    private fun sameQueue(x: Timeline, y: Timeline): Boolean {
        if (x.windowCount != y.windowCount) return false
        val wx = Timeline.Window()
        val wy = Timeline.Window()
        for (i in 0 until x.windowCount) if (x.getWindow(i, wx).mediaItem != y.getWindow(i, wy).mediaItem) return false
        return orderOf(x).contentEquals(orderOf(y))
    }

    // ───────── фокус (пока включено перекрытие) ─────────
    private fun setOwnFocus(on: Boolean, force: Boolean = false) {
        if (on == ownFocus || (phase != Phase.IDLE && !force)) return
        ownFocus = on
        if (on) {
            active.setAudioAttributes(audioAttributes, false)
            if (active.playWhenReady && active.playbackState != Player.STATE_IDLE) requestFocus()
        } else {
            abandonFocus()
            duck = 1f
            resumeOnGain = false
            applyGains(lastOutGain, lastInGain)
            active.setAudioAttributes(audioAttributes, callerHandlesFocus)
        }
    }

    @Suppress("DEPRECATION")
    private fun requestFocus() {
        if (hasFocus) return
        val r = if (Build.VERSION.SDK_INT >= 26) {
            val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes.audioAttributesV21.audioAttributes)
                .setOnAudioFocusChangeListener(focusListener, handler).build().also { focusRequest = it }
            audioManager.requestAudioFocus(req)
        } else audioManager.requestAudioFocus(focusListener, android.media.AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        hasFocus = r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!hasFocus && active.playWhenReady) { expectedFocusPause = true; active.playWhenReady = false }
    }

    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        if (!hasFocus) return
        hasFocus = false
        if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { audioManager.abandonAudioFocusRequest(it) } else audioManager.abandonAudioFocus(focusListener)
    }

    private fun onFocusChange(change: Int) {
        if (released || !ownFocus) return
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                duck = 1f
                applyGains(lastOutGain, lastInGain)
                if (resumeOnGain) { resumeOnGain = false; active.playWhenReady = true }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { duck = DUCK_GAIN; applyGains(lastOutGain, lastInGain) }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pauseForFocus(resume = true)
            AudioManager.AUDIOFOCUS_LOSS -> { pauseForFocus(resume = false); abandonFocus() }
        }
    }

    private fun pauseForFocus(resume: Boolean) {
        resumeOnGain = resume
        if (active.playWhenReady) { expectedFocusPause = true; active.playWhenReady = false } // перекрытие отменится по событию паузы
    }

    /** События активной деки, которые нужны фасаду, пока он владеет фокусом (то, что штатно делает ExoPlayer). */
    private fun focusEvent(name: String, args: Array<Any?>) {
        when (name) {
            "onPlayWhenReadyChanged" -> if (args[0] as Boolean) { resumeOnGain = false; requestFocus() }
            else if (expectedFocusPause) expectedFocusPause = false
            else { resumeOnGain = false; abandonFocus() }
            "onPlaybackStateChanged" -> if (args[0] == Player.STATE_IDLE) abandonFocus()
            else if (active.playWhenReady) requestFocus()
        }
    }

    fun release() {
        if (released) return
        released = true
        handler.removeCallbacksAndMessages(null)
        abandonFocus()
        active.release()
        standby.release()
        listeners.clear()
    }

    private fun info(p: ExoPlayer, positionMs: Long): Player.PositionInfo {
        val tl = p.currentTimeline
        val w = p.currentMediaItemIndex
        val pi = p.currentPeriodIndex
        val windowUid = if (tl.isEmpty) null else tl.getWindow(w, Timeline.Window()).uid
        val periodUid = if (tl.isEmpty) null else tl.getPeriod(pi, Timeline.Period(), true).uid
        return Player.PositionInfo(windowUid, w, p.currentMediaItem, periodUid, pi, positionMs, positionMs, C.INDEX_UNSET, C.INDEX_UNSET)
    }
}
