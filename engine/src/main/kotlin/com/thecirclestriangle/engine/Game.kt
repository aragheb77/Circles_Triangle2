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

    /** Computer mode: removes the AI reply and the human move so it is [player]'s turn again. */
    fun undoUntilTurnOf(player: Player): Boolean {
        if (!canUndo) return false
        do { history.removeAt(history.size - 1) } while (history.size > 1 && state.current != player)
        return state.current == player
    }
}
