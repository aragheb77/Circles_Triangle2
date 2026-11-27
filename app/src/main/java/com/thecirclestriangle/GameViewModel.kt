package com.thecirclestriangle

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thecirclestriangle.audio.Sfx
import com.thecirclestriangle.audio.Sound
import com.thecirclestriangle.engine.*
import com.thecirclestriangle.nearby.Connection
import com.thecirclestriangle.nearby.NearbyController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class Screen { SPLASH, SETUP, NEARBY, GAME, VICTORY }
enum class GameMode { COMPUTER, TWO_PLAYERS, NEARBY, ONLINE }
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

class GameViewModel(app: Application) : AndroidViewModel(app) {
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

    /** The player this device controls: You vs the computer, or this phone in Play Nearby. Null = shared device. */
    val localPlayer: Player? get() = when (mode) {
        GameMode.COMPUTER -> Player.ONE
        GameMode.NEARBY -> nearbyLocal
        else -> null
    }

    /** True when [p] is the person holding this device (shown as "You"). */
    fun isYou(p: Player): Boolean = localPlayer == p

    /** Name shown in the UI: "You" / "Computer", the nearby opponent's name, or the typed name / "Player N". */
    fun nameOf(p: Player): String = when {
        isYou(p) -> "You"
        mode == GameMode.COMPUTER -> "Computer"
        mode == GameMode.NEARBY -> remoteName.ifEmpty { "Opponent" }
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
        // (no undo in Play Nearby: the other phone has already seen the move)
        canUndo = g != null && mode != GameMode.NEARBY &&
            if (mode == GameMode.COMPUTER) g.canUndoUntilTurnOf(Player.ONE) else g.canUndo
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
        if (mode == GameMode.NEARBY) { openNearby(); return }
        if (!canStart) return
        // Against the computer who opens is random; in Two Players Player 1 opens.
        beginMatch(if (mode == GameMode.COMPUTER && Random.nextBoolean()) Player.TWO else Player.ONE)
    }

