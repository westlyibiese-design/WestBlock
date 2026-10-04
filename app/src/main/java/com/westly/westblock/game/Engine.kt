package com.westly.westblock.game

object Engine {

    val PIECE_COLORS = intArrayOf(
        0xFFFFB547.toInt(), // amber
        0xFF6DB8FF.toInt(), // blue
        0xFF7ED957.toInt(), // green
        0xFFC060F0.toInt(), // purple
        0xFFFF6B6B.toInt(), // red
        0xFFFFD98C.toInt(), // sand
    )

    private fun shape(vararg v: Int): List<Cell> =
        List(v.size / 2) { Cell(v[it * 2], v[it * 2 + 1]) }

    val SHAPES: List<List<Cell>> = listOf(
        shape(0, 0),
        shape(0, 0, 0, 1),
        shape(0, 0, 1, 0),
        shape(0, 0, 0, 1, 0, 2),
        shape(0, 0, 1, 0, 2, 0),
        shape(0, 0, 0, 1, 1, 0, 1, 1),
        shape(0, 0, 0, 1, 0, 2, 1, 2),
        shape(0, 0, 1, 0, 1, 1, 1, 2),
        shape(0, 1, 1, 1, 2, 0, 2, 1),
        shape(0, 0, 0, 1, 1, 0, 2, 0),
        shape(0, 0, 0, 1, 0, 2, 1, 0, 1, 1, 1, 2),
        shape(0, 0, 0, 1, 0, 2, 1, 0, 1, 1, 1, 2, 2, 0, 2, 1, 2, 2),
        shape(0, 0, 0, 1, 0, 2, 0, 3),
        shape(0, 0, 1, 0, 2, 0, 3, 0),
        shape(0, 0, 0, 1, 0, 2, 0, 3, 0, 4),
        shape(0, 0, 1, 0, 2, 0, 3, 0, 4, 0),
    )

    /** 1 or 2 blocks. Used by Shuffle. */
    val SMALL_SHAPES: List<List<Cell>> = SHAPES.take(3)

    /** Used when the board is crowded. */
    val EASY_SHAPES: List<List<Cell>> = SHAPES.take(10) + SMALL_SHAPES

    private var counter = 0L

    fun pieceSize(shape: List<Cell>): Pair<Int, Int> {
        var mr = 0
        var mc = 0
        for (cell in shape) {
            if (cell.r > mr) mr = cell.r
            if (cell.c > mc) mc = cell.c
        }
        return Pair(mr + 1, mc + 1)
    }

    fun randPiece(pool: List<List<Cell>> = SHAPES): Piece =
        Piece(pool.random(), PIECE_COLORS.random(), counter++)

    // ---------- placement ----------

    fun canPlace(board: Board, shape: List<Cell>, r0: Int, c0: Int): Boolean =
        shape.all { cell ->
            val r = r0 + cell.r
            val c = c0 + cell.c
            r in 0 until SIZE && c in 0 until SIZE && board[r, c] == 0
        }

    fun anyFit(board: Board, shape: List<Cell>): Boolean {
        for (r in 0 until SIZE) for (c in 0 until SIZE) {
            if (canPlace(board, shape, r, c)) return true
        }
        return false
    }

    /**
     * Forgiving drop: if the exact cell isn't valid, snap to the closest valid
     * neighbouring cell (within [radius]). Returns null if nothing fits.
     */
    fun findSnapCell(board: Board, piece: Piece, r0: Int, c0: Int, radius: Int = 1): Pair<Int, Int>? {
        if (canPlace(board, piece.shape, r0, c0)) return Pair(r0, c0)
        var best: Pair<Int, Int>? = null
        var bestDist = Int.MAX_VALUE
        for (dr in -radius..radius) for (dc in -radius..radius) {
            if (dr == 0 && dc == 0) continue
            val r = r0 + dr
            val c = c0 + dc
            if (canPlace(board, piece.shape, r, c)) {
                val dist = dr * dr + dc * dc
                if (dist < bestDist) {
                    bestDist = dist
                    best = Pair(r, c)
                }
            }
        }
        return best
    }

    // ---------- fair tray: only hand out 3 pieces that can really be placed ----------

    private fun clearFull(b: Array<BooleanArray>) {
        val rows = ArrayList<Int>()
        val cols = ArrayList<Int>()
        for (r in 0 until SIZE) if (b[r].all { it }) rows.add(r)
        for (c in 0 until SIZE) if ((0 until SIZE).all { r -> b[r][c] }) cols.add(c)
        for (r in rows) for (c in 0 until SIZE) b[r][c] = false
        for (c in cols) for (r in 0 until SIZE) b[r][c] = false
    }

    private fun fitsOn(b: Array<BooleanArray>, shape: List<Cell>, r0: Int, c0: Int): Boolean =
        shape.all { cell ->
            val r = r0 + cell.r
            val c = c0 + cell.c
            r in 0 until SIZE && c in 0 until SIZE && !b[r][c]
        }

    private fun anyFitOn(b: Array<BooleanArray>, shape: List<Cell>): Boolean {
        for (r in 0 until SIZE) for (c in 0 until SIZE) {
            if (fitsOn(b, shape, r, c)) return true
        }
        return false
    }

    private val PERMS = listOf(
        listOf(0, 1, 2), listOf(0, 2, 1), listOf(1, 0, 2),
        listOf(1, 2, 0), listOf(2, 0, 1), listOf(2, 1, 0),
    )

    /** Can all pieces be placed one after the other (in any order)? Line clears are counted. */
    private fun allThreeFit(board: Board, pieces: List<Piece>): Boolean {
        val start = board.toGrid()
        clearFull(start)
        var budget = 20000   // safety limit so it is always fast

        fun rec(b: Array<BooleanArray>, order: List<Int>, k: Int): Boolean {
            if (k == order.size) return true
            val shape = pieces[order[k]].shape
            for (r in 0 until SIZE) {
                for (c in 0 until SIZE) {
                    budget -= 1
                    if (budget <= 0) return false
                    if (!fitsOn(b, shape, r, c)) continue
                    val nb = Array(SIZE) { i -> b[i].copyOf() }
                    for (cell in shape) nb[r + cell.r][c + cell.c] = true
                    clearFull(nb)
                    if (rec(nb, order, k + 1)) return true
                }
            }
            return false
        }

        return PERMS.any { order -> rec(start, order, 0) }
    }

    fun makeTray(board: Board, pool: List<List<Cell>> = SHAPES): List<Piece?> {
        val start = board.toGrid()
        clearFull(start)
        var fallback: List<Piece>? = null
        for (i in 0 until 40) {
            // after some tries, lean to smaller pieces so a fair set is easier to find
            val p = if (i < 15 || pool !== SHAPES) pool else EASY_SHAPES
            val cand = List(3) { randPiece(p) }
            if (allThreeFit(board, cand)) return cand
            if (fallback == null && cand.any { anyFitOn(start, it.shape) }) fallback = cand
        }
        return fallback ?: List(3) { randPiece(SMALL_SHAPES) }
    }
}
