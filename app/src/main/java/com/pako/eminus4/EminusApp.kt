package com.pako.eminus4

import android.app.Application
import android.os.Build
import android.webkit.WebView

class EminusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Android 9+ (API 28) prohibe que dos WebViews en el mismo proceso compartan
        // el mismo data directory. Esto provoca que el segundo WebView (el popup del
        // visor "Ver en linea") cargue silenciosamente una pagina en blanco.
        // setDataDirectorySuffix DEBE llamarse ANTES de que cualquier WebView sea
        // instanciado, por eso va aqui en Application.onCreate().
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WebView.setDataDirectorySuffix("visor_popup")
        }
    }
}
