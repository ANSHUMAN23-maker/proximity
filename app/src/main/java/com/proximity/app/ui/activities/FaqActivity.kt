package com.proximity.app.ui.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proximity.app.ProximityApplication
import com.proximity.app.R
import com.proximity.app.ui.components.AnimatedContourBackground
import com.proximity.app.ui.components.pressScale
import com.proximity.app.ui.theme.LocalProximityAccentTheme
import com.proximity.app.ui.theme.ProximityTheme
import com.proximity.app.ui.theme.TealAccentTheme

data class FaqItem(
    val question: String,
    val answer: String
)

class FaqActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        val app = application as? ProximityApplication
        val settingsRepository = app?.settingsRepository ?: com.proximity.app.data.SettingsRepository(applicationContext)

        setContent {
            val savedSettings by settingsRepository.settingsFlow.collectAsState()
            CompositionLocalProvider(LocalProximityAccentTheme provides TealAccentTheme) {
                ProximityTheme(darkTheme = savedSettings.darkModeEnabled) {
                    FaqScreen(
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

private val FAQ_LIST = listOf(
    FaqItem(
        question = "What is Proximity?",
        answer = "Proximity is a payment application that helps you pay when your mobile data is weak or has run out. Instead of using the internet, it uses your phone's standard cellular call network."
    ),
    FaqItem(
        question = "Does Proximity work without the internet?",
        answer = "Yes. Proximity works without mobile data. However, your phone still needs a network signal and an active recharge plan, because the payment is routed through a voice call or a USSD code (a short code dialled on your phone)."
    ),
    FaqItem(
        question = "What is UPI123Pay?",
        answer = "UPI123Pay is NPCI's offline UPI service, where you pay through a voice call (IVR). You call the service, follow the spoken prompts, and confirm the payment with your UPI PIN."
    ),
    FaqItem(
        question = "What is USSD *99#?",
        answer = "USSD (*99#) is a short code dialled on your phone that opens the NPCI UPI menu. Proximity can start this for you, so you don't have to type the code yourself. It works on any phone with a signal and doesn't need a smartphone data connection."
    ),
    FaqItem(
        question = "Is Proximity a bank or a wallet?",
        answer = "No. Proximity is solely a payment aggregator. It does not hold your money or manage your bank account. Your money is always transferred from your bank through official channels."
    ),
    FaqItem(
        question = "Which banks does Proximity support?",
        answer = "Proximity works with any bank that is linked to UPI (Unified Payments Interface) and supports UPI123 or *99#."
    ),
    FaqItem(
        question = "Which mobile number should I use?",
        answer = "Use the same mobile number that is linked to your bank account and UPI."
    ),
    FaqItem(
        question = "How do I pay someone?",
        answer = "To pay someone:\n1. Tap Pay a number.\n2. Enter the recipient's mobile number and the amount.\n3. Review the payment details.\n4. Tap Pay.\nProximity then places an IVR (Interactive Voice Response) call to process the payment."
    ),
    FaqItem(
        question = "How do I pay a shop or merchant using a QR code?",
        answer = "To pay by scanning the shop's QR code:\n1. Tap Scan QR code.\n2. Point your camera at the shop's QR code.\n3. Your phone opens a bank pop-up. Enter your UPI PIN there.\n4. Return to Proximity and tap Finish Payment."
    ),
    FaqItem(
        question = "Why does a call open when I make a payment?",
        answer = "Proximity uses the official UPI123 phone service. The call is the channel through which your payment is transmitted when an internet connection is not available."
    ),
    FaqItem(
        question = "Is it safe to enter my UPI PIN?",
        answer = "Your UPI PIN is entered only in the official bank pop-up or call. Proximity never reads or stores your UPI PIN."
    ),
    FaqItem(
        question = "What information does Proximity save?",
        answer = "Proximity saves only your UPI-linked mobile number and your payment history on your own phone. It never requests your bank account number or IFSC code."
    ),
    FaqItem(
        question = "Why does the app ask for permission to read my SMS messages?",
        answer = "This permission allows Proximity to check the SMS sent by your bank and confirm that your payment was completed successfully."
    ),
    FaqItem(
        question = "What if my payment fails?",
        answer = "Please don't worry.\n- First, check the SMS from your bank to see whether the amount was debited.\n- If the amount was not debited, simply try the payment again.\n- If the amount was debited but the app shows \"Failed\", wait for some time, as banks usually return the amount within a few days. If it is still not returned, contact your bank with the transaction details."
    ),
    FaqItem(
        question = "Are there payment limits?",
        answer = "Yes. Payment limits are set by UPI and your bank, and they may be lower for call-based payments. Please check with your bank for the current limit."
    ),
    FaqItem(
        question = "Does Proximity charge any fee?",
        answer = "Proximity itself does not charge you a fee. However, your bank or mobile service provider may charge a small fee for calls or USSD, so please check your plan."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(
    onBack: () -> Unit
) {
    val accent = LocalProximityAccentTheme.current
    var searchQuery by remember { mutableStateOf("") }
    var expandedIndices by remember { mutableStateOf(setOf<Int>()) }

    val filteredFaqs = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            FAQ_LIST
        } else {
            FAQ_LIST.filter {
                it.question.contains(searchQuery, ignoreCase = true) ||
                        it.answer.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Help & FAQs",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContourBackground(modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "Search questions or keywords...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = accent.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        cursorColor = accent.primary,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (filteredFaqs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No FAQs found matching \"$searchQuery\"",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        itemsIndexed(filteredFaqs) { index, faq ->
                            val isExpanded = expandedIndices.contains(index)
                            FaqExpandableCard(
                                faq = faq,
                                isExpanded = isExpanded,
                                onToggle = {
                                    expandedIndices = if (isExpanded) {
                                        expandedIndices - index
                                    } else {
                                        expandedIndices + index
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FaqExpandableCard(
    faq: FaqItem,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val accent = LocalProximityAccentTheme.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(onClick = onToggle)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(
            width = 1.dp,
            color = if (isExpanded) accent.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        shadowElevation = 0.dp
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
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = if (isExpanded) accent.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = faq.answer,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Preview(name = "FAQ Screen - Light", showBackground = true)
@Preview(name = "FAQ Screen - Dark", showBackground = true, backgroundColor = 0xFF090D14)
@Composable
private fun FaqScreenPreview() {
    ProximityTheme {
        FaqScreen(onBack = {})
    }
}
