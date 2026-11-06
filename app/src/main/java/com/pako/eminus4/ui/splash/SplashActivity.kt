package com.pako.eminus4.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.pako.eminus4.R
import com.pako.eminus4.core.SessionManager
import com.pako.eminus4.ui.auth.LoginWebViewActivity
import com.pako.eminus4.ui.main.MainActivity

/**
 * SplashActivity — Punto de entrada de la app.
 * Muestra un splash animado y luego redirige según el estado de sesión:
 *  - Con sesión activa  → CoursesActivity
 *  - Sin sesión         → LoginWebViewActivity
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Esperar 1.5 segundos para que la animación del logo se aprecie
        Handler(Looper.getMainLooper()).postDelayed({
            redirigir()
        }, 1500)
    }

    private fun redirigir() {
        val sessionManager = SessionManager(this)
        val destino = if (sessionManager.hasSession()) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, LoginWebViewActivity::class.java)
        }
        destino.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(destino)
        finish()
    }
}
