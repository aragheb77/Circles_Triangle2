# Play Nearby - manual test plan

The nearby code could not be run in the sandbox where it was written (no Android SDK, no devices). Test on **two
real Android phones** (emulators cannot do Bluetooth, and two emulators are not on a shared Wi-Fi network by default).
Unit tests for the wire protocol are in `engine/src/test/.../NetProtocolTest.kt` (`./gradlew :engine:test`).

## Wi-Fi
1. Both phones on the same Wi-Fi. Phone A: Setup -> Play Nearby -> FIND A FRIEND -> Wi-Fi -> Host a game.
   Expect: radar + "Waiting for a player", an IP address shown.
2. Phone B: Wi-Fi -> Join a game. Expect: A's name appears within a few seconds -> tap it -> "Connected!" -> both
   phones show the "Who goes first?" draw with the same result -> play.
3. Typed address: on B choose Join, type A's address, Connect. Expect the same result.
4. Router with "client isolation" / guest network: discovery and typing both fail with a clear message (expected).

## Bluetooth
1. Allow the "Nearby devices" prompt (Android 12+) / Location prompt (<= 11, and keep Location switched ON).
2. A: Bluetooth -> Host a game (accept "make discoverable"). B: Bluetooth -> Join a game -> pick A's phone.
3. Never-paired phones should connect without a pairing code. If the first attempt fails the app retries once.

## Game behaviour (both transports)
- Names: each phone shows "You" and the other player's name; host = orange, guest = blue.
- Moves appear on the other phone with the line-draw / score animation; scoring and winner agree on both.
- Tapping while it is the other player's turn does nothing; the "NAME IS THINKING" pill shows.
- Result screen: PLAY AGAIN on one phone -> "waiting"; the other phone shows "X wants to play again"; when both
  press it a new match starts (random opener). New Setup / Main Menu on one phone -> the other sees "X left the game".
- Disconnect: switch off Wi-Fi/Bluetooth or move away mid-game -> within ~12 s the other phone shows "connection lost".
- Pause: Pause menu has no Restart/Game Setup in this mode; Exit asks for confirmation and tells the other phone.
- App to background: music pauses; the link survives short interruptions.
