package com.thecirclestriangle.engine

import kotlin.random.Random

enum class Difficulty { EASY, MEDIUM, HARD }

/**
 * Computer strategies. All use the authoritative [GameState] rules; callers must still
 * play the returned cell through the engine (which validates it).
 * HARD is a bounded negamax search with a heuristic - NOT proven optimal.
 */
object Ai {
    private const val INF = 1_000_000
    private const val NODE_BUDGET = 120_000

    fun chooseMove(state: GameState, difficulty: Difficulty, random: Random = Random.Default): Int? {
        if (state.isOver) return null
        val empties = state.emptyCells()
        if (empties.isEmpty()) return null
        return when (difficulty) {
            Difficulty.EASY -> empties[random.nextInt(empties.size)]
            Difficulty.MEDIUM -> medium(state, empties, random)
            Difficulty.HARD -> hard(state, empties, random)
        }
    }

    /** Hint = strongest known move for the player to move. Does not change any state. */
    fun hint(state: GameState): Int? = chooseMove(state, Difficulty.HARD, Random(0))

    /** Sum of sizes of lines that are one circle from completion: whoever moves next can cash them. */
    private fun threat(state: GameState): Int {
        var t = 0
        for (l in state.board.lines) if (state.emptiesInLine(l.id) == 1) t += l.size
        return t
    }

    private fun medium(state: GameState, empties: IntArray, random: Random): Int {
        var best = Int.MIN_VALUE
        val bests = ArrayList<Int>()
        for (cell in empties) {
            val out = state.play(cell) as MoveOutcome.Accepted
            val v = out.points * 100 - threat(out.state)
            if (v > best) { best = v; bests.clear(); bests.add(cell) } else if (v == best) bests.add(cell)
        }
        return bests[random.nextInt(bests.size)]
    }

    private class Search { var nodes = 0 }

    private fun depthFor(state: GameState, empties: Int): Int = when {
        empties <= 6 -> empties
        state.board.rows <= 7 -> 4
        else -> 3
    }

    private fun hard(state: GameState, empties: IntArray, random: Random): Int {
        val depth = depthFor(state, empties.size)
        val search = Search()
        var best = Int.MIN_VALUE
        val bests = ArrayList<Int>()
        for (cell in ordered(state)) {
            val out = state.play(cell) as MoveOutcome.Accepted
            val v = out.points * 100 - negamax(out.state, depth - 1, -INF, INF, search)
            if (v > best) { best = v; bests.clear(); bests.add(cell) } else if (v == best) bests.add(cell)
        }
        return bests[random.nextInt(bests.size)]
    }

    private fun ordered(state: GameState): IntArray {
        val empties = state.emptyCells()
        val packed = LongArray(empties.size) { i ->
            (state.pointsIfPlayed(empties[i]).toLong() shl 32) or empties[i].toLong()
        }
        packed.sortDescending()
        return IntArray(packed.size) { (packed[it] and 0xFFFFFFFFL).toInt() }
    }

    private fun leaf(state: GameState): Int {
        var m = 0
        for (c in state.emptyCells()) m = maxOf(m, state.pointsIfPlayed(c))
        return m * 100
    }

    /** Value (x100 points) for the side to move: own future gains minus opponent gains. */
    private fun negamax(state: GameState, depth: Int, alphaIn: Int, beta: Int, s: Search): Int {
        s.nodes++
        if (state.isOver) return 0
        if (depth <= 0 || s.nodes > NODE_BUDGET) return leaf(state)
        var alpha = alphaIn
        var best = -INF
        for (cell in ordered(state)) {
            val out = state.play(cell) as MoveOutcome.Accepted
            val g = out.points * 100
            val v = g - negamax(out.state, depth - 1, g - beta, g - alpha, s)
            if (v > best) best = v
            if (best > alpha) alpha = best
            if (alpha >= beta) break
        }
        return best
    }
}
