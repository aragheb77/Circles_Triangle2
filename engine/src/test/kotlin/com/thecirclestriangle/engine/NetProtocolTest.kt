package com.thecirclestriangle.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetProtocolTest {
    private fun roundTrip(m: NetMsg) = assertEquals(m, NetProtocol.decode(NetProtocol.encode(m)))

    @Test fun everyMessageSurvivesARoundTrip() {
        roundTrip(NetMsg.Hello(NET_PROTOCOL_VERSION, "Ana"))
        roundTrip(NetMsg.Hello(1, ""))
        roundTrip(NetMsg.Start(4, 9, Player.TWO))
        roundTrip(NetMsg.Start(0, 5, Player.ONE))
        roundTrip(NetMsg.Move(4, 17))
        roundTrip(NetMsg.Rematch); roundTrip(NetMsg.Ping); roundTrip(NetMsg.Bye)
    }

    @Test fun namesAreCleaned() {
        assertEquals("Ana", NetProtocol.cleanName("  Ana  "))
        assertEquals("AnaBob", NetProtocol.cleanName("Ana|Bob\n"))
        assertEquals(12, NetProtocol.cleanName("abcdefghijklmnopqrstuvwxyz").length)
        assertEquals("", NetProtocol.cleanName("|||\t"))
        // a name can never smuggle a second field into the line
        val line = NetProtocol.encode(NetMsg.Hello(1, "x|M|0|1"))
        assertEquals(NetMsg.Hello(1, "xM01"), NetProtocol.decode(line))
    }

    @Test fun nameTruncationNeverSplitsAnEmoji() {
        val s = NetProtocol.cleanName("12345678901\uD83D\uDE00")   // 11 chars + one emoji (2 UTF-16 units) = 13
        assertTrue(s.none { it.isSurrogate() })
        assertTrue(s.length <= NetProtocol.MAX_NAME)
    }

    @Test fun malformedInputIsRejectedNotThrown() {
        for (bad in listOf("", "X", "M|1", "M|a|2", "M|1|-3", "M|-1|3", "S|1|2|0", "S|1|20|0", "S|1|7|2", "S|1|7", "H|1", "R|1", "P|", "M|1|2|3", "||", "M|99999999999|1")) {
            assertNull(NetProtocol.decode(bad), "should reject '$bad'")
        }
        assertNull(NetProtocol.decode("H|1|" + "a".repeat(500)))
    }

    @Test fun trailingCarriageReturnIsTolerated() {
        assertEquals(NetMsg.Move(1, 2), NetProtocol.decode("M|1|2\r"))
    }

    @Test fun movesReplayedFromTheWireGiveTheSameGameOnBothDevices() {
        // host and guest both start from the same Start message and apply the same moves
        val start = NetProtocol.decode(NetProtocol.encode(NetMsg.Start(1, 5, Player.TWO))) as NetMsg.Start
        val host = Game(GameState.initial(Board.of(start.rows), start.first))
        val guest = Game(GameState.initial(Board.of(start.rows), start.first))
        for (cell in listOf(0, 1, 2, 3, 4, 5)) {
            val m = NetProtocol.decode(NetProtocol.encode(NetMsg.Move(1, cell))) as NetMsg.Move
            host.play(cell); guest.play(m.cell)
        }
        assertEquals(host.state.scoreOne, guest.state.scoreOne)
        assertEquals(host.state.scoreTwo, guest.state.scoreTwo)
        assertEquals(host.state.current, guest.state.current)
    }
}
