# Proximity -- Offline UPI (`*99#` USSD & `UPI 123Pay` IVR) & Carrier Recharge Android App

**Package:** `com.proximity.app`  
**Target / Compile SDK:** `35` (Android 15) | **Min SDK:** `29` (Android 10)  
**Toolchain:** AGP `8.5.0`, Kotlin `2.0.0`, KSP `2.0.0-1.0.13`, Compose BOM `2024.10.00`, Room `2.6.1` + SQLCipher `4.6.1`, CameraX `1.4.2`, ZXing Core `3.5.3`, Lottie Compose `6.7.1`.

## Overview
Proximity is a clean-room, local-first offline UPI payment aggregator and mobile recharge companion for India:
1. **Scan QR & Pay (`*99#` USSD)**: Offline CameraX + ZXing QR decoding and gallery picker, sensitive clipboard copy on Android 13+, `*99*1*3#` dial handoff, session-scoped auto-disabling `UssdDialogReaderService` (`disableSelf()` after completion or app backgrounding), and 25-second manual countdown + incoming bank SMS fallback. Automatically waived/gated on Reliance Jio SIMs (`USSD_UNSUPPORTED_CARRIERS = setOf("jio")`).
2. **Pay Phone / Contact (`UPI 123Pay` IVR `08045163666`)**: Two-step recipient + whole-rupee (`Rs 1` to `Rs 4,999`) input with searchable contact picker, pre-call step-by-step guidance screen, `CallRouterService` (`android.telecom.InCallService`) keeping the system dialer behind the in-app full-screen `PaymentProgressScreen`, 5-second minimum call duration enforcement via `CallMonitor`, and bank SMS verification.
3. **In-App Carrier Recharge (`RechargeWebScreen`)**: Cellular-bound WebView (`ConnectivityManager.bindProcessToNetwork`) for Jio, Airtel, Vi, and BSNL portals with shimmer skeleton loader, `upi://` and `intent://` deep-link handoff, SSL error user confirmation dialog, and error retry state.
4. **Encrypted Local Vault (`LocalVaultDatabase`)**: SQLCipher 4.6.1 encrypted Room database backed by an Android Keystore `AES/GCM/NoPadding` 256-bit master key (`VaultKeyManager`) with self-healing key regeneration on corruption and zero raw SMS storage (synthesized privacy-safe excerpts only).

## License & Intellectual Property

Copyright &copy; 2024-2026 **Proximity**. All rights reserved.

This software, source code, design systems, and documentation are **proprietary and confidential**. Unauthorized copying, modification, redistribution, reverse engineering, or commercial use without prior written consent from **Proximity** is strictly prohibited. See the [LICENSE](LICENSE) file for complete terms.

