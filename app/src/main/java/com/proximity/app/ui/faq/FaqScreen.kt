// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.faq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.proximity.app.AppCore
import com.proximity.app.R
import com.proximity.app.theme.AppTheme
import com.proximity.app.theme.ColorTokens
import com.proximity.app.theme.LocalAppVisuals
import com.proximity.app.ui.components.AnimatedBackground
import com.proximity.app.ui.components.GlassCard
import com.proximity.app.ui.components.pressScale

data class FaqEntry(
    val question: String,
    val answer: String
)

class FaqScreen : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = application as AppCore

        setContent {
            val settings by app.services.preferencesStore.settingsFlow.collectAsState()
            AppTheme(settings = settings) {
                FaqContentView(onBack = { finish() })
            }
        }
    }
}

private val EXACT_16_FAQS = listOf(
    FaqEntry(
        question = "What is Proximity?",
        answer = "Proximity is a payment application that helps you pay when your mobile data is weak or has run out. Instead of using the internet, it uses your phone's standard cellular call network."
    ),
    FaqEntry(
        question = "Does Proximity work without the internet?",
        answer = "Yes. Proximity works without mobile data. However, your phone still needs a network signal and an active recharge plan, because the payment is routed through a voice call or a USSD code (a short code dialled on your phone)."
    ),
    FaqEntry(
        question = "What is UPI123Pay?",
        answer = "UPI123Pay is NPCI's offline UPI service, where you pay through a voice call (IVR). You call the service, follow the spoken prompts, and confirm the payment with your UPI PIN."
    ),
    FaqEntry(
        question = "What is USSD *99#?",
        answer = "USSD (*99#) is a short code dialled on your phone that opens the NPCI UPI menu. Proximity can start this for you, so you don't have to type the code yourself. It works on any phone with a signal and doesn't need a smartphone data connection."
    ),
    FaqEntry(
        question = "Is Proximity a bank or a wallet?",
        answer = "No. Proximity is solely a payment aggregator. It does not hold your money or manage your bank account. Your money is always transferred from your bank through official channels."
    ),
    FaqEntry(
        question = "Which banks does Proximity support?",
        answer = "Proximity works with any bank that is linked to UPI (Unified Payments Interface) and supports UPI123 or *99#."
    ),
    FaqEntry(
        question = "Which mobile number should I use?",
        answer = "Use the same mobile number that is linked to your bank account and UPI."
    ),
    FaqEntry(
        question = "How do I pay someone?",
        answer = "To pay someone:\n1. Tap Pay a number.\n2. Enter the recipient's mobile number and the amount.\n3. Review the payment details.\n4. Tap Pay.\nProximity then places an IVR (Interactive Voice Response) call to process the payment."
    ),
    FaqEntry(
        question = "How do I pay a shop or merchant using a QR code?",
        answer = "To pay by scanning the shop's QR code:\n1. Tap Scan QR code.\n2. Point your camera at the shop's QR code.\n3. Your phone opens a bank pop-up. Enter your UPI PIN there.\n4. Return to Proximity and tap Finish Payment."
    ),
    FaqEntry(
        question = "Why does a call open when I make a payment?",
        answer = "Proximity uses the official UPI123 phone service. The call is the channel through which your payment is transmitted when an internet connection is not available."
    ),
    FaqEntry(
        question = "Is it safe to enter my UPI PIN?",
        answer = "Your UPI PIN is entered only in the official bank pop-up or call. Proximity never reads or stores your UPI PIN."
    ),
    FaqEntry(
        question = "What information does Proximity save?",
        answer = "Proximity saves only your UPI-linked mobile number and your payment history on your own phone. It never requests your bank account number or IFSC code."
    ),
    FaqEntry(
        question = "Why does the app ask for permission to read my SMS messages?",
        answer = "This permission allows Proximity to check the SMS sent by your bank and confirm that your payment was completed successfully."
    ),
    FaqEntry(
        question = "What if my payment fails?",
        answer = "Please don't worry.\n- First, check the SMS from your bank to see whether the amount was debited.\n- If the amount was not debited, simply try the payment again.\n- If the amount was debited but the app shows \"Failed\", wait for some time, as banks usually return the amount within a few days. If it is still not returned, contact your bank with the transaction details."
    ),
    FaqEntry(
        question = "Are there payment limits?",
        answer = "Yes. Payment limits are set by UPI and your bank, and they may be lower for call-based payments. Please check with your bank for the current limit."
    ),
    FaqEntry(
        question = "Does Proximity charge any fee?",
        answer = "Proximity itself does not charge you a fee. However, your bank or mobile service provider may charge a small fee for calls or USSD, so please check your plan."
    )
)

@Composable
fun FaqContentView(onBack: () -> Unit) {
    val visuals = LocalAppVisuals.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var expandedQuestions by rememberSaveable { mutableStateOf(emptyList<String>()) }

    val filteredFaqs = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            EXACT_16_FAQS
        } else {
            EXACT_16_FAQS.filter {
                it.question.contains(searchQuery, ignoreCase = true) ||
                    it.answer.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_back_arrow),
                        contentDescription = stringResource(id = R.string.cd_back),
                        tint = visuals.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(id = R.string.faq_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color = visuals.textPrimary,
                    modifier = Modifier.semantics { heading() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = stringResource(id = R.string.faq_search_placeholder),
                            color = visuals.textSecondary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_search),
                            contentDescription = stringResource(id = R.string.cd_search),
                            tint = visuals.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredFaqs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(id = R.string.faq_empty_search, searchQuery),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = visuals.textSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(
                        items = filteredFaqs,
                        key = { it.question }
                    ) { faq ->
                        val isExpanded = expandedQuestions.contains(faq.question)
                        FaqAccordionItem(
                            faq = faq,
                            isExpanded = isExpanded,
                            onToggle = {
                                expandedQuestions = if (isExpanded) {
                                    expandedQuestions - faq.question
                                } else {
                                    expandedQuestions + faq.question
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FaqAccordionItem(
    faq: FaqEntry,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val visuals = LocalAppVisuals.current
    val expandedBorder = if (isExpanded) {
        Brush.linearGradient(
            colors = listOf(
                ColorTokens.BrandTeal,
                ColorTokens.SpecularLightTeal
            )
        )
    } else {
        null
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.Button
            }
            .pressScale(onClick = onToggle)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        cornerRadius = 16.dp,
        customBorderBrush = expandedBorder
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = faq.question,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = visuals.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = if (isExpanded) visuals.adaptiveTealLabel else visuals.textSecondary,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(if (isExpanded) 180f else 0f)
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = visuals.outline.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = faq.answer,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = visuals.textSecondary
                )
            }
        }
    }
}
