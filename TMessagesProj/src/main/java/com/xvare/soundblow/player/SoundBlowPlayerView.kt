package com.xvare.soundblow.player

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/** Небольшой готовый виджет. Состояние берётся из переданного движка. */
@UnstableApi
class SoundBlowPlayerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private val title = TextView(context).apply { textSize=18f; setTextColor(Color.WHITE); text="Выберите трек" }
    private val action = Button(context).apply { text="▶" }
    private var engine: SoundBlowEngine? = null
    private val listener = object : Player.Listener { override fun onEvents(player: Player, events: Player.Events) { update() } }
    init {
        orientation=VERTICAL; setPadding(24,24,24,16); setBackgroundColor(Color.rgb(15,33,24)); addView(title)
        val bar=LinearLayout(context).apply { gravity=Gravity.CENTER; orientation=HORIZONTAL }
        fun control(label: String, run: () -> Unit) = Button(context).apply { text=label; setOnClickListener { run() } }
        bar.addView(control("⏮") { engine?.previous() }); bar.addView(action); bar.addView(control("⏭") { engine?.next() }); addView(bar)
        action.setOnClickListener { engine?.toggle() }
        addView(TextView(context).apply { text="Powered by SoundBlow"; textSize=11f; gravity=Gravity.END; setTextColor(Color.rgb(134,240,140)); setPadding(0,16,0,0) })
    }
    fun bind(value: SoundBlowEngine) { engine?.player?.removeListener(listener); engine=value; value.player.addListener(listener); update() }
    private fun update() { val p=engine?.player ?: return; title.text=p.mediaMetadata.title ?: "Выберите трек"; action.text=if(p.isPlaying) "❚❚" else "▶" }
    override fun onDetachedFromWindow() { engine?.player?.removeListener(listener); super.onDetachedFromWindow() }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); engine?.player?.addListener(listener) }
}
