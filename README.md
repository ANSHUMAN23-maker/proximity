# Proximity — UPI Payment App (Offline-First)

Proximity is an Android payment application that enables UPI transactions without requiring an active internet connection. It uses USSD (*99#) and TelecomManager-based call routing to process payments through the phone's native dialer and SMS infrastructure.

##  Features

| Feature | Description |
|---------|-------------|
| **QR Code Scanning** | Scan UPI QR codes to auto-fill payment details |
| **Pay Contact** | Send payments to saved contacts or phone numbers |
| **Switch Themes** | Theme toggle for light/dark appearance |
| **Recharge** | Go ahead and recharge your mobile plan even when it is exhausted |

##  Play Store Compliance

- **No `CALL_PHONE` permission**: Uses `Intent.ACTION_DIAL` instead of `Intent.ACTION_CALL`, eliminating the need for the high-risk `CALL_PHONE` runtime permission
- **`MANAGE_OWN_CALLS` only**: UPI123 calls use `TelecomManager.placeCall()` which requires only the normal `MANAGE_OWN_CALLS` permission (auto-granted at install)
- **Reduced permission prompts**: Fresh installs no longer trigger a `CALL_PHONE` permission dialog

##  Accessibility Feature

The app includes an optional accessibility service that reads USSD dialog boxes during QR code payment flows:

- **`PaymentAccessibilityService`**: Monitors window changes when a payment session is active and scrapes visible text from USSD dialogs
- **`UssdDialogParser`**: Extracts amount, reference number, recipient name, and success/failure status from dialog text
- **User-enabled**: Must be manually enabled in **Android Settings > Accessibility > Proximity**
- **Payment-scoped only**: Activates only when a payment session is in progress

##  Technical Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI | Jetpack Compose |
| Database | Room + SQLCipher (encrypted) |
| Dependency Injection | Manual composition root (`AppContainer`) |
| Coroutines | `kotlinx.coroutines` with `Dispatchers.IO` |
| Build System | Gradle (AGP 8.x) |
| Min SDK | 29 |
| Target SDK | 35 |

##  Project Structure

```
app/src/main/java/com/proximity/app/
├── accessibility/           # Accessibility service for USSD dialog reading
├── data/                    # Room database, entities, DAOs, repositories
├── di/                      # Dependency injection (AppContainer)
├── helpers/                 # Setup helper, transaction detector
├── managers/                # CallManager, PermissionManager
├── network/                 # Walled garden detection
├── payment/                 # Payment session manager, SMS parsing
├── receivers/               # SMS receiver, ingestion pipeline, result notifier
├── telecom/                 # ConnectionService, phone account manager
├── telephony/               # Call state coordinator
├── ui/                      # Compose UI (activities, components, dialogs, theme)
├── utils/                   # Currency format, phone utils, overlay logger
├── viewmodel/               # MainViewModel, TransactionViewModel
└── MainActivity.kt          # Home screen entry point
```

##  Permissions

| Permission | Type | Purpose |
|------------|------|---------|
| `READ_PHONE_STATE` | Normal | Detect call state (off-hook/idle) |
| `ANSWER_PHONE_CALLS` | Normal | End calls via TelecomManager |
| `MANAGE_OWN_CALLS` | Normal | Route UPI123 calls through ConnectionService |
| `CAMERA` | Runtime | QR code scanning |
| `RECEIVE_SMS` | Runtime | Receive bank SMS for payment confirmation |
| `READ_CONTACTS` | Runtime | Pay contact feature |
| `VIBRATE` | Normal | Haptic feedback |
| `INTERNET` | Normal | Walled garden detection |
| `SYSTEM_ALERT_WINDOW` | Special | Payment progress overlay |
| `POST_NOTIFICATIONS` | Runtime | Payment result notifications |

**Removed**: `CALL_PHONE` (no longer required — uses `ACTION_DIAL`)

##  Setup & Build

### Prerequisites
- Android Studio (latest stable)
- JDK 17+
- Android SDK 35

### Build
```bash
# Clone the repository
git clone https://github.com/ANSHUMAN23-maker/proximity.git
cd proximity

# Open in Android Studio and sync Gradle
# Build: ./gradlew assembleDebug
# Run: ./gradlew installDebug
```

### Signing
Still Working On it. Haven't signed the app bundle and made an .aab so far

##  Security

- **Encrypted Database**: Room database uses SQLCipher for AES-256 encryption
- **No Secrets in Code**: API keys and credentials must not be committed — use `local.properties` and `keystore.properties` (both gitignored)
- **Private Repository**: This repository is private on GitHub

##  License

None So far :3 . All modules and libraries used are licensed with Apache 2.0 License
