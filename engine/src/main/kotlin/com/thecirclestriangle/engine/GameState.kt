package com.thecirclestriangle.engine

enum class Player {
    ONE, TWO;
    val opponent: Player get() = if (this == ONE) TWO else ONE
    val index: Int get() = ordinal
}

enum class RejectReason { OUT_OF_RANGE, OCCUPIED, GAME_OVER }
enum class MatchResult { PLAYER_ONE_WINS, PLAYER_TWO_WINS, DRAW }

sealed interface MoveOutcome {
    data class Accepted(
        val state: GameState,
        val player: Player,
        val cell: Int,
        val newlyCompletedLines: List<Int>,
        /** Distinct circles in this scoring event (union of all newly completed lines). */
        val scoredCells: List<Int>,
        val points: Int,
    ) : MoveOutcome

    data class Rejected(val reason: RejectReason) : MoveOutcome
}

/** Immutable game state. Invalid moves never alter anything (play returns Rejected). */
class GameState private constructor(
    val board: Board,
    private val owners: ByteArray,
    private val lineFill: IntArray,
    private val scoreArr: IntArray,
    val current: Player,
    val lastMove: Int?,
    val moveCount: Int,
) {
    val scoreOne: Int get() = scoreArr[0]
    val scoreTwo: Int get() = scoreArr[1]
    fun score(p: Player): Int = scoreArr[p.index]

    fun ownerOf(cell: Int): Player? = when (owners[cell].toInt()) {
        1 -> Player.ONE
        2 -> Player.TWO
        else -> null
    }

    fun isEmpty(cell: Int): Boolean = cell in 0 until board.cellCount && owners[cell].toInt() == 0

    val isOver: Boolean get() = moveCount == board.cellCount

    val result: MatchResult?
        get() = if (!isOver) null else when {
            scoreArr[0] > scoreArr[1] -> MatchResult.PLAYER_ONE_WINS
            scoreArr[0] < scoreArr[1] -> MatchResult.PLAYER_TWO_WINS
            else -> MatchResult.DRAW
        }

    /** A line is completed only when every circle is occupied (confirmed scoring state). */
    fun isLineCompleted(lineId: Int): Boolean = lineFill[lineId] == board.lines[lineId].size
    fun emptiesInLine(lineId: Int): Int = board.lines[lineId].size - lineFill[lineId]

    val completedLineIds: List<Int>
        get() = board.lines.indices.filter { isLineCompleted(it) }

    fun emptyCells(): IntArray = (0 until board.cellCount).filter { owners[it].toInt() == 0 }.toIntArray()

    /** Points the current player would earn at [cell]; 0 if the move is illegal. Allocation-free fast path. */
    fun pointsIfPlayed(cell: Int): Int {
        if (isOver || !isEmpty(cell)) return 0
        var any = false
        for (lid in board.linesByCell[cell]) {
            if (lineFill[lid] + 1 == board.lines[lid].size) { any = true; break }
        }
        if (!any) return 0
        val seen = BooleanArray(board.cellCount)
        var count = 0
        for (lid in board.linesByCell[cell]) {
            val line = board.lines[lid]
            if (lineFill[lid] + 1 == line.size) {
                for (c in line.cells) if (!seen[c]) { seen[c] = true; count++ }
            }
        }
        return count
    }

    fun play(cell: Int): MoveOutcome {
        if (isOver) return MoveOutcome.Rejected(RejectReason.GAME_OVER)
        if (cell !in 0 until board.cellCount) return MoveOutcome.Rejected(RejectReason.OUT_OF_RANGE)
        if (owners[cell].toInt() != 0) return MoveOutcome.Rejected(RejectReason.OCCUPIED)

        val newOwners = owners.copyOf()
        newOwners[cell] = (current.index + 1).toByte()
        val newFill = lineFill.copyOf()
        val newly = ArrayList<Int>()
        for (lid in board.linesByCell[cell]) {
            newFill[lid]++
            if (newFill[lid] == board.lines[lid].size) newly.add(lid)
        }
        val seen = BooleanArray(board.cellCount)
        for (lid in newly) for (c in board.lines[lid].cells) seen[c] = true
        val scored = (0 until board.cellCount).filter { seen[it] }
        val newScores = scoreArr.copyOf()
        newScores[current.index] += scored.size
        val next = GameState(board, newOwners, newFill, newScores, current.opponent, cell, moveCount + 1)
        return MoveOutcome.Accepted(next, current, cell, newly, scored, scored.size)
    }

    companion object {
        /** [first] is the player who makes the opening move (default: Player ONE). */
        fun initial(board: Board, first: Player = Player.ONE): GameState =
            GameState(board, ByteArray(board.cellCount), IntArray(board.lines.size), IntArray(2), first, null, 0)

        /** Rebuilds a state from a move list (persistence / multiplayer recovery). Null if any move is invalid. */
        fun fromMoves(board: Board, moves: List<Int>, first: Player = Player.ONE): GameState? {
            var s = initial(board, first)
            for (m in moves) {
                val o = s.play(m)
                if (o !is MoveOutcome.Accepted) return null
                s = o.state
            }
            return s
        }
    }
}
