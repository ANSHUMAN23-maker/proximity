// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.terms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.proximity.app.AppCore
import com.proximity.app.R
import com.proximity.app.theme.AppTheme
import com.proximity.app.theme.ColorTokens
import com.proximity.app.theme.LocalAppVisuals
import com.proximity.app.ui.components.AnimatedBackground

class TermsScreen : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = application as AppCore

        setContent {
            val settings by app.services.preferencesStore.settingsFlow.collectAsState()
            AppTheme(settings = settings) {
                TermsContentView(onBackClick = { finish() })
            }
        }
    }
}

private val TERMS_LINES = listOf(
    "# Terms of Service \u2014 Proximity",
    "**Applicable jurisdiction:** India",
    "---",
    "## 1. Acceptance of Terms",
    "By downloading, installing, or using Proximity (\"the App\"), you (\"User\") agree to be bound by these Terms of Service (\"Terms\"). If you do not agree to these Terms, do not install or use the App.",
    "These Terms constitute a legally binding agreement under the **Indian Contract Act, 1872**.",
    "---",
    "## 2. Description of the App & Open-Source Distribution",
    "Proximity is an open-source, local-first Android application that enables offline UPI-based payments and mobile recharges without requiring an active internet connection. The App acts solely as an **offline payment aggregator** by providing a unified interface to initiate regulated payment channels on your device.",
    "* **QR Code Payments via USSD (`*99#`):** Scans UPI QR codes offline and dials `*99*1*3#` via your device's cellular voice/USSD network. An optional, **session-scoped, auto-disabling Android Accessibility Service** is used solely while a QR USSD payment is in flight to read system USSD confirmation dialogs (for outcome, amount, recipient name, and reference ID) and automatically disables itself at the OS level immediately afterward\u2014never reading or capturing UPI PINs.",
    "* **Payment to Mobile Number via `UPI123Pay`:** Initiates UPI payments to a 10-digit Indian mobile number by calling NPCI's official `UPI123Pay` IVR (`08045163666`) with automated DTMF routing.",
    "* **Mobile Recharge via Carrier Portal (Walled Garden):** Opens your selected telecom operator's official recharge portal (`www.jio.com`, `www.airtel.in`, `www.myvi.in`, `www.bsnl.co.in`) bound to your cellular interface.",
    "The App:",
    "* Stores all operational settings and encrypted transaction history locally on your device",
    "* Does **not** read, capture, store, process, or transmit your UPI PIN, OTP, bank account number, or IFSC code",
    "* Does **not** independently process, hold, clear, or settle any funds",
    "---",
    "## 3. Eligibility",
    "* Be at least **18 years of age**",
    "* Be a resident of India possessing a valid Indian bank account linked to an Indian mobile number and UPI",
    "* Have an active mobile SIM connection with a telecom operator supporting `*99#` USSD services and/or `UPI123Pay`",
    "* Have legal capacity to enter into a binding agreement under the **Indian Contract Act, 1872**",
    "---",
    "## 4. Nature of Service \u2014 Payment Aggregator",
    "Proximity operates strictly as a client-side **payment aggregator** interface. All underlying payment transactions are processed, authorized, and settled directly by:",
    "* The **National Payments Corporation of India (NPCI)** \u2014 for `*99#` USSD and `UPI123Pay` transactions",
    "* Your **bank** \u2014 for transaction authorization, UPI PIN verification, and fund settlement",
    "* Your **telecom operator** \u2014 for USSD transport, voice IVR connectivity, and mobile recharge processing",
    "---",
    "## 5. Regulatory & Statutory Alignment",
    "* **Payment and Settlement Systems Act, 2007 (PSS Act) & RBI / NPCI Guidelines:** All payment authorizations occur directly on NPCI and bank switches. Proximity never stores Sensitive Authentication Data (SAD).",
    "* **Telecom Regulatory Authority of India (TRAI):** USSD (`*99#`) and voice call charges are governed by your telecom operator tariff plan. Reliance Jio does not support `*99#` USSD.",
    "* **Prevention of Money Laundering Act, 2002 (PMLA):** All KYC and AML checks are performed by your bank and NPCI.",
    "* **Information Technology Act, 2000:** Local transaction records are encrypted at rest using AES-256 SQLCipher backed by the Android Keystore (`AES/GCM/NoPadding`).",
    "* **Consumer Protection Act, 2019:** Transaction disputes are resolved directly between the User and their respective bank, telecom operator, or NPCI grievance redressal mechanism.",
    "---",
    "## 6. Disclaimer of Warranties & Limitation of Liability",
    "THE APP IS PROVIDED ON AN **\"AS IS\"** AND **\"AS AVAILABLE\"** BASIS WITHOUT WARRANTIES OF ANY KIND. To the maximum extent permitted under Indian law, the developers and contributors of Proximity shall not be liable for any direct, indirect, incidental, or consequential financial losses arising from failed, delayed, or interrupted USSD, IVR, or carrier recharge sessions.",
    "---",
    "## 7. Dispute Resolution & Governing Law",
    "These Terms are governed by the laws of India. Any dispute relating to these Terms shall be subject to arbitration under the **Arbitration and Conciliation Act, 1996** in India.",
    "---",
    "## 8. Official Support & Grievance Redressal",
    "* **Official Support Channel:** Proximity Official Project Support Portal (via in-app Help & FAQs and official repository issue tracker)",
    "* **NPCI / Banking Disputes:** Please contact your bank directly or visit NPCI's official dispute redressal portal at `https://www.npci.org.in`"
)

