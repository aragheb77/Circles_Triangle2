# The Circles Triangle (Android)

Native Kotlin / Jetpack Compose. Modules: `engine` (pure Kotlin rules, AI, undo, hint) and `app` (Compose UI).

## Build the debug APK
Locally: open in Android Studio and run `app`, or `gradle :app:assembleDebug`
-> `app/build/outputs/apk/debug/app-debug.apk`.
CI: `.github/workflows/android.yml` runs engine tests, builds the APK and uploads it as artifact `circles-triangle-debug-apk`.

## Implemented
Splash > Setup > Game > Pause / Victory; Computer (Easy/Medium/Hard) and Two Players on one device; 5/7/9/11/13 rows; undo, hint, pause/restart/exit with confirmation; one-tap circle selection on every board size.

## v0.2 changes
- **Motion** (follows the Kite "Gameplay motion storyboard"): splash logo draws itself and circles pop in; screen cross-fade/scale transitions; board builds in row by row; placed circle springs up; scoring line draws with a glow; score card bumps and counts up; turn title slides; active card breathes; pause card springs in; victory emblem springs in and floats, scores count up, confetti on a win; buttons spring down when pressed. Everything is skipped when the system animation scale is 0 (reduced motion).
- **Sound**: effects (tap, place - different pitch per player, scoring, undo, draw-the-starter, win/lose/draw) and looping background music. Nothing is bundled: all audio is synthesised at start-up (`audio/Sound.kt`). **Music on/off** and **Sound effects on/off** are in Settings (gear on the setup screen); a music button is also in the game header and the pause menu. Choices are remembered.
- **Computer mode names**: "You" and "Computer" everywhere (turn title, score cards, thinking pill, result screen).
- **Random first player** (Computer mode): a "Who goes first?" spotlight animation picks You or Computer at random every game; the computer moves immediately if it wins the draw. Two Players: Player 1 starts.
- **Large boards**: the highlight-then-confirm step is removed; one tap plays the circle.
- **Two Players**: optional name fields on the setup screen (max 12 characters; empty = "Player 1" / "Player 2"); names are used in the turn title, score cards and result screen.

## Not implemented / known limitations
- Online and Wi-Fi/LAN modes (shown disabled), server, saving/restoring games.
- Inter font not bundled (system font used); icons are Material, not Kite's Lucide; splash logo circle positions approximated.
- No per-circle TalkBack nodes (board has a summary label); portrait only.
- Not yet compared pixel-by-pixel with Kite; no UI tests.
- Kotlin/Android sources were written without a compiler (also the v0.2 changes): the first CI run may need compile fixes.
- Sounds/music are synthesised, so they are simple; to use recorded audio replace the generators in `audio/Sound.kt` with `res/raw` files.
- Open: the 20 scoring fixtures and the minimum-line-length rule (docs/DESIGN_DECISIONS.md, G-15/G-16).
