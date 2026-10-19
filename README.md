# Proximity — Make UPI Payments OFFLINE

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

### Signing
Still Working On it. Haven't signed the app bundle and made an .aab so far

##  Security

- **Encrypted Database**: Room database uses SQLCipher for AES-256 encryption
- **No Secrets in Code**: API keys and credentials must not be committed — use `local.properties` and `keystore.properties` (both gitignored)
- **Private Repository**: This repository is private on GitHub

##  ⚖️ License & Legal Notice

**All rights reserved.**

Copyright © 2026 Proximity. All rights reserved.

This software and associated documentation files (the "Software") are the proprietary property of Proximity. **No license is granted** to use, copy, modify, merge, publish, distribute, sublicense, or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, **except as expressly permitted in writing by the copyright holder**.

###  Forking & Copying Restrictions

You may **not**:
- Fork this repository on GitHub or any other platform
- Copy, clone, or duplicate this project for your own use
- Create derivative works based on this codebase
- Use this code in your own applications, commercial or otherwise
- Publish this code under your own name or organization

This project is **private and proprietary**. If you received a copy of this code without authorization, you do not have permission to use it.

###  Third-Party Licenses

While this application itself is proprietary, it depends on open-source libraries (e.g., Jetpack Compose, Room, SQLCipher, kotlinx.coroutines). Those libraries remain licensed under their respective terms (primarily Apache 2.0), but **this does not grant any rights to the Proximity application code itself**.

---

##  ⚠️ Disclaimer

THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES, OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT, OR OTHERWISE, ARISING FROM, OUT OF, OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

###  Financial Services Disclaimer

Proximity facilitates access to UPI-based payment systems (USSD *99#, UPI123Pay) and mobile recharge portals. **Proximity is not a bank, wallet, or payment processor.** It does not hold, move, or have access to your money. All transactions are authorized and processed directly by you, your bank, NPCI, and your telecom operator.

- The developers make no warranty that USSD, UPI123Pay, or Walled Garden recharge services will be available or functional
- Transaction disputes are between the User and their respective bank, NPCI, or telecom operator
- Users retain all rights available under applicable consumer protection laws

---

##  📄 Full Terms

For the complete Terms of Service, Privacy Policy, and Disclaimer, see [`TermsActivity`](app/src/main/java/com/proximity/app/ui/activities/TermsActivity.kt) within the app.

---

##  Contact

For licensing inquiries or permission requests, contact the repository owner.

---

_All modules and libraries used internally are licensed with Apache 2.0 License where applicable. This does not affect the proprietary status of the Proximity application code._
