# Proximity

Proximity is an Android application for UPI-based payments. It supports scanning QR codes to initiate payments, paying contacts directly, and viewing transaction history.

## Project Details

- **Package name**: `com.proximity.app`
- **Minimum SDK**: 29
- **Target SDK**: 35
- **Compile SDK**: 35
- **Build tools version**: 35.0.0
- **Android Gradle Plugin**: 8.7.3
- **Kotlin**: 2.1.10
- **Module**: `:app` (single Android application module)

## Core Features

- **QR Code Scanning**: Scan UPI QR codes using the device camera (CameraX) with ZXing-based decoding. Supports both live camera scanning and gallery image selection.
- **Pay Contact**: Send payments to contacts from the address book.
- **Transaction History**: View past payment transactions.
- **USSD Payment Support**: Initiates UPI123 USSD calls (`*99#`) via TelecomManager so the system dialer does not appear during payment. Includes an accessibility service to read USSD payment dialogs and a broadcast receiver to capture payment confirmation SMS.
- **Payment Flow**: Splash screen entry, setup, main dashboard, payment progress, payment result, and user attestation screens.
- **Settings**: Theme toggle (dark mode) and related configuration.

## Key Permissions

The app requests the following permissions:

- `CALL_PHONE` — place UPI123 payment calls
- `READ_PHONE_STATE` — telephony state for payment calls
- `ANSWER_PHONE_CALLS` — end calls via TelecomManager
- `MANAGE_OWN_CALLS` — route UPI123 calls through ProximityConnectionService
- `CAMERA` — QR code scanning
- `RECEIVE_SMS` — receive payment confirmation SMS while a payment operation is active
- `READ_CONTACTS` — pay contacts from address book
- `SYSTEM_ALERT_WINDOW` — in-call overlay
- `POST_NOTIFICATIONS` — payment outcome notifications
- `INTERNET`, `ACCESS_NETWORK_STATE`, `CHANGE_NETWORK_STATE` — network connectivity for captive-portal routing
- `VIBRATE` — haptic feedback

`READ_SMS` is deliberately not requested. The app only receives incoming SMS messages while a payment operation is active; it does not read the SMS inbox.

## Key Components

- `SplashActivity` — launcher entry point
- `MainActivity` — home screen with Scan QR, Pay Contact, recent payments, and settings
- `QRScannerActivity` — QR code scanner for initiating payments
- `SetupActivity` — initial device and SIM setup
- `TestConfigurationActivity` — test configuration
- `UserAttestationActivity` — user payment attestation
- `PaymentResultActivity` — payment success/failure result screen
- `PaymentProgressActivity` — full-screen payment progress
- `PaymentGuidanceActivity` — payment guidance flow
- `RechargeWebActivity` — recharge web view
- `TermsActivity` — terms of service
- `FaqActivity` — frequently asked questions
- `PayContactActivity` — pay a contact
- `TransactionHistoryActivity` — transaction history
- `SettingsActivity` — app settings
- `ProximityConnectionService` — telecom connection service that intercepts UPI123 outgoing calls
- `UssdAccessibilityService` — accessibility service for reading USSD payment dialogs
- `SimpleSMSReceiver` — broadcast receiver for incoming payment confirmation SMS

## Libraries

- Jetpack Compose with Material 3
- CameraX (camera-core, camera-camera2, camera-lifecycle, camera-view)
- ZXing (QR code decoding)
- Room with SQLCipher (encrypted local database)
- Kotlin Coroutines
- Lottie Compose (animations)
- androidx.localbroadcastmanager

## Build

To build the debug APK:

```
./gradlew assembleDebug
```

A signed release build requires a `keystore.properties` file in the project root with `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`. Without it, release builds are left unsigned.

## Launch Film

A 60-second launch film for the project is available at:

`Proximity_Launch_Film_60s.mp4`
