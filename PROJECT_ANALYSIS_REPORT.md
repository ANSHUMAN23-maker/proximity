# OFFPAY 3 / Proximity — Project Analysis Report

*Generated from full codebase inspection*

---

## 1. LOGIC — How the Code Works & User Inconvenience Risks

### Core Architecture
- **UI Layer**: Jetpack Compose with Material3, single Activity per screen, ViewModels for state
- **Business Logic**: Helper classes (`MainActivityHelper`, `SetupHelper`, `TestConfigurationHelper`) isolate logic from UI
- **Telephony**: `CallStateCoordinator` (single authority, ref-counted listener) + `PaymentSessionManager` (only writer of payment lifecycle state)
- **Data**: Room + SQLCipher encrypted DB (`AppDatabase`), `TransactionDao`, `TransactionRepository`, `SettingsRepository`
- **SMS Ingestion**: `SmsTransactionParser` (pure, context-free parser) + `TransactionDetector` (operation window + cross-pipeline dedup)
- **Payments**: Two rails — *99# USSD (Scan QR) and UPI 123 IVR (Pay Contact); both gated by connectivity tests in `TestConfigurationActivity`

### Payment Flow (Manual / Pay Contact)
1. User fills phone + amount in `PayContactDialog` → `MainActivityHelper.initiateTransfer()`
2. Validates: phone format, amount > 0, amount ≤ ₹4,999 (UPI 123 IVR cap), overlay permission, phone permissions
3. `PaymentSessionManager.begin()` inserts **PENDING row before dialing** (survives process death)
4. `CallManager.initiateUPI123Call()` → `PaymentGuidanceActivity` → `PaymentProgressActivity`
5. `CallStateCoordinator` emits `OffHook` → `InProgress`; `Idle` after call → `WaitingForVerification`
6. Bank SMS arrives → `SmsTransactionParser.parse()` → `PaymentSessionManager.onSmsConfirmed()` → **SUCCESS/FAILED/NEEDS_REVIEW**
7. `PaymentResultActivity` launched by SMS pipeline; `PaymentProgressActivity` finishes

### QR / Scan-to-Pay Flow
- Locked until *99# test passes (`isUssdReady`)
- `QRScannerActivity` reads QR → extracts UPI ID / amount → `PaymentSessionManager.begin(source=QR)`
- USSD call placed; `PaymentSessionManager.onUssdCallEndedManually()` synthesizes call-end event
- Same SMS confirmation path as above

### Key Logic Invariants
- **No confirmation = no record**: PENDING rows past 10-min deadline are deleted (`onVerificationDeadline`, `reconcileStalePending`)
- **Supersession**: New payment cancels any in-flight session (discards its PENDING row)
- **Atomic claim-then-write**: `onSmsConfirmed`, `finishSession`, `onVerificationDeadline` all claim terminal state **inside a lock** before launching DB writes — prevents double-write races
- **Amount matching is paise-exact** (`isAmountMatching`) — ±₹0.99 tolerance removed

---

### User Inconvenience Risks

| Area | Issue | Impact |
|------|-------|--------|
| **Test Gate UX** | Both payment rails locked until *their* connectivity test passes. If *99# fails (e.g., Jio), Scan QR is permanently disabled unless user re-runs test or flags "USSD doesn't work" in setup. | User cannot scan QR on Jio even if they *know* it works on their specific plan; no "try anyway" escape hatch. |
| **Overlay Permission** | Required before Pay Contact dial. Dialog appears *after* user fills amount — late in flow. | Feels like a bait-and-switch; user invested effort then hits system settings. |
| **SMS Permission** | Asked at first payment (QR or Pay Contact). If denied, payment proceeds but confirmation won't be auto-detected. | Silent degradation — user may not realize why "Payment Successful" screen never appears. |
| **Dual-SIM Voice Mismatch** | Warning toast only; payment still dials on default voice SIM which may not be the UPI-registered SIM. | Payment mysteriously fails; user sees generic "Payment failed" with no actionable hint. |
| **TestConfigurationActivity Polling** | Uses 100ms `delay` loop to sync helper state to Compose (`LaunchedEffect(Unit) { while(true) { delay(100) ... } }`). | Burns CPU/battery while screen open; fragile — if helper state drifts, UI shows stale data. |
| **No Offline Queue** | If SMS permission denied *and* app killed before bank SMS arrives, payment is lost (PENDING row deleted on deadline). | User sees no history of the attempt; cannot manually mark "I paid this". |
| **Hardcoded Bank List** | 11 banks in `SetupHelper.getBanks()` + `SettingsActivity.banks`. Adding a new bank = code change + release. | Inflexible for users with smaller/regional banks. |
| **Setup Irreversibility** | `SettingsActivity.clearAllData()` wipes everything and restarts SetupActivity. No "reset test only" or "change bank only". | Overkill for minor changes; user re-enters SIM, permissions, disclaimer. |

