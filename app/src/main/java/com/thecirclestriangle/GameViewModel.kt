package com.thecirclestriangle

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thecirclestriangle.engine.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

enum class Screen { SPLASH, SETUP, GAME, VICTORY }
enum class GameMode { COMPUTER, TWO_PLAYERS }
enum class Confirm { RESTART, NEW_GAME, EXIT }

class GameViewModel : ViewModel() {
    var screen by mutableStateOf(Screen.SPLASH)
        private set
    var mode by mutableStateOf(GameMode.COMPUTER)
    var rows by mutableIntStateOf(9)
    var difficulty by mutableStateOf(Difficulty.MEDIUM)

    var state by mutableStateOf<GameState?>(null)

        private set
    var canUndo by mutableStateOf(false)
        private set
    var pendingCell by mutableStateOf<Int?>(null)
        private set
    var hintCell by mutableStateOf<Int?>(null)
        private set
    var aiThinking by mutableStateOf(false)
        private set
    var showPause by mutableStateOf(false)
        private set
    var confirm by mutableStateOf<Confirm?>(null)

    private var game: Game? = null
    private var aiJob: Job? = null
    private var generation = 0

    /** Boards of 9+ rows have circles closer than 48 dp: first tap selects, second tap confirms. */
    private val needsConfirm: Boolean get() = rows >= 9

    private fun sync() {
        state = game?.state
        canUndo = game?.canUndo == true
    }

    private fun cancelAi() {
        aiJob?.cancel(); aiJob = null; aiThinking = false; generation++
    }

    fun finishSplash() { if (screen == Screen.SPLASH) screen = Screen.SETUP }

    fun startGame() {
        cancelAi()
        game = Game(GameState.initial(Board.of(rows)))
        pendingCell = null; hintCell = null; showPause = false; confirm = null
        sync()
        screen = Screen.GAME
    }

    fun toSetup() { cancelAi(); showPause = false; confirm = null; screen = Screen.SETUP }
    fun toMainMenu() { cancelAi(); showPause = false; confirm = null; screen = Screen.SPLASH }

    fun pause() { if (screen == Screen.GAME) showPause = true }
    fun resume() { showPause = false; confirm = null }

    fun isHumanTurn(): Boolean {
        val s = state ?: return false
        return !s.isOver && !aiThinking && (mode == GameMode.TWO_PLAYERS || s.current == Player.ONE)
    }

    fun onCellTap(cell: Int) {
        val s = state ?: return
        if (screen != Screen.GAME || showPause || !isHumanTurn() || !s.isEmpty(cell)) return
        if (needsConfirm && pendingCell != cell) { pendingCell = cell; hintCell = null; return }
        playCell(cell)
    }

    private fun playCell(cell: Int) {
        val g = game ?: return
        val out = g.play(cell)
        if (out !is MoveOutcome.Accepted) return
        pendingCell = null; hintCell = null
        sync()
        val s = g.state
        if (s.isOver) { screen = Screen.VICTORY; return }
        if (mode == GameMode.COMPUTER && s.current == Player.TWO) scheduleAi()
    }

    private fun scheduleAi() {
        aiThinking = true
        val gen = generation
        val diff = difficulty
        aiJob = viewModelScope.launch {
            delay(450)
            val snapshot = state ?: return@launch
            val move = withContext(Dispatchers.Default) { Ai.chooseMove(snapshot, diff) }
            if (gen != generation || move == null) return@launch
            aiThinking = false
            playCell(move)
        }
    }

    fun undo() {
        val g = game ?: return
        if (aiThinking || screen != Screen.GAME) return
        val ok = if (mode == GameMode.COMPUTER) g.undoUntilTurnOf(Player.ONE) else g.undo()
        if (ok || !g.canUndo) { pendingCell = null; hintCell = null; generation++; sync() }
    }

    fun hint() {
        val s = state ?: return
        if (!isHumanTurn()) return
        pendingCell = null
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
