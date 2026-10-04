package app.limits.auth

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import app.limits.ServiceLocator
import app.limits.domain.ProviderId
import app.limits.providers.ClaudeProvider
import app.limits.ui.LimitsTheme
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.launch

class ClaudeAuthActivity : ComponentActivity() {
    private val verifier = randomUrlSafe(48)
    private val state = randomUrlSafe(24)
    private val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.init(this)
        enableEdgeToEdge()
        setContent {
            LimitsTheme {
                var busy by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf<String?>(null) }
                Box(Modifier.fillMaxSize()) {
                    ClaudeWebView(
                        onCallback = { code, returnedState ->
                            if (busy) return@ClaudeWebView
                            if (returnedState != state) {
                                error = "OAuth state did not match"
                                return@ClaudeWebView
                            }
                            busy = true
                            lifecycleScope.launch {
                                runCatching {
                                    ServiceLocator.repository.claude.exchangeAuthorizationCode(
                                        code = code,
                                        verifier = verifier,
                                        redirectUri = ClaudeProvider.REDIRECT_URI,
                                        state = state,
                                    )
                                    ServiceLocator.repository.refresh(ProviderId.CLAUDE)
                                    ServiceLocator.repository.refreshConnections()
                                }.onSuccess { finish() }
                                    .onFailure { error = it.message ?: "Claude sign-in failed"; busy = false }
                            }
                        },
                    )
                    if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.BottomCenter)) }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @androidx.compose.runtime.Composable
    private fun ClaudeWebView(onCallback: (String, String?) -> Unit) {
        val authUrl = "https://claude.ai/oauth/authorize?" +
            "code=true" +
            "&client_id=${ClaudeProvider.CLIENT_ID}" +
            "&response_type=code" +
            "&redirect_uri=${URLEncoder.encode(ClaudeProvider.REDIRECT_URI, "UTF-8")}" +
            "&scope=${URLEncoder.encode("org:create_api_key user:profile user:inference", "UTF-8")}" +
            "&state=$state" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256"

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                CookieManager.getInstance().setAcceptCookie(true)
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            return intercept(request.url, onCallback)
                        }
                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                            intercept(Uri.parse(url), onCallback)
                    }
                    loadUrl(authUrl)
                }
            },
        )
    }

    private fun intercept(uri: Uri, onCallback: (String, String?) -> Unit): Boolean {
        if (!uri.toString().startsWith(ClaudeProvider.REDIRECT_URI)) return false
        val encodedCode = uri.getQueryParameter("code") ?: return true
        val code = encodedCode.substringBefore('#')
        val returnedState = uri.getQueryParameter("state")
            ?: uri.fragment?.takeIf { it.isNotBlank() }
            ?: encodedCode.substringAfter('#', "").takeIf { it.isNotBlank() }
        onCallback(code, returnedState)
        return true
    }

    companion object {
        private fun randomUrlSafe(bytes: Int): String {
            val value = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value)
        }
    }
}
