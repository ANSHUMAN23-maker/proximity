# Splash Screen Concepts & Animation Ideas

Here are 3 distinct design concepts for the Proximity animated splash screen:

---

### Concept 1: "Radiant Pulse" (Implemented in Draft)
- **Visuals:** Central Proximity brand emblem with soft, continuous radial pulsing aura rings radiating outward.
- **Motion:** Logo scales up smoothly from 40% to 100% with spring dampening while ambient background contour waves gently move.
- **Best For:** Clean, modern, high-tech fintech feel.

---

### Concept 2: "Signal Connecting" (USSD / Telecom Inspired)
- **Visuals:** An animated signal wave / wifi-off indicator icon that transitions into a connected checkmark/shield badge.
- **Motion:** A horizontal signal wave line draws itself across the center, expanding into the Proximity wordmark.
- **Best For:** Emphasizing offline capability & safety.

---

### Concept 3: "Lottie Vector Animation"
- **Visuals:** A custom vector JSON animation file placed in `app/src/main/assets/lottie/splash_logo.json`.
- **Motion:** Rendered using the `LottieAnimation()` composable in Compose for complex motion graphics.
- **Best For:** High-fidelity custom brand logo reveals.

---

## Key Timing Specs
- **Entrance Animation:** 600ms – 800ms
- **Hold Duration:** 1200ms – 1800ms
- **Exit Crossfade:** 300ms – 500ms
- **Total Splash Time:** ~2.0s – 2.5s (fast & snappy, never frustrating for the user)
