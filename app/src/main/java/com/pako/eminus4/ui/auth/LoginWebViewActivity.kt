package com.pako.eminus4.ui.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.pako.eminus4.R
import com.pako.eminus4.core.Constants
import com.pako.eminus4.core.SessionManager
import com.pako.eminus4.ui.main.MainActivity

class LoginWebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var sessionManager: SessionManager
    private val handler = Handler(Looper.getMainLooper())
    private val gson = Gson()

    private var isSessionCaptured = false

    /**
     * Tras logout/401 bloqueamos reutilizar el JWT viejo.
     * Se libera al ver una pantalla de login real O al detectar un token distinto al expirado.
     */
    private var waitingForFreshLogin = false
    private var expiredToken: String? = null
    private var sawLoginPage = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        sessionManager = SessionManager(this)
        progressBar = findViewById(R.id.loginProgressBar)
        webView = findViewById(R.id.loginWebView)

        val isSessionExpired = intent.getBooleanExtra("SESSION_EXPIRED", false)
        expiredToken = intent.getStringExtra("EXPIRED_TOKEN")

        configurarWebView()

        if (isSessionExpired) {
            sessionManager.clearSession()
            limpiarSesionWebCompleta {
                waitingForFreshLogin = true
                Log.i("EMINUS_LOGIN", "Sesión limpiada. Esperando login fresco (token distinto al expirado).")
                webView.loadUrl(Constants.EMINUS_LOGIN_URL)
                iniciarSondeoContinuo()
            }
        } else {
            webView.loadUrl(Constants.EMINUS_LOGIN_URL)
            iniciarSondeoContinuo()
        }
    }

    private fun limpiarSesionWebCompleta(onDone: () -> Unit) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies {
            cookieManager.flush()
            webView.clearCache(true)
            webView.clearHistory()
            webView.clearFormData()
            webView.evaluateJavascript(
                """
                (function() {
                    try { localStorage.clear(); } catch(e) {}
                    try { sessionStorage.clear(); } catch(e) {}
                    return true;
                })();
                """.trimIndent()
            ) {
                // Pequeña espera para que Angular no restaure estado a medias
                handler.postDelayed(onDone, 150)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configurarWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(AuthBridge(), "AndroidAuthBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
                if (esUrlDeLogin(url)) {
                    sawLoginPage = true
                    // Ya pasó por el login institucional: a partir de aquí cualquier token nuevo es válido
                    if (waitingForFreshLogin) {
                        Log.d("EMINUS_LOGIN", "Pantalla de login detectada ($url) — habilitando captura de token nuevo")
                        waitingForFreshLogin = false
                    }
                }
                inyectarInterceptorDeStorage()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                CookieManager.getInstance().flush()
                inyectarInterceptorDeStorage()
                verificarToken()
                // Elimina el espacio en blanco inferior que deja la página de Eminus
                view?.evaluateJavascript(
                    """
                    (function() {
                        var s = document.body.style;
                        s.height = '100%';
                        s.overflow = 'hidden';
                        s.margin = '0';
                        s.padding = '0';
                        var html = document.documentElement;
                        html.style.height = '100%';
                        html.style.overflow = 'hidden';
                    })();
                    """.trimIndent(), null
                )
            }
        }
    }

    private fun esUrlDeLogin(url: String?): Boolean {
        val lower = url?.lowercase() ?: return false
        return lower.contains("default.aspx") ||
            lower.contains("login") ||
            lower.contains("signin") ||
            lower.contains("adfs") ||
            lower.contains("sts.") ||
            lower.contains("identity") ||
            lower.contains("oauth") ||
            lower.contains("sso")
    }

    inner class AuthBridge {
        @JavascriptInterface
        fun onSessionCaptured(jsonStr: String) {
            runOnUiThread {
                procesarJsonDeSesion(jsonStr)
            }
        }

        @JavascriptInterface
        fun onDebug(msg: String) {
            Log.d("EMINUS_LOGIN", "JS: $msg")
        }
    }

    /**
     * Inyecta un interceptor para que en cuanto Angular guarde accessToken en localStorage,
     * se invoque inmediatamente el puente nativo sin esperar al sondeo.
     */
    private fun inyectarInterceptorDeStorage() {
        val hookJs = """
            (function() {
                try {
                    function pickToken() {
                        var keys = ['accessToken', 'AccessToken', 'token', 'jwt', 'id_token', 'authToken'];
                        for (var i = 0; i < keys.length; i++) {
                            var v = localStorage.getItem(keys[i]);
                            if (v && v !== 'null' && v.length > 20) return v;
                        }
                        // Buscar cualquier valor tipo JWT en localStorage
                        for (var j = 0; j < localStorage.length; j++) {
                            var k = localStorage.key(j);
                            var val = localStorage.getItem(k);
                            if (val && typeof val === 'string' && val.indexOf('eyJ') === 0 && val.length > 40) {
                                return val;
                            }
                        }
                        return null;
                    }

                    function reportarSesion() {
                        var token = pickToken();
                        var url = (window.location.href || '').toLowerCase();
                        if (!token) {
                            if (window.AndroidAuthBridge && window.AndroidAuthBridge.onDebug) {
                                window.AndroidAuthBridge.onDebug('sin token | keys=' + Object.keys(localStorage).join(','));
                            }
                            return;
                        }
                        // En páginas de login institucionales aún no reportamos
                        if (url.indexOf('default.aspx') !== -1 || url.indexOf('adfs') !== -1) return;

                        var payload = JSON.stringify({
                            token: token,
                            userName: localStorage.getItem('userName') || localStorage.getItem('UserName'),
                            userId: localStorage.getItem('userId') || localStorage.getItem('userIdentity'),
                            userEmail: localStorage.getItem('userEmail'),
                            userCampus: localStorage.getItem('userCampus'),
                            avatar: localStorage.getItem('avatar'),
                            tokenExpiration: localStorage.getItem('tokenExpiration'),
                            userRoles: localStorage.getItem('userRoles')
                        });
                        if (window.AndroidAuthBridge && window.AndroidAuthBridge.onSessionCaptured) {
                            window.AndroidAuthBridge.onSessionCaptured(payload);
                        }
                    }

                    reportarSesion();

                    if (!window.__authHooked) {
                        window.__authHooked = true;
                        var origSetItem = localStorage.setItem.bind(localStorage);
                        localStorage.setItem = function(k, v) {
                            origSetItem(k, v);
                            if (String(k).toLowerCase().indexOf('token') !== -1 || k === 'userName') {
                                setTimeout(reportarSesion, 150);
                            }
                        };
                    }
                } catch(e) {
                    if (window.AndroidAuthBridge && window.AndroidAuthBridge.onDebug) {
                        window.AndroidAuthBridge.onDebug('hook error: ' + e);
                    }
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(hookJs, null)
    }

    private fun iniciarSondeoContinuo() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!isSessionCaptured && !isFinishing) {
                    verificarToken()
                    handler.postDelayed(this, 1000)
                }
            }
        }, 800)
    }

    private fun verificarToken() {
        if (isSessionCaptured || isFinishing) return

        val script = """
            (function() {
                try {
                    function pickToken() {
                        var keys = ['accessToken', 'AccessToken', 'token', 'jwt', 'id_token', 'authToken'];
                        for (var i = 0; i < keys.length; i++) {
                            var v = localStorage.getItem(keys[i]);
                            if (v && v !== 'null' && v.length > 20) return v;
                        }
                        for (var j = 0; j < localStorage.length; j++) {
                            var k = localStorage.key(j);
                            var val = localStorage.getItem(k);
                            if (val && typeof val === 'string' && val.indexOf('eyJ') === 0 && val.length > 40) {
                                return val;
                            }
                        }
                        return null;
                    }
                    var token = pickToken();
                    var url = (window.location.href || '').toLowerCase();
                    if (token && url.indexOf('default.aspx') === -1 && url.indexOf('adfs') === -1) {
                        return JSON.stringify({
                            token: token,
                            userName: localStorage.getItem('userName') || localStorage.getItem('UserName'),
                            userId: localStorage.getItem('userId') || localStorage.getItem('userIdentity'),
                            userEmail: localStorage.getItem('userEmail'),
                            userCampus: localStorage.getItem('userCampus'),
                            avatar: localStorage.getItem('avatar'),
                            tokenExpiration: localStorage.getItem('tokenExpiration'),
                            userRoles: localStorage.getItem('userRoles'),
                            debugKeys: Object.keys(localStorage)
                        });
                    }
                    return JSON.stringify({
                        token: null,
                        debugKeys: Object.keys(localStorage),
                        url: url
                    });
                } catch(e) {
                    return JSON.stringify({ token: null, error: String(e) });
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->
            if (isSessionCaptured || isFinishing) return@evaluateJavascript
            val json = unwrapEvaluateJavascriptResult(result) ?: return@evaluateJavascript
            try {
                val obj = JsonParser.parseString(json).asJsonObject
                val token = obj.get("token")?.takeIf { !it.isJsonNull }?.asString
                if (token.isNullOrBlank() || token == "null") {
                    val keys = obj.get("debugKeys")?.toString() ?: "[]"
                    Log.d("EMINUS_LOGIN", "Sondeo sin token. keys=$keys url=${obj.get("url")}")
                    return@evaluateJavascript
                }
                procesarJsonDeSesion(json)
            } catch (e: Exception) {
                Log.e("EMINUS_LOGIN", "Error parseando sondeo: $result", e)
            }
        }
    }

    /**
     * evaluateJavascript envuelve el resultado en JSON string (comillas + escapes).
     */
    private fun unwrapEvaluateJavascriptResult(result: String?): String? {
        if (result.isNullOrBlank() || result == "null") return null
        return try {
            // El resultado ya es un string JSON literal: "\"{...}\"" o "{...}"
            val element = JsonParser.parseString(result)
            when {
                element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString
                element.isJsonObject -> element.toString()
                else -> result.trim().trim('"')
            }
        } catch (_: Exception) {
            result.trim().removePrefix("\"").removeSuffix("\"")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
    }

    private fun puedeAceptarToken(token: String): Boolean {
        if (isSessionCaptured) return false

        // Si no estamos esperando login fresco, cualquier token válido sirve
        if (!waitingForFreshLogin) return true

        // Si el token es distinto al que expiró, el usuario ya re-autenticó
        // (aunque el IdP no haya pasado por default.aspx)
        if (!expiredToken.isNullOrBlank() && token != expiredToken) {
            Log.i("EMINUS_LOGIN", "Token nuevo distinto al expirado — aceptando sin exigir default.aspx")
            waitingForFreshLogin = false
            return true
        }

        // Si ya vimos la página de login, también aceptamos
        if (sawLoginPage) {
            waitingForFreshLogin = false
            return true
        }

        Log.d("EMINUS_LOGIN", "Captura bloqueada: esperando login fresco (mismo token viejo o aún no hubo login)")
        return false
    }

    private fun procesarJsonDeSesion(jsonStr: String) {
        if (isSessionCaptured || isFinishing) return
        try {
            val parsed = unwrapEvaluateJavascriptResult(jsonStr) ?: jsonStr
            val json = gson.fromJson(parsed, JsonObject::class.java) ?: return
            val token = json.get("token")?.takeIf { !it.isJsonNull }?.asString

            if (token.isNullOrBlank() || token == "null") return
            if (!puedeAceptarToken(token)) return

            isSessionCaptured = true
            handler.removeCallbacksAndMessages(null)

            val userName = json.get("userName")?.takeIf { !it.isJsonNull }?.asString
            val userId = json.get("userId")?.takeIf { !it.isJsonNull }?.asString
            val userEmail = json.get("userEmail")?.takeIf { !it.isJsonNull }?.asString
            val userCampus = json.get("userCampus")?.takeIf { !it.isJsonNull }?.asString
            val avatar = json.get("avatar")?.takeIf { !it.isJsonNull }?.asString
            val tokenExpiration = json.get("tokenExpiration")?.takeIf { !it.isJsonNull }?.asString
            val userRoles = json.get("userRoles")?.takeIf { !it.isJsonNull }?.asString

            sessionManager.saveSession(
                accessToken = token,
                userName = userName,
                userId = userId,
                userEmail = userEmail,
                userCampus = userCampus,
                avatarUrl = avatar,
                tokenExpiration = tokenExpiration,
                userRoles = userRoles
            )

            Log.i("EMINUS_LOGIN", "¡Sesión detectada y guardada con éxito para $userName!")
            Toast.makeText(this, "¡Sesión iniciada! Cargando diseño nativo…", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e("EMINUS_LOGIN", "Error decodificando payload de sesión: $jsonStr", e)
            // Permitir reintentos si falló el parseo
            isSessionCaptured = false
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
