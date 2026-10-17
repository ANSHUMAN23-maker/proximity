package com.proximity.app.ui.activities
import com.proximity.app.ui.components.*

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Network
import android.os.Bundle
import android.util.Log
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import com.proximity.app.R
import com.proximity.app.ui.components.shimmer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.proximity.app.ui.theme.*

class RechargeWebActivity : ComponentActivity() {

    companion object {
        private const val TAG = "RechargeWebActivity"
        const val EXTRA_PORTAL_URL = "portal_url"
        const val EXTRA_OPERATOR   = "operator_name"
        const val EXTRA_NETWORK    = "cellular_network"

        fun buildIntent(
            context: Context,
            portalUrl: String,
            operatorName: String,
            network: Network? = null
        ): Intent = Intent(context, RechargeWebActivity::class.java).apply {
            putExtra(EXTRA_PORTAL_URL, portalUrl)
            putExtra(EXTRA_OPERATOR, operatorName)
            putExtra(EXTRA_NETWORK, network)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val portalUrl    = intent.getStringExtra(EXTRA_PORTAL_URL) ?: run { finish(); return }
        val operatorName = intent.getStringExtra(EXTRA_OPERATOR) ?: "Operator"
        var network = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_NETWORK, Network::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_NETWORK)
        }

        val detector = com.proximity.app.ProximityApplication.from(this)?.container?.walledGardenDetector
        if (detector != null) {
            detector.findCellularNetwork()?.let { freshNet ->
                network = freshNet
            }
        }

        // NOTE: bindProcessToNetwork() binds the entire process, which would
        // break Wi-Fi for other components (background sync, other WebViews)
        // on dual-network devices. WebView does not expose a per-instance
        // network binding API, so we use default routing here. The operator
        // portal URL is loaded over whatever network the system selects.
        if (network == null) {
            Log.w(TAG, "No cellular network available — WebView will use default routing")
        }


        val displayDomain = runCatching {
            android.net.Uri.parse(portalUrl).host ?: operatorName
        }.getOrDefault(operatorName)

        val app = com.proximity.app.ProximityApplication.from(this)
        val settingsRepository = app?.settingsRepository ?: com.proximity.app.data.SettingsRepository(applicationContext)

        setContent {
            val savedSettings by settingsRepository.settingsFlow.collectAsState()
            CompositionLocalProvider(LocalProximityAccentTheme provides TealAccentTheme) {
                ProximityTheme(darkTheme = savedSettings.darkModeEnabled) {
                    RechargeWebScreen(
                        portalUrl    = portalUrl,
                        displayTitle = getString(R.string.recharge_toolbar_title, operatorName.replaceFirstChar { it.uppercaseChar() }),
                        displayUrl   = displayDomain,
                        isDarkTheme  = savedSettings.darkModeEnabled,
                        onBack       = { finishAndRelease() }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun finishAndRelease() {
        finish()
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RechargeWebScreen(
    portalUrl: String,
    displayTitle: String,
    displayUrl: String,
    isDarkTheme: Boolean,
    onBack: () -> Unit
) {
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var isLoading      by remember { mutableStateOf(true) }
    var webViewRef     by remember { mutableStateOf<WebView?>(null) }
    var isSkeletonVisible by remember { mutableStateOf(true) }
    var isError        by remember { mutableStateOf(false) }
    var isSuccess      by remember { mutableStateOf(false) }
    val context        = LocalContext.current
    val accent         = LocalProximityAccentTheme.current

    val handleBack: () -> Unit = {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onBack()
        }
    }

    androidx.activity.compose.BackHandler(onBack = handleBack)

    LaunchedEffect(isSuccess) {
        if (isSuccess) {
            kotlinx.coroutines.delay(3000)
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text     = displayTitle,
                            style    = MaterialTheme.typography.titleMedium,
                            color    = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text     = displayUrl,
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint               = MaterialTheme.colorScheme.onSurface
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
            // The WebView
            RechargeWebView(
                portalUrl       = portalUrl,
                isDarkTheme     = isDarkTheme,
                onProgressChanged = { progress ->
                    loadingProgress = progress / 100f
                    isLoading = progress < 100
                    if (progress == 100) {
                        isSkeletonVisible = false
                    }
                },
                onWebViewCreated = { webViewRef = it },
                onPageFinished = { },
                onError = {
                    isError = true
                    isSkeletonVisible = false
                },
                onSuccess = {
                    isSuccess = true
                }
            )

            // Shimmer Skeleton Layer
            androidx.compose.animation.AnimatedVisibility(
                visible = isSkeletonVisible,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Box(modifier = Modifier.fillMaxWidth(0.6f).height(24.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                    }
                }
            }

            // Error Overlay Layer
            androidx.compose.animation.AnimatedVisibility(
                visible = isError && !isSuccess,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.WifiOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.recharge_web_error_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.recharge_web_error_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {

                            androidx.compose.material3.Button(
                                onClick = {
                                    isError = false
                                    isSkeletonVisible = true
                                    webViewRef?.reload()
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = accent.primary
                                )
                            ) {
                                Text(androidx.compose.ui.res.stringResource(R.string.recharge_action_retry))
                            }
                        }
                    }
                }
            }

            // Success Overlay Layer
            androidx.compose.animation.AnimatedVisibility(
                visible = isSuccess,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SmoothCheckmarkAnimation(modifier = Modifier.size(120.dp))
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.recharge_success_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.recharge_success_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        androidx.compose.material3.Button(
                            onClick = onBack,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = accent.primary
                            )
                        ) {
                            Text(androidx.compose.ui.res.stringResource(R.string.action_done))
                        }
                    }
                }
            }

            // Thin loading bar at the top of the WebView
            if (isLoading && !isSkeletonVisible && !isError) {
                LinearProgressIndicator(
                    progress          = { loadingProgress },
                    modifier          = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.TopCenter),
                    color             = accent.primary,
                    trackColor        = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            }
        }
    }
}

