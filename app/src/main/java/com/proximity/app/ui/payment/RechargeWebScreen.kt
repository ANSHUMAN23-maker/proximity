// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.ui.payment

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.provider.Settings
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.proximity.app.AppCore
import com.proximity.app.R
import com.proximity.app.data.OperatorCatalog
import com.proximity.app.theme.AppTheme
import com.proximity.app.theme.ColorTokens
import com.proximity.app.theme.LocalAppVisuals
import com.proximity.app.ui.components.AnimatedBackground
import com.proximity.app.ui.components.CheckmarkConfettiBurst
import com.proximity.app.ui.components.pressScale
import kotlinx.coroutines.delay

class RechargeWebScreen : ComponentActivity() {

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var activeWebView: WebView? = null
    private var savedWebState: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        savedWebState = savedInstanceState?.getBundle(KEY_WEBVIEW_STATE)
        val app = application as AppCore

        setContent {
            val settings by app.services.preferencesStore.settingsFlow.collectAsState()
            val operator = OperatorCatalog.findById(settings.primarySimCarrierId) ?: OperatorCatalog.JIO

            AppTheme(settings = settings) {
                CarrierRechargePortalView(
                    carrierTitle = operator.title,
                    portalDomain = operator.portalDomain,
                    portalUrl = operator.portalUrl,
                    savedWebState = savedWebState,
                    onWebViewBound = { wv -> activeWebView = wv },
                    onBindCellular = { bindToActiveCellularNetwork() },
                    onReleaseCellular = { releaseCellularBinding() },
                    onClose = {
                        releaseCellularBinding()
                        finish()
                    }
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        activeWebView?.let { wv ->
            val webBundle = Bundle()
            wv.saveState(webBundle)
            outState.putBundle(KEY_WEBVIEW_STATE, webBundle)
        }
    }

    @Synchronized
    private fun bindToActiveCellularNetwork() {
        if (networkCallback != null) return
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            connectivityManager = cm
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()

            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    try {
                        cm.bindProcessToNetwork(network)
                    } catch (_: Exception) {
                    }
                }

                override fun onLost(network: Network) {
                    try {
                        cm.bindProcessToNetwork(null)
                    } catch (_: Exception) {
                    }
                }
            }
            networkCallback = cb
            cm.requestNetwork(request, cb)
        } catch (_: Exception) {
        }
    }

    @Synchronized
    private fun releaseCellularBinding() {
        try {
            connectivityManager?.bindProcessToNetwork(null)
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (_: Exception) {
        }
        networkCallback = null
    }

    override fun onDestroy() {
        releaseCellularBinding()
        activeWebView = null
        super.onDestroy()
    }

    companion object {
        private const val KEY_WEBVIEW_STATE = "webview_state"
    }
}

private val SUCCESS_PATH_SEGMENTS = listOf(
    "payment-success",
    "receipt",
    "thank-you-for-recharge",
    "txn-success",
    "payment_successful",
    "payment-confirmation"
)

private fun isRechargeSuccessUri(uri: Uri?): Boolean {
    if (uri == null) return false
    val path = uri.path?.lowercase().orEmpty()
    if (SUCCESS_PATH_SEGMENTS.any { path.contains(it) }) return true
    val txnStatus = uri.getQueryParameter("transaction-status")?.lowercase()
    return txnStatus == "success"
}

