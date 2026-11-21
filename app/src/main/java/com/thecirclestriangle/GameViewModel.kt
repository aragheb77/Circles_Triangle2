package com.thecirclestriangle

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thecirclestriangle.audio.Sfx
import com.thecirclestriangle.audio.Sound
import com.thecirclestriangle.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class Screen { SPLASH, SETUP, GAME, VICTORY }
enum class GameMode { COMPUTER, TWO_PLAYERS, ONLINE }
enum class Confirm { RESTART, NEW_GAME, EXIT }

/** Visual feedback stages after a move (design: Gameplay motion storyboard). UI-only; never alters rules. */
enum class Stage { IDLE, THINKING, REVEAL, SCORE, HANDOFF }

data class MoveFeedback(
    val id: Int,
    val cell: Int,
    val player: Player,
    val points: Int,
    val newLines: List<Int>,
    val scoreBefore: Int,
    val scoreAfter: Int,
)

class GameViewModel : ViewModel() {
    var screen by mutableStateOf(Screen.SPLASH)
        private set
    var mode by mutableStateOf(GameMode.COMPUTER)
    var rows by mutableIntStateOf(9)
    var difficulty by mutableStateOf(Difficulty.MEDIUM)

    /** Authoritative game state (updated immediately by the unchanged engine). */
    var state by mutableStateOf<GameState?>(null)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var hintCell by mutableStateOf<Int?>(null)
        private set
    var aiThinking by mutableStateOf(false)
        private set
    var showPause by mutableStateOf(false)
        private set
    var confirm by mutableStateOf<Confirm?>(null)

    // Player names. Typed on the setup screen in Two Players mode; blank = "Player 1" / "Player 2".
    var nameOne by mutableStateOf("")
    var nameTwo by mutableStateOf("")

    /** Increments with every new game so the UI can replay the board entrance animation. */
    var gameId by mutableIntStateOf(0)
        private set

    // "Who starts?" draw (Computer mode only). Purely presentational: the winner was chosen up front.
    var starterActive by mutableStateOf(false)
        private set
    var starterHighlight by mutableStateOf<Player?>(null)
        private set
    var starterRevealed by mutableStateOf(false)
        private set

    // Presentation state that lags behind the engine state so the four stages can play in order.
    var stage by mutableStateOf(Stage.IDLE)
        private set
    var feedback by mutableStateOf<MoveFeedback?>(null)
        private set
    var shownTurn by mutableStateOf(Player.ONE)
        private set
    var shownScoreOne by mutableIntStateOf(0)
        private set
    var shownScoreTwo by mutableIntStateOf(0)
        private set
    var reducedMotion: Boolean = false

    private var game: Game? = null
    private var flowJob: Job? = null
    private var generation = 0
    private var feedbackId = 0

    /** True when [p] is the human playing against the computer (shown as "You"). */
    fun isYou(p: Player): Boolean = mode == GameMode.COMPUTER && p == Player.ONE

    /** Name shown in the UI: "You" / "Computer" against the computer, otherwise the typed name or "Player N". */
    fun nameOf(p: Player): String = when {
        mode == GameMode.COMPUTER -> if (p == Player.ONE) "You" else "Computer"
        p == Player.ONE -> nameOne.trim().ifEmpty { "Player 1" }
        else -> nameTwo.trim().ifEmpty { "Player 2" }
    }

    val canStart: Boolean get() = mode != GameMode.ONLINE

    private fun ms(v: Long): Long = if (reducedMotion) 0L else v
    private suspend fun waitWhilePaused() { while (showPause) delay(50) }

    private fun syncState() {
        state = game?.state
        val g = game
        // Against the computer an undo must land on the human's turn; if the computer opened the game and
        // the human has not moved yet there is nothing to undo.
        canUndo = g != null && if (mode == GameMode.COMPUTER) g.canUndoUntilTurnOf(Player.ONE) else g.canUndo
    }

    private fun syncShown() {
        val s = game?.state ?: return
        shownTurn = s.current
        shownScoreOne = s.scoreOne
        shownScoreTwo = s.scoreTwo
    }

    private fun cancelFlow() {
        flowJob?.cancel(); flowJob = null
        aiThinking = false; stage = Stage.IDLE; feedback = null
        starterActive = false; starterHighlight = null; starterRevealed = false
        generation++
    }

    fun finishSplash() { if (screen == Screen.SPLASH) screen = Screen.SETUP }

    fun startGame() {
        if (!canStart) return
        cancelFlow()
        // Against the computer who opens is random; in Two Players Player 1 opens.
        val first = if (mode == GameMode.COMPUTER && Random.nextBoolean()) Player.TWO else Player.ONE
        game = Game(GameState.initial(Board.of(rows), first))
        hintCell = null; showPause = false; confirm = null
        gameId++
        syncState(); syncShown()
        screen = Screen.GAME
        if (mode == GameMode.COMPUTER) beginStarterDraw(first)
    }

