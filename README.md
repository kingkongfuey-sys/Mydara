# Mydara

A fast, tactical real-time strategy game for Android. Planets are connected by fixed links: send waves of units along them, break enemy shields, capture planets and take the whole map. Kotlin + Jetpack Compose, no external game libraries, no image or sound files (sound effects are synthesized in code).

## How to run
1. Push this repository to GitHub.
2. Open the **Actions** tab and wait for the **Android CI** run to finish (a few minutes).
3. Open the run, scroll to **Artifacts** and download **`mydara-debug`**. It contains `app-debug.apk`.
4. Copy the APK to an Android 8.0+ phone and install it (allow installs from unknown sources when asked).

If a run fails, the log shows the error, and a `mydara-reports` artifact with the Gradle reports is attached.

## Build locally
There is no Gradle wrapper JAR in the repository (only `gradle/wrapper/gradle-wrapper.properties`).
- Install JDK 17 and Gradle 8.7, then run `gradle assembleDebug` (APK in `app/build/outputs/apk/debug/`) and `gradle test`.
- Or generate a wrapper once with `gradle wrapper --gradle-version 8.7` and use `./gradlew` from then on.

## Gameplay
- Menu, skirmish setup (map size, 1-3 opponents, difficulty, fog of war), settings (sound, haptics).
- Maps: Small 10, Medium 20, Large 30, Huge 50 planets. Every match is a freshly generated map, centered on screen.
- Tap one of your planets to select it, then tap a linked planet to send everything. Long-press first, then tap, to send half. Swipe from your planet toward a linked planet to set an auto-target that re-sends its garrison. Tap an auto-targeting planet to cancel. Drag to pan (freely, at any zoom), pinch to zoom.
- Planets: normal (7 shield points), defensive (14 shield points, 3x faster regeneration), economic (no shield, double growth). Upgrades cost 10 units. Neutral planets are always normal.
- Three AI difficulties. All of them build every planet type; they differ only in tactics.
- Fog of war (optional): you see planet details one link from your planets and enemy traffic within two links. The AI plays under the same limits.
- In-game menu (three dots): Resume, Restart (same map), New map (same settings), Exit to menu. It pauses the game.

## Project layout
- `app/src/main/java/com/example/mydara/game/` - pure Kotlin simulation: `GameModel`, `Planet`, `Link`, `UnitBatch`, `MapGenerator`, `PathFinder`, `AiController`.
- `app/src/main/java/com/example/mydara/ui/` - Compose screens and renderer (`GameScreen`, `GameRenderer`, `MenuScreen`, `SetupScreen`, `SettingsScreen`, `PauseMenu`), plus the pure UI logic that is unit tested (`Camera`, `InputController`, `GameSession`, `Feedback`, `SfxSynth`, `Effects`, `GroundField`, `SetupConfig`).
- `app/src/test/` - unit tests for all of the pure logic.
- `.github/workflows/android-ci.yml` - the APK build.
- `DECISIONS.md` - every decision made where the brief did not say.
