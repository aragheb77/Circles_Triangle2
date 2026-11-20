package com.thecirclestriangle.engine

import kotlin.test.*

class ScoringTest {
    private fun play(rows: Int, moves: List<Int>): Pair<GameState, List<MoveOutcome.Accepted>> {
        var s = GameState.initial(Board.of(rows))
        val outs = ArrayList<MoveOutcome.Accepted>()
        for (m in moves) {
            val o = s.play(m)
            assertIs<MoveOutcome.Accepted>(o)
            outs.add(o); s = o.state
        }
        return s to outs
    }

    @Test fun bottomRowScoresFive_matchesKiteDemo() {
        val (s, outs) = play(5, listOf(10, 11, 12, 13, 14))
        assertEquals(5, outs.last().points)
        assertEquals(5, s.scoreOne); assertEquals(0, s.scoreTwo)
        assertEquals(1, s.completedLineIds.size)
    }

    @Test fun simultaneousIntersectingLines_sharedCircleCountsOnce() {
        val (s, outs) = play(5, listOf(1, 4, 7, 11, 2))
        assertEquals(2, outs.last().newlyCompletedLines.size)
        assertEquals(5, outs.last().points) // 2 + 4 circles, shared circle 2 counted once
        assertEquals(listOf(1, 2, 4, 7, 11), outs.last().scoredCells)
        assertEquals(5, s.scoreOne)
    }

    @Test fun previouslyScoredCircleCountsAgainInNewLine() {
        val (s, outs) = play(5, listOf(1, 4, 7, 11, 2, 0, 5, 9, 14))
        assertEquals(5, outs.last().points) // diagonal 0,2,5,9,14 includes circle 2 scored before
        assertEquals(10, s.scoreOne)
    }

    @Test fun completedLineNeverScoresAgain() {
        val (_, outs) = play(5, listOf(10, 11, 12, 13, 14, 0, 1))
        assertEquals(0, outs[5].points)
        assertEquals(0, outs[6].points)
    }

    @Test fun moveWithoutCompletionScoresZero() {
        val (s, outs) = play(7, listOf(0, 5, 9))
        assertTrue(outs.all { it.points == 0 })
        assertEquals(0, s.scoreOne + s.scoreTwo)
    }

    @Test fun fullBoard_sequentialFill_knownScore() {
        val (s, _) = play(5, (0 until 15).toList())
        assertTrue(s.isOver)
        assertEquals(21, s.scoreOne); assertEquals(17, s.scoreTwo) // cross-checked with Python reference
        assertEquals(MatchResult.PLAYER_ONE_WINS, s.result)
        assertEquals(Board.of(5).lines.size, s.completedLineIds.size)
    }

    @Test fun pointsIfPlayedMatchesPlay() {
        for (rows in Board.SUPPORTED_ROWS) {
            var s = GameState.initial(Board.of(rows))
            val order = (0 until s.board.cellCount).shuffled(kotlin.random.Random(rows))
            for (m in order) {
                val predicted = s.pointsIfPlayed(m)
                val o = s.play(m) as MoveOutcome.Accepted
                assertEquals(o.points, predicted)
                s = o.state
            }
            assertTrue(s.isOver)
            assertEquals(s.board.cellCount, s.moveCount)
        }
    }

    @Test fun invalidMovesDoNotChangeState() {
        val (s, _) = play(5, listOf(3))
        assertEquals(MoveOutcome.Rejected(RejectReason.OCCUPIED), s.play(3))
        assertEquals(MoveOutcome.Rejected(RejectReason.OUT_OF_RANGE), s.play(15))
        assertEquals(MoveOutcome.Rejected(RejectReason.OUT_OF_RANGE), s.play(-1))
        assertEquals(Player.TWO, s.current); assertEquals(1, s.moveCount)
        val (full, _) = play(5, (0 until 15).toList())
        assertEquals(MoveOutcome.Rejected(RejectReason.GAME_OVER), full.play(0))
    }

    @Test fun drawAndWinnerDetermination() {
        // Result must be consistent with scores for random complete games on every size.
        for (rows in Board.SUPPORTED_ROWS) {
            var s = GameState.initial(Board.of(rows))
            for (m in (0 until s.board.cellCount).shuffled(kotlin.random.Random(42 + rows))) s = (s.play(m) as MoveOutcome.Accepted).state
            val expected = when {
                s.scoreOne > s.scoreTwo -> MatchResult.PLAYER_ONE_WINS
                s.scoreOne < s.scoreTwo -> MatchResult.PLAYER_TWO_WINS
                else -> MatchResult.DRAW
            }
            assertEquals(expected, s.result)
        }
    }

    @Test fun replayFromMovesRejectsDuplicates() {
        assertNull(GameState.fromMoves(Board.of(5), listOf(1, 2, 1)))
        assertNotNull(GameState.fromMoves(Board.of(5), listOf(1, 2, 3)))
    }
}
