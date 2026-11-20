# Design handover (2026-11-20) - implementation status

Status legend: DONE = implemented in code; UNVERIFIED = implemented but not yet compiled/run/compared on device.
Nothing below is "verified" until the CI run is green and the APK has been installed and compared with Kite.

| Checklist item | Status | Notes |
|---|---|---|
| Setup shows 3 play modes (Computer / Two Players / Online) | DONE, UNVERIFIED | Lucide cpu / users / globe from Kite paths |
| Online Multiplayer remains | DONE | Selectable as in the design; Start shows "not available in this version" (no networking exists - not faked) |
| No separate Wi-Fi option | DONE | Removed |
| Board sizes 5-9 | DONE | Engine validation range widened to 5-13 (geometry/scoring untouched); UI offers 5-9 |
| Easy / Medium / Hard | DONE | Shown for Play Against Computer only |
| 15-circle triangular board, light lattice, no black fill | DONE, UNVERIFIED | Lattice #E7EAF7 1.5 px; renders the real game state |
| Player 1 orange / Player 2 blue turn states | DONE, UNVERIFIED | Header, score card, panel tint and border follow the turn |
| Board panels at 15% opacity | DONE | tint alpha 0.15 |
| Active label / score-card emphasis dynamic | DONE | cross-fade 200 ms |
| 4 feedback stages | DONE, UNVERIFIED | Thinking pill (450-650 ms) > line draw 300 ms + node pulse > "+N" float + count-up > tint cross-fade. Reduced-motion skips delays |
| Undo and Pause preserved | DONE | |
| Pause modal | DONE | RESUME GAME / Restart Game / Game Setup / Exit to Main Menu, confirmations kept |
| P1 Victory, P2 Victory, Defeat, Draw distinct | DONE, UNVERIFIED | Emblems drawn from Kite SVG paths |
| Result identities / scores dynamic | DONE | From the real match result |
| Business logic unchanged | DONE | Only change: Board.SUPPORTED_ROWS validation (5..13) + its tests |
| Build and tests pass | NOT CONFIRMED | Run CI |
| Compared against Kite | NOT DONE | Needs device screenshots |

## Decisions / deviations (please confirm)
1. **Hint control removed from the UI.** The approved Gameplay lists only Undo and Pause. Hint logic remains in the code.
2. **Defeat vs Player 2 Victory.** Computer mode: Player 2 (computer) wins -> Defeat; Two Players: Player 2 wins -> Player 2 Victory.
3. **Settings icon** opens a "not available yet" dialog (no settings screen is designed).
4. **Computer identification** is by the "AI IS THINKING" pill (as in the hidden Kite overlay); score cards read PLAYER 1 / PLAYER 2 as designed (the YOU/AI tags are hidden in Kite).
5. **Emblem gradients:** Kite's SVG gradients use bounding-box units with absolute coordinates, which render as their first stop; the emblems use those solid colours.
6. **"9 nodes" text** (Kite P1 victory) is shown as "N rows" (rows is the unit that matches the setup).
7. Board sizes 11 and 13 remain valid in the engine but are not offered in the UI.
8. Inter font still not bundled (system font fallback).