---

## 2. UI — Designer Perspective: Attraction & Usability

### Visual Identity
- **Dark-only theme** (`ProximityTheme(darkTheme = true)` enforced) — near-black `AbyssalCharcoal #0D0D0D` background
- **Teal accent** `#008774` (spec teal) — primary, FAB, selected states, progress indicators
- **Gold** `#E8B45A` — success confirmation, warning states
- **Serif headings** (Typography: `FontFamily.Serif` for display/headline) + **Sans body** — editorial/premium feel
- **Max content width 420dp**, centered — phone-sized card on tablets, safe on foldables

### Screen-by-Screen Assessment

| Screen | Strengths | Weaknesses |
|--------|-----------|------------|
| **Main (Home)** | 3 equal-width action tiles (Scan QR, Pay Contact, Recharge) with clear lock/unlock states; floating pill bottom nav; bank pill in header; recent transactions inline | Tiles feel cramped at 110dp height; recharge tile visually identical to payment rails but does something totally different (web view) — no visual distinction; no empty-state illustration for "no transactions yet" |
| **Setup (4-step wizard)** | Hero typography (32sp serif), linear progress bar, tappable SIM cards with border selection, custom checkbox for dual-SIM, full-screen terms inline | Bank selection uses `ExposedDropdownMenu` (spinner) — feels dated vs. SIM cards' tappable cards; no keyboard avoidance for bank dropdown on small screens; disclaimer text wall is long |
| **Test Configuration** | Clear *99# vs UPI 123 separation; consent dialog before real call; "Skip for now" escape hatch; progress dialogs with live message | 100ms polling loop (see Logic); USSD config options appear *inside* progress dialog — confusing modal stack; Jio unsupported state shows warning icon but button still clickable (shows toast) |
| **Payment Progress** | Full-screen, centered amount (36sp), Lottie-style pulse animation per phase, status line animates, cancel button only while non-terminal | All phases use same `SmoothPulseAnimation` — no visual distinction between "connecting", "call active", "waiting for SMS"; no timer/progress ring showing verification deadline |
| **Payment Result** | Staggered entrance animation (checkmark → title → amount → details → Done button); amount counts up; error message inline | No haptic feedback on success; "Done" button only action — no "View History", "Pay Again", "Share Receipt" |
| **Transaction History** | Date-grouped (Today/Yesterday/This Week/Date), search with live filter, swipe-to-delete via detail dialog, status pill with color bar | No pull-to-refresh; no filter by status/bank/date range; empty state uses same error animation as error state — confusing |
| **Settings** | Grouped cards with dividers, toggle rows with switches, permission rows with "Granted"/"Grant" pills, bank/SIM pickers in dialogs | Bank/SIM pickers are full-screen dialogs — heavy for 4-11 options; no "Re-run tests" entry point (only in Setup); dark mode toggle but app is dark-only |
| **Recharge Web** | Scaffold with top bar, shimmer skeleton, thin progress bar, error/success overlays, UPI intent interception | WebView binds process to cellular network — if user has Wi-Fi + cellular, other apps lose cellular; no offline cached fallback |

### Interaction & Motion
- **Animations**: `AnimatedVisibility` (fade + expand/shrink), `AnimatedContent` (cross-fade), `animateFloatAsState` (amount count-up), `scaleIn`/`slideInVertically` for result screen
- **Missing**: No shared element transitions; no haptics; no spring physics (mostly linear/ease); no reduced-motion respect
- **Touch Targets**: Most buttons ≥ 48dp; some icon buttons 36dp (toolbar actions) — acceptable but tight

### Accessibility
- `contentDescription` on most icons; `clickable` with `MutableInteractionSource` + `indication = null` (custom ripples missing)
- Color contrast: Teal on `AbyssalCharcoal` ≈ 4.5:1 (AA large text only); `StatusGreen`/`StatusRed` on dark surface pass AA
- No dynamic type scaling test; `fontSize` in `sp` but no `CompositionLocal` for user font scale

---

## 3. PROBLEMS & BUGS — Developer Perspective

### Critical / High Severity

