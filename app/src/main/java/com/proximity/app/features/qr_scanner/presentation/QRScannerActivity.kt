package com.proximity.app.features.qr_scanner.presentation

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.proximity.app.ProximityApplication
import com.proximity.app.R
import com.proximity.app.data.TransactionSource
import com.proximity.app.data.UPIData
import com.proximity.app.ui.components.AnimatedContourBackground
import com.proximity.app.ui.components.pressScale
import com.proximity.app.ui.theme.LocalProximityAccentTheme
import com.proximity.app.ui.theme.ProximityTheme
import com.proximity.app.ui.theme.StatusRed
import com.proximity.app.ui.theme.TealAccentTheme
import com.proximity.app.features.qr_scanner.domain.QRCodeAnalyzer
import com.proximity.app.features.qr_scanner.domain.QRCodeParser
import com.proximity.app.features.qr_scanner.domain.messageFor
import com.proximity.app.helpers.SetupHelper
import com.proximity.app.helpers.TransactionDetector
import com.proximity.app.managers.PermissionManager
import com.proximity.app.payment.PaymentSessionManager
import com.proximity.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class QRScannerState(
    val isProcessing: Boolean = false,
    val showBlackScreen: Boolean = false,
    val blackScreenStatusMessage: String? = null,
    val blackScreenStatusRes: Int? = null,
    val blackScreenStatusFormatArg: String? = null,
    val blackScreenStatusColor: Color = TextPrimary,
    val isFlashOn: Boolean = false,
    val scanError: String? = null,
    val instructionsExpanded: Boolean = false,
    val showTerminateButtons: Boolean = false
)

class QRScannerActivity : ComponentActivity() {

    companion object {
        const val RESULT_CANCELLED = 0
        const val RESULT_SUCCESS = 1
        const val RESULT_ERROR = 2
        private const val SCAN_ERROR_VISIBLE_MS = 3500L
        private const val WAITING_SCREEN_TIMEOUT_MS = PaymentSessionManager.DEFAULT_VERIFICATION_DEADLINE_MS
        private const val WAITING_SCREEN_EXIT_DELAY_MS = 2000L
        private const val GALLERY_MAX_EDGE_PX = 2048
    }

    private val _uiState = MutableStateFlow(QRScannerState())
    val uiState: StateFlow<QRScannerState> = _uiState.asStateFlow()

    private lateinit var cameraExecutor: ExecutorService
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private lateinit var permissionManager: PermissionManager
    private var isUSSDProcessActive = false
    private var messageHandler: Handler? = null
    private var smsTimeoutHandler: android.os.Handler? = null
    private var smsTimeoutRunnable: Runnable? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var isProcessingQRCode = false
    @Volatile private var hasDialedUSSD = false
    @Volatile private var finishedByWaitingScreenTimeout = false
    @Volatile private var finishedByManualCompletion = false
    private var didCopyVpa = false