// ─── WebView Component ────────────────────────────────────────────────────────

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun RechargeWebView(
    portalUrl: String,
    isDarkTheme: Boolean,
    onProgressChanged: (Int) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    onPageFinished: () -> Unit,
    onError: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory  = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled      = true   // Required for payment SDKs on portal
                    domStorageEnabled      = true   // Required for OTP autofill inputs
                    loadWithOverviewMode   = true
                    useWideViewPort        = true
                    setSupportZoom(false)
                    builtInZoomControls    = false
                    displayZoomControls    = false

                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        isAlgorithmicDarkeningAllowed = isDarkTheme
                    } else if (android.os.Build.VERSION.SDK_INT >= 29) {
                        @Suppress("DEPRECATION")
                        forceDark = if (isDarkTheme) {
                            android.webkit.WebSettings.FORCE_DARK_ON
                        } else {
                            android.webkit.WebSettings.FORCE_DARK_OFF
                        }
                    }

                    // Security hardening
                    allowFileAccess = false
                    allowContentAccess = false
                    allowFileAccessFromFileURLs = false
                    allowUniversalAccessFromFileURLs = false
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                    if (android.os.Build.VERSION.SDK_INT >= 26) {
                        safeBrowsingEnabled = true
                    }
                }

                webViewClient = ProximityWebViewClient(
                    context = context,
                    onPageFinishedCallback = onPageFinished,
                    onErrorCallback = onError,
                    onSuccessCallback = onSuccess
                )
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgressChanged(newProgress)
                    }
                }

                loadUrl(portalUrl)
                onWebViewCreated(this)
            }
        },
        update = { webView ->
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                webView.settings.isAlgorithmicDarkeningAllowed = isDarkTheme
            } else if (android.os.Build.VERSION.SDK_INT >= 29) {
                @Suppress("DEPRECATION")
                webView.settings.forceDark = if (isDarkTheme) {
                    android.webkit.WebSettings.FORCE_DARK_ON
                } else {
                    android.webkit.WebSettings.FORCE_DARK_OFF
                }
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }
    )
}

private class ProximityWebViewClient(
    private val context: Context,
    private val onPageFinishedCallback: () -> Unit,
    private val onErrorCallback: () -> Unit,
    private val onSuccessCallback: () -> Unit
) : WebViewClient() {

    companion object {
        private const val TAG = "ProximityWebViewClient"
        private val OPERATOR_DOMAINS = setOf(
            "jio.com",
            "airtel.in",
            "myvi.in",
            "bsnl.co.in",
            "bsnl.in",
            "mtnl.net.in"
        )
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        onPageFinishedCallback()
        val successKeywords = setOf("payment-success", "transaction-status=success", "receipt", "thank-you-for-recharge", "txn-success", "payment_successful", "payment-confirmation")
        if (successKeywords.any { url.contains(it, ignoreCase = true) }) {
            onSuccessCallback()
        }
    }

    override fun onReceivedError(
        view: WebView,
        request: android.webkit.WebResourceRequest,
        error: android.webkit.WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame) {
            onErrorCallback()
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: android.webkit.WebResourceRequest,
        errorResponse: android.webkit.WebResourceResponse
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request.isForMainFrame && errorResponse.statusCode >= 400) {
            onErrorCallback()
        }
    }

    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
        handleUrl(android.net.Uri.parse(url), view)

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest
    ): Boolean = handleUrl(request.url, view)

    private fun handleUrl(uri: android.net.Uri, view: WebView): Boolean {
        return when (uri.scheme?.lowercase()) {
            "upi" -> {
                Log.d(TAG, "Intercepted UPI intent: $uri")
                launchNativeIntent(Intent(Intent.ACTION_VIEW, uri), view)
                true
            }
            "intent" -> {
                Log.d(TAG, "Intercepted intent:// scheme: $uri")
                runCatching {
                    val intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                    launchNativeIntent(intent, view)
                }.onFailure { Log.w(TAG, "Failed to parse intent URI: $it") }
                true
            }
            else -> false
        }
    }

    private fun launchNativeIntent(intent: Intent, view: WebView? = null) {
        runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure {
            Log.w(TAG, "No app to handle intent: ${intent.action} — ${it.message}")
            val fallback = intent.getStringExtra("browser_fallback_url")
            if (fallback != null && view != null) {
                Log.d(TAG, "Loading fallback URL: $fallback")
                view.loadUrl(fallback)
            }
        }
    }

    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: android.net.http.SslError
    ) {
        val host = error.url?.let { android.net.Uri.parse(it).host } ?: ""
        val isKnownOperator = OPERATOR_DOMAINS.any { host == it || host.endsWith(".$it") }

        if (isKnownOperator) {
            Log.w(TAG, "SSL error on known operator domain: $host (primaryError=${error.primaryError}). Proceeding.")
            android.widget.Toast.makeText(context, "Portal certificate issue, continuing...", android.widget.Toast.LENGTH_SHORT).show()
            handler.proceed()
        } else {
            Log.w(TAG, "Cancelling SSL error on unknown host: $host")
            handler.cancel()
        }
    }
}



