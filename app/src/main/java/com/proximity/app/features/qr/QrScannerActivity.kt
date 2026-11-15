// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.features.qr

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.InvertedLuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.proximity.app.AppCore
import com.proximity.app.R
import com.proximity.app.accessibility.UssdDialogReaderService
import com.proximity.app.constants.AppLimits
import com.proximity.app.data.PaymentChannel
import com.proximity.app.data.PaymentRecord
import com.proximity.app.data.PaymentRecordStatus
import com.proximity.app.payment.ActivePaymentSession
import com.proximity.app.payment.PaymentState
import com.proximity.app.payment.QrCodeDecoder
import com.proximity.app.theme.AppTheme
import com.proximity.app.theme.ColorTokens
import com.proximity.app.theme.LocalAppVisuals
import com.proximity.app.ui.components.AnimatedBackground
import com.proximity.app.ui.components.GlassCard
import com.proximity.app.ui.components.pressScale
import com.proximity.app.ui.payment.PaymentResultScreen
import com.proximity.app.utils.PermissionChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.EnumMap
import java.util.concurrent.Executors

class QrScannerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = application as AppCore
        val curSettings = app.services.preferencesStore.settingsFlow.value

        if (curSettings.isJioPrimarySim) {
            Toast.makeText(this, R.string.testcfg_jio_no_ussd, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (curSettings.ussdReportedNotWorking) {
            Toast.makeText(this, R.string.testcfg_ussd_reported_issue, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            val settings by app.services.preferencesStore.settingsFlow.collectAsState()
            val paymentState by app.services.paymentLifecycleManager.state.collectAsState()

            AppTheme(settings = settings) {
                QrScannerAndUssdFlow(
                    configuredBankName = settings.selectedBankDisplayName,
                    paymentState = paymentState,
                    onStartUssdSession = { decodedQr ->
                        app.services.paymentLifecycleManager.startNewSession(
                            channel = PaymentChannel.USSD_99,
                            recipientName = decodedQr.payeeName,
                            phoneNumber = null,
                            vpa = decodedQr.vpa,
                            amountRupees = decodedQr.amount ?: "0.00",
                            configuredBankName = settings.selectedBankDisplayName
                        )
                    },
                    onMarkInProgress = {
                        app.services.paymentLifecycleManager.markCallOrUssdInProgress()
                    },
                    onMarkWaitingForVerification = {
                        app.services.appScope.launch {
                            app.services.paymentLifecycleManager.transitionToWaitingForVerification(
                                startQrGraceTimer = true
                            )
                        }
                    },
                    onTerminateSession = {
                        app.services.paymentLifecycleManager.cancelCurrentPaymentAsync("Terminated by user")
                    },
                    onNavigateToResult = { record ->
                        clearSensitiveClipboard(this@QrScannerActivity)
                        UssdDialogReaderService.disableService()
                        startActivity(
                            Intent(this@QrScannerActivity, PaymentResultScreen::class.java).apply {
                                putExtra(PaymentResultScreen.EXTRA_PAYMENT_RECORD, record)
                            }
                        )
                        finish()
                    },
                    onCloseScanner = {
                        clearSensitiveClipboard(this@QrScannerActivity)
                        UssdDialogReaderService.disableService()
                        finish()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            clearSensitiveClipboard(this)
        }
        super.onDestroy()
    }

    companion object {
        fun copySensitiveVpaToClipboard(context: Context, vpa: String) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            val clip = ClipData.newPlainText("UPI VPA", vpa)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
            }
            cm.setPrimaryClip(clip)
        }

        fun clearSensitiveClipboard(context: Context) {
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    cm.clearPrimaryClip()
                } else {
                    cm.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            } catch (_: Exception) {
            }
        }
    }
}

@Composable
fun QrScannerAndUssdFlow(
    configuredBankName: String,
    paymentState: PaymentState,
    onStartUssdSession: suspend (QrCodeDecoder.DecodeResult.Success) -> ActivePaymentSession,
    onMarkInProgress: suspend () -> Unit,
    onMarkWaitingForVerification: () -> Unit,
    onTerminateSession: () -> Unit,
    onNavigateToResult: (PaymentRecord) -> Unit,
    onCloseScanner: () -> Unit
) {
    val context = LocalContext.current
    val visuals = LocalAppVisuals.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(PermissionChecker.isCameraGranted(context))
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var lockedQr by remember { mutableStateOf<QrCodeDecoder.DecodeResult.Success?>(null) }
    var pendingQrWaitingForAccessibilityChoice by remember { mutableStateOf<QrCodeDecoder.DecodeResult.Success?>(null) }
    var activeUssdFlow by rememberSaveable { mutableStateOf(false) }
    var activeSessionEntity by remember { mutableStateOf<ActivePaymentSession?>(null) }
    var activeTransactionId by rememberSaveable { mutableStateOf<String?>(null) }
    var savedPayeeName by rememberSaveable { mutableStateOf<String?>(null) }
    var savedVpa by rememberSaveable { mutableStateOf<String?>(null) }
    var savedAmount by rememberSaveable { mutableStateOf<String?>(null) }

    var invalidReasonRes by remember { mutableStateOf<Int?>(null) }
    var lastScanAttemptMillis by rememberSaveable { mutableLongStateOf(0L) }
    var torchEnabled by rememberSaveable { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }

    var showStepCardAfter4s by rememberSaveable { mutableStateOf(false) }
    var ussdStatusRes by rememberSaveable { mutableIntStateOf(R.string.ussd_status_processing) }
    var isTerminating by rememberSaveable { mutableStateOf(false) }
    var awaitingCountdownSeconds by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(paymentState, activeTransactionId) {
        val targetTxnId = activeTransactionId ?: return@LaunchedEffect
        val matchedRecord = when (paymentState) {
            is PaymentState.Success -> paymentState.transaction.takeIf { it.id == targetTxnId }
            is PaymentState.Failed -> paymentState.transaction.takeIf { it.id == targetTxnId }
            is PaymentState.NeedsReview -> paymentState.transaction.takeIf { it.id == targetTxnId }
            else -> null
        }
        if (matchedRecord != null) {
            onNavigateToResult(matchedRecord)
        }
    }

    LaunchedEffect(invalidReasonRes) {
        if (invalidReasonRes != null) {
            delay(3500L)
            invalidReasonRes = null
        }
    }

    fun buildCurrentPendingRecord(): PaymentRecord {
        val session = activeSessionEntity
        return PaymentRecord(
            id = session?.transactionId ?: activeTransactionId ?: "PAY-PENDING",
            recipientName = lockedQr?.payeeName ?: savedPayeeName,
            phoneNumber = null,
            vpa = lockedQr?.vpa ?: savedVpa,
            amountRupees = lockedQr?.amount ?: savedAmount ?: "0.00",
            bankName = configuredBankName,
            bankReferenceId = null,
            status = PaymentRecordStatus.PENDING,
            channel = PaymentChannel.USSD_99,
            timestampMillis = session?.startedAtMillis ?: System.currentTimeMillis(),
            verificationDeadlineMillis = session?.smsWindowCloseMillis ?: System.currentTimeMillis(),
            bankConfirmationExcerpt = null
        )
    }

    fun beginUssdExecution(qr: QrCodeDecoder.DecodeResult.Success) {
        activeUssdFlow = true
        savedPayeeName = qr.payeeName
        savedVpa = qr.vpa
        savedAmount = qr.amount
        scope.launch {
            val session = onStartUssdSession(qr)
            activeSessionEntity = session
            activeTransactionId = session.transactionId
            QrScannerActivity.copySensitiveVpaToClipboard(context, qr.vpa)
            ussdStatusRes = R.string.ussd_status_processing
            delay(1000L)
            ussdStatusRes = R.string.ussd_status_calling_bank
            onMarkInProgress()

            val ussdUri = Uri.parse("tel:*99*1*3%23")
            try {
                context.startActivity(Intent(Intent.ACTION_CALL, ussdUri))
            } catch (_: SecurityException) {
                context.startActivity(Intent(Intent.ACTION_DIAL, ussdUri))
            } catch (_: Exception) {
            }

            delay(800L)
            ussdStatusRes = R.string.ussd_status_call_started
            delay(1200L)
            ussdStatusRes = R.string.ussd_status_complete_in_dialer
            delay(1000L)
            showStepCardAfter4s = true
        }
    }

    DisposableEffect(lifecycleOwner, pendingQrWaitingForAccessibilityChoice) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = PermissionChecker.isCameraGranted(context)
                val pending = pendingQrWaitingForAccessibilityChoice
                if (pending != null && UssdDialogReaderService.isAccessibilityEnabled(context)) {
                    pendingQrWaitingForAccessibilityChoice = null
                    beginUssdExecution(pending)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(awaitingCountdownSeconds != null) {
        if (awaitingCountdownSeconds != null) {
            var remaining = awaitingCountdownSeconds ?: 25
            while (remaining > 0) {
                awaitingCountdownSeconds = remaining
                delay(1000L)
                remaining--
            }
            onNavigateToResult(buildCurrentPendingRecord())
        }
    }

    fun handleDecodedQrString(rawText: String) {
        when (val result = QrCodeDecoder.decode(rawText)) {
            is QrCodeDecoder.DecodeResult.Success -> {
                lockedQr = result
                scope.launch {
                    delay(260L)
                    if (UssdDialogReaderService.isAccessibilityEnabled(context)) {
                        beginUssdExecution(result)
                    } else {
                        pendingQrWaitingForAccessibilityChoice = result
                    }
                }
            }
            is QrCodeDecoder.DecodeResult.Invalid -> {
                invalidReasonRes = when (result.reason) {
                    QrCodeDecoder.DecodeReason.EMPTY -> R.string.qr_reason_empty
                    QrCodeDecoder.DecodeReason.NOT_UPI -> R.string.qr_reason_not_upi
                    QrCodeDecoder.DecodeReason.MALFORMED -> R.string.qr_reason_malformed
                    QrCodeDecoder.DecodeReason.NO_PAYEE -> R.string.qr_reason_no_payee
                    QrCodeDecoder.DecodeReason.INVALID_PAYEE -> R.string.qr_reason_invalid_payee
                    QrCodeDecoder.DecodeReason.INVALID_AMOUNT -> R.string.qr_reason_invalid_amount
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val decodeOutcome = withContext(Dispatchers.IO) {
                    OfflineQrFrameAnalyzer.decodeFromUri(context, uri)
                }
                when (decodeOutcome) {
                    is GalleryQrOutcome.Decoded -> handleDecodedQrString(decodeOutcome.rawText)
                    GalleryQrOutcome.NoQrFound ->
                        Toast.makeText(context, R.string.qr_gallery_no_code, Toast.LENGTH_SHORT).show()
                    GalleryQrOutcome.UnreadableImage ->
                        Toast.makeText(context, R.string.qr_gallery_unreadable, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    BackHandler {
        if (activeUssdFlow) {
            scope.launch {
                onTerminateSession()
                onCloseScanner()
            }
        } else {
            onCloseScanner()
        }
    }

    if (!activeUssdFlow) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasCameraPermission) {
                CameraXViewfinder(
                    modifier = Modifier.fillMaxSize(),
                    onCameraBound = { cam -> cameraRef = cam },
                    onQrDetected = { raw ->
                        val now = System.currentTimeMillis()
                        if (lockedQr == null && now - lastScanAttemptMillis >= AppLimits.QR_SCAN_COOLDOWN_MS) {
                            lastScanAttemptMillis = now
                            handleDecodedQrString(raw)
                        }
                    }
                )
            } else {
                AnimatedBackground(modifier = Modifier.fillMaxSize())
            }

            ReticleAndLaserOverlay(
                isLockedOn = lockedQr != null,
                modifier = Modifier.fillMaxSize()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCloseScanner,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_back_arrow),
                        contentDescription = stringResource(id = R.string.cd_back),
                        tint = Color.White
                    )
                }
            }

            AnimatedVisibility(
                visible = invalidReasonRes != null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 68.dp, start = 24.dp, end = 24.dp)
            ) {
                val reasonText = invalidReasonRes?.let { stringResource(id = it) }.orEmpty()
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF090D14).copy(alpha = 0.88f))
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.qr_invalid_banner, reasonText),
                        color = ColorTokens.StatusErrorRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 42.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (torchEnabled) ColorTokens.BrandTeal
                            else Color.Black.copy(alpha = 0.55f)
                        )
                        .pressScale {
                            val next = !torchEnabled
                            torchEnabled = next
                            cameraRef?.cameraControl?.enableTorch(next)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (torchEnabled) R.drawable.ic_flash_on else R.drawable.ic_flash_off
                        ),
                        contentDescription = stringResource(id = R.string.qr_torch),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .pressScale { galleryLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_gallery),
                        contentDescription = stringResource(id = R.string.qr_pick_from_gallery),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedBackground(modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(id = R.string.ussd_follow_instructions),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorTokens.BrandTeal
                        )
                        AnimatedVisibility(visible = showStepCardAfter4s) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(id = R.string.ussd_step_card_text),
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = visuals.textPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (awaitingCountdownSeconds != null) {
                    CircularProgressIndicator(
                        color = ColorTokens.BrandTeal,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(id = R.string.ussd_countdown_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = visuals.textPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            id = R.string.ussd_countdown_subtitle,
                            awaitingCountdownSeconds ?: 0
                        ),
                        fontSize = 14.sp,
                        color = visuals.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = {
                            onNavigateToResult(buildCurrentPendingRecord())
                        },
                        shape = CircleShape
                    ) {
                        Text(
                            text = stringResource(id = R.string.ussd_check_status_later),
                            color = ColorTokens.BrandTeal
                        )
                    }
                } else {
                    Text(
                        text = if (isTerminating) {
                            stringResource(id = R.string.ussd_status_terminated)
                        } else {
                            stringResource(id = ussdStatusRes)
                        },
                        fontSize = 18.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isTerminating) ColorTokens.StatusErrorRed else visuals.textPrimary,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (awaitingCountdownSeconds == null && !isTerminating) {
                    Button(
                        onClick = {
                            ussdStatusRes = R.string.ussd_status_waiting_sms
                            awaitingCountdownSeconds = 25
                            onMarkWaitingForVerification()
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.BrandTeal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .pressScale()
                    ) {
                        Text(
                            text = stringResource(id = R.string.ussd_action_completed),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isTerminating = true
                                onTerminateSession()
                                delay(1500L)
                                onCloseScanner()
                            }
                        },
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.ussd_action_terminate),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ColorTokens.StatusErrorRed
                        )
                    }
                }
            }
        }
    }

    val pendingModalQr = pendingQrWaitingForAccessibilityChoice
    if (pendingModalQr != null) {
        AlertDialog(
            onDismissRequest = {
                pendingQrWaitingForAccessibilityChoice = null
                lockedQr = null
            },
            containerColor = visuals.highSurface,
            title = {
                Text(
                    text = stringResource(id = R.string.accessibility_modal_title),
                    fontWeight = FontWeight.Bold,
                    color = visuals.textPrimary
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.accessibility_modal_body),
                    color = visuals.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.BrandTeal)
                ) {
                    Text(
                        text = stringResource(id = R.string.accessibility_enable_settings),
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            pendingQrWaitingForAccessibilityChoice = null
                            lockedQr = null
                            onCloseScanner()
                        }
                    ) {
                        Text(
                            text = stringResource(id = R.string.action_cancel),
                            color = visuals.textSecondary
                        )
                    }
                    TextButton(
                        onClick = {
                            val qr = pendingModalQr
                            pendingQrWaitingForAccessibilityChoice = null
                            beginUssdExecution(qr)
                        }
                    ) {
                        Text(
                            text = stringResource(id = R.string.accessibility_continue_anyway),
                            color = ColorTokens.BrandTeal,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun ReticleAndLaserOverlay(
    isLockedOn: Boolean,
    modifier: Modifier = Modifier
) {
    val insetState = animateDpAsState(
        targetValue = if (isLockedOn) 6.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ReticleContraction"
    )
    val bracketColorState = animateColorAsState(
        targetValue = if (isLockedOn) ColorTokens.BrandTeal else Color.White,
        animationSpec = tween(durationMillis = 220),
        label = "ReticleColor"
    )

    val infinite = rememberInfiniteTransition(label = "LaserSweep")
    val laserFractionState = infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LaserFraction"
    )

    val cutoutPath = remember { Path() }

    Canvas(modifier = modifier) {
        val insetAnim = insetState.value
        val bracketColor = bracketColorState.value
        val laserFraction = laserFractionState.value
        val boxSize = 260.dp.toPx()
        val left = (size.width - boxSize) / 2f
        val top = (size.height - boxSize) / 2f
        val right = left + boxSize
        val bottom = top + boxSize
        val cornerRad = 20.dp.toPx()

        cutoutPath.reset()
        cutoutPath.addRoundRect(
            RoundRect(
                rect = Rect(left, top, right, bottom),
                cornerRadius = CornerRadius(cornerRad, cornerRad)
            )
        )
        clipPath(cutoutPath, clipOp = ClipOp.Difference) {
            drawRect(color = Color.Black.copy(alpha = 0.60f))
        }

        val insetPx = insetAnim.toPx()
        val bLeft = left + insetPx
        val bTop = top + insetPx
        val bRight = right - insetPx
        val bBottom = bottom - insetPx
        val arm = 32.dp.toPx()
        val stroke = 4.dp.toPx()

        drawLine(bracketColor, Offset(bLeft, bTop), Offset(bLeft + arm, bTop), stroke, StrokeCap.Round)
        drawLine(bracketColor, Offset(bLeft, bTop), Offset(bLeft, bTop + arm), stroke, StrokeCap.Round)
        drawLine(bracketColor, Offset(bRight, bTop), Offset(bRight - arm, bTop), stroke, StrokeCap.Round)
        drawLine(bracketColor, Offset(bRight, bTop), Offset(bRight, bTop + arm), stroke, StrokeCap.Round)
        drawLine(bracketColor, Offset(bLeft, bBottom), Offset(bLeft + arm, bBottom), stroke, StrokeCap.Round)
        drawLine(bracketColor, Offset(bLeft, bBottom), Offset(bLeft, bBottom - arm), stroke, StrokeCap.Round)
        drawLine(bracketColor, Offset(bRight, bBottom), Offset(bRight - arm, bBottom), stroke, StrokeCap.Round)
        drawLine(bracketColor, Offset(bRight, bBottom), Offset(bRight, bBottom - arm), stroke, StrokeCap.Round)

        val margin = 12.dp.toPx()
        val laserY = bTop + margin + (boxSize - 2 * margin - 2 * insetPx) * laserFraction
        val trailH = 28.dp.toPx()
        val leadH = 2.5.dp.toPx()

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    ColorTokens.BrandTeal.copy(alpha = 0.42f)
                ),
                startY = (laserY - trailH).coerceAtLeast(bTop),
                endY = laserY
            ),
            topLeft = Offset(bLeft + margin, (laserY - trailH).coerceAtLeast(bTop)),
            size = Size(boxSize - 2 * margin - 2 * insetPx, trailH)
        )

        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    ColorTokens.BrandTeal,
                    Color.White,
                    ColorTokens.BrandTeal,
                    Color.Transparent
                ),
                startX = bLeft + margin,
                endX = bRight - margin
            ),
            start = Offset(bLeft + margin, laserY),
            end = Offset(bRight - margin, laserY),
            strokeWidth = leadH,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun CameraXViewfinder(
    modifier: Modifier = Modifier,
    onCameraBound: (Camera) -> Unit,
    onQrDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCameraBound by rememberUpdatedState(onCameraBound)
    val currentOnQrDetected by rememberUpdatedState(onQrDetected)
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner) {
        var disposed = false
        var boundProvider: ProcessCameraProvider? = null
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            if (disposed) return@addListener
            try {
                val cameraProvider = cameraProviderFuture.get()
                if (disposed) return@addListener
                boundProvider = cameraProvider
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            val decoded = OfflineQrFrameAnalyzer.decodeFromImageProxy(imageProxy)
                            if (decoded != null && !disposed) {
                                previewView.post { currentOnQrDetected(decoded) }
                            }
                        }
                    }
                cameraProvider.unbindAll()
                val cam = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
                currentOnCameraBound(cam)
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            try {
                boundProvider?.unbindAll()
            } catch (_: Exception) {
            }
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { previewView }
    )
}

