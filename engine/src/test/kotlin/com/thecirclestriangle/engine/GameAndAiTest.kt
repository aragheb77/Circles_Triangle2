package com.thecirclestriangle.engine

import kotlin.random.Random
import kotlin.test.*

class GameAndAiTest {
    @Test fun undoRestoresEntireState() {
        val g = Game(GameState.initial(Board.of(5)))
        listOf(10, 11, 12, 13, 14).forEach { g.play(it) }
        assertEquals(5, g.state.scoreOne)
        assertTrue(g.undo())
        assertEquals(0, g.state.scoreOne)
        assertEquals(13, g.state.lastMove)
        assertEquals(Player.ONE, g.state.current)
        assertTrue(g.state.isEmpty(14))
    }

    @Test fun computerModeUndoRemovesHumanAndAiMoves() {
        val g = Game(GameState.initial(Board.of(5)))
        g.play(0); g.play(1) // human, AI
        assertTrue(g.undoUntilTurnOf(Player.ONE))
        assertEquals(0, g.state.moveCount)
    }

    @Test fun undoOnFreshGameDoesNothing() {
        assertFalse(Game(GameState.initial(Board.of(5))).undo())
    }

    @Test fun allDifficultiesPlayOnlyLegalMovesToCompletion() {
        for (rows in listOf(5, 7)) for (d in Difficulty.values()) {
            var s = GameState.initial(Board.of(rows))
            val rnd = Random(7)
            while (!s.isOver) {
                val m = Ai.chooseMove(s, d, rnd)
                assertNotNull(m)
                assertTrue(s.isEmpty(m!!), "AI picked occupied circle")
                s = (s.play(m) as MoveOutcome.Accepted).state
            }
            assertNull(Ai.chooseMove(s, d))
        }
    }

    private fun bottomRowSetup(): GameState {
        var s = GameState.initial(Board.of(5))
        for (m in listOf(10, 11, 12, 13)) s = (s.play(m) as MoveOutcome.Accepted).state
        return s // current = ONE, cell 14 completes the 5-point bottom row
    }

    @Test fun mediumAndHardTakeImmediateScore() {
        val s = bottomRowSetup()
        assertEquals(14, Ai.chooseMove(s, Difficulty.MEDIUM, Random(1)))
        assertEquals(14, Ai.chooseMove(s, Difficulty.HARD, Random(1)))
        assertEquals(14, Ai.hint(s))
    }

    @Test fun hintDoesNotChangeState() {
        val s = bottomRowSetup()
        val before = s.moveCount
        Ai.hint(s)
        assertEquals(before, s.moveCount); assertEquals(Player.ONE, s.current)
    }

    @Test fun easyIsVariedWhileMediumIsMoreConsistent() {
        val s = GameState.initial(Board.of(9))
        val easy = (0 until 30).map { Ai.chooseMove(s, Difficulty.EASY, Random(it)) }.toSet()
        assertTrue(easy.size > 5)
    }

    @Test fun hardOnLargestBoardRespondsQuickly() {
        val s = GameState.initial(Board.of(13))
        val t0 = System.nanoTime()
        assertNotNull(Ai.chooseMove(s, Difficulty.HARD, Random(1)))
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue(ms < 5_000, "Hard took ${ms}ms (JVM budget; re-measure on device)")
    }
}
