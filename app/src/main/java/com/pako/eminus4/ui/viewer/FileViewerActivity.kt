package com.pako.eminus4.ui.viewer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.webkit.WebViewAssetLoader
import androidx.core.content.ContextCompat
import coil.load
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.pako.eminus4.R
import com.pako.eminus4.core.SessionManager
import com.pako.eminus4.data.api.EminusApiClient
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class FileViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_URL        = "extra_file_url"
        const val EXTRA_FILE_NAME       = "extra_file_name"
        const val EXTRA_FILE_SIZE       = "extra_file_size"
        const val EXTRA_ACTIVITY_TITLE  = "extra_activity_title"

        fun start(context: Context, url: String, fileName: String, fileSize: String? = null, activityTitle: String? = null) {
            val intent = Intent(context, FileViewerActivity::class.java).apply {
                putExtra(EXTRA_FILE_URL, url)
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_FILE_SIZE, fileSize)
                putExtra(EXTRA_ACTIVITY_TITLE, activityTitle)
            }
            context.startActivity(intent)
        }
    }

    private lateinit var apiClient: EminusApiClient
    private var fileUrl: String = ""
    private var fileName: String = ""
    private var fileSize: String? = null
    private var activityTitle: String? = null

    private lateinit var toolbar: MaterialToolbar
    private lateinit var layoutLoading: LinearLayout
    private lateinit var layoutPdfViewer: FrameLayout
    private lateinit var recyclerPdfPages: RecyclerView
    private lateinit var txtPageIndicator: TextView
    private lateinit var layoutImageViewer: FrameLayout
    private lateinit var imgFullScreen: ImageView
    private lateinit var layoutTextViewer: View
    private lateinit var txtFileContent: TextView
    private lateinit var webOfficeViewer: WebView
    private lateinit var layoutOfficeViewer: LinearLayout
    private lateinit var txtOfficeEmoji: TextView
    private lateinit var txtOfficeFileName: TextView
    private lateinit var txtOfficeFileInfo: TextView
    private lateinit var btnOpenOfficeExternal: MaterialButton
    private lateinit var btnSaveOfficeCopy: MaterialButton
    private lateinit var layoutError: LinearLayout
    private lateinit var txtErrorMessage: TextView
    private lateinit var btnRetry: MaterialButton

    private var localFile: File? = null
    private var pdfRenderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null
    private var pdfAdapter: PdfPageAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_viewer)

        fileUrl       = intent.getStringExtra(EXTRA_FILE_URL) ?: ""
        fileName      = intent.getStringExtra(EXTRA_FILE_NAME) ?: "Documento"
        fileSize      = intent.getStringExtra(EXTRA_FILE_SIZE)
        activityTitle = intent.getStringExtra(EXTRA_ACTIVITY_TITLE)

        if (fileUrl.isBlank()) {
            Toast.makeText(this, "Enlace de archivo no válido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        apiClient = EminusApiClient(SessionManager(this))

        inicializarVistas()
        prepararArchivo()
    }

    private fun inicializarVistas() {
        toolbar               = findViewById(R.id.toolbar)
        val appBarLayout      = findViewById<View>(R.id.appBarLayout)
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        setSupportActionBar(toolbar)
        toolbar.title = fileName
        toolbar.subtitle = fileSize ?: activityTitle ?: "Visualizador en línea"
        toolbar.setNavigationOnClickListener { finish() }

        layoutLoading         = findViewById(R.id.layoutLoading)
        layoutPdfViewer       = findViewById(R.id.layoutPdfViewer)
        recyclerPdfPages      = findViewById(R.id.recyclerPdfPages)
        txtPageIndicator      = findViewById(R.id.txtPageIndicator)
        layoutImageViewer     = findViewById(R.id.layoutImageViewer)
        imgFullScreen         = findViewById(R.id.imgFullScreen)
        layoutTextViewer      = findViewById(R.id.layoutTextViewer)
        txtFileContent        = findViewById(R.id.txtFileContent)
        webOfficeViewer       = findViewById(R.id.webOfficeViewer)
        layoutOfficeViewer    = findViewById(R.id.layoutOfficeViewer)

        webOfficeViewer.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        webOfficeViewer.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_screen))
        txtOfficeEmoji        = findViewById(R.id.txtOfficeEmoji)
        txtOfficeFileName     = findViewById(R.id.txtOfficeFileName)
        txtOfficeFileInfo     = findViewById(R.id.txtOfficeFileInfo)
        btnOpenOfficeExternal = findViewById(R.id.btnOpenOfficeExternal)
        btnSaveOfficeCopy     = findViewById(R.id.btnSaveOfficeCopy)
        layoutError           = findViewById(R.id.layoutError)
        txtErrorMessage       = findViewById(R.id.txtErrorMessage)
        btnRetry              = findViewById(R.id.btnRetry)

        btnRetry.setOnClickListener {
            prepararArchivo()
        }

        btnOpenOfficeExternal.setOnClickListener {
            localFile?.let { abrirConAplicacionExterna(it) }
        }

        btnSaveOfficeCopy.setOnClickListener {
            localFile?.let { guardarCopiaEnDescargas(it) }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_file_viewer, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_share -> {
                localFile?.let { compartirArchivo(it) }
                true
            }
            R.id.action_download -> {
                localFile?.let { guardarCopiaEnDescargas(it) }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun prepararArchivo() {
        ocultarTodosLosContenedores()
        layoutLoading.visibility = View.VISIBLE

        // Nombre seguro para caché evitando caracteres no válidos
        val sanitized = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val cacheFolder = File(cacheDir, "temp_docs")
        if (!cacheFolder.exists()) cacheFolder.mkdirs()
        val targetFile = File(cacheFolder, sanitized)

        if (targetFile.exists() && targetFile.length() > 0) {
            localFile = targetFile
            renderizarArchivo(targetFile)
            return
        }

        apiClient.downloadFile(fileUrl, targetFile) { result ->
            layoutLoading.visibility = View.GONE
            result.onSuccess { downloadedFile ->
                localFile = downloadedFile
                renderizarArchivo(downloadedFile)
            }.onFailure { error ->
                mostrarError("No se pudo cargar el archivo: ${error.localizedMessage}")
            }
        }
    }

    private fun renderizarArchivo(file: File) {
        ocultarTodosLosContenedores()
        val ext = file.name.substringAfterLast(".", "").lowercase()

        when (ext) {
            "pdf" -> renderizarPdf(file)
            "png", "jpg", "jpeg", "webp", "gif" -> renderizarImagen(file)
            "txt", "json", "csv", "xml", "log", "md", "sql" -> renderizarTexto(file)
            else -> renderizarOfficeOAlternativo(file, ext)
        }
    }

    private fun renderizarPdf(file: File) {
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd!!)
            pdfRenderer = renderer

            layoutPdfViewer.visibility = View.VISIBLE
            txtPageIndicator.text = "Páginas: ${renderer.pageCount}"

            val screenWidth = resources.displayMetrics.widthPixels
            pdfAdapter = PdfPageAdapter(renderer, screenWidth)

            recyclerPdfPages.layoutManager = LinearLayoutManager(this)
            recyclerPdfPages.adapter = pdfAdapter

            recyclerPdfPages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    val firstPos = lm.findFirstVisibleItemPosition()
                    if (firstPos != RecyclerView.NO_POSITION) {
                        txtPageIndicator.text = "Página ${firstPos + 1} de ${renderer.pageCount}"
                    }
                }
            })
        } catch (e: Exception) {
            mostrarError("No se pudo procesar el PDF: ${e.localizedMessage}")
        }
    }

    private fun renderizarImagen(file: File) {
        layoutImageViewer.visibility = View.VISIBLE
        imgFullScreen.load(file) {
            crossfade(true)
        }
    }

    private fun renderizarTexto(file: File) {
        try {
            val content = file.readText(Charsets.UTF_8)
            layoutTextViewer.visibility = View.VISIBLE
            txtFileContent.text = content
        } catch (e: Exception) {
            mostrarError("Error al leer texto: ${e.localizedMessage}")
        }
    }

    private var assetLoader: WebViewAssetLoader? = null

    private fun buildAssetLoader(cacheFolder: File): WebViewAssetLoader {
        return WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler(
                "/assets/",
                WebViewAssetLoader.AssetsPathHandler(this)
            )
            .addPathHandler(
                "/files/",
                WebViewAssetLoader.InternalStoragePathHandler(this, cacheFolder)
            )
            .build()
    }

    private fun renderizarOfficeOAlternativo(file: File, ext: String) {
        val cacheFolder = File(cacheDir, "temp_docs")
        if (!cacheFolder.exists()) cacheFolder.mkdirs()

        val encodedName    = Uri.encode(file.name)
        val fileServingUrl = "https://appassets.androidplatform.net/files/$encodedName"
        val viewerBase     = "https://appassets.androidplatform.net/assets/viewer/"

        when (ext) {
            "docx" -> {
                // DOCX: intentar abrir en Word/Google Docs primero.
                // Si no hay app, caer al visor HTML interno.
                val uri = try {
                    androidx.core.content.FileProvider.getUriForFile(
                        this, "${applicationContext.packageName}.provider", file
                    )
                } catch (_: Exception) { null }

                val launched = if (uri != null) {
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                        true
                    } catch (_: android.content.ActivityNotFoundException) { false }
                      catch (_: Exception) { false }
                } else false

                if (!launched) {
                    // Fallback: visor HTML interno
                    cargarVisorHtml(file, "docx_viewer.html", fileServingUrl, viewerBase)
                } else {
                    // La Activity externa se encarga — cerramos el loading
                    ocultarTodosLosContenedores()
                    finish()
                }
            }
            "pptx" -> {
                cargarVisorHtml(file, "pptx_viewer.html", fileServingUrl, viewerBase)
            }
            "doc", "ppt", "xls", "xlsx" -> {
                val html = OfficeDocumentRenderer.renderLegacyOfficeToHtml(file)
                webOfficeViewer.visibility = View.VISIBLE
                webOfficeViewer.loadDataWithBaseURL(
                    "https://eminus.uv.mx", html, "text/html", "UTF-8", null
                )
            }
            else -> {
                layoutOfficeViewer.visibility = View.VISIBLE
                val (emoji, typeDesc) = when (ext) {
                    "zip", "rar", "7z" -> Pair("📦", "Archivo comprimido")
                    else               -> Pair("📎", "Archivo ($ext)")
                }
                txtOfficeEmoji.text    = emoji
                txtOfficeFileName.text = file.name
                txtOfficeFileInfo.text = "$fileSize · $typeDesc"
            }
        }
    }

    private fun cargarVisorHtml(file: File, htmlFile: String, fileServingUrl: String, viewerBase: String) {
        val cacheFolder = File(cacheDir, "temp_docs")
        val loader = buildAssetLoader(cacheFolder)
        assetLoader = loader

        webOfficeViewer.visibility = View.VISIBLE
        webOfficeViewer.settings.apply {
            javaScriptEnabled     = true
            domStorageEnabled     = true
            allowFileAccess       = false
            allowContentAccess    = false
            builtInZoomControls   = true
            displayZoomControls   = false
            useWideViewPort       = true
            loadWithOverviewMode  = true
            cacheMode             = android.webkit.WebSettings.LOAD_NO_CACHE
        }

        webOfficeViewer.webViewClient = object : android.webkit.WebViewClient() {
            override fun shouldInterceptRequest(
                view: android.webkit.WebView,
                request: android.webkit.WebResourceRequest
            ) = loader.shouldInterceptRequest(request.url)
                ?: super.shouldInterceptRequest(view, request)

            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                view?.evaluateJavascript("renderFile('$fileServingUrl');", null)
            }
        }

        webOfficeViewer.loadUrl("${viewerBase}$htmlFile")
    }

    private fun abrirConAplicacionExterna(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
            val mime = obtenerMimeType(file.name)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No tienes una aplicación instalada para abrir este tipo de documento.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al abrir: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun compartirArchivo(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
            val mime = obtenerMimeType(file.name)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Compartir recurso institucional…"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error al compartir: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun guardarCopiaEnDescargas(file: File) {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destination = File(downloadsDir, file.name)

            FileInputStream(file).use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }

            MediaScannerConnection.scanFile(this, arrayOf(destination.absolutePath), null, null)
            Toast.makeText(this, "Guardado en Descargas: ${destination.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo guardar copia: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun obtenerMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast(".", "").lowercase()
        return when (ext) {
            "pdf"        -> "application/pdf"
            "doc"        -> "application/msword"
            "docx"       -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "ppt"        -> "application/vnd.ms-powerpoint"
            "pptx"       -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "xls"        -> "application/vnd.ms-excel"
            "xlsx"       -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "png"        -> "image/png"
            "jpg", "jpeg"-> "image/jpeg"
            "webp"       -> "image/webp"
            "txt"        -> "text/plain"
            "json"       -> "application/json"
            "zip"        -> "application/zip"
            else         -> "*/*"
        }
    }

    private fun ocultarTodosLosContenedores() {
        layoutLoading.visibility      = View.GONE
        layoutPdfViewer.visibility    = View.GONE
        layoutImageViewer.visibility  = View.GONE
        layoutTextViewer.visibility   = View.GONE
        webOfficeViewer.visibility    = View.GONE
        layoutOfficeViewer.visibility = View.GONE
        layoutError.visibility        = View.GONE
    }

    private fun mostrarError(mensaje: String) {
        ocultarTodosLosContenedores()
        layoutError.visibility = View.VISIBLE
        txtErrorMessage.text = mensaje
    }

    override fun onDestroy() {
        super.onDestroy()
        pdfAdapter?.release()
        try {
            pdfRenderer?.close()
            pfd?.close()
        } catch (_: Exception) {}
    }
}

