# The Circles Triangle (Android)

Native Kotlin / Jetpack Compose. Modules: `engine` (pure Kotlin rules, AI, undo, hint) and `app` (Compose UI).

## Build the debug APK
Locally: open in Android Studio and run `app`, or `gradle :app:assembleDebug`
-> `app/build/outputs/apk/debug/app-debug.apk`.
CI: `.github/workflows/android.yml` runs engine tests, builds the APK and uploads it as artifact `circles-triangle-debug-apk`.

## Implemented
Splash > Setup > Game > Pause / Victory; Computer (Easy/Medium/Hard) and Two Players on one device; 5/7/9/11/13 rows; undo, hint, pause/restart/exit with confirmation; select-then-confirm on 9+ rows.

## Not implemented / known limitations
- Online and Wi-Fi/LAN modes (shown disabled), server, saving/restoring games.
- Inter font not bundled (system font used); icons are Material, not Kite's Lucide; splash logo circle positions approximated.
- No per-circle TalkBack nodes (board has a summary label); portrait only.
- Not yet compared pixel-by-pixel with Kite; no UI tests.
- Kotlin/Android sources were written without a compiler: the first CI run may need compile fixes.
- Open: the 20 scoring fixtures and the minimum-line-length rule (docs/DESIGN_DECISIONS.md, G-15/G-16).
