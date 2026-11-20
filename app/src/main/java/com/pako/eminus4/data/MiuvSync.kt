package com.pako.eminus4.data

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Lee los datos académicos oficiales del alumno desde MiUV (portal ASP.NET con login propio,
 * distinto de Eminus) usando el MISMO usuario y contraseña que el alumno escribió en el login.
 *
 * SEGURIDAD:
 *  - La contraseña solo vive en memoria (CharArray) durante esta sincronización.
 *  - Se sobrescribe con ceros al terminar (éxito, error o tiempo agotado). Nunca se guarda ni se loguea.
 *  - El usuario/contraseña se inyectan en el formulario de MiUV como lo haría la persona a mano.
 *
 * Flujo: login.aspx (SIN ReturnUrl: con él MiUV redirige a uv.mx) → llenar y enviar →
 * escritorio → enlace al portal de estudiantes → szigral.aspx → extraer datos.
 */
class MiuvSync(
    private val webView: WebView,
    private val dirFotos: File,
    usuario: String,
    clave: CharArray
) {
    /** Matrícula (usuario del login) para nombrar la foto: nunca se comparte archivo entre personas. */
    private val idFoto: String = usuario.trim().uppercase().replace(Regex("[^A-Z0-9]"), "_")

    data class DatosMiuv(
        val programa: String?,
        val campus: String?,
        val facultad: String?,
        val area: String?,
        val nivel: String?,
        /** Ruta local (file://) de la fotografía oficial, si se pudo descargar. */
        val fotoLocal: String?
    )

    companion object {
        const val TAG = "MIUV_SYNC"
        const val URL_LOGIN = "https://dsia.uv.mx/miuv/escritorio/login.aspx"
        const val URL_DATOS = "https://dsiapes.uv.mx/MIUV/portales/estudiantes/szigral.aspx"
        private const val TIMEOUT_MS = 45_000L
    }

    private var usuario: String? = usuario
    private var clave: CharArray? = clave
    private val handler = Handler(Looper.getMainLooper())
    private var terminado = false
    private var intentosLogin = 0
    private var irADatosTrasLogin = false
    private var yaPediDatos = false
    private lateinit var alTerminar: (DatosMiuv?) -> Unit

    /** Borra la contraseña (y el usuario) de memoria. Idempotente. */
    private fun descartarCredenciales() {
        clave?.fill('\u0000')
        clave = null
        usuario = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun iniciar(alTerminar: (DatosMiuv?) -> Unit) {
        this.alTerminar = alTerminar
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                Log.d(TAG, "Cargando ${url?.substringBefore('?')}")
            }

            override fun onReceivedError(
                view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    Log.w(TAG, "Error de red ${error?.errorCode} en ${request.url?.toString()?.substringBefore('?')}")
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (terminado) return
                CookieManager.getInstance().flush()
                val u = url?.lowercase() ?: return
                when {
                    u.contains("login.aspx") -> llenarLogin()
                    u.contains("szigral.aspx") -> extraerDatos()
                    // MiUV manda a la portada de la UV cuando rechaza la navegación
                    !u.contains("dsia") -> terminar(null, "MiUV redirigió fuera del portal (${u.substringBefore('?')})")
                    irADatosTrasLogin -> {
                        irADatosTrasLogin = false
                        irAlPortalDeEstudiantes()
                    }
                    !yaPediDatos -> irAlPortalDeEstudiantes()
                }
            }
        }
        handler.postDelayed({ terminar(null, "tiempo agotado") }, TIMEOUT_MS)
        webView.loadUrl(URL_LOGIN)
    }

    private fun llenarLogin() {
        val u = usuario
        val c = clave
        if (u == null || c == null || intentosLogin >= 1) {
            // Ya se intentó y MiUV volvió a pedir login: credenciales rechazadas o captcha
            terminar(null, "MiUV rechazó el inicio de sesión")
            return
        }
        intentosLogin++
        irADatosTrasLogin = true
        // Se pasan como JSON para escapar correctamente cualquier carácter
        val usuarioJs = com.google.gson.Gson().toJson(u)
        val claveJs = com.google.gson.Gson().toJson(String(c))
        val js = """
            (function() {
              try {
                var pass = document.querySelector('input[type=password]');
                if (!pass) return 'sin_campo_clave';
                var form = pass.form || document;
                var candidatos = Array.prototype.slice.call(
                  form.querySelectorAll('input[type=text], input[type=email], input:not([type])'));
                var user = candidatos.filter(function(e){ return e.offsetParent !== null; })[0] || candidatos[0];
                if (!user) return 'sin_campo_usuario';
                function poner(el, v) {
                  el.focus(); el.value = v;
                  el.dispatchEvent(new Event('input', {bubbles:true}));
                  el.dispatchEvent(new Event('change', {bubbles:true}));
                }
                poner(user, $usuarioJs);
                poner(pass, $claveJs);
                var boton = form.querySelector('input[type=submit], button[type=submit], input[type=image], button:not([type])')
                         || document.querySelector('input[type=submit], button');
                if (boton) { boton.click(); return 'enviado_boton'; }
                if (pass.form) { pass.form.submit(); return 'enviado_form'; }
                return 'sin_boton';
              } catch (e) { return 'error:' + e; }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { r -> Log.d(TAG, "Login MiUV: $r") }
        // El script ya tiene la clave: borrarla de inmediato de este objeto
        descartarCredenciales()
    }

    /**
     * Ya dentro de MiUV: seguir el enlace del escritorio al portal de estudiantes
     * (puede llevar un token de paso entre dsia y dsiapes). Si no hay, ir directo a szigral.
     */
    private fun irAlPortalDeEstudiantes() {
        if (yaPediDatos) return
        yaPediDatos = true
        val js = """
            (function() {
              var a = document.querySelector('a[href*="szigral"]')
                   || document.querySelector('a[href*="portales/estudiantes"]')
                   || document.querySelector('a[href*="Estudiante"], a[href*="estudiante"]');
              return a ? a.href : '';
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { r ->
            val href = try { JsonParser.parseString(r).asString } catch (_: Exception) { "" }
            Log.d(TAG, "Enlace al portal: ${if (href.isBlank()) "no encontrado, voy directo" else href.substringBefore('?')}")
            webView.loadUrl(href.ifBlank { URL_DATOS })
        }
    }

    private fun extraerDatos() {
        val js = """
            (function() {
              var pares = {};
              var ps = document.querySelectorAll('#div-iAcademica p, #div-iPersonal p, .bloques p');
              for (var i = 0; i < ps.length; i++) {
                var t = (ps[i].innerText || '').replace(/\s+/g, ' ').trim();
                var k = t.indexOf(':');
                if (k > 0) pares[t.substring(0, k).trim().toLowerCase()] = t.substring(k + 1).trim();
              }
              var prog = document.getElementById('content_wucDatosGralAlum1_lblProg');
              if (prog) pares['programa'] = (prog.innerText || '').trim();
              var imgs = [];
              var im = document.querySelectorAll('#div-iPersonal img, img[id*=oto], img[src*=oto]');
              for (var j = 0; j < im.length; j++) if (im[j].src) imgs.push(im[j].src);
              return JSON.stringify({ pares: pares, imgs: imgs });
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { resultado ->
            try {
                val texto = JsonParser.parseString(resultado).asString
                val obj = JsonParser.parseString(texto).asJsonObject
                val pares = obj.getAsJsonObject("pares").entrySet().associate { it.key to it.value.asString }
                val imgs = obj.getAsJsonArray("imgs").map { it.asString }
                // Solo nombres de campos al log: nunca valores personales
                Log.i(TAG, "Campos leídos: ${pares.keys} · imágenes candidatas: ${imgs.size}")

                fun campo(vararg nombres: String) = nombres.firstNotNullOfOrNull { n ->
                    pares.entries.firstOrNull { it.key.startsWith(n) }?.value?.takeIf { it.isNotBlank() }
                }

                val base = DatosMiuv(
                    programa = campo("programa")?.let(::capitalizar),
                    campus = campo("campus")?.let(::capitalizar),
                    facultad = campo("facultad")?.let(::capitalizar),
                    area = campo("área", "area")?.let(::capitalizar),
                    nivel = campo("nivel")?.let(::capitalizar),
                    fotoLocal = null
                )
                if (base.programa == null) {
                    terminar(null, "szigral.aspx sin programa")
                    return@evaluateJavascript
                }
                descargarFoto(imgs.firstOrNull()) { foto -> terminar(base.copy(fotoLocal = foto), null) }
            } catch (e: Exception) {
                terminar(null, "error leyendo datos: ${e.message}")
            }
        }
    }

    /** Descarga la foto con las cookies de la sesión de MiUV (en segundo plano). */
    private fun descargarFoto(url: String?, listo: (String?) -> Unit) {
        if (url.isNullOrBlank() || !url.startsWith("http")) {
            Log.w(TAG, "Sin foto en la página de MiUV")
            listo(null); return
        }
        val cookies = CookieManager.getInstance().getCookie(url)
        Thread {
            val ruta = try {
                val cliente = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS).build()
                val req = Request.Builder().url(url).apply {
                    if (!cookies.isNullOrBlank()) header("Cookie", cookies)
                }.build()
                cliente.newCall(req).execute().use { r ->
                    val tipo = r.header("Content-Type") ?: ""
                    if (!r.isSuccessful || !tipo.startsWith("image")) {
                        Log.w(TAG, "Foto no disponible: HTTP ${r.code}, tipo '$tipo'")
                        null
                    } else {
                        // Un archivo nuevo por persona y por sincronización: la caché de Coil
                        // (por ruta) nunca puede mostrar la foto de otra cuenta.
                        dirFotos.mkdirs()
                        dirFotos.listFiles()?.forEach { it.delete() }
                        val destino = File(dirFotos, "foto_${idFoto}_${System.currentTimeMillis()}.jpg")
                        r.body?.byteStream()?.use { i -> destino.outputStream().use { o -> i.copyTo(o) } }
                        Log.i(TAG, "Foto oficial guardada (${destino.length()} bytes)")
                        "file://" + destino.absolutePath
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo descargar la foto: ${e.message}")
                null
            }
            handler.post { listo(ruta) }
        }.start()
    }

    private fun terminar(datos: DatosMiuv?, motivoError: String?) {
        if (terminado) return
        terminado = true
        handler.removeCallbacksAndMessages(null)
        descartarCredenciales()
        if (motivoError != null) Log.w(TAG, "Sin datos de MiUV: $motivoError") else Log.i(TAG, "Datos de MiUV obtenidos")
        alTerminar(datos)
    }

    /** "INGENIERÍA DE SOFTWARE" / "ingeniería de software" → "Ingeniería de Software" */
    private fun capitalizar(s: String): String {
        val menores = setOf("de", "del", "la", "las", "los", "y", "e", "en", "para", "con")
        return s.lowercase().split(" ").filter { it.isNotBlank() }.mapIndexed { i, w ->
            if (i > 0 && w in menores) w else w.replaceFirstChar { it.uppercase() }
        }.joinToString(" ")
    }
}