    /**
     * Presentation of the random draw: a spotlight jumps between "You" and "Computer", slows down and
     * settles on [first]; then the board unlocks (and the computer moves at once if it won the draw).
     */
    private fun beginStarterDraw(first: Player) {
        val gen = generation
        starterActive = true; starterRevealed = false; starterHighlight = null
        flowJob = viewModelScope.launch {
            delay(ms(650))                                   // let the board build in behind the overlay
            // Step i highlights ONE when i is even; pick the length so the last step is [first].
            val steps = if (first == Player.TWO) 12 else 13
            for (i in 0 until steps) {
                waitWhilePaused()
                if (gen != generation) return@launch
                starterHighlight = if (i % 2 == 0) Player.ONE else Player.TWO
                if (!reducedMotion) Sound.play(Sfx.TICK)
                val f = i.toFloat() / (steps - 1)
                delay(ms((85 + 360 * f * f).toLong()))      // 85 ms -> ~445 ms: decelerating spin
            }
            starterHighlight = first; starterRevealed = true
            Sound.play(Sfx.STARTER)
            delay(ms(1500)); waitWhilePaused()
            if (gen != generation) return@launch
            starterActive = false; starterHighlight = null; starterRevealed = false
            if (first == Player.TWO) aiTurn(gen)
        }
    }

    fun toSetup() { cancelFlow(); showPause = false; confirm = null; screen = Screen.SETUP }
    fun toMainMenu() { cancelFlow(); showPause = false; confirm = null; screen = Screen.SPLASH }

    fun pause() { if (screen == Screen.GAME) showPause = true }
    fun resume() { showPause = false; confirm = null }

    fun isHumanTurn(): Boolean {
        val s = state ?: return false
        return !s.isOver && stage == Stage.IDLE && !aiThinking && !starterActive &&
            (mode == GameMode.TWO_PLAYERS || s.current == Player.ONE)
    }

    /** One tap plays the circle (the former highlight-then-confirm step has been removed on every board size). */
    fun onCellTap(cell: Int) {
        val s = state ?: return
        if (screen != Screen.GAME || showPause || !isHumanTurn() || !s.isEmpty(cell)) return
        playCell(cell)
    }

    private fun playCell(cell: Int) {
        val g = game ?: return
        val before = g.state
        val out = g.play(cell)
        if (out !is MoveOutcome.Accepted) return
        hintCell = null
        syncState()
        Sound.play(if (out.player == Player.ONE) Sfx.PLACE_ONE else Sfx.PLACE_TWO)
        val gen = generation
        val fb = MoveFeedback(
            id = ++feedbackId, cell = out.cell, player = out.player, points = out.points,
            newLines = out.newlyCompletedLines,
            scoreBefore = before.score(out.player), scoreAfter = out.state.score(out.player),
        )
        feedback = fb
        flowJob = viewModelScope.launch {
            // Stage 2 - connection reveal (line draw + node pulse)
            stage = Stage.REVEAL
            delay(ms(320)); waitWhilePaused()
            if (gen != generation) return@launch
            // Stage 3 - score count-up
            if (out.player == Player.ONE) shownScoreOne = fb.scoreAfter else shownScoreTwo = fb.scoreAfter
            if (out.points > 0) {
                Sound.play(Sfx.LINE)
                stage = Stage.SCORE
                delay(ms(550)); waitWhilePaused()
                if (gen != generation) return@launch
            }
            // Stage 4 - turn handoff (tint cross-fade)
            stage = Stage.HANDOFF
            shownTurn = out.state.current
            delay(ms(200))
            if (gen != generation) return@launch
            stage = Stage.IDLE; feedback = null
            if (out.state.isOver) {
                Sound.play(
                    when (out.state.result) {
                        MatchResult.PLAYER_ONE_WINS -> Sfx.WIN
                        MatchResult.PLAYER_TWO_WINS -> if (mode == GameMode.COMPUTER) Sfx.LOSE else Sfx.WIN
                        else -> Sfx.DRAW
                    },
                )
                screen = Screen.VICTORY; return@launch
            }
            if (mode == GameMode.COMPUTER && out.state.current == Player.TWO) aiTurn(gen)
        }
    }

    /** Stage 1 - opponent thinking (450-650 ms), then the unchanged AI chooses and the move is played. */
    private suspend fun aiTurn(gen: Int) {
        val snapshot = state ?: return
        val diff = difficulty
        aiThinking = true; stage = Stage.THINKING
        val t0 = System.nanoTime()
        val move = withContext(Dispatchers.Default) { Ai.chooseMove(snapshot, diff) }
        val thinkMs = ms(Random.nextLong(450, 651))
        val spent = (System.nanoTime() - t0) / 1_000_000
        if (thinkMs > spent) delay(thinkMs - spent)
        waitWhilePaused()
        if (gen != generation || move == null) return
        aiThinking = false; stage = Stage.IDLE
        playCell(move)
    }

    fun undo() {
        val g = game ?: return
        if (aiThinking || starterActive || stage != Stage.IDLE || screen != Screen.GAME || !canUndo) return
        val ok = if (mode == GameMode.COMPUTER) g.undoUntilTurnOf(Player.ONE) else g.undo()
        if (ok) {
            Sound.play(Sfx.UNDO)
            hintCell = null; generation++
            syncState(); syncShown()
        }
    }

    /** Hint logic is retained; the approved gameplay design has no Hint control, so the UI does not expose it. */
    fun hint() {
        val s = state ?: return
        if (!isHumanTurn()) return
        hintCell = Ai.hint(s)
    }

    fun requestConfirm(c: Confirm) { confirm = c }
    fun applyConfirm() {
        when (confirm) {
            Confirm.RESTART -> startGame()
            Confirm.NEW_GAME -> toSetup()
            Confirm.EXIT -> toMainMenu()
            null -> {}
        }
        confirm = null
    }
}