    lateinit var viewFinder: PreviewView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResult(permissions)
    }

    private val callPhonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            dialUSSD()
        } else {
            showError(getString(R.string.error_call_permission_required_payments))
        }
    }

    private val galleryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            Log.d("QRScanner", "Gallery pick cancelled")
        } else {
            decodeQrFromGallery(uri)
        }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed

    private val overlayDismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "DISMISS_OVERLAY") {
                Log.d("QRScanner", "USSD overlay dismissed - continuing to wait for SMS")
                if (isUSSDProcessActive) {
                    updateBlackScreenStatus(R.string.qr_status_ussd_completed)
                }
            }
        }
    }

    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.proximity.app.SMS_RECEIVED") {
                Log.d("QRScanner", "SMS received - transaction successful!")
                if (isUSSDProcessActive) {
                    handleSMSReceived(intent)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("QRScanner", "=== QR SCANNER ACTIVITY CREATED ===")

        WindowCompat.setDecorFitsSystemWindows(window, false)

        SetupHelper.getScanToPayBlockedMessage(this)?.let { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELLED)
            finish()
            return
        }

        viewFinder = PreviewView(this)
        permissionManager = PermissionManager(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        LocalBroadcastManager.getInstance(this).registerReceiver(overlayDismissReceiver, IntentFilter("DISMISS_OVERLAY"))
        LocalBroadcastManager.getInstance(this).registerReceiver(smsReceiver, IntentFilter("com.proximity.app.SMS_RECEIVED"))

        checkPermissionsAndStartCamera()

        val app = application as? ProximityApplication
        val settingsRepository = app?.settingsRepository ?: com.proximity.app.data.SettingsRepository(applicationContext)

        setContent {
            val savedSettings by settingsRepository.settingsFlow.collectAsState()
            val state by uiState.collectAsState()

            CompositionLocalProvider(LocalProximityAccentTheme provides TealAccentTheme) {
                ProximityTheme(darkTheme = savedSettings.darkModeEnabled) {
                    QRScannerScreen(
                        state = state,
                        viewFinder = viewFinder,
                        onBackClick = {
                            setResult(RESULT_CANCELLED)
                            finish()
                        },
                        onTerminateClick = { terminateUSSDProcess() },
                        onUssdCompletedClick = {
                            val sessionManager = ProximityApplication.from(this@QRScannerActivity)?.paymentSessionManager
                            sessionManager?.onUssdCallEndedManually()
                            finishedByManualCompletion = true
                            finish()
                        },
                        onFlashClick = {
                            val newFlash = !state.isFlashOn
                            camera?.cameraControl?.enableTorch(newFlash)
                            _uiState.update { it.copy(isFlashOn = newFlash) }
                        },
                        onGalleryClick = {
                            galleryPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                }
            }
        }
    }

    private fun checkPermissionsAndStartCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        } else {
            startCamera()
        }
    }

    private fun handlePermissionResult(permissions: Map<String, Boolean>) {
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        if (cameraGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required for QR scanning", Toast.LENGTH_LONG).show()
            setResult(RESULT_ERROR)
            finish()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                if (!isActivityAlive()) return@addListener
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }
                imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, QRCodeAnalyzer { qrCode -> processQRCode(qrCode) })
                    }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                val analyzer = imageAnalyzer ?: return@addListener
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, analyzer)
            } catch (e: Exception) {
                Log.e("QRScanner", "Error in camera setup", e)
                Toast.makeText(this, R.string.error_camera_setup, Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processQRCode(qrCode: String) {
        try {
            if (isProcessingQRCode) return
            isProcessingQRCode = true
            imageAnalyzer?.clearAnalyzer()

            _uiState.update { it.copy(isProcessing = true) }

            when (val result = QRCodeParser.parse(qrCode)) {
                is QRCodeParser.ParseResult.Valid -> {
                    initiateUSSDPayment(result.data)
                }
                is QRCodeParser.ParseResult.Invalid -> {
                    showInvalidQrCode(result.reason.messageFor(this))
                }
            }
        } catch (e: Exception) {
            showError(getString(R.string.error_qr_processing))
            isProcessingQRCode = false
        }
    }

    private fun initiateUSSDPayment(upiData: UPIData) {
        proceedWithPayment(upiData)
    }

    private fun proceedWithPayment(upiData: UPIData) {
        try {
            if (upiData.vpa.isBlank()) {
                showError(getString(R.string.error_qr_invalid_vpa))
                return
            }
            if (!beginQrPaymentSession(upiData)) return

            showBlackScreenWithStatus("Processing payment...")
            copyVpaToClipboard(upiData.vpa)

            mainHandler.postDelayed({
                if (!isActivityAlive()) return@postDelayed
                if (hasDialedUSSD) return@postDelayed
                hasDialedUSSD = true
                updateBlackScreenStatus(R.string.qr_status_initiating_call)
                dialUSSD()
            }, 1000)
        } catch (e: Exception) {
            showError(getString(R.string.error_unexpected))
        }
    }

    private fun beginQrPaymentSession(upiData: UPIData): Boolean {
        val sessionManager = ProximityApplication.from(this)?.paymentSessionManager
        val sessionTxnId = sessionManager?.begin("", upiData.amount ?: "", upiData.vpa, TransactionSource.QR)
        return try {
            TransactionDetector.getInstance(this).startOperation("QR_SCAN", upiData.amount, sessionTxnId)
            true
        } catch (e: IllegalStateException) {
            sessionManager?.onDialFailed("Could not start SMS monitoring")
            showError(getString(R.string.error_payment_init_failed))
            false
        }
    }

    private fun copyVpaToClipboard(vpa: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("VPA", vpa)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
            }
            clipboard.setPrimaryClip(clip)
            didCopyVpa = true
        } catch (e: Exception) { }
    }

    private fun clearVpaClipboard() {
        if (!didCopyVpa) return
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.clearPrimaryClip()
            didCopyVpa = false
        } catch (e: Exception) { }
    }

    private fun dialUSSD() {
        val ussdCode = "*99*1*3#"
        val ussd = ussdCode.replace("#", Uri.encode("#"))
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            callPhonePermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            return
        }
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$ussd")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            updateBlackScreenStatus(R.string.qr_status_call_initiated)
            startActivity(intent)
            startUSSDProcessMessages()
        } catch (e: Exception) {
            ProximityApplication.from(this)?.paymentSessionManager?.onDialFailed("Could not start the payment call")
            showError(getString(R.string.error_ussd_call_failed))
        }
    }

    private fun startUSSDProcessMessages() {
        isUSSDProcessActive = true
        updateBlackScreenStatus(R.string.qr_status_complete_in_dialer)
        startSMSTimeout()
    }

    private fun startSMSTimeout() {
        smsTimeoutHandler = Handler(Looper.getMainLooper())
        smsTimeoutRunnable = Runnable {
            if (!isActivityAlive()) return@Runnable
            if (isUSSDProcessActive) {
                updateBlackScreenStatus(R.string.qr_waiting_timeout)
                mainHandler.postDelayed({
                    if (!isActivityAlive()) return@postDelayed
                    finishedByWaitingScreenTimeout = true
                    setResult(RESULT_CANCELLED)
                    finish()
                }, WAITING_SCREEN_EXIT_DELAY_MS)
            }
        }
        smsTimeoutHandler?.postDelayed(smsTimeoutRunnable!!, WAITING_SCREEN_TIMEOUT_MS)
    }

    private fun handleSMSReceived(intent: Intent) {
        smsTimeoutRunnable?.let { smsTimeoutHandler?.removeCallbacks(it) }
        messageHandler?.removeCallbacksAndMessages(null)
        _uiState.update { it.copy(showBlackScreen = false) }
        setResult(RESULT_SUCCESS)
        finish()
    }

    private fun terminateUSSDProcess() {
        try {
            isUSSDProcessActive = false
            messageHandler?.removeCallbacksAndMessages(null)
            smsTimeoutRunnable?.let { smsTimeoutHandler?.removeCallbacks(it) }
            try {
                TransactionDetector.getInstance(this).stopOperation()
            } catch (e: Exception) {}
            ProximityApplication.from(this)?.paymentSessionManager?.onUserCancelled()
            updateBlackScreenStatus(R.string.qr_status_terminated)
            _uiState.update { it.copy(showTerminateButtons = false) }
            mainHandler.postDelayed({
                if (!isActivityAlive()) return@postDelayed
                setResult(RESULT_CANCELLED)
                finish()
            }, 1500)
        } catch (e: Exception) {
            setResult(RESULT_ERROR)
            finish()
        }
    }

    private fun showBlackScreenWithStatus(message: String) {
        _uiState.update {
            it.copy(
                showBlackScreen = true,
                blackScreenStatusMessage = message,
                blackScreenStatusRes = null,
                blackScreenStatusColor = TextPrimary,
                instructionsExpanded = false,
                showTerminateButtons = true
            )
        }
        lifecycleScope.launch {
            delay(4000)
            if (isActivityAlive()) {
                _uiState.update { it.copy(instructionsExpanded = true) }
            }
        }
    }

    private fun updateBlackScreenStatus(@StringRes messageRes: Int, formatArg: Any? = null) {
        _uiState.update {
            it.copy(
                blackScreenStatusRes = messageRes,
                blackScreenStatusFormatArg = formatArg?.toString(),
                blackScreenStatusMessage = null
            )
        }
    }

    private fun decodeQrFromGallery(uri: Uri) {
        lifecycleScope.launch {
            val decoded = withContext(Dispatchers.IO) {
                runCatching {
                    val source = ImageDecoder.createSource(contentResolver, uri)
                    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = false
                        val longest = maxOf(info.size.width, info.size.height)
                        if (longest > GALLERY_MAX_EDGE_PX) {
                            decoder.setTargetSampleSize(longest / GALLERY_MAX_EDGE_PX + 1)
                        }
                    }
                    QRCodeAnalyzer.decodeBitmap(bitmap).also { bitmap.recycle() }
                }
            }
            if (!isActivityAlive()) return@launch
            when {
                decoded.isFailure -> showInvalidQrCode(getString(R.string.qr_gallery_unreadable))
                decoded.getOrNull() == null -> showInvalidQrCode(getString(R.string.qr_gallery_no_code))
                else -> {
                    isProcessingQRCode = false
                    processQRCode(decoded.getOrNull()!!)
                }
            }
        }
    }

    private fun showInvalidQrCode(reason: String) {
        _uiState.update { it.copy(scanError = getString(R.string.qr_invalid_banner, reason), isProcessing = false) }
        isProcessingQRCode = false
        lifecycleScope.launch {
            delay(SCAN_ERROR_VISIBLE_MS)
            _uiState.update { it.copy(scanError = null) }
        }
    }

    private fun showError(message: String) {
        _uiState.update {
            it.copy(
                showBlackScreen = true,
                blackScreenStatusRes = R.string.qr_error_prefix,
                blackScreenStatusFormatArg = message,
                blackScreenStatusColor = StatusRed
            )
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        lifecycleScope.launch {
            delay(3000)
            if (isActivityAlive()) {
                _uiState.update {
                    it.copy(
                        showBlackScreen = false,
                        isProcessing = false,
                        instructionsExpanded = false,
                        showTerminateButtons = false,
                        isFlashOn = false
                    )
                }
                camera?.cameraControl?.enableTorch(false)
                isProcessingQRCode = false
                hasDialedUSSD = false
                if (::cameraExecutor.isInitialized && !cameraExecutor.isShutdown) {
                    imageAnalyzer?.clearAnalyzer()
                    imageAnalyzer?.setAnalyzer(cameraExecutor, QRCodeAnalyzer { qrCode -> processQRCode(qrCode) })
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        try { LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("APP_PAUSED")) } catch (e: Exception) {}
        try { imageAnalyzer?.clearAnalyzer() } catch (e: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        try { LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("APP_STOPPED")) } catch (e: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        try {
            if (!::cameraExecutor.isInitialized || cameraExecutor.isShutdown) return
            if (imageAnalyzer != null && !isProcessingQRCode) {
                imageAnalyzer?.clearAnalyzer()
                imageAnalyzer?.setAnalyzer(cameraExecutor, QRCodeAnalyzer { qrCode -> processQRCode(qrCode) })
            }
        } catch (e: Exception) {}
    }

    override fun onDestroy() {
        try {
            mainHandler.removeCallbacksAndMessages(null)
            try { LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("APP_DESTROYED")) } catch (e: Exception) {}
            isUSSDProcessActive = false
            messageHandler?.removeCallbacksAndMessages(null)
            smsTimeoutRunnable?.let { smsTimeoutHandler?.removeCallbacks(it) }
            isProcessingQRCode = false
            hasDialedUSSD = false

            if (isFinishing && !finishedByWaitingScreenTimeout && !finishedByManualCompletion) {
                ProximityApplication.from(this)?.paymentSessionManager?.onUserCancelled()
            }
            clearVpaClipboard()
            try {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(overlayDismissReceiver)
                LocalBroadcastManager.getInstance(this).unregisterReceiver(smsReceiver)
            } catch (e: Exception) {}
            imageAnalyzer?.clearAnalyzer()
            imageAnalyzer = null
            if (::cameraExecutor.isInitialized && !cameraExecutor.isShutdown) {
                cameraExecutor.shutdown()
            }
        } catch (e: Exception) {}
        finally {
            super.onDestroy()
        }
    }
}

@Composable
fun QRScannerScreen(
    state: QRScannerState,
    viewFinder: PreviewView,
    onBackClick: () -> Unit,
    onTerminateClick: () -> Unit,
    onUssdCompletedClick: () -> Unit,
    onFlashClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    if (state.showBlackScreen) {
        BlackScreenOverlay(
            state = state,
            onTerminateClick = onTerminateClick,
            onUssdCompletedClick = onUssdCompletedClick
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { viewFinder },
                modifier = Modifier.fillMaxSize()
            )
            ScannerOverlay(
                state = state,
                onBackClick = onBackClick,
                onFlashClick = onFlashClick,
                onGalleryClick = onGalleryClick
            )
        }
    }
}

@Composable
fun ScannerOverlay(
    state: QRScannerState,
    onBackClick: () -> Unit,
    onFlashClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        // Scrim
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val rectSize = 260.dp.toPx()
            val rectLeft = (canvasWidth - rectSize) / 2
            val rectTop = (canvasHeight - rectSize) / 2

            drawRect(color = Color(0x99000000))
            drawRect(
                color = Color.Transparent,
                topLeft = Offset(rectLeft, rectTop),
                size = Size(rectSize, rectSize),
                blendMode = androidx.compose.ui.graphics.BlendMode.Clear
            )

            // Draw brackets
            val bracketLen = 32.dp.toPx()
            val strokeWidth = 4.dp.toPx()
            val color = Color.White

            // Top Left
            drawLine(color, Offset(rectLeft, rectTop), Offset(rectLeft + bracketLen, rectTop), strokeWidth)
            drawLine(color, Offset(rectLeft, rectTop), Offset(rectLeft, rectTop + bracketLen), strokeWidth)
            // Top Right
            drawLine(color, Offset(rectLeft + rectSize, rectTop), Offset(rectLeft + rectSize - bracketLen, rectTop), strokeWidth)
            drawLine(color, Offset(rectLeft + rectSize, rectTop), Offset(rectLeft + rectSize, rectTop + bracketLen), strokeWidth)
            // Bottom Left
            drawLine(color, Offset(rectLeft, rectTop + rectSize), Offset(rectLeft + bracketLen, rectTop + rectSize), strokeWidth)
            drawLine(color, Offset(rectLeft, rectTop + rectSize), Offset(rectLeft, rectTop + rectSize - bracketLen), strokeWidth)
            // Bottom Right
            drawLine(color, Offset(rectLeft + rectSize, rectTop + rectSize), Offset(rectLeft + rectSize - bracketLen, rectTop + rectSize), strokeWidth)
            drawLine(color, Offset(rectLeft + rectSize, rectTop + rectSize), Offset(rectLeft + rectSize, rectTop + rectSize - bracketLen), strokeWidth)
        }

        // Bouncing Line
        val infiniteTransition = rememberInfiniteTransition()
        val lineOffset by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 260f - 24f, // 260dp square size minus some margin
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        Box(
            modifier = Modifier.align(Alignment.Center)
                .size(260.dp)
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .offset(y = lineOffset.dp)
                    .background(ElectricIndigo)
            )
        }

        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }

        // Scan Error Banner
        if (state.scanError != null) {
            Text(
                text = state.scanError,
                color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp).background(Color(0xCC000000), RoundedCornerShape(8.dp)).padding(16.dp)
            )
        }

        // Processing
        if (state.isProcessing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = ElectricIndigo)
            Text(
                text = stringResource(R.string.qr_processing),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(top = 80.dp)
            )
        }

        // Bottom Actions
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(
                onClick = onFlashClick,
                modifier = Modifier.background(if (state.isFlashOn) ElectricIndigo else Color(0x33FFFFFF), CircleShape).size(56.dp)
            ) {
                Icon(painter = painterResource(id = if (state.isFlashOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off), contentDescription = "Flash", tint = Color.White)
            }
            IconButton(
                onClick = onGalleryClick,
                modifier = Modifier.background(Color(0x33FFFFFF), CircleShape).size(56.dp)
            ) {
                Icon(painter = painterResource(id = R.drawable.ic_gallery), contentDescription = "Gallery", tint = Color.White)
            }
        }
    }
}

@Composable
fun BlackScreenOverlay(
    state: QRScannerState,
    onTerminateClick: () -> Unit,
    onUssdCompletedClick: () -> Unit
) {
    val accent = LocalProximityAccentTheme.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        AnimatedContourBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            if (!state.instructionsExpanded) {
                Text(
                    text = "Follow instructions...",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "1. Copy VPA\n2. Paste in dialer\n3. Complete payment",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        val text = state.blackScreenStatusMessage
            ?: state.blackScreenStatusRes?.let { res ->
                state.blackScreenStatusFormatArg?.let { arg ->
                    stringResource(res, arg)
                } ?: stringResource(res)
            }
            ?: ""

        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp)
        )

        if (state.showTerminateButtons) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onUssdCompletedClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .pressScale(onClick = onUssdCompletedClick),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent.primary)
                ) {
                    Text(
                        text = "I completed it",
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = Color.White
                    )
                }
                OutlinedButton(
                    onClick = onTerminateClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .pressScale(onClick = onTerminateClick),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed)
                ) {
                    Text(
                        text = "Terminate",
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
