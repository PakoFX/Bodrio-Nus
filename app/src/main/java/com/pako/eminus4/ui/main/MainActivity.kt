package com.pako.eminus4.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.pako.eminus4.AppUpdater
import com.pako.eminus4.R
import com.pako.eminus4.core.SessionManager
import com.pako.eminus4.ui.auth.LoginWebViewActivity
import com.pako.eminus4.ui.courses.CoursesFragment
import com.pako.eminus4.ui.notifications.NotificationsFragment
import com.pako.eminus4.ui.profile.ProfileFragment

class MainActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var layoutOfflineBanner: View
    private lateinit var txtBannerMessage: TextView
    private lateinit var btnBannerRenew: MaterialButton

    private var coursesFragment = CoursesFragment()
    private var notificationsFragment = NotificationsFragment()
    private var profileFragment = ProfileFragment()
    private var activeFragment: Fragment = coursesFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)
        // Permitir entrada si hay datos guardados (modo offline)
        if (!sessionManager.hasSession()) {
            navegarALogin()
            return
        }

        setContentView(R.layout.activity_main)

        layoutOfflineBanner = findViewById(R.id.layoutOfflineBanner)
        txtBannerMessage = findViewById(R.id.txtBannerMessage)
        btnBannerRenew = findViewById(R.id.btnBannerRenew)

        btnBannerRenew.setOnClickListener {
            abrirLoginParaRenovar()
        }

        bottomNav = findViewById(R.id.bottomNavigation)

        // Respetar insets de sistema para Status Bar (banner) y Navigation Bar (bottomNav)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainLayout)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            val padTopBanner = systemBars.top + (8 * resources.displayMetrics.density).toInt()
            val padBottomBanner = (8 * resources.displayMetrics.density).toInt()
            layoutOfflineBanner.setPadding(
                layoutOfflineBanner.paddingLeft,
                padTopBanner,
                layoutOfflineBanner.paddingRight,
                padBottomBanner
            )

            bottomNav.setPadding(0, 0, 0, systemBars.bottom)
            ViewCompat.requestApplyInsets(findViewById(R.id.fragmentContainer))
            insets
        }

        // Inicializar o restaurar fragmentos en el contenedor
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, profileFragment, "profile").hide(profileFragment)
                .add(R.id.fragmentContainer, notificationsFragment, "notifications").hide(notificationsFragment)
                .add(R.id.fragmentContainer, coursesFragment, "courses")
                .commit()
        } else {
            profileFragment = supportFragmentManager.findFragmentByTag("profile") as? ProfileFragment ?: profileFragment
            notificationsFragment = supportFragmentManager.findFragmentByTag("notifications") as? NotificationsFragment ?: notificationsFragment
            coursesFragment = supportFragmentManager.findFragmentByTag("courses") as? CoursesFragment ?: coursesFragment
            activeFragment = when (bottomNav.selectedItemId) {
                R.id.nav_profile -> profileFragment
                R.id.nav_notifications -> notificationsFragment
                else -> coursesFragment
            }
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_courses -> {
                    cambiarFragmento(coursesFragment)
                    true
                }
                R.id.nav_notifications -> {
                    cambiarFragmento(notificationsFragment)
                    true
                }
                R.id.nav_profile -> {
                    cambiarFragmento(profileFragment)
                    true
                }
                else -> false
            }
        }

        // Verificar actualizaciones de GitHub en segundo plano
        AppUpdater.verificarActualizaciones(this, silent = true)
    }

    override fun onResume() {
        super.onResume()
        if (sessionManager.isSessionExpired()) {
            val tieneCursos = sessionManager.getCachedCourses().isNotEmpty()
            val msg = if (tieneCursos) "⚠️ Sesión caducada. Mostrando datos guardados." else "⚠️ Sesión en línea caducada."
            mostrarBannerSesionExpirada(msg)
        } else {
            ocultarBannerSesion()
            coursesFragment.recargarCursos()
        }
    }

    fun isBannerVisible(): Boolean {
        return ::layoutOfflineBanner.isInitialized && layoutOfflineBanner.visibility == View.VISIBLE
    }

    fun mostrarBannerSesionExpirada(mensaje: String = "⚠️ Sesión caducada. Mostrando datos guardados.") {
        runOnUiThread {
            layoutOfflineBanner.visibility = View.VISIBLE
            txtBannerMessage.text = mensaje
            ViewCompat.requestApplyInsets(findViewById(R.id.fragmentContainer))
        }
    }

    fun ocultarBannerSesion() {
        runOnUiThread {
            layoutOfflineBanner.visibility = View.GONE
            ViewCompat.requestApplyInsets(findViewById(R.id.fragmentContainer))
        }
    }

    fun abrirLoginParaRenovar() {
        val intent = Intent(this, LoginWebViewActivity::class.java).apply {
            putExtra("SESSION_EXPIRED", true)
            putExtra("RENEWAL_ONLY", true)
            val currentToken = sessionManager.getAccessToken()
            if (!currentToken.isNullOrBlank()) {
                putExtra("EXPIRED_TOKEN", currentToken)
            }
        }
        startActivity(intent)
    }

    fun notificarSesionExpirada() {
        sessionManager.setSessionExpired(true)
        mostrarBannerSesionExpirada("⚠️ Sesión caducada. Mostrando datos guardados.")
    }

    private fun cambiarFragmento(target: Fragment) {
        if (target != activeFragment) {
            supportFragmentManager.beginTransaction()
                .hide(activeFragment)
                .show(target)
                .commit()
            activeFragment = target
        }
    }

    fun cerrarSesionDirecta() {
        val expiredToken = sessionManager.getAccessToken()
        sessionManager.clearSession(preserveCredential = true)
        navegarALogin(sessionExpired = false, isLogout = true, expiredToken = expiredToken)
    }

    fun navegarALoginPorExpiracion() {
        notificarSesionExpirada()
    }

    private fun navegarALogin(sessionExpired: Boolean = false, isLogout: Boolean = false, expiredToken: String? = null) {
        val intent = Intent(this, LoginWebViewActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("SESSION_EXPIRED", sessionExpired)
            putExtra("LOGOUT_ACTION", isLogout)
            if (!expiredToken.isNullOrBlank()) {
                putExtra("EXPIRED_TOKEN", expiredToken)
            }
        }
        startActivity(intent)
        finish()
    }
}

