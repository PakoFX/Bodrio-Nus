package com.pako.eminus4.ui.viewer

import android.util.Base64
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.regex.Pattern
import java.util.zip.ZipFile

object OfficeDocumentRenderer {

    /**
     * Convierte un documento Microsoft Word (.docx) a HTML responsivo para visualización in-app.
     */
    fun renderDocxToHtml(file: File): String {
        return try {
            val zip = ZipFile(file)
            val docEntry = zip.getEntry("word/document.xml") ?: return renderFallback(file)
            val xmlText = BufferedReader(InputStreamReader(zip.getInputStream(docEntry), Charsets.UTF_8)).use { it.readText() }

            // Extraer imágenes embebidas de word/media/
            val imagesMap = mutableMapOf<String, String>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.startsWith("word/media/")) {
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val mime = if (entry.name.endsWith(".png", true)) "image/png" else "image/jpeg"
                    val fileName = entry.name.substringAfterLast("/")
                    imagesMap[fileName] = "data:$mime;base64,$b64"
                }
            }
            zip.close()

            val bodyHtml = StringBuilder()
            val paragraphPattern = Pattern.compile("<w:p[^>]*>(.*?)</w:p>")
            val pMatcher = paragraphPattern.matcher(xmlText)

            while (pMatcher.find()) {
                val pContent = pMatcher.group(1) ?: continue
                val runsPattern = Pattern.compile("<w:r[^>]*>(.*?)</w:r>")
                val rMatcher = runsPattern.matcher(pContent)
                val lineSb = StringBuilder()

                while (rMatcher.find()) {
                    val rContent = rMatcher.group(1) ?: continue
                    val isBold = rContent.contains("<w:b/>") || rContent.contains("<w:b ")
                    val isItalic = rContent.contains("<w:i/>") || rContent.contains("<w:i ")

                    // Extraer texto
                    val tPattern = Pattern.compile("<w:t[^>]*>([^<]+)</w:t>")
                    val tMatcher = tPattern.matcher(rContent)
                    val tSb = StringBuilder()
                    while (tMatcher.find()) {
                        tSb.append(tMatcher.group(1))
                    }

                    var text = escapeHtml(tSb.toString())
                    if (text.isNotBlank()) {
                        if (isBold) text = "<b>$text</b>"
                        if (isItalic) text = "<i>$text</i>"
                        lineSb.append(text)
                    }
                }

                val line = lineSb.toString().trim()
                if (line.isNotEmpty()) {
                    bodyHtml.append("<p>").append(line).append("</p>\n")
                }
            }

            // Si hay imágenes, las anexamos al final del documento si no se incrustaron en párrafo
            if (imagesMap.isNotEmpty()) {
                bodyHtml.append("<div style='margin-top: 24px; border-top: 1px dashed #CBD5E1; padding-top: 16px;'>")
                bodyHtml.append("<p style='font-size: 13px; color: #64748B; font-weight: bold;'>IMÁGENES DEL DOCUMENTO:</p>")
                for ((_, b64Uri) in imagesMap) {
                    bodyHtml.append("<img src='").append(b64Uri).append("' style='max-width:100%; height:auto; margin-bottom:12px; border-radius:6px;' /><br/>")
                }
                bodyHtml.append("</div>")
            }