| # | File / Area | Problem | Fix |
|---|-------------|---------|-----|
| 1 | `TestConfigurationActivity.kt:222-237` | **100ms polling loop** in `LaunchedEffect(Unit)` — runs forever while screen open, burns CPU, races with helper state updates. | Replace with `MutableStateFlow` in `TestConfigurationHelper` and `collectAsState()` in Compose. |
| 2 | `PaymentSessionManager.kt:191-194` | **Watchdog delay uses `verificationDeadlineMs` (10 min)** but `onCallNeverStarted` also has 40s × 3 retries = 120s. If call never starts, user waits 2 min before `onCallNeverStarted` fires, then 10 min for watchdog — confusing. | Unify: single timeout source; fire `onCallNeverStarted` immediately when call fails to start; watchdog only for SMS verification. |
| 3 | `RechargeWebActivity.kt:108` | `bindProcessToNetwork(network)` binds **entire process** to cellular. Other components (e.g., background sync, other WebViews) lose Wi-Fi. | Use `Network.bindToProcess()` per-request via `WebView.setNetwork()` (API 29+) or `ConnectivityManager.bindProcessToNetwork` only for WebView's thread via `Network.boundToProcess`. |
| 4 | `SmsTransactionParser.kt:355-361` | Generic DLT sender fallback (`^[A-Z]{6}$` or `^[0-9]{6}$`) matches any 6-char uppercase sender (e.g., "AMAZON", "GOOGLE"). Downstream amount-mismatch guard helps but **promo SMS can enter pipeline**. | Tighten: require known bank shortcode list OR DLT header pattern (`^[A-Z]{2}-[A-Z0-9]{6}(-[A-Z])?$`); drop bare 6-char fallback. |
| 5 | `MainActivity.kt:534-537` | `PermissionManager` created via `remember(hostActivity)` but `hostActivity` from `findComponentActivity()` can be null → `permissionManager` null → `checkSMSPermissions()` crashes. | Guard: `val permissionManager = remember(hostActivity) { hostActivity?.let { PermissionManager(it) } }` already nullable; add `?.` at all call sites (some missing). |
| 6 | `PaymentProgressActivity.kt:193-216` | Fallback timeout logic has **nested `delay` loops with mutable `retries`** — if `coordinator.callState.value` is `Idle` but device is *ringing* (not captured), it treats as timeout incorrectly. | Use `CallStateCoordinator.sessionEvents` for `Started`/`Ended` instead of polling `callState.value`. |
| 7 | `AppDatabase.kt:48-49` | `DatabaseEncryptionMigrator.ensureEncrypted()` runs on **main thread** inside `getDatabase()` (called from `TransactionViewModel.init()` via `repository()` on IO, but first access may be on main). | Ensure migrator runs on `Dispatchers.IO`; wrap `getDatabase()` in `withContext(Dispatchers.IO)` at call sites. |

### Medium Severity

| # | File / Area | Problem | Fix |
|---|-------------|---------|-----|
| 8 | `MainActivity.kt:249-257` | `StrictMode.allowThreadDiskReads()` to read `setupCompleted`/`testCompleted` synchronously on `onCreate`. Acceptable once, but masks real main-thread I/O if prefs grow. | Move to `LaunchedEffect` + `viewModelScope.launch(Dispatchers.IO)`; show skeleton until loaded. |
| 9 | `SettingsActivity.kt:198-221` | `primarySimId` read from `ProximityPrefs` once at composition; **not reactive** to changes from other screens (Setup, TestConfig). | Use `MutableStateFlow` in `SettingsRepository` or `SharedPreferencesFlow` (via `PreferenceDataStore`). |
| 10 | `TransactionHistoryActivity.kt:152-172` | Grouping logic creates `linkedMapOf` + `sortedByDescending` + `groupBy` + `forEach` on every recomposition of `filteredTransactions`. | Move to `derivedStateOf` or ViewModel `combine` flow; memoize with `remember(filteredTransactions)`. |
| 11 | `SetupHelper.kt:21-26` | `isPrimarySimUssdCapable()` hardcodes `"jio"` → **Jio permanently blocked** from *99#. No override except user-reported flag. | Add carrier capability detection via `TelephonyManager.getCarrierConfig()` or allow manual override in Settings. |
| 12 | `PaymentSessionManager.kt:127-198` | `begin()` supersedes in-flight session by discarding its PENDING row **after** inserting new one. If `insertPending` fails (DB error), old session already cleaned up — **orphaned payment attempt**. | Reverse order: insert new PENDING first, *then* delete superseded; wrap in transaction. |
| 13 | `MainActivity.kt:720-721` | `onRequestOverlayPermission` launches settings intent but **no callback to re-check** — relies on `onResume` toast. If user returns without granting, no feedback. | Add `overlayPermissionLauncher` result handling to re-check and show toast (already exists for Settings launch but not for this path). |
| 14 | `Colors.kt` | 50+ `val` color constants + legacy `@Composable` getters mapping to `MaterialTheme.colorScheme`. **Duplication & drift risk** — e.g., `ProximityDarkGray` = background, `ProximitySurfaceDim` = surface, but `InkCard` = surfaceVariant. | Consolidate: keep only `Color.kt` palette; remove legacy aliases; use `MaterialTheme.colorScheme` directly or a single `ProximityColors` object. |

