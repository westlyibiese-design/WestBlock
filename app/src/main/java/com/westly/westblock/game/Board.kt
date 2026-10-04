package com.westly.westblock.game

const val SIZE = 8

data class Cell(val r: Int, val c: Int)

/** A piece in the tray. [color] is an ARGB int. [id] is unique so the UI can tell pieces apart. */
class Piece(val shape: List<Cell>, val color: Int, val id: Long)

/** Immutable 8x8 board. 0 = empty, anything else = the ARGB color of the block. */
class Board private constructor(private val cells: IntArray) {

    operator fun get(r: Int, c: Int): Int = cells[r * SIZE + c]

    fun placed(piece: Piece, r0: Int, c0: Int): Board {
        val n = cells.copyOf()
        for (cell in piece.shape) {
            n[(r0 + cell.r) * SIZE + (c0 + cell.c)] = piece.color
        }
        return Board(n)
    }

    fun fullRows(): List<Int> =
        (0 until SIZE).filter { r -> (0 until SIZE).all { c -> get(r, c) != 0 } }

    fun fullCols(): List<Int> =
        (0 until SIZE).filter { c -> (0 until SIZE).all { r -> get(r, c) != 0 } }

    fun cleared(rows: List<Int>, cols: List<Int>): Board {
        val n = cells.copyOf()
        for (r in rows) for (c in 0 until SIZE) n[r * SIZE + c] = 0
        for (c in cols) for (r in 0 until SIZE) n[r * SIZE + c] = 0
        return Board(n)
    }

    fun toGrid(): Array<BooleanArray> =
        Array(SIZE) { r -> BooleanArray(SIZE) { c -> get(r, c) != 0 } }

    companion object {
        fun empty(): Board = Board(IntArray(SIZE * SIZE))
    }
}
