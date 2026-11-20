package com.pako.eminus4.ui.profile

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.pako.eminus4.R
import com.pako.eminus4.core.SessionManager
import com.pako.eminus4.data.model.StudentCredential
import com.pako.eminus4.util.BarcodeGenerator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CredentialFullscreenActivity : AppCompatActivity() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, CredentialFullscreenActivity::class.java)
            context.startActivity(intent)
        }
    }

    private lateinit var sessionManager: SessionManager
    private val clockHandler = Handler(Looper.getMainLooper())
    private lateinit var txtLiveClock: TextView
    private val clockFormat = SimpleDateFormat("dd/MMM/yyyy HH:mm:ss", Locale("es", "MX"))

    private val clockRunnable = object : Runnable {
        override fun run() {
            if (::txtLiveClock.isInitialized) {
                txtLiveClock.text = clockFormat.format(Date()).lowercase(Locale("es", "MX"))
            }
            clockHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar barra de estado en tono azul cielo que combina con el gradiente oficial
        configurarBarraEstado()

        setContentView(R.layout.activity_credential_fullscreen)

        // Brillo máximo en pantalla para lectura institucional y escáneres
        ajustarBrilloMaximo()

        sessionManager = SessionManager(this)

        txtLiveClock = findViewById(R.id.txtLiveClock)

        cargarFondoAnimado()
        cargarDatosCredencial()
        iniciarRelojOficial()
    }

    /** GIF oficial de MiUV empaquetado en res/raw (funciona sin internet). */
    private fun cargarFondoAnimado() {
        findViewById<ImageView>(R.id.imgFondoAnimado).load(R.raw.credencial_fondo_animado) {
            decoderFactory(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) coil.decode.ImageDecoderDecoder.Factory()
                else coil.decode.GifDecoder.Factory()
            )
        }
    }

    private fun configurarBarraEstado() {
        try {
            window.statusBarColor = Color.parseColor("#BEE5F3")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        } catch (_: Exception) {}
    }

    private fun ajustarBrilloMaximo() {
        try {
            val layoutParams = window.attributes
            layoutParams.screenBrightness = 1.0f
            window.attributes = layoutParams
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (_: Exception) {}
    }

    private fun iniciarRelojOficial() {
        txtLiveClock.text = clockFormat.format(Date()).lowercase(Locale("es", "MX"))
        clockHandler.post(clockRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockRunnable)
    }

    private fun cargarDatosCredencial() {
        // Solo la credencial de quien tiene la sesión; si no hay, se arma con SUS datos
        val cred: StudentCredential = sessionManager.getCredencialDelUsuarioActual() ?: run {
            val profile = sessionManager.getUserProfile()
            val matricula = sessionManager.getMatriculaSesion() ?: profile.userId.uppercase()
            StudentCredential(
                user = matricula,
                name = profile.getNombreCompleto(),
                program = "",
                region = profile.userCampus,
                monthsValidity = "AGO-ENE",
                yearValidity = "2027",
                photo = null,
                qrCode = "https://dsia.uv.mx/credencial/validar?u=$matricula",
                barCode = matricula
            )
        }

        // Si la credencial aún no trae nombre, usar el de la sesión (nunca dejarlo vacío)
        val credConNombre = if (cred.name.isBlank())
            cred.copy(name = sessionManager.getUserProfile().getNombreCompleto()) else cred
        findViewById<TextView>(R.id.txtFullStudentName).text = credConNombre.getNombreFormateado()
        findViewById<TextView>(R.id.txtFullStudentId).text = cred.getMatriculaOficial()
        findViewById<TextView>(R.id.txtProgram).text = cred.getProgramaOficial().ifBlank { "Programa no registrado" }
        findViewById<TextView>(R.id.txtRegion).text = cred.getCampusOficial().ifBlank { "Campus no registrado" }
        findViewById<TextView>(R.id.txtValidity).text = cred.getVigenciaTexto()

        // Cargar fotografía oficial institucional (MIUV, no Eminus avatar)
        val imgPhoto = findViewById<ImageView>(R.id.imgStudentPhoto)
        val fotoValida = cred.getFotoValida()
        if (fotoValida is String) {
            imgPhoto.load(fotoValida) {
                crossfade(true)
                placeholder(R.drawable.foto_credencial_oficial)
                error(R.drawable.foto_credencial_oficial)
            }
        } else {
            imgPhoto.setImageResource(fotoValida as Int)
        }

        // Generar y cargar QR azul institucional con logo UV en el centro
        val matriculaOficial = cred.getMatriculaOficial()
        val qrContent = cred.qrCode ?: "https://dsia.uv.mx/credencial/validar?u=$matriculaOficial"
        val centerLogo = BitmapFactory.decodeResource(resources, R.drawable.logo_uv_qr_center)
        val qrBitmap: Bitmap? = BarcodeGenerator.generateQrBitmap(
            content = qrContent,
            size = 540,
            qrColor = Color.parseColor("#00348A"),
            centerLogo = centerLogo
        )
        val imgQr = findViewById<ImageView>(R.id.imgQrCode)
        if (qrBitmap != null) {
            imgQr.setImageBitmap(qrBitmap)
        }
    }
}
