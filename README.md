# Mydara

A fast, tactical real-time strategy game for Android: link planets, send waves of units, break enemy shields and take the whole map. Kotlin + Jetpack Compose, no external game libraries.

Gameplay rules are modeled on the skirmish mode of [Opacha-mda](https://github.com/yiotro/Opacha-mda) by yiotro. Mydara is an independent re-implementation and shares no code with it.

## Get the APK
1. Push this repo to GitHub.
2. Open the **Actions** tab, then the latest **Android CI** run.
3. Download the `mydara-debug` artifact (contains `app-debug.apk`).

## Build locally
Install Gradle 8.7 and JDK 17, then run `gradle assembleDebug` (no wrapper JAR is included).

## Status
Stages 0-4 done, plus sound and haptics: core model, map generator + AI, game screen, main menu, skirmish setup, settings. Flow: Menu -> Setup -> Game -> result -> Setup (system back goes one step up; no quit button). Neutral planets are always normal planets. Sound effects are synthesized in code (no audio assets).