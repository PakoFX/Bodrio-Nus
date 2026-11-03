package com.pako.eminus4

import android.app.Application
import android.os.Build
import android.webkit.WebView
import coil.Coil
import coil.ImageLoader
import com.pako.eminus4.core.SessionManager
import okhttp3.OkHttpClient

class EminusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WebView.setDataDirectorySuffix("visor_popup")
        }

        // Configura Coil con un OkHttpClient que inyecta el Bearer token
        // en cada petición de imagen (los thumbnails requieren autenticación)
        val sessionManager = SessionManager(this)
        val coilClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = sessionManager.getAccessToken()
                val req = chain.request().newBuilder().apply {
                    if (!token.isNullOrBlank()) {
                        header("Authorization", "Bearer $token")
                    }
                    header("Referer", "https://eminus.uv.mx/eminus4/page/course/list")
                    header("Accept", "image/png,image/jpeg,image/webp,image/*,*/*")
                    header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    // Cookies de sesión del WebView (requeridas por el servidor UV)
                    try {
                        val cookies = android.webkit.CookieManager.getInstance()
                            .getCookie("https://eminus.uv.mx")
                        if (!cookies.isNullOrBlank()) {
                            header("Cookie", cookies)
                        }
                    } catch (_: Exception) {}
                }.build()
                chain.proceed(req)
            }
            .build()

        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient(coilClient)
                .crossfade(true)
                .build()
        )
    }
}