@Composable
fun TermsContentView(onBackClick: () -> Unit) {
    val visuals = LocalAppVisuals.current

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_back_arrow),
                        contentDescription = stringResource(id = R.string.cd_back),
                        tint = visuals.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(id = R.string.terms_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color = visuals.textPrimary
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TERMS_LINES.size) { idx ->
                    val line = TERMS_LINES[idx]
                    if (line.isBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                    } else {
                        TermsMarkdownRow(line = line)
                    }
                }
            }
        }
    }
}

@Composable
private fun TermsMarkdownRow(line: String) {
    val visuals = LocalAppVisuals.current
    var text = line
    var isH1 = false
    var isH2 = false

    when {
        text.startsWith("# ") -> {
            isH1 = true
            text = text.removePrefix("# ")
        }
        text.startsWith("## ") -> {
            isH2 = true
            text = text.removePrefix("## ")
        }
        text.startsWith("* ") -> {
            text = "\u2022 " + text.removePrefix("* ")
        }
    }

    if (text == "---") {
        HorizontalDivider(
            color = visuals.outline.copy(alpha = 0.25f),
            modifier = Modifier.padding(vertical = 8.dp)
        )
        return
    }

    val baseStyle = SpanStyle(
        color = if (isH1 || isH2) visuals.textPrimary else visuals.textSecondary,
        fontSize = when {
            isH1 -> 24.sp
            isH2 -> 18.sp
            else -> 14.sp
        },
        fontWeight = if (isH1 || isH2) FontWeight.Bold else FontWeight.Normal,
        fontFamily = if (isH1 || isH2) FontFamily.Serif else FontFamily.SansSerif
    )

    val boldStyle = SpanStyle(
        fontWeight = FontWeight.Bold,
        color = visuals.textPrimary
    )

    val codeStyle = SpanStyle(
        background = ColorTokens.BrandTeal.copy(alpha = 0.16f),
        color = visuals.adaptiveTealLabel,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold
    )

    val annotated = remember(text, visuals.textPrimary, visuals.textSecondary, visuals.adaptiveTealLabel) {
        buildAnnotatedString {
            withStyle(baseStyle) {
                val boldParts = text.split("**")
                for (i in boldParts.indices) {
                    if (i % 2 == 1) {
                        withStyle(boldStyle) { append(boldParts[i]) }
                    } else {
                        val codeParts = boldParts[i].split("`")
                        for (j in codeParts.indices) {
                            if (j % 2 == 1) {
                                withStyle(codeStyle) { append(" ${codeParts[j]} ") }
                            } else {
                                append(codeParts[j])
                            }
                        }
                    }
                }
            }
        }
    }

    Text(
        text = annotated,
        lineHeight = if (isH1 || isH2) 28.sp else 21.sp,
        modifier = if (isH1 || isH2) {
            Modifier
                .padding(top = 12.dp, bottom = 2.dp)
                .semantics { heading() }
        } else {
            Modifier
        }
    )
}
