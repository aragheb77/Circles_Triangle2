package com.thecirclestriangle.engine

/** Bumped whenever the wire format changes; the two devices must agree. */
const val NET_PROTOCOL_VERSION = 1

/**
 * Messages exchanged between two nearby devices (Wi-Fi or Bluetooth). The host plays [Player.ONE], the guest
 * [Player.TWO]. Both devices run the same deterministic engine and only exchange the *moves*, so there is no
 * shared state that can drift apart.
 */
sealed interface NetMsg {
    /** First message from both sides. */
    data class Hello(val version: Int, val name: String) : NetMsg
    /** Host -> guest: a (re)match begins. [first] is the player who opens. */
    data class Start(val matchId: Int, val rows: Int, val first: Player) : NetMsg
    /** A move in match [matchId]. */
    data class Move(val matchId: Int, val cell: Int) : NetMsg
    /** "I would like to play again." A new match starts when both sides have sent it. */
    data object Rematch : NetMsg
    /** Keep-alive. */
    data object Ping : NetMsg
    /** Polite goodbye (the player left on purpose). */
    data object Bye : NetMsg
}

/** One message = one line of text: `H|1|Ana`, `S|3|7|1`, `M|3|12`, `R`, `P`, `B`. Decoding never throws. */
object NetProtocol {
    const val MAX_LINE = 120
    const val MAX_NAME = 12
    const val MIN_ROWS = 3
    const val MAX_ROWS = 13
    private const val SEP = '|'

    /** Removes control characters and the separator, trims, and limits the length (never splits a surrogate pair). */
    fun cleanName(raw: String): String {
        val kept = raw.filter { !it.isISOControl() && it != SEP && it != '\u2028' && it != '\u2029' }.trim()
        var s = kept.take(MAX_NAME)
        if (s.isNotEmpty() && s.last().isHighSurrogate()) s = s.dropLast(1)
        return s.trim()
    }

    fun encode(m: NetMsg): String = when (m) {
        is NetMsg.Hello -> "H$SEP${m.version}$SEP${cleanName(m.name)}"
        is NetMsg.Start -> "S$SEP${m.matchId}$SEP${m.rows}$SEP${m.first.index}"
        is NetMsg.Move -> "M$SEP${m.matchId}$SEP${m.cell}"
        NetMsg.Rematch -> "R"
        NetMsg.Ping -> "P"
        NetMsg.Bye -> "B"
    }

    /** Returns null for anything malformed, oversized or unknown. */
    fun decode(line: String): NetMsg? {
        val text = line.trimEnd('\r')
        if (text.isEmpty() || text.length > MAX_LINE) return null
        val p = text.split(SEP)
        return try {
            when (p[0]) {
                "H" -> if (p.size == 3) NetMsg.Hello(p[1].toInt(), cleanName(p[2])) else null
                "S" -> {
                    if (p.size != 4) return null
                    val match = p[1].toInt(); val rows = p[2].toInt(); val first = p[3].toInt()
                    if (match < 0 || rows !in MIN_ROWS..MAX_ROWS || first !in 0..1) null
                    else NetMsg.Start(match, rows, Player.values()[first])
                }
                "M" -> {
                    if (p.size != 3) return null
                    val match = p[1].toInt(); val cell = p[2].toInt()
                    if (match < 0 || cell < 0) null else NetMsg.Move(match, cell)
                }
                "R" -> if (p.size == 1) NetMsg.Rematch else null
                "P" -> if (p.size == 1) NetMsg.Ping else null
                "B" -> if (p.size == 1) NetMsg.Bye else null
                else -> null
            }
        } catch (e: NumberFormatException) {
            null
        }
    }
}
