package com.proximity.app.ui.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proximity.app.ui.components.*
import com.proximity.app.ProximityApplication
import com.proximity.app.R
import com.proximity.app.states.PaymentState
import com.proximity.app.ui.theme.ProximityTheme
import com.proximity.app.ui.theme.InkCard
import com.proximity.app.ui.theme.StatusGreen
import com.proximity.app.ui.theme.StatusRed
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UserAttestationActivity : ComponentActivity() {
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val amount = intent.getStringExtra("amount") ?: ""
        val upiId = intent.getStringExtra("upi_id") ?: ""
        val sessionManager = ProximityApplication.from(this)?.paymentSessionManager

        setContent {
            ProximityTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(containerColor = InkCard),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                SmoothPulseAnimation(modifier = Modifier.size(120.dp))
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Text(
                                    text = getString(R.string.attestation_message, upiId, amount),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                                
                                Spacer(modifier = Modifier.height(32.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            sessionManager?.onUserDeclined()
                                            finish()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                                    ) {
                                        Text(text = stringResource(R.string.attestation_no), color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                    
                                    Button(
                                        onClick = {
                                            sessionManager?.onUserAttested()
                                            finish()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = StatusGreen)
                                    ) {
                                        Text(text = stringResource(R.string.attestation_yes), color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // If an SMS magically arrives while this is on screen, close automatically
        scope.launch {
            sessionManager?.paymentState?.collectLatest { state ->
                if (state is PaymentState.Success || state is PaymentState.Failed) {
                    finish() // Close if it was resolved in the background
                }
            }
        }
    }
}

