package com.westly.westblock.vm

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.westly.westblock.R
import com.westly.westblock.audio.Haptics
import com.westly.westblock.audio.SoundFx
import com.westly.westblock.game.Board
import com.westly.westblock.game.Cell
import com.westly.westblock.game.Engine
import com.westly.westblock.game.Piece
import com.westly.westblock.game.SIZE
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** A "+30" style popup floating up from a cell. */
data class FloatPts(val id: Int, val text: String, val r: Int, val c: Int)

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("westblock", Context.MODE_PRIVATE)
    private val fx = SoundFx(app)
    private val haptics = Haptics(app)

    var board by mutableStateOf(Board.empty())
        private set
    var tray by mutableStateOf<List<Piece?>>(emptyList())
        private set
    var score by mutableIntStateOf(0)
        private set
    var best by mutableIntStateOf(0)
        private set
    var shufflesLeft by mutableIntStateOf(2)
        private set
    var gameOver by mutableStateOf(false)
        private set
    var newBestThisGame by mutableStateOf(false)
        private set
    var soundOn by mutableStateOf(true)
        private set

    /** Cell indices (row * SIZE + col) currently flashing before they disappear. */
    var clearing by mutableStateOf<Set<Int>>(emptySet())
        private set
    var clearTick by mutableIntStateOf(0)
        private set

    var comboText by mutableStateOf("")
        private set
    var comboVisible by mutableStateOf(false)
        private set

    val floating = mutableStateListOf<FloatPts>()

    val isBusy: Boolean get() = clearing.isNotEmpty()

    private var gameId = 0
    private var bestCelebrated = false
    private var clearStreak = 0
    private var welcomed = false
    private var nextFloatId = 0
    private var comboJob: Job? = null

    // Secret helper: taps on the Score box (2) then the Best box (3) within 3 seconds.
    private var secretCount = 0
    private var secretStart = 0L

    init {
        best = prefs.getInt("best", 0)
        soundOn = prefs.getBoolean("sound", true)
        fx.enabled = soundOn
        if (!restoreGame()) newGame()
    }

    // ------------------------------------------------------------------ actions

    fun newGame() {
        gameId++
        bestCelebrated = false
        clearStreak = 0
        newBestThisGame = false
        fx.stopVoice()
        comboJob?.cancel()
        comboVisible = false
        floating.clear()
        clearing = emptySet()
        board = Board.empty()
        score = 0
        shufflesLeft = 2
        gameOver = false
        tray = Engine.makeTray(board)
        saveGame()
    }

    fun shuffle() {
        if (gameOver || isBusy || shufflesLeft <= 0) return
        shufflesLeft -= 1
        tray = Engine.makeTray(board, Engine.SMALL_SHAPES)   // Shuffle only gives small pieces
        haptics.tick(8)
        saveGame()
        checkGameOver()
    }

    /**
     * A tap on the Score box ([score] = true) or the Best box. Score, Score, Best, Best, Best inside
     * 3 seconds swaps the tray for 3 pieces that fit. Anything else does nothing and shows nothing.
     */
    fun onSecretTap(score: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (secretCount > 0 && now - secretStart > 3000L) secretCount = 0
        val wantScore = secretCount < 2
        if (score == wantScore) {
            if (secretCount == 0) secretStart = now
            secretCount++
        } else if (score) {
            secretCount = 1          // a Score tap can always be the start of a new try
            secretStart = now
        } else {
            secretCount = 0
        }
        if (secretCount == 5) {
            secretCount = 0
            fitTray()
        }
    }

    private fun fitTray() {
        if (gameOver || isBusy) return
        tray = Engine.makeFitTray(board)
        haptics.tick(25)             // one tiny buzz, nothing on screen
        saveGame()
        checkGameOver()
    }

    fun toggleSound() {
        soundOn = !soundOn
        fx.enabled = soundOn
        prefs.edit().putBoolean("sound", soundOn).apply()
        if (!soundOn) fx.stopVoice() else fx.play("place", 0.5f)
    }

    /** Called on the first touch of the game: plays the welcome voice for returning players. */
    fun onTouch() {
        if (welcomed) return
        welcomed = true
        if (best > 0) voiceAfter("welcome", 0.3, true)
    }

    fun onPickUp() {
        haptics.tick(8)
    }

    fun pauseAudio() {
        fx.stopVoice()
    }

    /** Drop the tray piece [idx] with its top-left at board cell ([r0], [c0]). Returns true if placed. */
    fun place(idx: Int, r0: Int, c0: Int): Boolean {
        if (gameOver || isBusy) return false
        val piece = tray.getOrNull(idx) ?: return false
        val snap = Engine.findSnapCell(board, piece, r0, c0)
        if (snap == null) {
            fx.play("wrong")
            return false
        }
        val sr = snap.first
        val sc = snap.second

        board = board.placed(piece, sr, sc)
        score += piece.shape.size
        fx.play("place")
        haptics.tick(10)
        tray = tray.mapIndexed { i, p -> if (i == idx) null else p }
        bumpBest()

        val rows = board.fullRows()
        val cols = board.fullCols()
        val n = rows.size + cols.size
        if (n > 0) startClear(rows, cols, n, sr, sc)

        if (tray.all { it == null }) tray = Engine.makeTray(board)

        // Saved with the full lines already gone, so a game left during the flash comes back clean.
        saveGame(if (n > 0) board.cleared(rows, cols) else board)

        if (n == 0) {
            clearStreak = 0
            checkGameOver()
        }
        return true
    }

    // ------------------------------------------------------------------ save and resume

    /** Writes the game in progress (board, the 3 tray pieces, score, shuffles left) to the phone. */
    private fun saveGame(b: Board = board) {
        try {
            val cells = JSONArray()
            for (i in 0 until SIZE * SIZE) cells.put(b[i / SIZE, i % SIZE])
            val t = JSONArray()
            for (p in tray) {
                if (p == null) {
                    t.put(JSONObject.NULL)
                } else {
                    val sh = JSONArray()
                    for (c in p.shape) {
                        sh.put(c.r)
                        sh.put(c.c)
                    }
                    t.put(JSONObject().put("s", sh).put("k", p.color).put("i", p.id))
                }
            }
            val o = JSONObject()
                .put("board", cells)
                .put("tray", t)
                .put("score", score)
                .put("shuffles", shufflesLeft)
            prefs.edit().putString("save", o.toString()).apply()
        } catch (e: Exception) {
            // saving is a bonus: never crash the game because of it
        }
    }

    private fun clearSave() {
        prefs.edit().remove("save").apply()
    }

    /** Loads the saved game. Returns false (and changes nothing) when there is none or it is damaged. */
    private fun restoreGame(): Boolean {
        val text = prefs.getString("save", null) ?: return false
        return try {
            val o = JSONObject(text)
            val ca = o.getJSONArray("board")
            val b = Board.fromInts(IntArray(SIZE * SIZE) { ca.getInt(it) }) ?: return false
            val ta = o.getJSONArray("tray")
            if (ta.length() != 3) return false
            val pieces = ArrayList<Piece?>()
            for (i in 0 until ta.length()) {
                if (ta.isNull(i)) {
                    pieces.add(null)
                    continue
                }
                val po = ta.getJSONObject(i)
                val sa = po.getJSONArray("s")
                val shape = List(sa.length() / 2) { Cell(sa.getInt(it * 2), sa.getInt(it * 2 + 1)) }
                if (shape.isEmpty()) return false
                pieces.add(Piece(shape, po.getInt("k"), po.getLong("i")))
            }
            // Only resume a game that can still be played.
            if (pieces.none { it != null && Engine.anyFit(b, it.shape) }) return false
            for (p in pieces) if (p != null) Engine.reserveId(p.id)

            gameId++
            bestCelebrated = false
            clearStreak = 0
            newBestThisGame = false
            floating.clear()
            clearing = emptySet()
            board = b
            tray = pieces
            score = o.getInt("score").coerceAtLeast(0)
            shufflesLeft = o.getInt("shuffles").coerceIn(0, 2)
            gameOver = false
            true
        } catch (e: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------ internals

    private fun startClear(rows: List<Int>, cols: List<Int>, n: Int, originR: Int, originC: Int) {
        val cells = HashSet<Int>()
        for (r in rows) for (c in 0 until SIZE) cells.add(r * SIZE + c)
        for (c in cols) for (r in 0 until SIZE) cells.add(r * SIZE + c)
        clearing = cells
        clearTick += 1

        val mult = when (n) {
            1 -> 1.0
            2 -> 1.5
            else -> 2.0
        }
        val bonus = (n * 10 * mult).roundToInt()
        score += bonus
        bumpBest()

        fx.play(if (n >= 2) "clear2" else "clear1")
        cheerForClear(n)
        if (n > 1) haptics.pattern(0, 15, 30, 15) else haptics.tick(15)
        showFloat(bonus, originR, originC)
        if (n > 1) showCombo(n)

        val gid = gameId
        viewModelScope.launch {
            delay(290)
            if (gid != gameId) return@launch
            board = board.cleared(rows, cols)
            clearing = emptySet()
            checkGameOver()
            if (!gameOver) saveGame()
        }
    }

    private fun showFloat(amount: Int, r: Int, c: Int) {
        val item = FloatPts(nextFloatId++, "+$amount", r, c)
        floating.add(item)
        viewModelScope.launch {
            delay(1050)
            floating.remove(item)
        }
    }

    private fun showCombo(lines: Int) {
        comboText = if (lines == 2) "🔥 Double! ×1.5" else "🔥 ×$lines Combo! ×2"
        comboVisible = true
        comboJob?.cancel()
        comboJob = viewModelScope.launch {
            delay(1400)
            comboVisible = false
        }
    }

    private fun cheerForClear(lines: Int) {
        clearStreak += 1
        if (lines >= 3 || clearStreak >= 4) voiceAfter("combo", 0.35)
        else if (lines == 2 || clearStreak == 3) voiceAfter("great", 0.35)
    }

    private fun bumpBest() {
        if (score > best) {
            val firstBeat = best > 0 && !bestCelebrated
            best = score
            newBestThisGame = true
            prefs.edit().putInt("best", best).apply()
            if (firstBeat) {
                bestCelebrated = true
                celebrateBest()
            }
        }
    }

    private fun celebrateBest() {
        if (!soundOn) return
        val gid = gameId
        viewModelScope.launch {
            delay(400)
            if (gid == gameId) fx.play("newbest", 0.7f)
        }
        voiceAfter("best", 0.9, true)
    }

    private fun checkGameOver() {
        if (gameOver) return
        val alive = tray.any { p -> p != null && Engine.anyFit(board, p.shape) }
        if (alive) return
        gameOver = true
        clearSave()   // a finished game is not resumed: the next start is a new game
        fx.play("gameover")
        haptics.pattern(0, 40, 60, 40)
        val seconds = fx.durationMs(R.raw.gameover) / 1000.0
        voiceAfter("over", maxOf(0.4, seconds - 0.3), true)   // starts as the sound ends
    }

    private fun voiceAfter(group: String, seconds: Double, force: Boolean = false) {
        val gid = gameId
        viewModelScope.launch {
            delay((seconds * 1000).toLong())
            if (gid != gameId) return@launch
            if (force) {
                val remaining = fx.voiceRemainingMs()   // an important cheer waits for the current voice
                if (remaining > 0) {
                    delay(remaining + 150)
                    if (gid != gameId) return@launch
                }
            }
            fx.playVoice(group, force)
        }
    }

    override fun onCleared() {
        fx.release()
        super.onCleared()
    }
}
