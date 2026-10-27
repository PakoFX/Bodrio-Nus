package com.pako.eminus4

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    // Botones de la barra flotante de navegación
    private lateinit var btnNavBack: ImageButton
    private lateinit var btnNavForward: ImageButton
    private lateinit var btnNavHome: TextView
    private lateinit var btnNavReload: ImageButton

    // Manejador para cuando Eminus pida subir una tarea/archivo
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uriResult = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            fileUploadCallback?.onReceiveValue(uriResult)
        } else {
            fileUploadCallback?.onReceiveValue(null)
        }
        fileUploadCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Respetar la barra de estado y notch de tu POCO X6
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        btnNavBack = findViewById(R.id.btnNavBack)
        btnNavForward = findViewById(R.id.btnNavForward)
        btnNavHome = findViewById(R.id.btnNavHome)
        btnNavReload = findViewById(R.id.btnNavReload)

        // Desactivar el gesto táctil de jalar para refrescar para que no interrumpa el scroll hacia arriba.
        // La recarga se hace de forma limpia y directa con el botón 🔄 de la barra flotante.
        swipeRefreshLayout.isEnabled = false

        configurarWebView()
        configurarNavegacion()

        // Cargar Eminus 4 inicial
        webView.loadUrl("https://eminus.uv.mx/eminus4/")
    }

    override fun onPause() {
        super.onPause()
        // Guardar cookies y sesión inmediatamente al minimizar la app
        CookieManager.getInstance().flush()
    }

    private fun configurarNavegacion() {
        // Botón físico o gesto atrás de Android
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                regresarPagina()
            }
        })

        // Botón Atrás en la barra flotante
        btnNavBack.setOnClickListener {
            regresarPagina()
        }

        // Botón Adelante en la barra flotante
        btnNavForward.setOnClickListener {
            if (webView.canGoForward()) {
                webView.goForward()
            }
        }

        // Botón Inicio: Salida de emergencia directa a tus cursos de Eminus
        btnNavHome.setOnClickListener {
            webView.loadUrl("https://eminus.uv.mx/eminus4/")
            Toast.makeText(this, "Volviendo a Eminus...", Toast.LENGTH_SHORT).show()
        }

        // Botón Recargar en la barra flotante
        btnNavReload.setOnClickListener {
            webView.reload()
        }
    }

    private fun regresarPagina() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            finish()
        }
    }

    private fun configurarWebView() {
        WebView.setWebContentsDebuggingEnabled(true)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.textZoom = 100
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // Permitir que JavaScript abra ventanas emergentes (necesario para 'ver en línea')
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // Permitir cookies (crucial para el inicio de sesión de la UV y visores en línea)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // Mantener enlaces web DENTRO de la app para no perder la sesión
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase() ?: ""
                val url = uri.toString()

                Log.d("EMINUS_NAV", "shouldOverrideUrlLoading: $url")

                // Si es un esquema no-web (llamada, correo, whatsapp), abrir la app externa
                if (scheme == "tel" || scheme == "mailto" || scheme == "sms" || scheme == "whatsapp") {
                    return try {
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        startActivity(intent)
                        true
                    } catch (e: Exception) {
                        false
                    }
                }

                // Si es un archivo PDF directo o documento, interceptarlo para descargarlo y abrirlo
                if (url.endsWith(".pdf", ignoreCase = true) ||
                    url.endsWith(".docx", ignoreCase = true) ||
                    url.endsWith(".xlsx", ignoreCase = true)
                ) {
                    descargarYAbrirArchivo(url, settings.userAgentString, "", "")
                    return true
                }

                // Cualquier enlace web (http / https), incluidos visores de OneDrive, Office 365,
                // SharePoint y UV, se cargan DENTRO del WebView con las credenciales de la app
                if (scheme == "http" || scheme == "https") {
                    return false
                }

                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
                CookieManager.getInstance().flush()

                inyectarEstilosYScriptsMovil()

                // Inspeccionar nombres de clases reales de Eminus 4 para CSS exacto
                webView.evaluateJavascript("""
                    (function() {
                        var search = document.querySelector('input[placeholder*="Search"]');
                        var card = document.querySelector('.card, .panel, [class*="course"], [class*="curso"]');
                        return 'SEARCH_PARENT: ' + (search ? search.parentElement.className + ' | ' + search.parentElement.parentElement.className : 'NONE') +
                               ' || CARD_CLASSES: ' + (card ? card.className + ' | TAG: ' + card.tagName + ' | PARENT: ' + card.parentElement.className : 'NONE');
                    })();
                """.trimIndent()) { result ->
                    Log.i("EMINUS_SELECTORS", "Clases detectadas: " + result)
                }
            }
        }

        // Manejador avanzado de ChromeClient (Intercepta ventanas emergentes de 'Ver en línea')
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
            }

            // Clave: Cuando Eminus hace window.open(...) para visualizar un archivo en línea,
            // atrapamos esa nueva ventana y la cargamos directamente aquí dentro
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val tempWebView = WebView(this@MainActivity)
                tempWebView.settings.javaScriptEnabled = true
                tempWebView.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) {
                        url?.let { dest ->
                            if (dest.isNotBlank() && dest != "about:blank") {
                                Log.d("EMINUS_POPUP", "Ventana emergente interceptada: $dest")
                                this@MainActivity.webView.loadUrl(dest)
                            }
                        }
                    }

                    override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                        val dest = request?.url?.toString() ?: return false
                        Log.d("EMINUS_POPUP", "Ventana emergente override: $dest")
                        this@MainActivity.webView.loadUrl(dest)
                        return true
                    }
                }

                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = tempWebView
                resultMsg?.sendToTarget()
                return true
            }

            // Selector para subir tareas / documentos
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback

                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }

                filePickerLauncher.launch(intent)
                return true
            }
        }

        // Gestor de descargas seguro e independiente: Descarga con cookies y abre de inmediato
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            descargarYAbrirArchivo(url, userAgent, contentDisposition, mimetype)
        }
    }

    /**
     * Inyecta la hoja de estilos CSS y el script JS para transformar
     * la interfaz de Eminus 4 en una experiencia 100% móvil y moderna.
     */
    private fun inyectarEstilosYScriptsMovil() {
        try {
            val url = webView.url ?: ""
            if (!url.contains("eminus.uv.mx", ignoreCase = true)) {
                return
            }

            val cssContent = assets.open("eminus_mobile.css").bufferedReader().use { it.readText() }
            val encodedCss = android.util.Base64.encodeToString(cssContent.toByteArray(), android.util.Base64.NO_WRAP)

            val iframeCssContent = assets.open("eminus_iframe.css").bufferedReader().use { it.readText() }
            val encodedIframeCss = android.util.Base64.encodeToString(iframeCssContent.toByteArray(), android.util.Base64.NO_WRAP)

            val injection = """
                (function() {
                    var styleId = 'eminus-mobile-injected-style';
                    var iframeStyleId = 'eminus-iframe-injected-style';

                    function asegurarEstilo() {
                        var style = document.getElementById(styleId);
                        if (!style) {
                            style = document.createElement('style');
                            style.id = styleId;
                            style.type = 'text/css';
                            (document.head || document.documentElement).appendChild(style);
                        }
                        var css = atob('$encodedCss');
                        if (style.textContent !== css) {
                            style.textContent = css;
                        }

                        // Bloquear zoom multitáctil y fijar escala 1:1 nativa (como pidió el usuario)
                        var vp = document.querySelector('meta[name="viewport"]');
                        if (vp) {
                            vp.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0, user-scalable=no');
                        } else {
                            var m = document.createElement('meta');
                            m.name = 'viewport';
                            m.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0, user-scalable=no';
                            (document.head || document.documentElement).appendChild(m);
                        }

                        // Asegurar también los iframes (vistas de actividades / exámenes / foros)
                        asegurarEstiloIframes();

                        // Configurar botón de hamburguesa y cierre automático de la barra lateral
                        configurarToggleSidebar();
                    }

                    function configurarToggleSidebar() {
                        var togglers = document.querySelectorAll('#m_aside_left_offcanvas_toggle, #m_aside_left_minimize_toggle, .m-brand__toggler');
                        togglers.forEach(function(btn) {
                            if (!btn.__bodrioBound) {
                                btn.__bodrioBound = true;
                                btn.addEventListener('click', function(e) {
                                    var isOpen = document.body.classList.contains('m-aside-left--on') || document.body.classList.contains('bodrio-aside-open');
                                    if (isOpen) {
                                        document.body.classList.remove('m-aside-left--on', 'bodrio-aside-open');
                                    } else {
                                        document.body.classList.add('m-aside-left--on', 'bodrio-aside-open');
                                    }
                                }, true);
                            }
                        });

                        // Cerrar al hacer clic en cualquier opción de la barra
                        var asideLinks = document.querySelectorAll('m-aside-left a, .m-aside-left a');
                        asideLinks.forEach(function(link) {
                            if (!link.__bodrioBound) {
                                link.__bodrioBound = true;
                                link.addEventListener('click', function() {
                                    setTimeout(function() {
                                        document.body.classList.remove('m-aside-left--on', 'bodrio-aside-open');
                                    }, 150);
                                }, true);
                            }
                        });

                        // Cerrar al tocar el overlay oscuro
                        var overlays = document.querySelectorAll('.m-aside-left-overlay');
                        overlays.forEach(function(ov) {
                            if (!ov.__bodrioBound) {
                                ov.__bodrioBound = true;
                                ov.addEventListener('click', function() {
                                    document.body.classList.remove('m-aside-left--on', 'bodrio-aside-open');
                                }, true);
                            }
                        });
                    }


                    function asegurarEstiloIframes() {
                        try {
                            var iframes = document.querySelectorAll('iframe');
                            for (var i = 0; i < iframes.length; i++) {
                                var f = iframes[i];
                                var doc = f.contentDocument;
                                if (doc && (doc.head || doc.documentElement)) {
                                    var s = doc.getElementById(iframeStyleId);
                                    if (!s) {
                                        s = doc.createElement('style');
                                        s.id = iframeStyleId;
                                        s.type = 'text/css';
                                        (doc.head || doc.documentElement).appendChild(s);
                                    }
                                    var ifrCss = atob('$encodedIframeCss');
                                    if (s.textContent !== ifrCss) {
                                        s.textContent = ifrCss;
                                    }
                                }
                            }
                        } catch(e) {}
                    }

                    // Asegurar estilos inmediatamente al cargar
                    asegurarEstilo();

                    // Detectar transiciones de Angular SPA y creación de iframes
                    if (!window.__eminusSpaHooked) {
                        window.__eminusSpaHooked = true;
                        var origPush = history.pushState;
                        history.pushState = function() {
                            origPush.apply(history, arguments);
                            asegurarEstilo();
                        };
                        var origReplace = history.replaceState;
                        history.replaceState = function() {
                            origReplace.apply(history, arguments);
                            asegurarEstilo();
                        };
                        window.addEventListener('popstate', asegurarEstilo);
                        window.addEventListener('hashchange', asegurarEstilo);

                        // Si el usuario hace submit en el login o da clic en Iniciar Sesión
                        document.addEventListener('submit', function() {
                            setTimeout(asegurarEstilo, 300);
                            setTimeout(asegurarEstilo, 1000);
                            setTimeout(asegurarEstilo, 2000);
                        }, true);

                        // Observador de mutaciones para aplicar estilos a nuevos iframes o vistas creadas
                        var observer = new MutationObserver(function() {
                            asegurarEstilo();
                        });
                        observer.observe(document.body || document.documentElement, {
                            childList: true,
                            subtree: true
                        });

                        // Monitoreo periódico ligero para garantizar persistencia en iframes SPA lentos
                        setInterval(asegurarEstiloIframes, 1500);
                    }
                })();
            """.trimIndent()

            webView.evaluateJavascript(injection, null)
            Log.d("EMINUS_STYLE", "CSS móvil e iframe inyectados con éxito")
        } catch (e: Exception) {
            Log.e("EMINUS_STYLE", "Error al inyectar CSS", e)
        }
    }


    /**
     * Descarga el archivo de forma nativa e independiente usando las cookies de sesión
     * y lo abre de inmediato en el visor del sistema (Drive, Mi Doc Viewer, etc.).
     */
    private fun descargarYAbrirArchivo(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimetype: String
    ) {
        Log.d("EMINUS_DOWNLOAD", "Iniciando descarga: url=$url, mime=$mimetype, disp=$contentDisposition")

        // Determinar nombre de archivo limpio
        var fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
        fileName = fileName.replace("\"", "").replace("'", "").trim()
        if (fileName.isBlank() || fileName == "downloadfile") {
            val ext = when {
                mimetype.contains("pdf", true) || url.contains(".pdf", true) -> ".pdf"
                mimetype.contains("word", true) || url.contains(".docx", true) -> ".docx"
                mimetype.contains("excel", true) || url.contains(".xlsx", true) -> ".xlsx"
                mimetype.contains("image", true) || url.contains(".jpg", true) -> ".jpg"
                else -> ".pdf"
            }
            fileName = "documento_eminus_${System.currentTimeMillis()}$ext"
        }

        Toast.makeText(this, "Descargando: $fileName", Toast.LENGTH_SHORT).show()
        progressBar.visibility = View.VISIBLE

        val cookies = CookieManager.getInstance().getCookie(url) ?: ""

        thread {
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    if (cookies.isNotEmpty()) {
                        setRequestProperty("Cookie", cookies)
                    }
                    if (userAgent.isNotEmpty()) {
                        setRequestProperty("User-Agent", userAgent)
                    }
                    instanceFollowRedirects = true
                    connectTimeout = 20000
                    readTimeout = 25000
                }

                connection.connect()
                val responseCode = connection.responseCode
                Log.d("EMINUS_DOWNLOAD", "Código de respuesta del servidor: $responseCode")

                if (responseCode in 200..299) {
                    // Guardar en el almacenamiento de la app
                    val storageDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
                    val file = File(storageDir, fileName)

                    connection.inputStream.use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }

                    Log.d("EMINUS_DOWNLOAD", "Descarga exitosa, tamaño: ${file.length()} bytes en ${file.absolutePath}")

                    // Registrar en la carpeta pública de Descargas de Android
                    guardarEnDescargasPublicas(file, fileName, mimetype)

                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        abrirArchivoLocal(file, mimetype)
                    }
                } else {
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@MainActivity, "Error del servidor UV ($responseCode)", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("EMINUS_DOWNLOAD", "Error al procesar la descarga", e)
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "No se pudo procesar la descarga: ${e.localizedMessage ?: "Error de red"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Abre el archivo descargado usando FileProvider para que el usuario pueda verlo al instante
     */
    private fun abrirArchivoLocal(file: File, mimetype: String) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                file
            )

            var finalMime = mimetype
            if (finalMime.isBlank() || finalMime == "application/octet-stream") {
                finalMime = when (file.extension.lowercase()) {
                    "pdf" -> "application/pdf"
                    "doc", "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    "xls", "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    "ppt", "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "zip" -> "application/zip"
                    else -> "*/*"
                }
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, finalMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            startActivity(Intent.createChooser(intent, "Abrir archivo con:"))
            Toast.makeText(this, "Abriendo archivo...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("EMINUS_DOWNLOAD", "Error al abrir visor para ${file.name}", e)
            Toast.makeText(this, "Guardado en Descargas: ${file.name}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Copia el archivo a la carpeta pública Descargas de Android usando MediaStore
     */
    private fun guardarEnDescargasPublicas(file: File, fileName: String, mimetype: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, if (mimetype.isNotBlank()) mimetype else "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("EMINUS_DOWNLOAD", "No se pudo sincronizar a MediaStore público", e)
            }
        }
    }
}
