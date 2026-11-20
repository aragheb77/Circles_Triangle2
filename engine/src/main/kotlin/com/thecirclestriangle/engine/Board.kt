package com.thecirclestriangle.engine

import java.util.concurrent.ConcurrentHashMap

enum class Direction { HORIZONTAL, DOWN_LEFT, DOWN_RIGHT }

/** A maximal straight line of circles. [cells] are circle ids in board order. */
class Line(val id: Int, val direction: Direction, val cells: IntArray) {
    val size: Int get() = cells.size
}

/**
 * Immutable triangular board. Circle id = row*(row+1)/2 + col, 0 <= col <= row < rows.
 *
 * Maximal lines: horizontal (start (r,0)), down-left [1,0] (start (c,c)),
 * down-right [1,1] (start (r,0)). Each family has [rows] lines of length 1..rows.
 * [minLineLength] filters out too-short lines (OPEN RULE QUESTION G-15; default 2).
 */
class Board private constructor(val rows: Int, val minLineLength: Int) {
    val cellCount: Int = rows * (rows + 1) / 2
    val rowOf = IntArray(cellCount)
    val colOf = IntArray(cellCount)
    val lines: List<Line>
    val linesByCell: Array<IntArray>

    init {
        for (r in 0 until rows) for (c in 0..r) {
            val id = idOf(r, c)
            rowOf[id] = r
            colOf[id] = c
        }
        val raw = ArrayList<Pair<Direction, IntArray>>()
        for (r in 0 until rows) raw.add(Direction.HORIZONTAL to IntArray(r + 1) { c -> idOf(r, c) })
        for (c in 0 until rows) raw.add(Direction.DOWN_LEFT to IntArray(rows - c) { k -> idOf(c + k, c) })
        for (r in 0 until rows) raw.add(Direction.DOWN_RIGHT to IntArray(rows - r) { k -> idOf(r + k, k) })
        lines = raw.filter { it.second.size >= minLineLength }
            .mapIndexed { i, p -> Line(i, p.first, p.second) }
        val byCell = Array(cellCount) { ArrayList<Int>() }
        for (l in lines) for (cell in l.cells) byCell[cell].add(l.id)
        linesByCell = Array(cellCount) { byCell[it].toIntArray() }
    }

    companion object {
        const val DEFAULT_MIN_LINE_LENGTH = 2
        val SUPPORTED_ROWS: List<Int> = listOf(5, 7, 9, 11, 13)
        private val cache = ConcurrentHashMap<Int, Board>()

        fun idOf(row: Int, col: Int): Int = row * (row + 1) / 2 + col

        fun of(rows: Int, minLineLength: Int = DEFAULT_MIN_LINE_LENGTH): Board {
            require(rows in SUPPORTED_ROWS) { "Unsupported board size: $rows" }
            require(minLineLength in 1..rows) { "Invalid minLineLength: $minLineLength" }
            return cache.getOrPut(rows * 100 + minLineLength) { Board(rows, minLineLength) }
        }
    }
}
