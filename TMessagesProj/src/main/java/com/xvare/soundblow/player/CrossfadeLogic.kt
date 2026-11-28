package com.xvare.soundblow.player

import androidx.media3.common.C
import androidx.media3.common.Player
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Чистая логика перекрытия треков (без Android и плеера) — отдельно, чтобы проверять юнит-тестами.
 * Управление деками и событиями — в [CrossfadePlayer].
 */
object CrossfadeLogic {
    /** Перекрытие короче этого не имеет смысла: проще дать штатный переход. */
    const val MIN_FADE_MS = 500L
    /** За сколько до начала перекрытия готовим следующий трек (буфер), мс. */
    const val PRELOAD_LEAD_MS = 8_000L
    /** Если до конца трека осталось меньше этого, перекрытие уже не начинаем. */
    const val MIN_LATE_START_MS = 400L
    /** Конец перекрытия: фиксируем переход чуть раньше конца уходящего трека, чтобы штатный автопереход не успел сработать. */
    const val COMMIT_GUARD_MS = 120L

    /** Запасная дека зеркалит очередь целиком; для очень длинных очередей перестроение зеркала слишком дорого — штатный переход. */
    const val MAX_MIRRORED_QUEUE = 500

    fun mirrorAllowed(queueSize: Int): Boolean = queueSize <= MAX_MIRRORED_QUEUE

    /** Вызовы, которые снимают «защёлку» (не пытаться перекрывать на этом треке), — пользователь начал заново. */
    fun callClearsLatch(method: String): Boolean = method == "seekTo" || method == "setMediaItems" || method == "setMediaItem" || method == "seekToDefaultPosition"

    /** Реальная длина перекрытия: не больше половины трека; 0 — перекрытия нет. */
    fun fadeMs(seconds: Int, durationMs: Long): Long {
        if (seconds <= 0 || durationMs == C.TIME_UNSET || durationMs <= 0) return 0
        val fade = minOf(seconds * 1000L, durationMs / 2)
        return if (fade < MIN_FADE_MS) 0 else fade
    }

    /** Равномерная по мощности пара кривых: сумма квадратов всегда 1, без провала громкости посередине. */
    fun outgoingGain(progress: Float): Float = cos(progress.coerceIn(0f, 1f) * PI.toFloat() / 2f)
    fun incomingGain(progress: Float): Float = sin(progress.coerceIn(0f, 1f) * PI.toFloat() / 2f)

    /** Доля перекрытия по позиции уходящего трека между началом перекрытия и его концом (с запасом [COMMIT_GUARD_MS]). */
    fun progress(outgoingPositionMs: Long, startPositionMs: Long, durationMs: Long): Float {
        val end = durationMs - COMMIT_GUARD_MS
        val span = end - startPositionMs
        if (span <= 0) return 1f
        return ((outgoingPositionMs - startPositionMs).toFloat() / span).coerceIn(0f, 1f)
    }

    class Context(
        val seconds: Int,
        val repeatMode: Int,
        val currentIsVideo: Boolean,
        val nextIsVideo: Boolean,
        /** Индекс естественного преемника по [androidx.media3.common.Timeline.getNextWindowIndex], либо C.INDEX_UNSET. */
        val nextIndex: Int,
        val currentIndex: Int,
        val durationMs: Long,
    )

    /** Можно ли вообще перекрывать текущий переход; иначе остаётся штатный путь плеера. */
    fun eligible(c: Context): Boolean =
        c.seconds > 0 &&
            c.repeatMode != Player.REPEAT_MODE_ONE &&
            !c.currentIsVideo && !c.nextIsVideo &&
            c.nextIndex != C.INDEX_UNSET && c.nextIndex != c.currentIndex &&
            fadeMs(c.seconds, c.durationMs) > 0

    /** Пора готовить следующий трек в запасной деке. */
    fun shouldPrepare(remainingMs: Long, fadeMs: Long): Boolean = remainingMs <= fadeMs + PRELOAD_LEAD_MS

    /** Пора включать следующий трек: окно перекрытия началось, преемник готов, времени ещё достаточно. */
    fun shouldStart(remainingMs: Long, fadeMs: Long, standbyReady: Boolean): Boolean =
        standbyReady && remainingMs <= fadeMs && remainingMs > MIN_LATE_START_MS

    /** Завершать ли перекрытие: уходящий трек дошёл до конца окна. */
    fun shouldCommit(progress: Float): Boolean = progress >= 1f

    /** Вызовы плеера, после которых перекрытие отменяется (ручное действие пользователя или правка очереди). */
    private val cancelingCalls = setOf(
        "setMediaItems", "setMediaItem", "addMediaItem", "addMediaItems", "moveMediaItem", "moveMediaItems",
        "replaceMediaItem", "replaceMediaItems", "removeMediaItem", "removeMediaItems", "clearMediaItems",
        "seekTo", "seekToDefaultPosition", "seekBack", "seekForward",
        "previous", "seekToPrevious", "seekToPreviousWindow", "seekToPreviousMediaItem",
        "next", "seekToNext", "seekToNextWindow", "seekToNextMediaItem",
        "stop", "pause", "setRepeatMode", "setShuffleModeEnabled", "setShuffleOrder", "prepare",
    )

    fun callCancelsOverlap(method: String, args: Array<Any?>?): Boolean = when (method) {
        "setPlayWhenReady" -> args?.getOrNull(0) == false
        else -> method in cancelingCalls
    }

    /** События активной деки, после которых перекрытие отменяется (пауза по фокусу/наушникам, буферизация, ошибка, прыжок позиции). */
    fun playWhenReadyCancels(playWhenReady: Boolean): Boolean = !playWhenReady
    fun playbackStateCancels(state: Int): Boolean = state != Player.STATE_READY
}
