# Design decisions (corrective actions G-01 … G-23)

Full table with status: `Circles_Triangle_Design_Audit.xlsx` → sheet **Gaps & Decisions**.

| ID | Decision |
|---|---|
| G-01 | Splash tagline follows Kite: **THINK • CONNECT • WIN** |
| G-02 | Victory shows "9 **rows**" (Kite says "9 nodes", which is inaccurate) – deviation, needs approval |
| G-03 | player1/player2/lattice/marker-outline/scrim become named theme tokens; recommend adding them to the Kite design system |
| G-04 | **Open:** Inter TTFs (700/800/900) must be added to `res/font` – not downloadable here |
| G-05 | Online/Wi-Fi connection UI: bottom sheet built from existing card/button tokens – deviation |
| G-06 | Difficulty card hidden outside Computer mode |
| G-07 | Draw / loss variants reuse Victory layout – deviation |
| G-08 | Hint = pulsing gold-light ring, static under reduced motion |
| G-09 | Disabled Undo/Hint = 40% opacity |
| G-10 | Settings button → dialog with Music and Sound-effects switches (implemented v0.2) – deviation; music button also added to the game header and pause card |
| G-11 | Boards of 7–13 rows scale the 5-row triangle (formula in design_tokens.json) |
| G-12 | Large boards have pitch < 48 dp → nearest-circle hit-testing. v0.2: the confirm-tap step was **removed** on request; a tap plays the nearest empty circle directly (watch for mis-taps on 8/9 rows; Undo remains available) |
| G-13 | Pause dialog drawn over the live game |
| G-14 | Splash progress 0→100 % in 1.8 s |
| G-15 | **Open rule question:** do 1-circle "lines" score? Engine parameter `minLineLength` (default 2) |
| G-16 | **Open:** the 20 original scoring fixtures are not available |
| G-17 | Open: Kite comments / prototype links not yet inspected |
| G-18 | Open: two-device LAN and secure online tests must be run manually |
| G-19 | v0.2 Computer mode names: "You" / "Computer" instead of Player 1 / Player 2 (turn title "Your Turn"; result "YOU WIN!" / "DEFEAT") |
| G-20 | v0.2 Random first player (Computer mode only) with "Who goes first?" spotlight draw; engine `GameState.initial(board, first)`. Undo never removes the computer's opening move |
| G-21 | v0.2 Two Players: optional name fields (12 chars max) on setup; blank = default |
| G-22 | v0.2 Audio synthesised at runtime (no licensed assets); music + effects switches persisted |
| G-23 | v0.2 Extra motion beyond the storyboard (splash draw-on, board entrance, confetti, button press) – deviation, needs design sign-off; all disabled under reduced motion |

Turn rule used: players strictly alternate (no extra turn for scoring) – taken from "exactly one valid move per turn".

## App implementation notes (v0.1)
- Player 2 circles get a small white inner dot so players are distinguishable without colour (deviation, no numerals).
- Online / Wi-Fi mode rows are visible but disabled and labelled "NOT YET AVAILABLE".
- "Main Menu" returns to the splash, which auto-advances to Setup; "New Setup" goes straight to Setup.
- Material icons replace Lucide until the Kite SVG paths are converted to VectorDrawables.
