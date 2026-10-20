# Proximity Animated Splash Screen Sandbox

This folder is an isolated workspace for designing, prototyping, and testing an animated splash screen for the **Proximity** Android app without affecting the main build or production code.

---

## Workspace Structure

- `AnimatedSplashScreenDraft.kt` — A standalone Jetpack Compose composable template featuring scale, fade, and glowing ring animations.
- `splash_ideas.md` — Animation specs, timing guidelines, and visual concept notes.

---

## How to Integrate with the App (When Ready)

When you're ready to enable the splash screen in the production app:

1. Copy `AnimatedSplashScreenDraft.kt` (or your final composable) into `app/src/main/java/com/proximity/app/ui/activities/` or `ui/components/`.
2. Set up the entry Activity (or launch route) in `AndroidManifest.xml` or `MainActivity.kt`.
3. Call `onSplashFinished()` to navigate smoothly to `MainActivity` or `SetupActivity`.
