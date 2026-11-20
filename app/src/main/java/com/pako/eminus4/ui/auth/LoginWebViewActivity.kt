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
import com.pako.eminus4.data.MiuvSync
import com.pako.eminus4.ui.main.MainActivity

class LoginWebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var sessionManager: SessionManager
    private val handler = Handler(Looper.getMainLooper())
    private val gson = Gson()

    private var isSessionCaptured = false

    /**
     * Usuario y contraseña escritos en el login, SOLO en memoria, para entrar una vez a MiUV
     * (que tiene su propio login) y leer el programa educativo. Se borran al terminar.
     * Nunca se guardan en disco ni se escriben en el log.
     */
    private var credUsuario: String? = null
    private var credClave: CharArray? = null

    private fun descartarCredenciales() {
        credClave?.fill('\u0000')
        credClave = null
        credUsuario = null
    }

    /**
     * Tras logout/401 bloqueamos reutilizar el JWT viejo.
     * Se libera al ver una pantalla de login real O al detectar un token distinto al expirado.
     */
    private var waitingForFreshLogin = false
    private var expiredToken: String? = null
    private var sawLoginPage = false

    /**
     * Si el token "caducado" reaparece sin pasar por el login, se pregunta al servidor si sigue
     * siendo válido (falsas alarmas de 401). Mientras se valida, no se repite la consulta.
     */
    private var validandoTokenViejo = false

    /**
     * Tras limpiar el almacenamiento web se vio localStorage SIN token: cualquier token que
     * aparezca después tuvo que salir de un login nuevo (el login de Eminus vive dentro de
     * eminus4/ sin cambiar de URL, así que no siempre se detecta como "pantalla de login").
     */
    private var vioStorageSinToken = false
    private var ultimaValidacionMs = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        sessionManager = SessionManager(this)
        progressBar = findViewById(R.id.loginProgressBar)
        webView = findViewById(R.id.loginWebView)

        val isSessionExpired = intent.getBooleanExtra("SESSION_EXPIRED", false)
        val isRenewalOnly = intent.getBooleanExtra("RENEWAL_ONLY", false)
        val isLogout = intent.getBooleanExtra("LOGOUT_ACTION", false)
        expiredToken = intent.getStringExtra("EXPIRED_TOKEN")

        configurarWebView()

        if (isSessionExpired || isLogout) {
            if (!isRenewalOnly) {
                sessionManager.clearSession(preserveCredential = true)
            }
            limpiarSesionWebCompleta {
                waitingForFreshLogin = true
                sawLoginPage = false
                Log.i("EMINUS_LOGIN", "Sesión web limpiada (logout=$isLogout, renewal=$isRenewalOnly). Esperando login fresco.")
                webView.loadUrl(Constants.EMINUS_LOGIN_URL)
                iniciarSondeoContinuo()
            }
        } else {
            waitingForFreshLogin = false
            webView.loadUrl(Constants.EMINUS_LOGIN_URL)
            iniciarSondeoContinuo()
        }
    }

    private fun limpiarSesionWebCompleta(onDone: () -> Unit) {
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies {
                cookieManager.flush()
            }
            android.webkit.WebStorage.getInstance().deleteAllData()
            webView.clearCache(true)
            webView.clearHistory()
            webView.clearFormData()
        } catch (e: Exception) {
            Log.e("EMINUS_LOGIN", "Error limpiando almacenamiento web", e)
        }
        handler.postDelayed(onDone, 200)
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
                inyectarCapturaDeCredenciales()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                CookieManager.getInstance().flush()
                inyectarInterceptorDeStorage()
                inyectarCapturaDeCredenciales()
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

        /** Recibe usuario/contraseña al enviar el formulario de login (no se loguean). */
        @JavascriptInterface
        fun onCredenciales(usuario: String, clave: String) {
            if (usuario.isBlank() || clave.isEmpty()) return
            runOnUiThread {
                if (isSessionCaptured) return@runOnUiThread
                descartarCredenciales()
                credUsuario = usuario.trim()
                credClave = clave.toCharArray()
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
                        // Limpia la clave basura que dejaba la versión anterior del hook
                        try { if (localStorage.getItem('setItem') !== null) localStorage.removeItem('setItem'); } catch (e) {}
                        // Se envuelve Storage.prototype: asignar localStorage.setItem = f
                        // crea una CLAVE llamada "setItem" en vez de reemplazar el método
                        var origSetItem = Storage.prototype.setItem;
                        Storage.prototype.setItem = function(k, v) {
                            origSetItem.apply(this, arguments);
                            if (this === window.localStorage &&
                                (String(k).toLowerCase().indexOf('token') !== -1 || k === 'userName')) {
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

    /**
     * Escucha el envío del formulario de login (submit, clic en botón o Enter) y pasa
     * usuario/contraseña a la app en memoria, para reutilizarlos UNA vez en MiUV.
     */
    private fun inyectarCapturaDeCredenciales() {
        if (isSessionCaptured) return
        val js = """
            (function() {
              if (window.__credHooked) return;
              window.__credHooked = true;
              function enviar() {
                try {
                  var p = document.querySelector('input[type=password]');
                  if (!p || !p.value) return;
                  var f = p.form || document;
                  var c = Array.prototype.slice.call(
                    f.querySelectorAll('input[type=text], input[type=email], input:not([type])'))
                    .filter(function(e) { return e.value; });
                  if (!c.length) return;
                  window.AndroidAuthBridge.onCredenciales(c[0].value, p.value);
                } catch (e) {}
              }
              document.addEventListener('submit', enviar, true);
              document.addEventListener('keydown', function(e) { if (e.key === 'Enter') enviar(); }, true);
              document.addEventListener('change', function(e) {
                if (e.target && e.target.type === 'password') enviar();
              }, true);
              document.addEventListener('click', function(e) {
                var t = e.target;
                if (t && (t.closest && t.closest('button, input[type=submit], input[type=image], input[type=button]'))) enviar();
              }, true);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
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
                    vioStorageSinToken = true
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

        // El almacenamiento se vio vacío tras limpiarlo: este token salió de un login nuevo
        if (vioStorageSinToken) {
            Log.i("EMINUS_LOGIN", "Token nuevo tras limpiar el almacenamiento — aceptando")
            waitingForFreshLogin = false
            return true
        }

        // Si ya vimos la página de login, también aceptamos
        if (sawLoginPage) {
            waitingForFreshLogin = false
            return true
        }

        // El mismo token "caducado" reapareció sin pasar por el login: puede que nunca haya
        // caducado (p. ej. un 401 transitorio). Se valida contra el servidor en segundo plano.
        if (!expiredToken.isNullOrBlank() && token == expiredToken) {
            validarTokenEnServidor(token)
        }
        Log.d("EMINUS_LOGIN", "Captura bloqueada: esperando login fresco (mismo token viejo o aún no hubo login)")
        return false
    }

    /**
     * Eminus a veces aún no ha escrito nombre/usuario en localStorage cuando aparece el token.
     * Se completan desde la API para que Perfil, saludo y credencial no queden vacíos.
     */
    private fun completarPerfilSiFalta(userName: String?, userId: String?) {
        if (!userName.isNullOrBlank() && !userId.isNullOrBlank()) return
        val sm = SessionManager(applicationContext)
        com.pako.eminus4.data.api.EminusApiClient(sm).getUserProfile { r ->
            r.onSuccess { p ->
                sm.saveSession(
                    accessToken = sm.getAccessToken() ?: return@onSuccess,
                    userName = p.getNombreCompleto(),
                    firstName = p.nombre,
                    userId = p.userId,
                    userEmail = p.userEmail,
                    userCampus = p.userCampus,
                    avatarUrl = p.getFullAvatarUrl()
                )
                Log.i("EMINUS_LOGIN", "Perfil completado desde la API")
            }.onFailure { Log.w("EMINUS_LOGIN", "No se pudo completar el perfil: ${it.message}") }
        }
    }

    /** GET al perfil con el token; si responde 200, el token es válido y se acepta. */
    private fun validarTokenEnServidor(token: String) {
        val ahora = System.currentTimeMillis()
        if (validandoTokenViejo || ahora - ultimaValidacionMs < 15_000) return
        validandoTokenViejo = true
        ultimaValidacionMs = ahora
        Thread {
            val valido = try {
                val cliente = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build()
                val req = okhttp3.Request.Builder()
                    .url("${Constants.API_GLOBAL_URL}${Constants.ENDPOINT_GET_USER}")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .build()
                cliente.newCall(req).execute().use { it.code == 200 }
            } catch (_: Exception) {
                false
            }
            runOnUiThread {
                validandoTokenViejo = false
                if (valido && !isSessionCaptured && !isFinishing) {
                    Log.i("EMINUS_LOGIN", "El token marcado como caducado sigue siendo válido: se acepta")
                    waitingForFreshLogin = false
                    verificarToken()
                } else {
                    Log.d("EMINUS_LOGIN", "El token viejo no es válido: se espera un login nuevo")
                }
            }
        }.start()
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

            // Si entra otra persona, su credencial no debe mostrar los datos de la anterior
            sessionManager.descartarCredencialAjena(userId)
            com.pako.eminus4.data.AgendaLoader.limpiar()

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
            sessionManager.setSessionExpired(false)
            completarPerfilSiFalta(userName, userId)

            Log.i("EMINUS_LOGIN", "¡Sesión detectada y guardada con éxito para $userName!")

            val usuario = credUsuario
            val clave = credClave
            credUsuario = null
            credClave = null   // la propiedad de la clave pasa a MiuvSync, que la borra al terminar
            if (usuario != null && clave != null) {
                sincronizarConMiuv(usuario, clave, userName)
            } else {
                Log.i("EMINUS_LOGIN", "Sin usuario/contraseña capturados (sesión recordada): se omite MiUV")
                entrarALaApp()
            }
        } catch (e: Exception) {
            Log.e("EMINUS_LOGIN", "Error decodificando payload de sesión: $jsonStr", e)
            // Permitir reintentos si falló el parseo
            isSessionCaptured = false
        }
    }

    /** Entra una vez a MiUV con los mismos datos, lee el programa educativo y sigue. */
    private fun sincronizarConMiuv(usuario: String, clave: CharArray, userName: String?) {
        // El usuario del login es la matrícula: sirve de respaldo si Eminus aún no reportó userId
        val matriculaLogin = usuario
        sessionManager.descartarCredencialAjena(matriculaLogin)
        findViewById<View>(R.id.layoutSyncMiuv).visibility = View.VISIBLE
        MiuvSync(webView, java.io.File(filesDir, "credencial"), usuario, clave).iniciar { datos ->
            if (isFinishing || isDestroyed) return@iniciar
            if (datos != null) {
                sessionManager.aplicarDatosMiuv(datos, userName, matriculaLogin)
                Toast.makeText(this, "Credencial actualizada: ${datos.programa}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No se pudo leer MiUV; puedes editar tu programa en Perfil.", Toast.LENGTH_LONG).show()
            }
            entrarALaApp()
        }
    }

    private fun entrarALaApp() {
        if (intent.getBooleanExtra("RENEWAL_ONLY", false)) {
            finish()
        } else {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        descartarCredenciales()
        super.onDestroy()
    }
}
