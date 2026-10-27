package com.pako.eminus4

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object AppUpdater {

    private const val GITHUB_REPO = "PakoFX/Bodrio-Nus"
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    var pendingApkToInstall: File? = null

    /**
     * Revisa en segundo plano si hay una nueva versión disponible en GitHub Releases.
     * @param silent Si es true, no muestra mensajes si ya está actualizado o si hay error de red.
     */
    fun verificarActualizaciones(activity: Activity, silent: Boolean = true) {
        thread {
            try {
                val pInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
                val currentVersionName = pInfo.versionName ?: "1.0"

                val url = URL(LATEST_RELEASE_API)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", "BodrioNusApp")
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    connectTimeout = 8000
                    readTimeout = 8000
                }

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseText)

                    val tagName = json.optString("tag_name", "")
                    val releaseTitle = json.optString("name", tagName)
                    val releaseNotes = json.optString("body", "").trim()

                    // Buscar el archivo APK en los assets del release
                    val assets = json.optJSONArray("assets")
                    var downloadUrl = ""
                    var apkFileName = "BodrioNus_${tagName}.apk"

                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                downloadUrl = asset.optString("browser_download_url", "")
                                apkFileName = name
                                break
                            }
                        }
                    }

                    if (downloadUrl.isNotBlank() && esVersionMayor(tagName, currentVersionName)) {
                        activity.runOnUiThread {
                            mostrarDialogoActualizacion(
                                activity,
                                tagName,
                                releaseTitle,
                                releaseNotes,
                                downloadUrl,
                                apkFileName
                            )
                        }
                    } else if (!silent) {
                        activity.runOnUiThread {
                            Toast.makeText(
                                activity,
                                "¡Ya tienes la versión más reciente ($currentVersionName)!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else if (!silent) {
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "No se encontraron actualizaciones en GitHub.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.w("APP_UPDATER", "Error al verificar actualizaciones: ${e.message}")
                if (!silent) {
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "Error al conectar con GitHub: ${e.localizedMessage ?: "Error de red"}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    /**
     * Compara números de versión semántica (ej. "v1.1" vs "1.0", "1.2.1" vs "1.2.0")
     */
    fun esVersionMayor(versionRemota: String, versionActual: String): Boolean {
        val remotaLimpia = versionRemota.trim().removePrefix("v").removePrefix("V")
        val actualLimpia = versionActual.trim().removePrefix("v").removePrefix("V")

        val partesRemotas = remotaLimpia.split(".").mapNotNull { it.toIntOrNull() }
        val partesActuales = actualLimpia.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(partesRemotas.size, partesActuales.size)
        for (i in 0 until maxLen) {
            val r = partesRemotas.getOrElse(i) { 0 }
            val a = partesActuales.getOrElse(i) { 0 }
            if (r > a) return true
            if (r < a) return false
        }
        return false
    }

    private fun mostrarDialogoActualizacion(
        activity: Activity,
        tagName: String,
        title: String,
        notes: String,
        downloadUrl: String,
        apkFileName: String
    ) {
        val mensajeNotas = if (notes.isNotBlank()) "\n\nNovedades:\n$notes" else ""
        AlertDialog.Builder(activity)
            .setTitle("🚀 Actualización disponible ($tagName)")
            .setMessage("Hay una nueva versión de Bodrio-Nus disponible.$mensajeNotas\n\n¿Deseas descargar e instalar la actualización ahora?")
            .setPositiveButton("Actualizar") { _, _ ->
                descargarEInstalarApk(activity, downloadUrl, apkFileName)
            }
            .setNegativeButton("Más tarde", null)
            .show()
    }

    private fun descargarEInstalarApk(activity: Activity, downloadUrl: String, apkFileName: String) {
        @Suppress("DEPRECATION")
        val progressDialog = ProgressDialog(activity).apply {
            setTitle("Descargando actualización")
            setMessage("Descargando la nueva versión...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setCancelable(false)
            max = 100
            show()
        }

        thread {
            try {
                val url = URL(downloadUrl)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15000
                    readTimeout = 30000
                }
                connection.connect()

                val fileLength = connection.contentLength
                val cacheDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: activity.cacheDir
                val destFile = File(cacheDir, apkFileName)

                connection.inputStream.use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(8192)
                        var total: Long = 0
                        var count: Int
                        while (input.read(buffer).also { count = it } != -1) {
                            total += count
                            output.write(buffer, 0, count)
                            if (fileLength > 0) {
                                val progress = (total * 100 / fileLength).toInt()
                                activity.runOnUiThread {
                                    progressDialog.progress = progress
                                }
                            }
                        }
                    }
                }

                activity.runOnUiThread {
                    progressDialog.dismiss()
                    instalarApk(activity, destFile)
                }
            } catch (e: Exception) {
                Log.e("APP_UPDATER", "Error descargando APK", e)
                activity.runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(
                        activity,
                        "Error al descargar actualización: ${e.localizedMessage ?: "Error de red"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun instalarApk(activity: Activity, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    pendingApkToInstall = apkFile
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                    activity.startActivity(intent)
                    Toast.makeText(
                        activity,
                        "Por favor activa 'Permitir desde esta fuente' y regresa para continuar.",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
            }

            pendingApkToInstall = null
            val apkUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.provider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e("APP_UPDATER", "Error al abrir instalador de APK", e)
            Toast.makeText(
                activity,
                "No se pudo abrir el instalador: ${e.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
