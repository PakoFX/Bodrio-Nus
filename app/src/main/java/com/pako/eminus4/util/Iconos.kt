package com.pako.eminus4.util

import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.pako.eminus4.R

/** Ícono y color según la extensión de un archivo (PDF rojo, Word azul, Excel verde…). */
data class IconoArchivo(@DrawableRes val icono: Int, @ColorRes val color: Int)

fun iconoArchivo(nombre: String?): IconoArchivo =
    when (nombre?.substringAfterLast('.', "")?.lowercase()) {
        "pdf" -> IconoArchivo(R.drawable.ic_picture_as_pdf, R.color.badge_error_text)
        "doc", "docx", "odt", "rtf" -> IconoArchivo(R.drawable.ic_article, R.color.badge_info_text)
        "xls", "xlsx", "csv", "ods" -> IconoArchivo(R.drawable.ic_table_chart, R.color.badge_success_text)
        "ppt", "pptx", "odp" -> IconoArchivo(R.drawable.ic_slideshow, R.color.badge_warning_text)
        "zip", "rar", "7z" -> IconoArchivo(R.drawable.ic_folder_zip, R.color.text_secondary)
        "png", "jpg", "jpeg", "webp", "gif" -> IconoArchivo(R.drawable.ic_image, R.color.brand_uv_blue)
        "mp4", "mov", "avi", "mkv" -> IconoArchivo(R.drawable.ic_play_circle, R.color.brand_uv_blue)
        "txt" -> IconoArchivo(R.drawable.ic_description, R.color.text_secondary)
        else -> IconoArchivo(R.drawable.ic_attach_file, R.color.text_secondary)
    }

/** Pinta en un ImageView el ícono del archivo con su color. */
fun ImageView.mostrarIconoArchivo(nombre: String?) {
    val i = iconoArchivo(nombre)
    setImageResource(i.icono)
    imageTintList = ContextCompat.getColorStateList(context, i.color)
}

/**
 * Ícono vectorial a la izquierda de un texto (reemplazo de los emojis).
 * @param res null quita el ícono.
 */
fun TextView.iconoInicio(
    @DrawableRes res: Int?,
    @ColorInt color: Int = currentTextColor,
    tamanoDp: Int = 16,
    separacionDp: Int = 6
) {
    if (res == null) {
        setCompoundDrawablesRelative(null, null, null, null)
        return
    }
    val d = resources.displayMetrics.density
    val icono = ContextCompat.getDrawable(context, res)?.mutate()?.apply {
        val px = (tamanoDp * d).toInt()
        setBounds(0, 0, px, px)
        setTint(color)
    }
    compoundDrawablePadding = (separacionDp * d).toInt()
    setCompoundDrawablesRelative(icono, null, null, null)
}