### Low Severity / Tech Debt

| # | File / Area | Problem | Fix |
|---|-------------|---------|-----|
| 15 | `build.gradle.kts:172-188` | `kover` coverage only on `payment`, `sms`, `telephony`, `TransactionDetector*`. UI/ViewModel/Repository untested. | Add integration tests for critical flows (payment → SMS → DB); use Compose UI testing for happy paths. |
| 16 | `PaymentGuidanceActivity.kt:197` | Hardcoded `"Union Bank"` in payment details card — should come from selected bank. | Read from `SettingsRepository.settingsFlow.value.bankId` → bank name map. |
| 17 | `TransactionDetailDialog.kt:389-396` | `statusExplainerText` uses `when` on `status.uppercase()` but `TransactionStatus` enum exists — **stringly-typed**. | Use `TransactionStatus` enum; map to string resources. |
| 18 | `MainActivity.kt:618-631` | `selectedBankName` `when` mapping duplicates `SetupHelper.getBanks()` list. | Centralize bank metadata (id, name, USSD support) in one `object BankRegistry`. |
| 19 | `SetupActivity.kt:161-175` | Progress bar uses hardcoded fractions (0.33, 0.66, 1.0) tied to `SetupStep` enum order. | Compute `progress = (currentStep.ordinal + 1) / SetupStep.values().size.toFloat()`. |
| 20 | `Colors.kt:68-117` | Legacy aliases (`ProximityBlack`, `ProximityDarkGray`, `InkBlack`, `InkDark` all = `background`) — **semantic confusion**. | Remove aliases; use semantic names (`surface`, `surfaceVariant`, `background`) consistently. |

### Architectural Smells

| Area | Concern |
|------|---------|
| **God Helper Classes** | `MainActivityHelper`, `SetupHelper`, `TestConfigurationHelper` each 250-700 lines, mixing validation, navigation, prefs, toast, permission logic. Split into `Validation`, `Navigation`, `Prefs`, `Permission` modules. |
| **Callback Hell** | `UICallback` interfaces with 6-15 methods — brittle when adding new UI events. Replace with sealed `UiEvent` + `SharedFlow` (like `MainViewModel.MainUiEvent`). |
| **Two SharedPreferences Files** | `ProximityPrefs` (setup) + `Proximity_settings` (settings) + `AppConstants.PREFS_NAME` (legacy). Single source of truth needed — migrate to `PreferencesDataStore`. |
| **No Dependency Injection** | `ProximityApplication.container` holds `CallManager`, `CallStateCoordinator`, `PaymentSessionManager`, `TransactionRepository`, `SettingsRepository`, `WalledGardenDetector`. Manual wiring; hard to test. Add Hilt/Koin. |
| **QR Scanner Separation** | `QRScannerActivity` (CameraX + ZXing) → returns UPI string → `MainActivity` parses → `PaymentSessionManager.begin(source=QR)`. Parser logic duplicated in `QRCodeParser` + `Upi123CallStringBuilder`. Unify. |

---

## Summary of Recommended Fixes (Priority Order)

1. **Kill the 100ms polling loop** in `TestConfigurationActivity` — replace with Flow
2. **Fix process-wide network binding** in `RechargeWebActivity` — bind per-WebView
3. **Tighten generic DLT sender fallback** in `SmsTransactionParser`
4. **Unify timeout logic** in `PaymentSessionManager` (watchdog vs call-never-started)
5. **Guard null `PermissionManager`** at all call sites in `MainActivity`
6. **Make `primarySimId` reactive** in `SettingsActivity` via DataStore/Flow
7. **Consolidate color system** — remove 50+ legacy aliases
8. **Centralize bank metadata** — single `BankRegistry` object
9. **Add Hilt/Koin** for DI — clean up `ProximityApplication.container`
10. **Migrate to Preferences DataStore** — unify `ProximityPrefs` + `Proximity_settings`

---

*End of Report*