sealed class GalleryQrOutcome {
    data class Decoded(val rawText: String) : GalleryQrOutcome()
    object NoQrFound : GalleryQrOutcome()
    object UnreadableImage : GalleryQrOutcome()
}

object OfflineQrFrameAnalyzer {

    private fun createReader(): MultiFormatReader {
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
            put(DecodeHintType.TRY_HARDER, true)
            put(DecodeHintType.CHARACTER_SET, "UTF-8")
        }
        return MultiFormatReader().apply { setHints(hints) }
    }

    private val threadLocalReader = ThreadLocal.withInitial { createReader() }

    private fun extractTightLuminancePlane(imageProxy: ImageProxy): ByteArray? {
        val plane = imageProxy.planes.firstOrNull() ?: return null
        val width = imageProxy.width
        val height = imageProxy.height
        val buffer = plane.buffer
        buffer.rewind()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        if (pixelStride == 1 && rowStride == width) {
            val out = ByteArray(width * height)
            val toRead = minOf(out.size, buffer.remaining())
            buffer.get(out, 0, toRead)
            return out
        }

        val out = ByteArray(width * height)
        val rowData = ByteArray(rowStride)
        val limit = buffer.limit()
        for (row in 0 until height) {
            val rowStart = row * rowStride
            if (rowStart >= limit) break
            buffer.position(rowStart)
            val available = minOf(rowStride, buffer.remaining())
            buffer.get(rowData, 0, available)
            if (pixelStride == 1) {
                val copyLen = minOf(width, available)
                System.arraycopy(rowData, 0, out, row * width, copyLen)
            } else {
                for (col in 0 until width) {
                    val srcIdx = col * pixelStride
                    if (srcIdx < available) {
                        out[row * width + col] = rowData[srcIdx]
                    }
                }
            }
        }
        return out
    }

    fun decodeFromImageProxy(imageProxy: ImageProxy): String? {
        return try {
            val data = extractTightLuminancePlane(imageProxy) ?: return null
            val width = imageProxy.width
            val height = imageProxy.height
            val source = PlanarYUVLuminanceSource(
                data, width, height, 0, 0, width, height, false
            )
            val reader = threadLocalReader.get() ?: createReader()
            reader.reset()
            try {
                reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
            } catch (_: Exception) {
                reader.reset()
                val inverted = InvertedLuminanceSource(source)
                reader.decodeWithState(BinaryBitmap(HybridBinarizer(inverted))).text
            }
        } catch (_: Exception) {
            null
        } finally {
            imageProxy.close()
        }
    }

    fun decodeFromUri(context: Context, uri: Uri): GalleryQrOutcome {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
                return GalleryQrOutcome.UnreadableImage
            }
            var sampleSize = 1
            while (boundsOpts.outWidth / sampleSize > 2048 || boundsOpts.outHeight / sampleSize > 2048) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return GalleryQrOutcome.UnreadableImage

            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            bitmap.recycle()

            val source = RGBLuminanceSource(w, h, pixels)
            val reader = createReader()
            val text = try {
                reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
            } catch (_: Exception) {
                reader.reset()
                val inverted = InvertedLuminanceSource(source)
                reader.decodeWithState(BinaryBitmap(HybridBinarizer(inverted))).text
            }
            if (text.isNullOrBlank()) GalleryQrOutcome.NoQrFound
            else GalleryQrOutcome.Decoded(text)
        } catch (_: com.google.zxing.NotFoundException) {
            GalleryQrOutcome.NoQrFound
        } catch (_: Exception) {
            GalleryQrOutcome.NoQrFound
        }
    }
}