    /** Sets up a fresh board where [first] opens and shows the game screen (shared by every mode). */
    private fun beginMatch(first: Player) {
        cancelFlow()
        game = Game(GameState.initial(Board.of(rows), first))
        hintCell = null; showPause = false; confirm = null
        rematchMine = false; rematchTheirs = false
        gameId++
        syncState(); syncShown()
        screen = Screen.GAME
        if (mode == GameMode.COMPUTER || mode == GameMode.NEARBY) beginStarterDraw(first)
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
            if (first == Player.TWO && mode == GameMode.COMPUTER) aiTurn(gen)
        }
    }

    fun toSetup() { cancelFlow(); dropLink(); showPause = false; confirm = null; screen = Screen.SETUP }
    fun toMainMenu() { cancelFlow(); dropLink(); showPause = false; confirm = null; screen = Screen.SPLASH }

    fun pause() { if (screen == Screen.GAME) showPause = true }
    fun resume() { showPause = false; confirm = null }

    fun isHumanTurn(): Boolean {
        val s = state ?: return false
        val me = localPlayer
        return !s.isOver && stage == Stage.IDLE && !aiThinking && !starterActive &&
            (me == null || s.current == me) && (mode != GameMode.NEARBY || conn != null)
    }

    /** One tap plays the circle (the former highlight-then-confirm step has been removed on every board size). */
    fun onCellTap(cell: Int) {
        val s = state ?: return
        if (screen != Screen.GAME || showPause || !isHumanTurn() || !s.isEmpty(cell)) return
        if (playCell(cell) && mode == GameMode.NEARBY) conn?.send(NetMsg.Move(matchId, cell))
    }

    private fun playCell(cell: Int): Boolean {
        val g = game ?: return false
        val before = g.state
        val out = g.play(cell)
        if (out !is MoveOutcome.Accepted) return false
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
                val me = localPlayer          // null on a shared device: every win is a "win" sound
                Sound.play(
                    when (out.state.result) {
                        MatchResult.PLAYER_ONE_WINS -> if (me == null || me == Player.ONE) Sfx.WIN else Sfx.LOSE
                        MatchResult.PLAYER_TWO_WINS -> if (me == null || me == Player.TWO) Sfx.WIN else Sfx.LOSE
                        else -> Sfx.DRAW
                    },
                )
                screen = Screen.VICTORY; return@launch
            }
            if (mode == GameMode.COMPUTER && out.state.current == Player.TWO) aiTurn(gen)
        }
        return true
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

    // ------------------------------------------------------------------ Play Nearby (Wi-Fi / Bluetooth)
    val nearby = NearbyController(app)

    /** Name typed in the nearby lobby (shown to the other player). */
    var myName by mutableStateOf("")
    var remoteName by mutableStateOf("")
        private set
    /** Set when the other phone left or the link dropped; the game / result screen shows it. */
    var nearbyEnded by mutableStateOf<String?>(null)
    /** A phone is connected (the match starts as soon as both sides have said hello). */
    var nearbyConnected by mutableStateOf(false)
        private set
    var rematchMine by mutableStateOf(false)
        private set
    var rematchTheirs by mutableStateOf(false)
        private set

    private var nearbyLocal = Player.ONE        // the host plays ONE (orange), the guest TWO (blue)
    private var nearbyHost = false
    private var conn: Connection? = null
    private var matchId = 0
    private var helloReceived = false
    private val remoteMoves = Channel<Pair<Int, Int>>(Channel.UNLIMITED)   // (matchId, cell)
    private var remoteJob: Job? = null

    init { nearby.onLinked = { c, host -> onNearbyLinked(c, host) } }

    private fun myDisplayName(): String = NetProtocol.cleanName(myName).ifEmpty { "Player" }

    /** True when it is the other phone's turn and we are waiting for its move. */
    val waitingForRemote: Boolean
        get() {
            val s = state
            return mode == GameMode.NEARBY && s != null && !s.isOver && !starterActive && stage == Stage.IDLE &&
                s.current != nearbyLocal && nearbyEnded == null
        }

    fun openNearby() {
        mode = GameMode.NEARBY
        cancelFlow(); dropLink()
        nearbyEnded = null; nearby.error = null
        screen = Screen.NEARBY
    }

    fun leaveNearby() { dropLink(); nearby.stop(); nearbyEnded = null; screen = Screen.SETUP }

    /** Closes the current link (telling the other phone) and discards anything still queued from it. */
    private fun dropLink() {
        nearby.stop()
        conn?.let { it.listener = null; it.close() }
        conn = null
        remoteJob?.cancel(); remoteJob = null
        while (remoteMoves.tryReceive().isSuccess) { /* discard */ }
        helloReceived = false; rematchMine = false; rematchTheirs = false; remoteName = ""
        nearbyEnded = null; nearbyConnected = false
    }

    private fun onNearbyLinked(c: Connection, host: Boolean) {
        dropLink()
        conn = c; nearbyHost = host; nearbyLocal = if (host) Player.ONE else Player.TWO
        matchId = 0; nearbyConnected = true
        c.listener = object : Connection.Listener {
            override fun onMessage(msg: NetMsg) = onNet(c, msg)
            override fun onClosed(reason: Connection.CloseReason) = onNetClosed(c, reason)
        }
        c.start()
        c.send(NetMsg.Hello(NET_PROTOCOL_VERSION, myDisplayName()))
        startRemoteLoop()
    }

    private fun onNet(c: Connection, msg: NetMsg) {
        if (c !== conn) return
        when (msg) {
            is NetMsg.Hello -> {
                if (helloReceived) return
                if (msg.version != NET_PROTOCOL_VERSION) { ended("The other phone has a different version of the game. Please update both phones."); return }
                helloReceived = true
                remoteName = msg.name.ifEmpty { "Opponent" }
                if (nearbyHost) startNearbyMatch()
            }
            is NetMsg.Start -> if (!nearbyHost && helloReceived) { matchId = msg.matchId; rows = msg.rows; beginMatch(msg.first) }
            is NetMsg.Move -> remoteMoves.trySend(msg.matchId to msg.cell)
            NetMsg.Rematch -> { rematchTheirs = true; maybeRematch() }
            NetMsg.Ping, NetMsg.Bye -> Unit
        }
    }

    private fun onNetClosed(c: Connection, reason: Connection.CloseReason) {
        if (c !== conn) return
        conn = null
        remoteJob?.cancel(); remoteJob = null
        val who = remoteName.ifEmpty { "The other player" }
        ended(if (reason == Connection.CloseReason.PEER_LEFT) "$who left the game." else "The connection to $who was lost.")
    }

    /** Ends the nearby session with a message: in the lobby as an error, otherwise as a dialog on the game / result screen. */
    private fun ended(message: String) {
        conn?.let { it.listener = null; it.close() }
        conn = null; nearbyConnected = false
        remoteJob?.cancel(); remoteJob = null
        if (screen == Screen.NEARBY) nearby.error = message else nearbyEnded = message
    }

    /** Host only: picks the opener at random, tells the guest, and starts the match here. */
    private fun startNearbyMatch() {
        matchId++
        val first = if (Random.nextBoolean()) Player.TWO else Player.ONE
        conn?.send(NetMsg.Start(matchId, rows, first))
        beginMatch(first)
    }

    fun requestRematch() {
        val c = conn ?: return
        if (rematchMine) return
        rematchMine = true
        c.send(NetMsg.Rematch)
        maybeRematch()
    }

    private fun maybeRematch() { if (rematchMine && rematchTheirs && nearbyHost && conn != null) startNearbyMatch() }

    /**
     * Applies the other player's moves one at a time. A move waits until the previous animation and the
     * "who starts" draw are finished, and is checked against the rules before it is played.
     */
    private fun startRemoteLoop() {
        remoteJob?.cancel()
        remoteJob = viewModelScope.launch {
            for ((mid, cell) in remoteMoves) {
                if (mid != matchId) continue
                while (mid == matchId && (stage != Stage.IDLE || starterActive)) delay(25)
                if (mid != matchId) continue
                val s = state ?: continue
                if (screen != Screen.GAME || s.isOver) continue
                if (s.current == nearbyLocal || cell !in 0 until s.board.cellCount || !s.isEmpty(cell)) {
                    ended("The other player sent a move that is not allowed.")
                    continue
                }
                playCell(cell)
            }
        }
    }

    override fun onCleared() { dropLink() }

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