            wrapInDocumentTheme(file.name, bodyHtml.toString())
        } catch (e: Exception) {
            renderFallback(file, e.localizedMessage)
        }
    }

    /**
     * Convierte una presentación PowerPoint (.pptx) a un carrusel de diapositivas en HTML in-app.
     */
    fun renderPptxToHtml(file: File): String {
        return try {
            val zip = ZipFile(file)
            val slideEntries = mutableListOf<String>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val name = entries.nextElement().name
                if (name.matches(Regex("^ppt/slides/slide\\d+\\.xml$"))) {
                    slideEntries.add(name)
                }
            }

            // Ordenar numéricamente: slide1, slide2, slide3...
            slideEntries.sortBy { name ->
                name.replace(Regex("\\D"), "").toIntOrNull() ?: 0
            }

            val slidesHtml = StringBuilder()
            var slideNumber = 1

            for (entryName in slideEntries) {
                val entry = zip.getEntry(entryName) ?: continue
                val xml = BufferedReader(InputStreamReader(zip.getInputStream(entry), Charsets.UTF_8)).use { it.readText() }

                val paragraphs = mutableListOf<String>()
                val pPattern = Pattern.compile("<a:p[^>]*>(.*?)</a:p>")
                val pMatcher = pPattern.matcher(xml)

                while (pMatcher.find()) {
                    val pXml = pMatcher.group(1) ?: continue
                    val tPattern = Pattern.compile("<a:t>([^<]+)</a:t>")
                    val tMatcher = tPattern.matcher(pXml)
                    val pSb = StringBuilder()
                    while (tMatcher.find()) {
                        pSb.append(tMatcher.group(1))
                    }
                    val text = escapeHtml(pSb.toString()).trim()
                    if (text.isNotEmpty()) {
                        paragraphs.add(text)
                    }
                }

                slidesHtml.append("<div class='slide-card'>")
                slidesHtml.append("<span class='slide-badge'>Diapositiva ").append(slideNumber).append(" de ").append(slideEntries.size).append("</span>")

                if (paragraphs.isNotEmpty()) {
                    // Primer párrafo como título de la diapositiva si aplica
                    val title = paragraphs[0]
                    slidesHtml.append("<div class='slide-title'>").append(title).append("</div>")
                    slidesHtml.append("<div class='slide-body'>")
                    for (i in 1 until paragraphs.size) {
                        slidesHtml.append("<div class='slide-bullet'>• ").append(paragraphs[i]).append("</div>")
                    }
                    slidesHtml.append("</div>")
                } else {
                    slidesHtml.append("<div class='slide-body' style='color:#94A3B8; font-style:italic;'>Diapositiva visual con gráficos o elementos de diseño.</div>")
                }

                slidesHtml.append("</div>\n")
                slideNumber++
            }

            zip.close()
            wrapInPresentationTheme(file.name, slidesHtml.toString())
        } catch (e: Exception) {
            renderFallback(file, e.localizedMessage)
        }
    }

    /**
     * Fallback para documentos binarios antiguos (.doc, .ppt, .xls) o texto plano.
     */
    fun renderLegacyOfficeToHtml(file: File): String {
        return try {
            val bytes = file.readBytes()
            val text = extractAsciiStrings(bytes)
            val cleanText = escapeHtml(text).replace("\n", "<br/>")
            wrapInDocumentTheme(file.name, "<div style='font-family:monospace; font-size:13px;'>$cleanText</div>")
        } catch (e: Exception) {
            renderFallback(file, e.localizedMessage)
        }
    }

    private fun extractAsciiStrings(bytes: ByteArray): String {
        val sb = StringBuilder()
        var current = StringBuilder()
        for (b in bytes) {
            val c = b.toInt().toChar()
            if (c in ' '..'~' || c == '\n' || c == '\r' || c == '\t' || c.code in 192..255) {
                current.append(c)
            } else {
                if (current.length >= 4) {
                    sb.append(current.toString().trim()).append("\n\n")
                }
                current = StringBuilder()
            }
        }
        if (current.length >= 4) {
            sb.append(current.toString().trim())
        }
        return if (sb.isNotEmpty()) sb.toString() else "No se pudo extraer texto del formato binario."
    }

    private fun wrapInDocumentTheme(title: String, contentHtml: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
            <style>
              * { box-sizing: border-box; }
              body {
                background-color: #0F172A;
                margin: 0;
                padding: 14px 10px;
                font-family: -apple-system, Roboto, Helvetica, Arial, sans-serif;
                color: #1E293B;
              }
              .page-container {
                background: #FFFFFF;
                border-radius: 12px;
                padding: 24px 20px;
                max-width: 820px;
                margin: 0 auto;
                box-shadow: 0 4px 12px rgba(0,0,0,0.3);
              }
              .doc-header {
                border-bottom: 2px solid #E2E8F0;
                padding-bottom: 12px;
                margin-bottom: 20px;
              }
              .doc-title {
                font-size: 18px;
                font-weight: bold;
                color: #0F172A;
              }
              .doc-badge {
                display: inline-block;
                background: #E0F2FE;
                color: #0284C7;
                font-size: 11px;
                font-weight: bold;
                padding: 3px 8px;
                border-radius: 6px;
                margin-top: 6px;
              }
              p {
                font-size: 15px;
                line-height: 1.65;
                margin: 0 0 14px 0;
                color: #334155;
              }
              b, strong { color: #0F172A; }
            </style>
            </head>
            <body>
              <div class="page-container">
                <div class="doc-header">
                  <div class="doc-title">${escapeHtml(title)}</div>
                  <span class="doc-badge">DOCUMENTO WORD · VISTA EN LÍNEA</span>
                </div>
                $contentHtml
              </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun wrapInPresentationTheme(title: String, slidesHtml: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
            <style>
              * { box-sizing: border-box; }
              body {
                background-color: #0F172A;
                margin: 0;
                padding: 14px 10px;
                font-family: -apple-system, Roboto, Helvetica, Arial, sans-serif;
              }
              .deck-header {
                max-width: 820px;
                margin: 0 auto 16px auto;
                padding: 0 6px;
              }
              .deck-title {
                font-size: 18px;
                font-weight: bold;
                color: #FFFFFF;
              }
              .deck-badge {
                display: inline-block;
                background: #00897B;
                color: #FFFFFF;
                font-size: 11px;
                font-weight: bold;
                padding: 3px 8px;
                border-radius: 6px;
                margin-top: 6px;
              }
              .slide-card {
                background: #FFFFFF;
                color: #0F172A;
                border-radius: 14px;
                padding: 22px 20px;
                margin: 0 auto 16px auto;
                max-width: 820px;
                box-shadow: 0 6px 16px rgba(0,0,0,0.25);
                border: 1px solid #334155;
              }
              .slide-badge {
                display: inline-block;
                background: #00897B;
                color: #FFFFFF;
                font-size: 11px;
                font-weight: bold;
                padding: 3px 10px;
                border-radius: 6px;
                margin-bottom: 12px;
              }
              .slide-title {
                font-size: 17px;
                font-weight: bold;
                color: #0F172A;
                margin-bottom: 12px;
                border-bottom: 1px solid #E2E8F0;
                padding-bottom: 8px;
              }
              .slide-body {
                font-size: 15px;
                line-height: 1.6;
                color: #334155;
              }
              .slide-bullet {
                margin-bottom: 8px;
                padding-left: 4px;
              }
            </style>
            </head>
            <body>
              <div class="deck-header">
                <div class="deck-title">${escapeHtml(title)}</div>
                <span class="deck-badge">DIAPOSITIVAS POWERPOINT · VISTA EN LÍNEA</span>
              </div>
              $slidesHtml
            </body>
            </html>
        """.trimIndent()
    }

    private fun renderFallback(file: File, errorMsg: String? = null): String {
        return """
            <!DOCTYPE html>
            <html>
            <body style='background:#0F172A; color:#E2E8F0; font-family:sans-serif; padding:24px; text-align:center;'>
              <h3>${escapeHtml(file.name)}</h3>
              <p style='color:#94A3B8;'>${errorMsg ?: "No se pudo interpretar el archivo para vista previa directa."}</p>
            </body>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}

