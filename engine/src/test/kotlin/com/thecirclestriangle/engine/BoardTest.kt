package com.thecirclestriangle.engine

import kotlin.test.*

class BoardTest {
    @Test fun circleCounts() {
        val expected = mapOf(5 to 15, 7 to 28, 9 to 45, 11 to 66, 13 to 91)
        for ((rows, n) in expected) assertEquals(n, Board.of(rows).cellCount)
    }

    @Test fun lineCountsMinLength2() {
        val expected = mapOf(5 to 12, 7 to 18, 9 to 24, 11 to 30, 13 to 36)
        for ((rows, n) in expected) assertEquals(n, Board.of(rows).lines.size)
    }

    @Test fun lineCountsMinLength1() {
        for (rows in Board.SUPPORTED_ROWS) assertEquals(3 * rows, Board.of(rows, 1).lines.size)
    }

    @Test fun lineIdsAreUniqueAndSequential() {
        for (rows in Board.SUPPORTED_ROWS) {
            val ids = Board.of(rows).lines.map { it.id }
            assertEquals(ids.indices.toList(), ids)
        }
    }

    @Test fun linesAreStraightAndDeterministic() {
        for (rows in Board.SUPPORTED_ROWS) {
            val b = Board.of(rows, 1)
            for (l in b.lines) {
                val dr = when (l.direction) { Direction.HORIZONTAL -> 0; else -> 1 }
                val dc = when (l.direction) { Direction.DOWN_LEFT -> 0; else -> 1 }
                for (i in 1 until l.size) {
                    assertEquals(b.rowOf[l.cells[i - 1]] + dr, b.rowOf[l.cells[i]])
                    assertEquals(b.colOf[l.cells[i - 1]] + dc, b.colOf[l.cells[i]])
                }
            }
            assertSame(b, Board.of(rows, 1))
        }
    }

    @Test fun unsupportedSizeRejected() {
        assertFailsWith<IllegalArgumentException> { Board.of(15) }
        assertFailsWith<IllegalArgumentException> { Board.of(6) }
    }
}