private fun sanitizeExternalIntent(context: Context, intentUri: String): Intent? {
    val parsedIntent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
    if (parsedIntent.`package` == context.packageName) return null
    val action = parsedIntent.action
    if (action != null && action != Intent.ACTION_VIEW) return null
    parsedIntent.component = null
    parsedIntent.selector = null
    parsedIntent.addCategory(Intent.CATEGORY_BROWSABLE)
    return parsedIntent
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CarrierRechargePortalView(
    carrierTitle: String,
    portalDomain: String,
    portalUrl: String,
    savedWebState: Bundle? = null,
    onWebViewBound: (WebView?) -> Unit = {},
    onBindCellular: () -> Unit = {},
    onReleaseCellular: () -> Unit = {},
    onClose: () -> Unit
) {
    val visuals = LocalAppVisuals.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isInitialShimmerLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var hasLoadError by remember { mutableStateOf(false) }
    var isRechargeSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(isRechargeSuccess) {
        if (isRechargeSuccess) {
            delay(3000L)
            onClose()
        }
    }

    BackHandler {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onClose()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> onBindCellular()
                Lifecycle.Event.ON_RESUME -> webViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> webViewRef?.onPause()
                Lifecycle.Event.ON_STOP -> onReleaseCellular()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onReleaseCellular()
            onWebViewBound(null)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(visuals.mainBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val wv = webViewRef
                    if (wv != null && wv.canGoBack()) {
                        wv.goBack()
                    } else {
                        onClose()
                    }
                }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_back_arrow),
                    contentDescription = stringResource(id = R.string.cd_back),
                    tint = visuals.textPrimary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.recharge_toolbar_title, carrierTitle),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = visuals.textPrimary
                )
                Text(
                    text = portalDomain,
                    fontSize = 12.sp,
                    color = ColorTokens.BrandTeal
                )
            }
        }

        if (loadProgress in 1..99) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = ColorTokens.BrandTeal,
                trackColor = visuals.outline.copy(alpha = 0.2f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewRef = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false

                        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, visuals.isDark)
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                                if (newProgress >= 100) {
                                    isInitialShimmerLoading = false
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val targetUri = request?.url ?: return false
                                if (isRechargeSuccessUri(targetUri)) {
                                    isRechargeSuccess = true
                                }

                                val scheme = targetUri.scheme?.lowercase().orEmpty()
                                if (scheme == "upi") {
                                    try {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, targetUri))
                                    } catch (_: Exception) {
                                    }
                                    return true
                                }

                                if (scheme == "intent") {
                                    try {
                                        val safeIntent = sanitizeExternalIntent(ctx, targetUri.toString())
                                        if (safeIntent != null && safeIntent.resolveActivity(ctx.packageManager) != null) {
                                            ctx.startActivity(safeIntent)
                                        } else {
                                            val fallbackUrl = safeIntent?.getStringExtra("browser_fallback_url")
                                            val fallbackUri = fallbackUrl?.let { Uri.parse(it) }
                                            if (fallbackUri != null && fallbackUri.scheme.equals("https", ignoreCase = true)) {
                                                view?.loadUrl(fallbackUri.toString())
                                            }
                                        }
                                    } catch (_: Exception) {
                                    }
                                    return true
                                }

                                // Block non-HTTPS schemes (http, file, content, javascript) from loading in the WebView.
                                return scheme != "https"
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isInitialShimmerLoading = false
                                val parsed = url?.let { runCatching { Uri.parse(it) }.getOrNull() }
                                if (isRechargeSuccessUri(parsed)) {
                                    isRechargeSuccess = true
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    hasLoadError = true
                                    isInitialShimmerLoading = false
                                }
                            }

                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?
                            ) {
                                handler?.cancel()
                                hasLoadError = true
                                isInitialShimmerLoading = false
                            }
                        }

                        onWebViewBound(this)
                        if (savedWebState != null) {
                            restoreState(savedWebState)
                        } else {
                            loadUrl(portalUrl)
                        }
                    }
                },
                update = { wv ->
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                        WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, visuals.isDark)
                    }
                },
                onRelease = { wv ->
                    wv.stopLoading()
                    wv.destroy()
                    if (webViewRef === wv) {
                        webViewRef = null
                    }
                }
            )

            if (isInitialShimmerLoading && !hasLoadError) {
                RechargeSkeletonView(modifier = Modifier.fillMaxSize())
            }

            if (hasLoadError) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(visuals.mainBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(28.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_error),
                            contentDescription = null,
                            tint = ColorTokens.StatusErrorRed,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(id = R.string.recharge_web_error_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = visuals.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(id = R.string.recharge_web_error_body),
                            fontSize = 14.sp,
                            color = visuals.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                hasLoadError = false
                                isInitialShimmerLoading = true
                                webViewRef?.reload()
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.BrandTeal)
                        ) {
                            Text(
                                text = stringResource(id = R.string.recharge_action_retry),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            if (isRechargeSuccess) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedBackground(modifier = Modifier.fillMaxSize())
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(28.dp)
                    ) {
                        CheckmarkConfettiBurst(sizeDp = 160.dp)
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = stringResource(id = R.string.recharge_success_title),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = visuals.textPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(id = R.string.recharge_success_body),
                            fontSize = 14.sp,
                            color = visuals.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onClose,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.BrandTeal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .pressScale()
                        ) {
                            Text(
                                text = stringResource(id = R.string.action_done),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RechargeSkeletonView(modifier: Modifier = Modifier) {
    val visuals = LocalAppVisuals.current
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val shimmerState = if (reducedMotion) {
        remember { mutableStateOf(0.5f) }
    } else {
        val infinite = rememberInfiniteTransition(label = "RechargeShimmer")
        infinite.animateFloat(
            initialValue = -0.35f,
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ShimmerFraction"
        )
    }

    val cardBase = visuals.cardSurface
    val highlight = ColorTokens.BrandTeal.copy(alpha = 0.22f)
    val shimmerMod = Modifier.drawBehind {
        drawRect(color = cardBase)
        val frac = shimmerState.value
        val startX = size.width * frac
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(cardBase, highlight, cardBase),
                start = Offset(startX, 0f),
                end = Offset(startX + 320f, 320f)
            )
        )
    }

    Column(
        modifier = modifier
            .background(visuals.mainBackground)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(20.dp))
                .then(shimmerMod)
        )
        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .then(shimmerMod)
            )
        }
    }
}
