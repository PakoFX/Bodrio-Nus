package com.pako.eminus4.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
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

    private val coursesFragment = CoursesFragment()
    private val notificationsFragment = NotificationsFragment()
    private val profileFragment = ProfileFragment()
    private var activeFragment: Fragment = coursesFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)
        if (!sessionManager.isLoggedIn()) {
            navegarALogin()
            return
        }

        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottomNavigation)

        // Respetar insets de la barra de navegación del sistema
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        // Inicializar fragmentos en el contenedor
        supportFragmentManager.beginTransaction()
            .add(R.id.fragmentContainer, profileFragment, "profile").hide(profileFragment)
            .add(R.id.fragmentContainer, notificationsFragment, "notifications").hide(notificationsFragment)
            .add(R.id.fragmentContainer, coursesFragment, "courses")
            .commit()

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
        sessionManager.clearSession()
        navegarALogin()
    }

    private fun navegarALogin() {
        val intent = Intent(this, LoginWebViewActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}

