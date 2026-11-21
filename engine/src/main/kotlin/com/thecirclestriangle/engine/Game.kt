package com.thecirclestriangle.engine

/** Local game session with full-state undo. Not used for multiplayer (server is authoritative). */
class Game(initial: GameState) {
    private val history = ArrayList<GameState>().apply { add(initial) }

    val state: GameState get() = history.last()
    val canUndo: Boolean get() = history.size > 1
    val moves: List<Int> get() = history.drop(1).map { it.lastMove!! }

    fun play(cell: Int): MoveOutcome {
        val o = state.play(cell)
        if (o is MoveOutcome.Accepted) history.add(o.state)
        return o
    }

    fun undo(): Boolean {
        if (!canUndo) return false
        history.removeAt(history.size - 1)
        return true
    }

    /** True if [undoUntilTurnOf] would succeed (false e.g. when only the computer's opening move exists). */
    fun canUndoUntilTurnOf(player: Player): Boolean = earlierIndexOfTurn(player) >= 0

    /**
     * Computer mode: removes the AI reply and the human move so it is [player]'s turn again.
     * Leaves the game untouched and returns false if no earlier position has [player] to move
     * (e.g. the computer opened the game and the human has not moved yet).
     */
    fun undoUntilTurnOf(player: Player): Boolean {
        val target = earlierIndexOfTurn(player)
        if (target < 0) return false
        while (history.size - 1 > target) history.removeAt(history.size - 1)
        return true
    }

    private fun earlierIndexOfTurn(player: Player): Int {
        var i = history.size - 2
        while (i >= 0 && history[i].current != player) i--
        return i
    }
}
