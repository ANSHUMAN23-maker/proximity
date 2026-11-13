# Proximity Design System & Web Architecture (v2)

## 1. Visual Identity & Authentic App Parity
- **Dual Theme Parity (`Light` & `Dark`)**: Toggling `[data-theme="dark"]` switches BOTH the outer site AND the simulated Proximity Android interface between Light surfaces (`#F8FAFC`, `#FFFFFF`, `#F1F5F9`, `#0F172A`, `#008774`) and Dark surfaces (`#090D14`, `#0B0F17`, `#101721`, `#131B26`, `#1F2A38`, `#00A38D`, `#F8FAFC`). Zero Gold (`#E8B45A` / `#FBBF24`) anywhere.
- **Signature Textures**:
  - **Animated Contour Background**: Subtle fixed `<canvas>` porting the 5 harmonic cubic-bezier contours from `AnimatedContourBackground.kt`, gently responding with damped elastic ripple wake around the pointer (`hover: hover`).
  - **Liquid Glass Surfaces**: Translucent frosted panels with top specular sheen and hairline border reflections ported from `LiquidGlass.kt` & `GlassTokens.kt`.

## 2. Single Continuous Scroll Architecture
- Zero horizontal scroll-jacking and zero full-screen pin locks.
- **Unified Story Flow (`#flow`)**: ONE sticky Android device frame (`#story-phone`, clean Hero opening with zero orbiting emblem) travels naturally beside vertically stacked narrative beats as you scroll down (`Setup` -> `MainScreen` -> `QRScanner` -> `PaymentResult`), with `Rim` / `Rupee ₹` depth planes separating subtly mid-flow.
- **Tactile Liquid-Glass Pill Playground + Three Relatable Fallback Rails (`#rails`)**: A draggable/flickable clone of `FloatingBottomNavBar.kt` (`DampedDragAnimation` velocity stretch & spring snap) placed directly on the canvas above three relatable visual schematics (real Proximity vector icons + Voice IVR call prompts, Merchant QR + `*99#`, Zero-Data Cellular Walled Garden).
- **Security & Carrier Matrix (`#security`)**, **Searchable 16-Item FAQ (`#faq`)**, **Climax Close (`#climax` with live `splash_logo.json`)**, and **Waitlist Dialog**.
