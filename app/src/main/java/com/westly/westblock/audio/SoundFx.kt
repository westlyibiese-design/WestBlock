package com.westly.westblock.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import com.westly.westblock.R

/** Short sound effects (SoundPool) plus voice cheers (one at a time, with a cooldown). */
class SoundFx(private val context: Context) {

    var enabled: Boolean = true

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids = HashMap<String, Int>()

    private val voices: Map<String, List<Int>> = mapOf(
        "best" to listOf(R.raw.best1, R.raw.best2),
        "great" to listOf(R.raw.great1, R.raw.great2, R.raw.great3),
        "combo" to listOf(R.raw.combo1, R.raw.combo2),
        "over" to listOf(R.raw.over1, R.raw.over2),
        "welcome" to listOf(R.raw.welcome),
    )

    private var voice: MediaPlayer? = null
    private var voiceEndsAt = 0L
    private var lastVoiceAt = -1_000_000_000L
    private val lastPick = HashMap<String, Int>()

    init {
        ids["place"] = pool.load(context, R.raw.place, 1)
        ids["wrong"] = pool.load(context, R.raw.wrong, 1)
        ids["clear1"] = pool.load(context, R.raw.clear1, 1)
        ids["clear2"] = pool.load(context, R.raw.clear2, 1)
        ids["gameover"] = pool.load(context, R.raw.gameover, 1)
        ids["newbest"] = pool.load(context, R.raw.newbest, 1)
    }

    fun play(name: String, vol: Float = 1f) {
        if (!enabled) return
        val id = ids[name] ?: return
        pool.play(id, vol, vol, 1, 0, 1f)
    }

    fun voiceRemainingMs(): Long = maxOf(0L, voiceEndsAt - SystemClock.elapsedRealtime())

    fun playVoice(group: String, force: Boolean = false) {
        if (!enabled) return
        val now = SystemClock.elapsedRealtime()
        if (!force && (now < voiceEndsAt || now - lastVoiceAt < VOICE_COOLDOWN)) return
        val list = voices[group] ?: return
        var res = list.random()
        if (list.size > 1 && res == lastPick[group]) {
            res = list[(list.indexOf(res) + 1) % list.size]
        }
        lastPick[group] = res
        stopVoice()
        val mp = MediaPlayer.create(context, res) ?: return
        voice = mp
        mp.setOnCompletionListener { done ->
            try {
                done.release()
            } catch (e: Exception) {
            }
            if (voice === done) voice = null
        }
        voiceEndsAt = now + mp.duration
        lastVoiceAt = now
        mp.start()
    }

    fun stopVoice() {
        val mp = voice
        voice = null
        voiceEndsAt = 0L
        if (mp != null) {
            try {
                mp.stop()
            } catch (e: Exception) {
            }
            try {
                mp.release()
            } catch (e: Exception) {
            }
        }
    }

    /** Length of a raw sound in milliseconds (falls back to 1500 if it can't be read). */
    fun durationMs(res: Int): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            val fd = context.resources.openRawResourceFd(res)
            retriever.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            retriever.release()
            fd.close()
            ms ?: 1500L
        } catch (e: Exception) {
            1500L
        }
    }

    fun release() {
        stopVoice()
        pool.release()
    }

    private companion object {
        const val VOICE_COOLDOWN = 14000L
    }
}

class Haptics(context: Context) {
    private val vibrator: Vibrator? = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    fun tick(ms: Long) {
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
        }
    }

    fun pattern(vararg timings: Long) {
        try {
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, -1))
        } catch (e: Exception) {
        }
    }
}
