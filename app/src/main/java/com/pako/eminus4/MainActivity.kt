package com.pako.eminus4

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
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

    // Visor interno (Ver en línea / PDF)
    private lateinit var viewerOverlay: View
    private lateinit var viewerWebContainer: FrameLayout
    private lateinit var pdfScroll: View
    private lateinit var pdfPages: LinearLayout
    private lateinit var viewerProgress: ProgressBar
    private lateinit var btnCerrarVisor: TextView
    private lateinit var txtTituloVisor: TextView
    private var popupWebView: WebView? = null
    private val pdfBitmaps = mutableListOf<Bitmap>()

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

    // Descarga con selector nativo de ubicación (SAF)
    private data class PendingDownload(
        val url: String,
        val userAgent: String,
        val contentDisposition: String,
        val mimetype: String
    )
    private var pendingDownload: PendingDownload? = null
    private var pendingBlobBytes: ByteArray? = null   // bytes pre-leídos del blob antes del picker

    @Volatile
    private var isDownloadPickerActive = false
    private var lastDownloadTime = 0L

    private val saveDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        isDownloadPickerActive = false
        val dl = pendingDownload ?: return@registerForActivityResult
        pendingDownload = null
        val blobBytes = pendingBlobBytes
        pendingBlobBytes = null
        if (uri == null) {
            Toast.makeText(this, "Descarga cancelada", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        if (blobBytes != null) {
            // Los bytes ya están en memoria (leídos antes de abrir el picker)
            thread {
                try {
                    contentResolver.openOutputStream(uri)?.use { it.write(blobBytes) }
                    runOnUiThread {
                        Toast.makeText(this, "✅ Archivo guardado correctamente", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("EMINUS_DOWNLOAD", "Error escribiendo blob a URI", e)
                    runOnUiThread {
                        Toast.makeText(this, "Error al guardar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else if (!dl.url.startsWith("blob:")) {
            descargarHttpAUri(dl, uri)
        }
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

        viewerOverlay = findViewById(R.id.viewerOverlay)
        viewerWebContainer = findViewById(R.id.viewerWebContainer)
        pdfScroll = findViewById(R.id.pdfScroll)
        pdfPages = findViewById(R.id.pdfPages)
        viewerProgress = findViewById(R.id.viewerProgress)
        btnCerrarVisor = findViewById(R.id.btnCerrarVisor)
        txtTituloVisor = findViewById(R.id.txtTituloVisor)
        btnCerrarVisor.setOnClickListener { cerrarVisor() }

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

    override fun onDestroy() {
        if (::viewerOverlay.isInitialized) {
            cerrarVisor()
        }
        super.onDestroy()
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
            val visor = popupWebView
            if (visorActivo() && visor != null && visor.canGoForward()) {
                visor.goForward()
            } else if (webView.canGoForward()) {
                webView.goForward()
            }
        }

        // Botón Inicio: Salida de emergencia directa a tus cursos de Eminus
        btnNavHome.setOnClickListener {
            cerrarVisor()
            webView.loadUrl("https://eminus.uv.mx/eminus4/")
            Toast.makeText(this, "Volviendo a Eminus...", Toast.LENGTH_SHORT).show()
        }

        // Botón Recargar en la barra flotante
        btnNavReload.setOnClickListener {
            val visor = popupWebView
            if (visorActivo() && visor != null) {
                visor.reload()
            } else {
                webView.reload()
            }
        }
    }

    private fun visorActivo(): Boolean = viewerOverlay.visibility == View.VISIBLE

    private fun regresarPagina() {
        if (visorActivo()) {
            val visor = popupWebView
            if (visor != null && visor.canGoBack()) {
                visor.goBack()
            } else {
                cerrarVisor()
            }
            return
        }
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
        // Microsoft 365 / OneDrive bloquean el UA con "; wv". Lo quitamos para el visor en línea.
        settings.userAgentString = userAgentSinWebView()

        // Permitir que JavaScript abra ventanas emergentes (necesario para 'ver en línea')
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // Permitir cookies (crucial para el inicio de sesión de la UV y visores en línea)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // Puente nativo para recibir los datos de descarga instantáneamente desde JS sin polling
        webView.addJavascriptInterface(BlobDownloadBridge(), "AndroidDownloadBridge")

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

                // No abrir la app de Office / intents externos: el visor se queda en Eminus
                if (scheme != "http" && scheme != "https" && scheme != "about") {
                    Log.d("EMINUS_NAV", "Esquema ignorado para no salir de la app: $scheme")
                    return true
                }

                // Visores de Office/SharePoint en el frame principal se quedan en el overlay
                if (request.isForMainFrame && esUrlDeVisorEnLinea(url)) {
                    abrirEnVisorWeb(url)
                    return true
                }

                // Cualquier enlace web (http / https) se carga DENTRO del WebView
                if (scheme == "http" || scheme == "https") {
                    return false
                }

                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
                inyectarInterceptorDeBlobs(view)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
                CookieManager.getInstance().flush()

                inyectarEstilosYScriptsMovil()
                inyectarInterceptorDeBlobs(view)

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

            // Previene crash si el renderer del WebView principal muere (Android 8+)
            @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.O)
            override fun onRenderProcessGone(
                view: WebView?,
                detail: android.webkit.RenderProcessGoneDetail?
            ): Boolean {
                Log.e("EMINUS_NAV", "Renderer principal caido: crash=${detail?.didCrash()}")
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "La pagina no pudo cargarse. Recargando...",
                        Toast.LENGTH_LONG
                    ).show()
                    // Reemplazar la instancia muerta con una nueva
                    webView.loadUrl("https://eminus.uv.mx/eminus4/")
                }
                return true
            }
        }

        // Manejador avanzado de ChromeClient (Intercepta ventanas emergentes de 'Ver en línea')
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
            }

            // Clave: window.open de "Ver en línea" debe vivir en un WebView propio.
            // Si robamos solo la URL GET, Office/OneDrive pierden el POST y el visor queda en blanco.
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                Log.d("EMINUS_POPUP", "onCreateWindow: dialog=$isDialog gesture=$isUserGesture")
                return abrirPopupEnVisor(resultMsg)
            }

            override fun onCloseWindow(window: WebView?) {
                if (window == popupWebView) {
                    cerrarVisor()
                }
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

        // Si el servidor manda el archivo crudo (PDF), lo abrimos dentro de la app
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            manejarDescargaOVisor(url, userAgent, contentDisposition, mimetype)
        }
    }

    private fun userAgentSinWebView(): String {
        return WebSettings.getDefaultUserAgent(this)
            .replace("; wv", "")
            .replace(" Version/4.0", "")
    }

    private fun esUrlDeVisorEnLinea(url: String): Boolean {
        val u = url.lowercase()
        if (u.contains("download.aspx") || u.contains("downloadfile")) {
            return false
        }
        return listOf(
            "officeapps.live.com",
            "view.officeapps",
            "sharepoint.com",
            "onedrive.live.com",
            "office.com",
            "microsoft365.com",
            "wopiframe",
            "sourcedoc=",
            "pdfjs",
            "viewer.html"
        ).any { u.contains(it) }
    }

    private fun esPdf(url: String, mimetype: String, contentDisposition: String): Boolean {
        if (mimetype.contains("application/pdf", ignoreCase = true)) return true
        val nombre = URLUtil.guessFileName(url, contentDisposition, mimetype).lowercase()
        return nombre.endsWith(".pdf") || url.lowercase().contains(".pdf")
    }

    private fun parecePdf(file: File): Boolean {
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(4)
                if (input.read(header) < 4) return false
                header.decodeToString() == "%PDF"
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun manejarDescargaOVisor(
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimetype: String?
    ) {
        val disp = contentDisposition ?: ""
        val mime = mimetype ?: ""
        Log.d("EMINUS_DOWNLOAD", "listener url=$url mime=$mime disp=$disp")

        // Si es una URL de visor en línea (Office Online, etc.) sin attachment → abrir en visor web
        if (esUrlDeVisorEnLinea(url) && !disp.contains("attachment", ignoreCase = true)) {
            abrirEnVisorWeb(url)
            return
        }

        // Blob URL → leer el blob AHORA (antes del picker) para garantizar que sigue válido
        if (url.startsWith("blob:")) {
            if (pendingBlobBytes != null) {
                Log.d("EMINUS_DOWNLOAD", "Blob ya pre-leído y listo en memoria!")
                return
            }
            leerBlobParaDescarga(url, userAgent, disp, mime)
            return
        }

        // URL http/https → abrir picker inmediatamente
        iniciarDescargaConPicker(url, userAgent, disp, mime)
    }

    private var pendingBlobDisposition: String = ""

    /**
     * Puente nativo expuesto a JavaScript para transferir los blobs descargados
     * de forma instantánea a Kotlin, eliminando la latencia de polling (3-4 segundos).
     */
    inner class BlobDownloadBridge {
        @android.webkit.JavascriptInterface
        fun onBlobReady(dataUrl: String, mimeType: String, suggestedFileName: String) {
            runOnUiThread {
                procesarDataUrlParaDescarga(dataUrl, suggestedFileName, pendingBlobDisposition, mimeType)
            }
        }

        @android.webkit.JavascriptInterface
        fun onBlobFailed(error: String) {
            Log.e("EMINUS_DOWNLOAD", "Error leyendo blob desde JS: $error")
            runOnUiThread {
                progressBar.visibility = View.GONE
                Toast.makeText(this@MainActivity, "No se pudo leer el archivo: $error", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Para blob: URLs, lee el blob del WebView ANTES de abrir el picker.
     * El interceptor en JS ya capturó el objeto Blob en memoria y retrasó su revocación,
     * por lo que se lee inmediatamente a través del puente nativo.
     */
    private fun leerBlobParaDescarga(
        blobUrl: String,
        userAgent: String,
        contentDisposition: String,
        mimetype: String
    ) {
        Toast.makeText(this, "Preparando descarga…", Toast.LENGTH_SHORT).show()
        progressBar.visibility = View.VISIBLE
        pendingBlobDisposition = contentDisposition

        val jsRead = """
            (function() {
                var targetUrl = '$blobUrl';

                // 1. Verificar si ya tenemos el DataURL pre-convertido
                var preData = (window.top && window.top.__eminusDataUrls && window.top.__eminusDataUrls[targetUrl]) ||
                              (window.__eminusDataUrls && window.__eminusDataUrls[targetUrl]);

                var fileName = (window.top && window.top.__eminusBlobNames && window.top.__eminusBlobNames[targetUrl]) ||
                               (window.__eminusBlobNames && window.__eminusBlobNames[targetUrl]) || '';

                function reportReady(dataUrl, mime) {
                    if (window.AndroidDownloadBridge && window.AndroidDownloadBridge.onBlobReady) {
                        window.AndroidDownloadBridge.onBlobReady(dataUrl, mime || '', fileName || '');
                    }
                }

                function reportFailed(msg) {
                    if (window.AndroidDownloadBridge && window.AndroidDownloadBridge.onBlobFailed) {
                        window.AndroidDownloadBridge.onBlobFailed(msg);
                    }
                }

                if (preData && preData.indexOf('data:') === 0) {
                    reportReady(preData, '');
                    return;
                }

                // 2. Buscar recursivamente el objeto Blob y su ventana en todos los frames
                function searchBlob(win) {
                    try {
                        if (!win) return null;
                        if (win.__eminusBlobs && win.__eminusBlobs[targetUrl]) {
                            var entry = win.__eminusBlobs[targetUrl];
                            return { blob: (entry.blob || entry), win: win };
                        }
                        if (win.document) {
                            var iframes = win.document.querySelectorAll('iframe');
                            for (var i = 0; i < iframes.length; i++) {
                                var found = searchBlob(iframes[i].contentWindow);
                                if (found) return found;
                            }
                        }
                    } catch(e) {}
                    return null;
                }

                var foundEntry = searchBlob(window.top || window);

                function readBlob(b) {
                    var reader = new FileReader();
                    reader.onloadend = function() {
                        if (reader.result) {
                            reportReady(reader.result, b.type);
                        } else {
                            reportFailed('El lector de archivo no retorno datos');
                        }
                    };
                    reader.onerror = function() {
                        reportFailed('Error al leer el archivo con FileReader');
                    };
                    reader.readAsDataURL(b);
                }

                if (foundEntry && foundEntry.blob) {
                    readBlob(foundEntry.blob);
                } else {
                    // 3. Fallback: fetch/XHR en la ventana que sea dueña o en top
                    var fetchWin = (foundEntry && foundEntry.win) || window.top || window;
                    try {
                        fetchWin.fetch(targetUrl)
                            .then(function(r) { return r.blob(); })
                            .then(function(b) { readBlob(b); })
                            .catch(function(err) {
                                try {
                                    var xhr = new fetchWin.XMLHttpRequest();
                                    xhr.open('GET', targetUrl, true);
                                    xhr.responseType = 'blob';
                                    xhr.onload = function() {
                                        if (xhr.response) {
                                            readBlob(xhr.response);
                                        } else {
                                            reportFailed('XHR no retorno respuesta');
                                        }
                                    };
                                    xhr.onerror = function() {
                                        reportFailed('No se pudo acceder al blob por URL');
                                    };
                                    xhr.send();
                                } catch(e) {
                                    reportFailed(e.toString());
                                }
                            });
                    } catch(errFetch) {
                        reportFailed('Error al iniciar peticion de blob: ' + errFetch);
                    }
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsRead, null)

        // Timeout de seguridad en caso de que JS no responda
        webView.postDelayed({
            if (progressBar.visibility == View.VISIBLE && pendingBlobBytes == null) {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Tiempo de espera agotado al preparar el archivo.", Toast.LENGTH_LONG).show()
            }
        }, 8000)
    }

    /** Decodifica el data URL y lanza el picker SAF con los bytes listos en memoria */
    private fun procesarDataUrlParaDescarga(
        dataUrl: String,
        suggestedFileName: String,
        contentDisposition: String,
        mimetype: String
    ) {
        thread {
            try {
                val mimeReal = dataUrl.substringAfter("data:").substringBefore(";")
                    .takeIf { it.isNotBlank() } ?: mimetype.ifBlank { "application/octet-stream" }
                val b64   = dataUrl.substringAfter(";base64,")
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)

                val ext = when {
                    mimeReal.contains("pdf")          -> ".pdf"
                    mimeReal.contains("word") || mimeReal.contains("officedocument.wordprocessingml") -> ".docx"
                    mimeReal.contains("spreadsheet") || mimeReal.contains("officedocument.spreadsheetml") -> ".xlsx"
                    mimeReal.contains("presentation") || mimeReal.contains("officedocument.presentationml") -> ".pptx"
                    mimeReal.contains("zip")          -> ".zip"
                    mimeReal.contains("image/jpeg")   -> ".jpg"
                    mimeReal.contains("image/png")    -> ".png"
                    else                              -> ""
                }

                var fileName = suggestedFileName.trim().replace("\"", "").replace("'", "")
                if (fileName.isBlank() || fileName == "downloadfile" || fileName == "file") {
                    fileName = URLUtil.guessFileName("file", contentDisposition, mimeReal)
                        .replace("\"", "").trim()
                }
                if (fileName.isBlank() || fileName == "downloadfile" || fileName == "file") {
                    fileName = "documento_eminus_${System.currentTimeMillis()}$ext"
                }
                if (ext.isNotEmpty() && !fileName.contains(".")) {
                    fileName += ext
                }

                Log.d("EMINUS_DOWNLOAD", "Blob listo: ${bytes.size} bytes, mime=$mimeReal, file=$fileName")
                runOnUiThread {
                    val now = System.currentTimeMillis()
                    if (isDownloadPickerActive || (now - lastDownloadTime < 2000)) {
                        Log.d("EMINUS_DOWNLOAD", "Lanzamiento duplicado de picker prevenido!")
                        return@runOnUiThread
                    }
                    isDownloadPickerActive = true
                    lastDownloadTime = now

                    progressBar.visibility = View.GONE
                    pendingBlobBytes = bytes
                    val ua = webView.settings.userAgentString
                    pendingDownload = PendingDownload("blob:", ua, contentDisposition, mimeReal)
                    saveDocumentLauncher.launch(fileName)
                }
            } catch (e: Exception) {
                Log.e("EMINUS_DOWNLOAD", "Error decodificando blob", e)
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Error al preparar archivo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Lanza el selector nativo de Android (SAF) para que el usuario elija dónde guardar el archivo.
     * El nombre sugerido y el MIME se extraen de la URL/cabeceras del servidor.
     */
    private fun iniciarDescargaConPicker(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimetype: String
    ) {
        var fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
            .replace("\"", "").replace("'", "").trim()
        if (fileName.isBlank() || fileName == "downloadfile") {
            val ext = detectarMimePorUrl(url).let {
                when {
                    it.contains("pdf")          -> ".pdf"
                    it.contains("word")         -> ".docx"
                    it.contains("spreadsheet")  -> ".xlsx"
                    it.contains("presentation") -> ".pptx"
                    else                        -> ""
                }
            }
            fileName = "documento_eminus_${System.currentTimeMillis()}$ext"
        }

        val mime = when {
            mimetype.isNotBlank() && mimetype != "application/octet-stream" -> mimetype
            else -> detectarMimePorUrl(url).takeIf { it != "*/*" } ?: "application/octet-stream"
        }

        val now = System.currentTimeMillis()
        if (isDownloadPickerActive || (now - lastDownloadTime < 2000)) {
            Log.d("EMINUS_DOWNLOAD", "Lanzamiento duplicado de picker HTTP prevenido!")
            return
        }
        isDownloadPickerActive = true
        lastDownloadTime = now

        Log.d("EMINUS_DOWNLOAD", "Lanzando picker: fileName=$fileName mime=$mime")
        pendingDownload = PendingDownload(url, userAgent, contentDisposition, mime)
        saveDocumentLauncher.launch(fileName)
    }

    /**
     * Descarga el archivo a la URI elegida por el usuario en el picker SAF.
     * Solo se llama para URLs http/https; los blobs ya están en pendingBlobBytes.
     */
    private fun descargarAUri(dl: PendingDownload, destUri: Uri) {
        descargarHttpAUri(dl, destUri)
    }

    /** Descarga un archivo http/https con cookies de sesión directamente al URI SAF */
    private fun descargarHttpAUri(dl: PendingDownload, destUri: Uri) {
        Toast.makeText(this, "Descargando...", Toast.LENGTH_SHORT).show()
        progressBar.visibility = View.VISIBLE
        val cookies = CookieManager.getInstance().getCookie(dl.url) ?: ""

        thread {
            try {
                val connection = (URL(dl.url).openConnection() as HttpURLConnection).apply {
                    if (cookies.isNotEmpty()) setRequestProperty("Cookie", cookies)
                    if (dl.userAgent.isNotEmpty()) setRequestProperty("User-Agent", dl.userAgent)
                    instanceFollowRedirects = true
                    connectTimeout = 20_000
                    readTimeout   = 60_000
                }
                connection.connect()
                val code = connection.responseCode
                Log.d("EMINUS_DOWNLOAD", "Respuesta servidor: $code para ${dl.url}")

                if (code in 200..299) {
                    contentResolver.openOutputStream(destUri)?.use { out ->
                        connection.inputStream.use { it.copyTo(out) }
                    }
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@MainActivity, "✅ Archivo guardado correctamente", Toast.LENGTH_LONG).show()
                    }
                } else {
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@MainActivity, "Error del servidor UV ($code)", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("EMINUS_DOWNLOAD", "Error descargando a URI", e)
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Error al descargar: ${e.localizedMessage ?: "Error de red"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun crearPopupWebView(): WebView {
        // El sufijo de data directory ya se configura en EminusApp.onCreate(),
        // antes de que cualquier WebView sea creado. No repetir aqui.
        return WebView(this)
    }

    /**
     * Detecta URLs de Office Online o archivos Office directos dentro del popup WebView.
     * Si se detectan, detiene la carga y los maneja con la app del sistema o PdfRenderer,
     * evitando el OOM que causaba el crash al cargar Office Online en el proceso principal.
     * Retorna true si interceptó la URL (el caller debe parar la carga).
     */
    private fun interceptarArchivoEnVisor(view: WebView?, url: String): Boolean {
        if (url.isBlank() || url == "about:blank") return false
        val lower = url.lowercase()

        // Office Online Viewer (Word, Excel, PPT, PDF) → extraer URL real del archivo
        if (lower.contains("view.officeapps.live.com") ||
            (lower.contains("officeapps.live.com") && lower.contains("/op/"))
        ) {
            val srcUrl = try { Uri.parse(url).getQueryParameter("src") } catch (_: Exception) { null }
            val fileUrl = srcUrl ?: url
            val ua = view?.settings?.userAgentString ?: userAgentSinWebView()
            val mime = detectarMimePorUrl(fileUrl)
            Log.d("EMINUS_INTERCEPT", "Office Online interceptado, fileUrl=$fileUrl mime=$mime")
            view?.stopLoading()
            descargarYAbrirArchivo(fileUrl, ua, "", mime, abrirDentroDeLaApp = true)
            return true
        }

        // URL directa de archivo Office (Word, Excel, PPT)
        val esArchivoOffice = lower.contains(".docx") || lower.contains(".doc?") ||
            lower.contains(".doc&") || lower.contains(".doc/") ||
            lower.contains(".xlsx") || lower.contains(".xls?") ||
            lower.contains(".pptx") || lower.contains(".ppt?")
        if (esArchivoOffice || (lower.endsWith(".docx") || lower.endsWith(".doc") ||
                lower.endsWith(".xlsx") || lower.endsWith(".xls") ||
                lower.endsWith(".pptx") || lower.endsWith(".ppt"))
        ) {
            val ua = view?.settings?.userAgentString ?: userAgentSinWebView()
            val mime = detectarMimePorUrl(lower)
            Log.d("EMINUS_INTERCEPT", "Archivo Office directo interceptado: $url mime=$mime")
            view?.stopLoading()
            descargarYAbrirArchivo(url, ua, "", mime, abrirDentroDeLaApp = true)
            return true
        }

        return false
    }

    /** Devuelve el MIME type apropiado basado en la extensión de la URL */
    private fun detectarMimePorUrl(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains(".pdf")  -> "application/pdf"
            lower.contains(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            lower.contains(".doc")  -> "application/msword"
            lower.contains(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            lower.contains(".xls")  -> "application/vnd.ms-excel"
            lower.contains(".pptx") -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            lower.contains(".ppt")  -> "application/vnd.ms-powerpoint"
            else                    -> "*/*"
        }
    }

    /**
     * Se llama cuando Eminus abre una ventana nueva (window.open).
     *
     * El problema raíz: Eminus usa JS para descargar el PDF en memoria como un Blob
     * y luego llama window.open(blobUrl) para mostrarlo. El blobUrl es solo válido
     * en el contexto JS del WebView padre — ningún otro WebView puede accederlo.
     *
     * Solución: en lugar de crear un segundo WebView (que crashea por data directory),
     * inyectamos JS en el WebView PADRE para leer el blob directamente.
     * La URL blob se captura desde el mensaje de transporte via WebView.HitTestResult
     * o desde el JS hook de window.open que ya está inyectado.
     */
    private fun abrirPopupEnVisor(resultMsg: Message?): Boolean {
        if (resultMsg == null) return false

        // Usamos un WebView mínimo solo para recibir el mensaje del transport
        // y extraer la URL sin cargar nada. Luego lo destruimos inmediatamente.
        // IMPORTANTE: Solo crear si tenemos el sufijo configurado en EminusApp.
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false

        // Crear un WebView temporal ultraligero solo para capturar la URL del blob
        val capturer = WebView(this)
        capturer.settings.javaScriptEnabled = true

        capturer.webViewClient = object : WebViewClient() {
            private var handled = false

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (handled) return true
                val url = request?.url?.toString() ?: return false
                Log.d("EMINUS_POPUP", "capturer URL capturada: $url")
                handled = true
                view?.stopLoading()
                runOnUiThread {
                    viewerWebContainer.removeView(capturer)
                    capturer.destroy()
                    manejarUrlVisor(url)
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                val u = url ?: ""
                if (handled || u.isBlank() || u == "about:blank") return
                if (u.startsWith("blob:") || u.startsWith("http")) {
                    Log.d("EMINUS_POPUP", "capturer onPageStarted URL: $u")
                    handled = true
                    view?.stopLoading()
                    runOnUiThread {
                        viewerWebContainer.removeView(capturer)
                        capturer.destroy()
                        manejarUrlVisor(u)
                    }
                }
            }
        }

        // Adjuntar al layout — necesario para que el WebView engine inicialice correctamente
        viewerWebContainer.addView(
            capturer,
            FrameLayout.LayoutParams(1, 1)  // 1x1px, invisible
        )

        transport.webView = capturer
        resultMsg.sendToTarget()
        return true
    }

    /** Decide cómo abrir la URL capturada: blob → leer JS, http → popup WebView */
    private fun manejarUrlVisor(url: String) {
        Log.d("EMINUS_POPUP", "manejarUrlVisor: $url")
        when {
            url.startsWith("blob:") -> leerBlobDesdePadre(url)
            url.startsWith("http://") || url.startsWith("https://") -> {
                if (interceptarArchivoEnVisor(null, url)) return
                abrirPopupReal(url)
            }
            else -> Log.w("EMINUS_POPUP", "URL ignorada: $url")
        }
    }

    /** Abre el visor WebView real para una URL http/https normal */
    private fun abrirPopupReal(url: String) {
        val popup = crearPopupWebView()
        aplicarAjustesDeVisor(popup)
        viewerWebContainer.removeAllViews()
        viewerWebContainer.addView(
            popup,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        popupWebView = popup
        mostrarOverlayWeb()
        popup.loadUrl(url)
    }

    /**
     * Inyecta JS en el WebView PADRE (que posee el blob en memoria) para leer el blob
     * como ArrayBuffer, convertirlo a base64 y enviarlo a Kotlin via evaluateJavascript callback.
     */
    private fun leerBlobDesdePadre(blobUrl: String) {
        // Inyecta XHR en el WebView padre para leer el blob (que vive en su memoria JS)
        // y almacenarlo en una variable global. No se puede usar Promise directamente
        // con evaluateJavascript porque el resultado sería "[object Promise]".
        val jsCb = """
            (function() {
                var blobUrl = '$blobUrl';
                window.__eminusBlobResult = null;
                var xhr = new XMLHttpRequest();
                xhr.open('GET', blobUrl, true);
                xhr.responseType = 'blob';
                xhr.onload = function() {
                    var reader = new FileReader();
                    reader.onloadend = function() {
                        window.__eminusBlobResult = reader.result || '';
                    };
                    reader.readAsDataURL(xhr.response);
                };
                xhr.onerror = function() { window.__eminusBlobResult = ''; };
                xhr.send();
                return 'iniciado';
            })();
        """.trimIndent()

        runOnUiThread {
            viewerProgress.visibility = View.VISIBLE
            mostrarOverlayWeb()
            webView.evaluateJavascript(jsCb) { _ ->
                // Polling cada 500ms hasta que el FileReader haya terminado (máx 15 s)
                esperarBlobResultado(intentos = 0, maxIntentos = 30)
            }
        }
    }

    /** Sondea window.__eminusBlobResult cada 500 ms hasta que esté listo o se agote el tiempo */
    private fun esperarBlobResultado(intentos: Int, maxIntentos: Int) {
        webView.postDelayed({
            webView.evaluateJavascript("window.__eminusBlobResult") { raw ->
                val valor = raw?.trim('"') ?: "null"
                Log.d("EMINUS_POPUP", "Poll blob intento=${intentos+1} valor_len=${valor.length}")

                when {
                    valor.startsWith("data:") && valor.contains(";base64,") -> {
                        // Blob listo
                        viewerProgress.visibility = View.GONE
                        procesarBlobDataUrl(valor)
                        webView.evaluateJavascript("window.__eminusBlobResult = null", null)
                    }
                    valor == "" || valor == "null" && intentos + 1 < maxIntentos -> {
                        // Todavía no está listo → reintentar
                        esperarBlobResultado(intentos + 1, maxIntentos)
                    }
                    else -> {
                        // Tiempo agotado o error
                        viewerProgress.visibility = View.GONE
                        cerrarVisor()
                        Toast.makeText(this, "No se pudo leer el archivo. Intenta desde el navegador.", Toast.LENGTH_LONG).show()
                        webView.evaluateJavascript("window.__eminusBlobResult = null", null)
                    }
                }
            }
        }, 500)
    }

    /** Decodifica el data URL (data:mime;base64,...), guarda el archivo y lo abre */
    private fun procesarBlobDataUrl(dataUrl: String) {
        thread {
            try {
                val commaIdx = dataUrl.indexOf(',') 
                if (commaIdx < 0) throw IllegalArgumentException("data URL inválido")
                val header = dataUrl.substring(0, commaIdx)  // "data:application/pdf;base64"
                val b64 = dataUrl.substring(commaIdx + 1)
                val mime = header.substringAfter("data:").substringBefore(";")

                val ext = when {
                    mime.contains("pdf") -> ".pdf"
                    mime.contains("word") || mime.contains("docx") -> ".docx"
                    mime.contains("spreadsheet") || mime.contains("xlsx") -> ".xlsx"
                    mime.contains("presentation") || mime.contains("pptx") -> ".pptx"
                    mime.contains("jpeg") || mime.contains("jpg") -> ".jpg"
                    mime.contains("png") -> ".png"
                    else -> ".bin"
                }

                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                val storageDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
                val fileName = "eminus_blob_${System.currentTimeMillis()}$ext"
                val file = File(storageDir, fileName)
                file.writeBytes(bytes)

                Log.d("EMINUS_POPUP", "Blob guardado: ${file.absolutePath} (${bytes.size} bytes) mime=$mime")
                guardarEnDescargasPublicas(file, fileName, mime)

                runOnUiThread {
                    cerrarVisor()
                    if (mime.contains("pdf") || parecePdf(file)) {
                        mostrarPdfEnApp(file)
                    } else {
                        abrirArchivoLocal(file, mime)
                    }
                }
            } catch (e: Exception) {
                Log.e("EMINUS_POPUP", "Error procesando blob", e)
                runOnUiThread {
                    cerrarVisor()
                    Toast.makeText(this, "No se pudo abrir el archivo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun aplicarAjustesDeVisor(target: WebView) {
        val settings = target.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.userAgentString = userAgentSinWebView()
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        CookieManager.getInstance().setAcceptThirdPartyCookies(target, true)
        target.addJavascriptInterface(BlobDownloadBridge(), "AndroidDownloadBridge")

        target.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase() ?: ""
                val dest = uri.toString()
                Log.d("EMINUS_POPUP", "visor nav: $dest")

                if (scheme == "http" || scheme == "https") {
                    // Interceptar archivos Office y Office Online: descargar y abrir con app del sistema
                    // para evitar OOM del renderer al cargar Office Online dentro del proceso.
                    if (interceptarArchivoEnVisor(view, dest)) return true
                }

                if (scheme != "http" && scheme != "https" && scheme != "about") {
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                // onPageStarted también se llama para la PRIMERA URL del popup
                // (la navegación inicial vía transport NO pasa por shouldOverrideUrlLoading).
                // Es el único punto donde podemos interceptar esa carga inicial.
                if (url != null && interceptarArchivoEnVisor(view, url)) {
                    view?.stopLoading()
                    return
                }
                viewerProgress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                viewerProgress.visibility = View.GONE
                CookieManager.getInstance().flush()
            }

            // CRÍTICO: Sin este override, cuando el renderer del WebView muere por OOM
            // (p.ej. al cargar Office Online con un Word/PPT pesado), la app crashea
            // inmediatamente en Android 8+. Con él, tomamos el control y mostramos un mensaje.
            @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.O)
            override fun onRenderProcessGone(
                view: WebView?,
                detail: android.webkit.RenderProcessGoneDetail?
            ): Boolean {
                Log.e("EMINUS_VIEWER", "Renderer del visor caído: crash=${detail?.didCrash()}")
                runOnUiThread {
                    cerrarVisor()
                    Toast.makeText(
                        this@MainActivity,
                        "El visor no pudo cargar el archivo. Descárgalo para abrirlo.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return true // true = manejado, la app NO crashea
            }
        }

        target.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                viewerProgress.visibility = View.VISIBLE
                viewerProgress.progress = newProgress
                if (newProgress >= 100) {
                    viewerProgress.visibility = View.GONE
                }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                return abrirPopupEnVisor(resultMsg)
            }

            override fun onCloseWindow(window: WebView?) {
                cerrarVisor()
            }
        }

        target.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            manejarDescargaOVisor(url, userAgent, contentDisposition, mimetype)
        }
    }

    private fun abrirEnVisorWeb(url: String) {
        Log.d("EMINUS_POPUP", "abrirEnVisorWeb: $url")

        // Sincronizar cookies al disco antes de cargar el popup
        CookieManager.getInstance().flush()

        val existente = popupWebView
        if (existente != null && visorActivo() && pdfScroll.visibility != View.VISIBLE) {
            existente.loadUrl(url)
            mostrarOverlayWeb()
            return
        }

        // Destruir visor previo antes de crear uno nuevo
        existente?.apply {
            stopLoading()
            loadUrl("about:blank")
            destroy()
        }
        popupWebView = null
        viewerWebContainer.removeAllViews()

        val popup = crearPopupWebView()
        aplicarAjustesDeVisor(popup)
        viewerWebContainer.addView(
            popup,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        popupWebView = popup
        mostrarOverlayWeb()
        popup.loadUrl(url)
    }

    private fun mostrarOverlayWeb() {
        txtTituloVisor.text = "Ver en línea"
        pdfScroll.visibility = View.GONE
        viewerWebContainer.visibility = View.VISIBLE
        viewerOverlay.visibility = View.VISIBLE
    }

    private fun mostrarOverlayPdf(nombre: String) {
        txtTituloVisor.text = nombre
        viewerWebContainer.visibility = View.GONE
        pdfScroll.visibility = View.VISIBLE
        viewerOverlay.visibility = View.VISIBLE
    }

    private fun cerrarVisor() {
        viewerOverlay.visibility = View.GONE
        viewerProgress.visibility = View.GONE
        pdfScroll.visibility = View.GONE
        viewerWebContainer.visibility = View.VISIBLE
        popupWebView?.apply {
            stopLoading()
            loadUrl("about:blank")
            destroy()
        }
        popupWebView = null
        viewerWebContainer.removeAllViews()
        limpiarPdf()
    }

    private fun limpiarPdf() {
        pdfPages.removeAllViews()
        pdfBitmaps.forEach { it.recycle() }
        pdfBitmaps.clear()
    }

    private fun mostrarPdfEnApp(file: File) {
        thread {
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val anchoPantalla = resources.displayMetrics.widthPixels.coerceAtLeast(1)
                val paginas = mutableListOf<Bitmap>()
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val scale = anchoPantalla.toFloat() / page.width.toFloat()
                    val w = (page.width * scale).toInt().coerceAtLeast(1)
                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    paginas.add(bitmap)
                    page.close()
                }
                renderer.close()
                pfd.close()

                runOnUiThread {
                    limpiarPdf()
                    pdfBitmaps.addAll(paginas)
                    paginas.forEach { bmp ->
                        val image = ImageView(this).apply {
                            adjustViewBounds = true
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setImageBitmap(bmp)
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        }
                        pdfPages.addView(image)
                    }
                    mostrarOverlayPdf(file.name)
                }
            } catch (e: Exception) {
                Log.e("EMINUS_VIEWER", "No se pudo renderizar PDF: ${e.message}", e)
                runOnUiThread {
                    // Cerrar overlay en blanco y abrir con el visor del sistema como fallback.
                    // Antes solo se mostraba un Toast y el overlay quedaba abierto y vacío.
                    cerrarVisor()
                    Toast.makeText(this, "Abriendo PDF con el visor del sistema...", Toast.LENGTH_SHORT).show()
                    abrirArchivoLocal(file, "application/pdf")
                }
            }
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

            inyectarInterceptorDeBlobs(webView)
        } catch (e: Exception) {
            Log.e("EMINUS_STYLE", "Error al inyectar CSS", e)
        }
    }

    /**
     * Instala interceptores globales recursivos en window y en todos los iframes para:
     * 1. Capturar objetos Blob sincrónicamente en URL.createObjectURL y pre-convertirlos en background
     * 2. Retrasar la revocación en URL.revokeObjectURL 60 segundos con Object.defineProperty
     * 3. Capturar el atributo 'download' y despachar inmediatamente a AndroidDownloadBridge en a.click()
     * 4. Escaneo continuo cada 250 ms y evento 'load' global para cubrir cualquier iframe dinámico SPA
     */
    private fun inyectarInterceptorDeBlobs(view: WebView?) {
        val script = """
            (function() {
                try {
                    window.__eminusBlobs = window.__eminusBlobs || {};
                    window.__eminusBlobNames = window.__eminusBlobNames || {};
                    window.__eminusDataUrls = window.__eminusDataUrls || {};

                    function hookWindow(w) {
                        try {
                            if (!w) return;
                            var urlObj = w.URL || w.webkitURL;
                            if (urlObj && !urlObj.__eminusHooked) {
                                urlObj.__eminusHooked = true;
                                var origCreate = urlObj.createObjectURL.bind(urlObj);
                                var origRevoke = urlObj.revokeObjectURL.bind(urlObj);

                                try {
                                    Object.defineProperty(urlObj, 'createObjectURL', {
                                        configurable: true,
                                        writable: true,
                                        value: function(obj) {
                                            var url = origCreate(obj);
                                            try {
                                                w.__eminusBlobs = w.__eminusBlobs || {};
                                                w.__eminusBlobs[url] = obj;
                                                if (window.top) {
                                                    window.top.__eminusBlobs = window.top.__eminusBlobs || {};
                                                    window.top.__eminusBlobs[url] = { blob: obj, win: w };
                                                }
                                                if (obj instanceof Blob) {
                                                    var reader = new FileReader();
                                                    reader.onloadend = function() {
                                                        if (reader.result) {
                                                            if (window.top) {
                                                                window.top.__eminusDataUrls = window.top.__eminusDataUrls || {};
                                                                window.top.__eminusDataUrls[url] = reader.result;
                                                            }
                                                            w.__eminusDataUrls = w.__eminusDataUrls || {};
                                                            w.__eminusDataUrls[url] = reader.result;
                                                        }
                                                    };
                                                    reader.readAsDataURL(obj);
                                                }
                                            } catch(e) {}
                                            return url;
                                        }
                                    });
                                } catch(e) {
                                    urlObj.createObjectURL = function(obj) {
                                        var url = origCreate(obj);
                                        try {
                                            w.__eminusBlobs = w.__eminusBlobs || {};
                                            w.__eminusBlobs[url] = obj;
                                            if (window.top) {
                                                window.top.__eminusBlobs = window.top.__eminusBlobs || {};
                                                window.top.__eminusBlobs[url] = { blob: obj, win: w };
                                            }
                                        } catch(err) {}
                                        return url;
                                    };
                                }

                                try {
                                    Object.defineProperty(urlObj, 'revokeObjectURL', {
                                        configurable: true,
                                        writable: true,
                                        value: function(url) {
                                            setTimeout(function() {
                                                try { origRevoke(url); } catch(e) {}
                                            }, 60000);
                                        }
                                    });
                                } catch(e) {
                                    urlObj.revokeObjectURL = function(url) {
                                        setTimeout(function() {
                                            try { origRevoke(url); } catch(e) {}
                                        }, 60000);
                                    };
                                }
                            }

                            if (w.HTMLAnchorElement && w.HTMLAnchorElement.prototype && !w.HTMLAnchorElement.prototype.__eminusClickHooked) {
                                w.HTMLAnchorElement.prototype.__eminusClickHooked = true;
                                var origClick = w.HTMLAnchorElement.prototype.click;
                                w.HTMLAnchorElement.prototype.click = function() {
                                    try {
                                        var href = this.href || this.getAttribute('href') || '';
                                        var dl = this.download || this.getAttribute('download') || '';
                                        if (href && href.indexOf('blob:') === 0) {
                                            if (window.top) {
                                                window.top.__eminusBlobNames = window.top.__eminusBlobNames || {};
                                                window.top.__eminusBlobNames[href] = dl;
                                            }
                                            w.__eminusBlobNames = w.__eminusBlobNames || {};
                                            w.__eminusBlobNames[href] = dl;
                                        }
                                    } catch(e) {}
                                    return origClick.apply(this, arguments);
                                };
                            }

                            if (w.document && !w.document.__eminusClickDocHooked) {
                                w.document.__eminusClickDocHooked = true;
                                w.document.addEventListener('click', function(e) {
                                    var el = e.target;
                                    while (el && el.tagName !== 'A') {
                                        el = el.parentElement;
                                    }
                                    if (el) {
                                        var href = el.href || el.getAttribute('href') || '';
                                        var dl = el.download || el.getAttribute('download') || '';
                                        if (href && href.indexOf('blob:') === 0) {
                                            if (window.top) {
                                                window.top.__eminusBlobNames = window.top.__eminusBlobNames || {};
                                                window.top.__eminusBlobNames[href] = dl;
                                            }
                                            w.__eminusBlobNames = w.__eminusBlobNames || {};
                                            w.__eminusBlobNames[href] = dl;
                                        }
                                    }
                                }, true);
                            }
                        } catch(e) {}
                    }

                    function scanAll(root) {
                        try {
                            if (!root) return;
                            hookWindow(root);
                            if (!root.document) return;
                            var iframes = root.document.querySelectorAll('iframe');
                            for (var i = 0; i < iframes.length; i++) {
                                try {
                                    scanAll(iframes[i].contentWindow);
                                } catch(e) {}
                            }
                        } catch(e) {}
                    }

                    scanAll(window);

                    if (!window.__eminusScannerActive) {
                        window.__eminusScannerActive = true;
                        setInterval(function() {
                            scanAll(window);
                        }, 250);

                        window.addEventListener('load', function(e) {
                            if (e.target && e.target.tagName === 'IFRAME') {
                                scanAll(window);
                            }
                        }, true);
                    }
                } catch(e) {}
            })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
    }


    /**
     * Descarga el archivo de forma nativa e independiente usando las cookies de sesión
     * y lo abre de inmediato en el visor del sistema (Drive, Mi Doc Viewer, etc.).
     */
    private fun descargarYAbrirArchivo(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimetype: String,
        abrirDentroDeLaApp: Boolean = true
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
                        if (abrirDentroDeLaApp && (esPdf(file.name, mimetype, contentDisposition) || parecePdf(file))) {
                            // PDF: renderizar páginas dentro de la app
                            mostrarPdfEnApp(file)
                        } else if (abrirDentroDeLaApp) {
                            // Word, Excel, PPT u otro: abrir con la app del sistema (Drive, WPS, etc.)
                            abrirArchivoLocal(file, mimetype)
                        } else {
                            abrirArchivoLocal(file, mimetype)
                        }
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
