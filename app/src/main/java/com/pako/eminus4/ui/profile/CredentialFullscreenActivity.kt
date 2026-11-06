package com.pako.eminus4.ui.profile

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.appbar.MaterialToolbar
import com.pako.eminus4.R
import com.pako.eminus4.core.SessionManager
import com.pako.eminus4.data.model.StudentCredential
import com.pako.eminus4.util.BarcodeGenerator

class CredentialFullscreenActivity : AppCompatActivity() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, CredentialFullscreenActivity::class.java)
            context.startActivity(intent)
        }
    }

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_credential_fullscreen)

        // Brillo máximo en pantalla para que los lectores de código de barras/QR funcionen perfectamente
        ajustarBrilloMaximo()

        sessionManager = SessionManager(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        cargarDatosCredencial()
    }

    private fun ajustarBrilloMaximo() {
        try {
            val layoutParams = window.attributes
            layoutParams.screenBrightness = 1.0f
            window.attributes = layoutParams
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (_: Exception) {}
    }

    private fun cargarDatosCredencial() {
        val cred: StudentCredential = sessionManager.getStudentCredential() ?: run {
            val profile = sessionManager.getUserProfile()
            StudentCredential(
                user = profile.userId.ifBlank { "zs00000000" },
                name = profile.getNombreCompleto(),
                program = "Estudiante Activo",
                region = profile.userCampus.ifBlank { "Universidad Veracruzana" },
                monthsValidity = "FEB - JUL",
                yearValidity = "2026",
                photo = profile.getFullAvatarUrl(),
                qrCode = "https://dsia.uv.mx/credencial/validar?u=${profile.userId}",
                barCode = profile.userId
            )
        }

        findViewById<TextView>(R.id.txtFullStudentName).text = cred.getNombreCompleto()
        findViewById<TextView>(R.id.txtFullStudentId).text = cred.user.uppercase()
        findViewById<TextView>(R.id.txtProgram).text = cred.program
        findViewById<TextView>(R.id.txtRegion).text = cred.region
        findViewById<TextView>(R.id.txtValidity).text = cred.getVigenciaTexto()
        findViewById<TextView>(R.id.txtBarCodeText).text = "*${cred.user.uppercase()}*"

        // Cargar foto
        val imgPhoto = findViewById<ImageView>(R.id.imgStudentPhoto)
        if (!cred.photo.isNullOrBlank()) {
            imgPhoto.load(cred.photo) {
                crossfade(true)
                transformations(CircleCropTransformation())
                placeholder(R.drawable.ic_nav_profile)
                error(R.drawable.ic_nav_profile)
            }
        } else {
            imgPhoto.setImageResource(R.drawable.ic_nav_profile)
        }

        // Generar y cargar QR
        val qrContent = cred.qrCode ?: "https://dsia.uv.mx/credencial/validar?u=${cred.user}"
        val qrBitmap: Bitmap? = BarcodeGenerator.generateQrBitmap(qrContent, size = 512)
        val imgQr = findViewById<ImageView>(R.id.imgQrCode)
        if (qrBitmap != null) {
            imgQr.setImageBitmap(qrBitmap)
        }

        // Generar y cargar Código de Barras (Code 128)
        val barCodeContent = cred.barCode ?: cred.user
        val barBitmap: Bitmap? = BarcodeGenerator.generateBarcodeBitmap(barCodeContent, width = 600, height = 140)
        val imgBar = findViewById<ImageView>(R.id.imgBarCode)
        if (barBitmap != null) {
            imgBar.setImageBitmap(barBitmap)
        }
    }
}

