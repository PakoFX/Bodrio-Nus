# Instrucciones para Asistentes de IA (AGENTS.md)

> **⚠️ DETÉNTE - NO ESCANEES EL REPOSITORIO NI LEAS ARCHIVOS AL AZAR.**
> Toda la arquitectura, endpoints descubiertos, decisiones tomadas, estado actual del desarrollo y mapa exacto de archivos ya están completamente documentados en:
> **[`DEV_CONTEXT.md`](./DEV_CONTEXT.md)**

---

## ⚡ RESUMEN RÁPIDO PARA AHORRAR TOKENS (TL;DR)

1. **¿Qué es este proyecto?**
   - `BodrioNus-Native`: Cliente Android nativo en Kotlin para **Eminus 4** (LMS de la Universidad Veracruzana - UV).
   - Sustituye a la app web híbrida vieja. Solo usa WebView para el login SSO institucional; todo lo demás es nativo consumiendo la API REST de Eminus.
   - Package ID: `com.pako.eminus4`.

2. **Reglas de Oro (NO NEGOCIABLES):**
   - 🚫 **NO commits ni push** hasta que el usuario lo pida explícitamente.
   - 🚫 **NO agregar Retrofit, ViewModel ni LiveData**. Mantenemos OkHttp directo y callbacks simples.
   - 🚫 **NO parsear Office (DOCX / PPTX) a texto plano.** El usuario exige renderizado visual fiel con diseño original (`docx-preview` y `PptxViewJS` sobre WebViews en assets).
   - 🚫 **NO romper WebViews con insets**: No uses `WindowCompat.setDecorFitsSystemWindows(false)`.
   - 🇪🇸 **Toda la UI debe ser en Español.**

3. **Endpoints de Red (Eminus 4):**
   - Cursos: `GET https://eminus.uv.mx/eminusapi8/api/Course/getAllCourses` (v8)
   - Actividades: `GET https://eminus.uv.mx/eminusapi/api/Actividad/getActividadesEstudiante/{idCurso}` (Global)
   - Detalle Actividad: `GET https://eminus.uv.mx/eminusapi/api/Actividad/getActividadEstudiante/{idCurso}/{idActividad}` (Global)
   - Entrega Estudiante: `GET https://eminus.uv.mx/eminusapi8/api/Activity/getActUsuarioRevision/{idCurso}/{idActividad}/{idUsuario}` (v8)
   - Descarga Archivos Profe: `GET https://eminus.uv.mx/eminusapi/api/actividad/descargaActividadArchivo/{idCurso}/{idActividad}/{idArchivo}`
   - Descarga Archivo Alumno: `GET https://eminus.uv.mx/eminusapi/api/actividad/descargaEntregaArchivo/{idCurso}/{idActividad}/{idArchivosEntregas}`

4. **Estado de la Tarea Actual:**
   - La visualización de PDFs, imágenes, DOCX y PPTX in-app ya está implementada en `FileViewerActivity.kt`.
   - La pantalla de detalle de actividad (`ActivityDetailActivity.kt`) ya muestra adjuntos de profesores y entregas del alumno, bloquea entregas vencidas y muestra feedback con stickers.
   - Lo que sigue: Probar renderizado Office en vivo y cablear el upload multipart de entregas (`POST actividad/adjuntarEntrega`).

Para más detalles exhaustivos, consulta siempre **`DEV_CONTEXT.md`**.

