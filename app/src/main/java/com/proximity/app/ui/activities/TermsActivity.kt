package com.proximity.app.ui.activities
import androidx.compose.ui.res.painterResource
import com.proximity.app.R

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proximity.app.ui.components.AnimatedContourBackground
import com.proximity.app.ui.theme.LocalProximityAccentTheme
import com.proximity.app.ui.theme.ProximityBlack
import com.proximity.app.ui.theme.ProximityTheme
import com.proximity.app.ui.theme.TealAccentTheme

class TermsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            CompositionLocalProvider(LocalProximityAccentTheme provides TealAccentTheme) {
                ProximityTheme {
                    TermsScreen(
                        onBackClick = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(onBackClick: () -> Unit) {
    val termsText = """
# Terms of Service — Proximity

**Last updated:** October 4, 2026

**Applicable jurisdiction:** India

---

## 1. Acceptance of Terms

By downloading, installing, or using Proximity ("the App"), you ("User") agree to be bound by these Terms of Service ("Terms"). If you do not agree to these Terms, do not install or use the App.

These Terms constitute a legally binding agreement under the **Indian Contract Act, 1872**.

---

## 2. Description of the App

Proximity is a proprietary Android application that enables offline UPI-based payments and mobile recharges without requiring an active internet connection. The App acts as a **payment aggregator** by providing a unified interface to access multiple regulated payment channels.

The App currently supports:

* **QR Code Payments via USSD (`*99#`):** The App reads UPI QR codes and initiates payment by dialing the corresponding USSD codes using your device's telephony capabilities, with an Accessibility Service used to navigate USSD menu dialogs.
* **Payment to Mobile Number via UPI123Pay:** The App enables UPI payments to a registered mobile number using NPCI's UPI123Pay service, designed for feature phones and offline environments.
* **Mobile Recharge via ISP Whitelisting (Walled Garden):** The App facilitates prepaid mobile recharges through Walled Garden's ISP-whitelisted network, allowing data-limited connectivity for recharge transactions.

The App:

* Stores all operational data locally on your device
* Does **not** read, store, process, or transmit any of your sensitive bank credentials, UPI PIN, OTP, or card details on any server operated by Proximity
* Does **not** independently process, clear, or settle any financial transaction

All rights in the App, including its source code, design, branding, and content, are proprietary and owned exclusively by the developers of Proximity. The App is **not open-source**.

---

## 3. Eligibility

To use the App, you must:

* Be at least **18 years of age**
* Be a resident of India or possess a valid Indian bank account linked to an Indian mobile number
* Have an active mobile connection with a telecom operator supporting `*99#` USSD services and/or UPI123Pay, as applicable
* Have a bank account enabled for USSD-based mobile banking and/or UPI
* Have legal capacity to enter into a binding agreement under the **Indian Contract Act, 1872**

---

## 4. Nature of Service — Payment Aggregator

Proximity operates as a **payment aggregator** by providing users access to multiple regulated offline payment channels through a single interface. As a payment aggregator, Proximity:

* Integrates with NPCI-regulated services, including the `*99#` USSD payment network and UPI123Pay
* Integrates with Walled Garden's ISP-whitelisted network for mobile recharges
* Provides a unified user interface to initiate payment instructions across these channels

**All underlying payment transactions are processed, authorized, and settled by:**

* The **National Payments Corporation of India (NPCI)** — for USSD-based UPI and UPI123Pay transactions
* Your **bank** — for transaction authorization, UPI PIN verification, and fund settlement
* Your **telecom operator** — for USSD transport and mobile recharge processing
* **Walled Garden** — for ISP-whitelisted mobile recharge facilitation

**Proximity does NOT:**

* Handle, store, access, or transmit your UPI PIN, OTP, bank account credentials, or card details
* Hold, pool, or maintain any user funds or escrow accounts
* Act as a bank, non-banking financial company (NBFC), or wallet provider
* Guarantee the completion, accuracy, timeliness, or success of any transaction
* Independently reverse or cancel any transaction once it has been processed by the banking system

---

## 5. Payment Aggregator Compliance

Proximity acknowledges its role as a payment aggregator and commits to operating in compliance with applicable RBI regulations, including the **RBI Guidelines on Regulation of Payment Aggregators and Payment Gateways (2020)**, as may be updated from time to time. Proximity does not store or process any sensitive payment credentials (Sensitive Authentication Data / SAD) and relies entirely on the regulated infrastructure of NPCI and partner banks for authentication and settlement.

Users acknowledge that regulatory approvals, licensing requirements, and compliance obligations applicable to Proximity's aggregator functions are the responsibility of the Proximity development team and subject to applicable RBI directives.

---

## 6. User Responsibilities

By using the App, you agree to:

### 6.1 Compliance with Laws

* Comply with all applicable laws and regulations of India, including the **Information Technology Act, 2000**, the **Payment and Settlement Systems Act, 2007**, RBI regulations, and NPCI guidelines
* Ensure your use of UPI, `*99#`, UPI123Pay, and Walled Garden recharge services is in accordance with the respective terms of service of those platforms and your bank
* Not use the App for any unlawful, fraudulent, or unauthorized purpose

### 6.2 Transaction Responsibility

* Verify all transaction details — including recipient UPI ID, mobile number, QR code details, recharge amount, and operator — before confirming any payment or recharge
* Accept full responsibility for all transactions initiated through the App
* Understand that USSD and UPI123Pay transactions, once processed by the banking system, **cannot be reversed** through the App

### 6.3 Security

* Maintain the security of your device and prevent unauthorized access
* Not share your device with individuals who should not have access to your financial services
* Enable the App's biometric or device PIN lock feature for additional security
* Immediately contact your bank if you suspect unauthorized or fraudulent transactions

### 6.4 Prohibited Uses

You shall **not** use the App to:

* Conduct fraudulent, deceptive, or illegal transactions
* Launder money or finance terrorism in violation of the **Prevention of Money Laundering Act, 2002** (PMLA)
* Circumvent transaction limits set by NPCI, RBI, your bank, or Walled Garden
* Attempt to reverse-engineer, decompile, disassemble, exploit, or tamper with the App or the underlying USSD, UPI, or Walled Garden systems
* Reproduce, distribute, sublicense, sell, or create derivative works of the App or any part thereof
* Use the App in a manner that could damage, disable, or impair any payment network or infrastructure

---

## 7. Intellectual Property

### 7.1 Proprietary Rights

The App, including but not limited to its source code, object code, design, user interface, graphics, branding, and all related materials, is the exclusive proprietary property of the Proximity development team. **All rights are reserved.** No part of the App may be copied, reproduced, modified, distributed, or reverse-engineered without the prior written consent of the developers.

### 7.2 Trademarks and Third-Party Marks

* "Proximity" and its associated logo and branding are the property of the Proximity development team.
* `*99#`, "UPI", "UPI123Pay", "NPCI", and related marks are the property of the **National Payments Corporation of India**.
* "RBI" and "Reserve Bank of India" are the property of the **Reserve Bank of India**.
* "Walled Garden" and related marks are the property of their respective owners.
* The App does **not** claim any affiliation with or endorsement by NPCI, RBI, any bank, any telecom operator, or Walled Garden, beyond the integration described herein.

---

## 8. Data Privacy and Security

Proximity does **not** collect, store, read, or transmit any of the following on any Proximity-operated server:

* UPI PIN, OTP, MPIN, or authentication credentials
* Bank account numbers, IFSC codes, or debit/credit card details
* Any Sensitive Authentication Data (SAD) as defined under PCI-DSS and applicable RBI guidelines

All payment authentications are handled entirely within the regulated banking infrastructure (your bank and NPCI). Proximity's data handling practices are further described in the **Privacy Policy**, which forms part of these Terms.

---

## 9. Disclaimer of Warranties

TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW, INCLUDING THE **CONSUMER PROTECTION ACT, 2019**:

THE APP IS PROVIDED ON AN **"AS IS"** AND **"AS AVAILABLE"** BASIS, WITHOUT ANY WARRANTIES OF ANY KIND, WHETHER EXPRESS, IMPLIED, OR STATUTORY, INCLUDING BUT NOT LIMITED TO:

* MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE
* ACCURACY, RELIABILITY, OR COMPLETENESS OF THE APP'S FUNCTIONALITY
* UNINTERRUPTED OR ERROR-FREE OPERATION
* COMPATIBILITY WITH YOUR DEVICE, SIM, BANK, OR TELECOM OPERATOR

The developers make **no warranty** that:

* The `*99#` USSD service, UPI123Pay, or Walled Garden recharge services will be available or function correctly at all times
* USSD or UPI transactions will be processed accurately, completely, or on time
* The Accessibility Service will correctly interpret all USSD menus across all devices and Android versions
* Your bank, telecom operator, NPCI, or Walled Garden will not suspend, restrict, or modify access to their services

---

## 10. Limitation of Liability

TO THE MAXIMUM EXTENT PERMITTED UNDER INDIAN LAW:

The developers, contributors, and maintainers of Proximity shall **NOT** be liable for any:

* **Direct, indirect, incidental, special, consequential, or punitive damages**
* **Financial losses** arising from failed, incorrect, duplicate, delayed, or unauthorized transactions through any integrated payment channel
* **Account suspension or blocking** by your bank, NPCI, telecom operator, or Walled Garden
* **Loss of data** stored on your device
* **Damages arising from reliance** on the App for financial transactions or mobile recharges
* **Losses due to device theft, loss, or unauthorized access**
* **Service interruptions** caused by NPCI, your bank, your telecom operator, or Walled Garden

This limitation applies whether the claim is based on contract, tort (including negligence), strict liability, or any other legal theory, even if the developers have been advised of the possibility of such damages.

### Maximum Liability

In any event, the total aggregate liability of the developers to a User for all claims arising from or related to the App shall not exceed **INR 500 (five hundred)** for paid features, or **INR 0 (zero)** for features provided free of charge, in the twelve (12) months preceding the claim.

---

## 11. Indemnification

You agree to **indemnify, defend, and hold harmless** the developers, contributors, and maintainers of Proximity from and against any claims, damages, losses, liabilities, costs, and expenses (including reasonable legal fees) arising out of or related to:

* Your use of the App
* Any transaction or recharge initiated through the App
* Your violation of these Terms
* Your violation of any applicable Indian law or regulation
* Any third-party claim arising from your use of the App

---

## 12. Regulatory Compliance

### 12.1 Payment and Settlement Systems

The payment channels integrated into Proximity are regulated under the **Payment and Settlement Systems Act, 2007** (PSS Act) and governed by the **Reserve Bank of India (RBI)** and the **National Payments Corporation of India (NPCI)**. Users must comply with all applicable RBI circulars and NPCI guidelines governing UPI, `*99#`, and UPI123Pay services.

### 12.2 Payment Aggregator Regulations

Proximity operates in the capacity of a payment aggregator and is committed to compliance with **RBI's Guidelines on Regulation of Payment Aggregators and Payment Gateways (March 2020)**, as amended from time to time. Proximity does not store any card or sensitive payment credential data, consistent with RBI data localization and security mandates.

### 12.3 Telecom Regulations

USSD services are governed by the **Indian Telegraph Act, 1885**, the **Indian Wireless Telegraphy Act, 1933**, and regulations issued by the **Telecom Regulatory Authority of India (TRAI)**. Mobile recharge functionality through Walled Garden is subject to the policies of your telecom operator and Walled Garden. The developers are not responsible for any telecom-related failures or restrictions.

### 12.4 Anti-Money Laundering

Users must comply with the **Prevention of Money Laundering Act, 2002** (PMLA) and the **PMLA Rules**. All Know Your Customer (KYC) requirements are handled by your bank and NPCI. Proximity does not perform independent KYC verification.

### 12.5 Information Technology

To the extent applicable, Proximity complies with the **Information Technology Act, 2000** and the **Information Technology (Reasonable Security Practices and Procedures and Sensitive Personal Data or Information) Rules, 2011**, in handling any user data.

---

## 13. Dispute Resolution

### 13.1 Governing Law

These Terms shall be governed by and construed in accordance with the laws of India.

### 13.2 Transaction Disputes

Any disputes regarding specific financial transactions must be resolved directly with your bank, NPCI's grievance redressal mechanism, or Walled Garden, as applicable. Proximity developers have **no access to, control over, or information about** any transaction processed through the `*99#`, UPI123Pay, or Walled Garden networks.

### 13.3 Arbitration

Any dispute arising out of or relating to these Terms that cannot be resolved amicably within thirty (30) days shall be settled by arbitration in accordance with the **Arbitration and Conciliation Act, 1996**, as amended. The seat and venue of arbitration shall be in India. The language of arbitration shall be English.

### 13.4 Jurisdiction

Subject to the arbitration clause above, the courts in India shall have exclusive jurisdiction over any disputes arising from or in connection with these Terms.

---

## 14. Consumer Protection

In accordance with the **Consumer Protection Act, 2019**, and the **Consumer Protection (E-Commerce) Rules, 2020**:

* Proximity does not independently sell goods or services; it aggregates access to regulated third-party payment and recharge services.
* Transaction disputes are between the User and their respective bank, NPCI, or Walled Garden.
* Users retain all rights available to them under the Consumer Protection Act, 2019, in relation to the services of the respective payment service providers.

---

## 15. Modification of Terms

The developers reserve the right to modify these Terms at any time. Updated Terms will be notified to users through the App or the project's official communication channels, with a revised "Last updated" date. Continued use of the App after notification of any modification constitutes acceptance of the updated Terms.

---

## 16. Termination

* You may stop using the App at any time by uninstalling it from your device.
* The developers reserve the right to suspend or discontinue the App, or any feature thereof, at any time without prior notice, subject to applicable law.
* These Terms survive the uninstallation of the App with respect to any claims, liabilities, or obligations that arose during its use.

---

## 17. Severability

If any provision of these Terms is held invalid, illegal, or unenforceable by a court of competent jurisdiction in India, the remaining provisions shall continue in full force and effect.

---

## 18. Entire Agreement

These Terms, together with the Privacy Policy and Disclaimer, constitute the entire agreement between you and the developers regarding the use of the App.

---

## 19. Contact

For questions, grievances, or support related to these Terms, please contact us at:

**Proximity Support** [anshumanpati411@gmail.com]

For transaction disputes, please contact your bank or NPCI's grievance redressal portal at https://www.npci.org.in.
"""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terms & Conditions", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_back_arrow),
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContourBackground(modifier = Modifier.fillMaxSize())

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val lines = termsText.split("\n")
                items(lines.size) { index ->
                    val line = lines[index]
                    if (line.isBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                    } else {
                        MarkdownLine(line)
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownLine(line: String) {
    var text = line
    var isH1 = false
    var isH2 = false
    var isH3 = false

    if (text.startsWith("# ")) {
        isH1 = true
        text = text.removePrefix("# ")
    } else if (text.startsWith("## ")) {
        isH2 = true
        text = text.removePrefix("## ")
    } else if (text.startsWith("### ")) {
        isH3 = true
        text = text.removePrefix("### ")
    } else if (text.startsWith("* ")) {
        text = "• " + text.removePrefix("* ")
    } else if (text.startsWith("- ")) {
        text = "• " + text.removePrefix("- ")
    }

    // Strip out horizontal rules
    if (text == "---" || text == "***") {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))
        return
    }

    val textColor = MaterialTheme.colorScheme.onBackground
    val baseStyle = androidx.compose.ui.text.SpanStyle(
        color = textColor.copy(alpha = 0.85f),
        fontSize = if (isH1) 24.sp else if (isH2) 20.sp else if (isH3) 18.sp else 14.sp,
        fontWeight = if (isH1 || isH2 || isH3) FontWeight.Bold else FontWeight.Normal
    )

    val boldStyle = androidx.compose.ui.text.SpanStyle(
        fontWeight = FontWeight.Bold,
        color = textColor
    )

    val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
        withStyle(baseStyle) {
            val parts = text.split("**")
            for (i in parts.indices) {
                if (i % 2 == 1) {
                    withStyle(boldStyle) { append(parts[i]) }
                } else {
                    // Quick and dirty handling of inline code blocks like `*99#`
                    val codeParts = parts[i].split("`")
                    for (j in codeParts.indices) {
                        if (j % 2 == 1) {
                            withStyle(androidx.compose.ui.text.SpanStyle(
                                background = Color.White.copy(alpha = 0.1f),
                                color = LocalProximityAccentTheme.current.primary,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )) { append(codeParts[j]) }
                        } else {
                            append(codeParts[j])
                        }
                    }
                }
            }
        }
    }

    Text(
        text = annotatedString,
        lineHeight = if (isH1 || isH2 || isH3) 32.sp else 22.sp,
        modifier = if (isH1 || isH2 || isH3) Modifier.padding(top = 16.dp, bottom = 4.dp) else Modifier
    )
}


