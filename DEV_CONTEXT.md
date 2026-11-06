# Bodrio-Nus v2.0 (Arquitectura Nativa) - Guía Maestra de Contexto Técnico

> **⚠️ PROTOCOLO OBLIGATORIO PARA CUALQUIER IA QUE RETOME ESTE PROYECTO ⚠️**
>
> **NO GASTES TOKENS ESCANEANDO LA BASE DE CÓDIGO NI LEYENDO ARCHIVOS DESDE CERO.**
> Este documento contiene **toda la arquitectura, endpoints descubiertos, decisiones tomadas, estado actual del desarrollo y mapa exacto de archivos**. Léelo y pasa directamente a resolver la tarea del usuario.

---

## 🚨 REGLAS CRÍTICAS Y NO NEGOCIABLES

1. **NO HACER COMMITS NI PUSH HASTA QUE EL USUARIO LO ORDENE EXPLÍCITAMENTE.** Todo el trabajo se mantiene en local (`dev-v2`).
2. **NO INTRODUCIR RETROFIT, VIEWMODEL NI LIVEDATA.** La arquitectura es deliberadamente directa: OkHttp nativo con `SessionManager`, llamadas asíncronas con callbacks o corrutinas y pintado en hilo principal con `runOnUiThread` / `Handler(Looper.getMainLooper())`.
3. **NO USAR `WindowCompat.setDecorFitsSystemWindows(false)`:** Rompe los WebViews en Android 14/HyperOS dejando pantalla negra. El manejo de insets se hace exclusivamente mediante `android:fitsSystemWindows="true"` en XML o listeners de `WindowInsetsCompat`.
4. **`windowSoftInputMode="adjustPan"` EN LOGIN:** Obligatorio en `AndroidManifest.xml` para evitar que el teclado redimensione agresivamente el WebView de login institucional.
5. **VISUALIZADOR OFFICE FIEL AL DISEÑO ORIGINAL (NO MONOLITO DE TEXTO):**
   - El usuario rechazó terminantemente extraer texto plano o listar párrafos/diapositivas en texto crudo.
   - DOCX debe renderizarse con diseño original (tablas, tipografías, encabezados, imágenes) usando `docx-preview.min.js`.
   - PPTX debe renderizarse con diapositivas completas (colores, fondos, formas, gráficos) usando `PptxViewJS.min.js` + `chart.umd.min.js` sobre `<canvas>` con controles Anterior/Siguiente y swipe táctil.
6. **ACTIVIDADES CERRADAS O CALIFICADAS NO ADMITEN ENTREGAS:** Si la actividad ya tiene calificación o pasó su `fechaTermino`, los botones de selección y envío de archivos DEBEN ocultarse y mostrarse el aviso con candado `🔒`.
7. **FEEDBACK DEL PROFESOR CON IMÁGENES/STICKERS:** Eminus usa Froala con imágenes públicas en `https://eminus.uv.mx/eminusapi/froala/...`. Deben cargarse mediante `webTeacherFeedback` transparente con `loadDataWithBaseURL("https://eminus.uv.mx", ...)`.
8. **IDIOMA DE LA UI:** Todo el texto visible para el usuario debe estar en **Español**.
9. **VERSIÓN DE LA APP:** Con cada sesión de trabajo que añada funcionalidad real, incrementar `versionCode` (+1) y `versionName` (semver) en `app/build.gradle.kts`. Esquema: `MAJOR.MINOR.PATCH` — MINOR para features nuevas, PATCH para bugfixes. **Versión actual: 2.4.3 (versionCode 11).**
10. **MODO SIN CONEXIÓN Y RESILIENCIA DE SESIÓN (NO EXPULSIÓN ABRUPTA):**
    - Los errores 401 / caducidad de token **NUNCA DEBEN EXPULSAR AL USUARIO** a la pantalla de login ni borrar `SessionManager`.
    - `SplashActivity` permite entrar a `MainActivity` si hay datos en el teléfono (`hasSession()`), garantizando acceso offline a la credencial digital y cursos guardados.
    - Cuando el token expira, se muestra un banner superior discreto en `MainActivity` (`layoutOfflineBanner`) con el aviso y el botón "Renovar" respetando insets de la barra de estado.
    - Los cursos se guardan en caché local (`saveCachedCourses`) y se pintan de inmediato al abrir la app. Si no hay cursos en caché y la sesión caducó, se muestra el estado informativo adecuado invitando a renovar.

---

## 📍 1. ESTADO ACTUAL Y TAREA ACTIVA (DÓNDE ESTAMOS)

### ¿Qué se completó exitosamente?
- ✅ **Login SSO UV:** Extracción de JWT desde `localStorage`, persistencia en SharedPreferences y prevención del bucle de sesión expirada.
- ✅ **Arquitectura Base con BottomNav:** Pestañas de Cursos, Avisos y Perfil institucional funcionando.
- ✅ **Detalle de Curso y Lista de Actividades:** Consumo correcto de `GET Actividad/getActividadesEstudiante/{idCurso}`.
- ✅ **Detalle de Actividad (`ActivityDetailActivity`):**
  - Instrucciones enriquecidas con HTML y enlaces interactivos.
  - Lista de adjuntos del docente (`archivosActsConTamano`) con botones "Ver en línea" y "Descargar".
  - Lista de archivos entregados por el estudiante (`archivosEntregaConTamano`) con botones "Ver en línea" y "Descargar".
  - Retroalimentación del docente con soporte para stickers Froala (imágenes cargadas vía WebView con `loadDataWithBaseURL`).
  - Bloqueo automático de entregas en actividades cerradas/calificadas (`estaCerrada`).
  - Selector de archivos nativo con vista previa del archivo listo para subir.
- ✅ **Visor Integrado de Archivos In-App (`FileViewerActivity`):**
  - **PDF:** `PdfRenderer` nativo de Android en `RecyclerView` con scroll fluido y contador de página.
  - **Imágenes:** Coil a pantalla completa con soporte de zoom/escalado.
  - **DOCX:** WebView local cargando `file:///android_asset/viewer/docx_viewer.html` con `docx-preview.min.js` → renderizado con diseño real (tablas, fuentes, imágenes).
  - **PPTX:** WebView local cargando `file:///android_asset/viewer/pptx_viewer.html` con `PptxViewJS.min.js` + `chart.umd.min.js` → diapositivas reales con navegación y swipe.
  - Botones superiores en visor: Compartir y Guardar en Descargas.

- ✅ **Módulo Avisos — Pestaña "Avisos" (NotificationsFragment) con 4 cards funcionales:**
  - **Actividades (`AllActivitiesActivity`):** Lista todas las actividades de todos los cursos activos. Filtros: Todas / Pendientes / Entregadas / Calificadas. Click en ítem abre `ActivityDetailActivity`.
  - **Exámenes (`ExamsActivity`):** Lista todos los exámenes de todos los cursos que tengan exámenes (`isActivo() || examenes > 0`). Filtros: Todos / Disponibles / Calificados. Click muestra dialog informativo "Accede desde Eminus Web".
  - **Foros (`ForumsActivity`):** Lista todos los foros de todos los cursos que tengan foros (`isActivo() || foros > 0`). Filtros: Todos / Con aportes nuevos. Badge de nuevos comentarios. Click muestra dialog informativo.
  - **Mensajería (`MessagesActivity` + `MessageDetailActivity`):** Bandeja Recibidos/Enviados (77+ mensajes reales). Click en mensaje abre detalle completo con cuerpo HTML, remitente, fecha, lista de adjuntos con botones Descargar/Ver.
- ✅ **Credencial Digital UV Offline (Perfil):**
  - Tarjeta en `ProfileFragment` con franja verde institucional UV (`#00897B`).
  - **Reloj antifraude dinámico en tiempo real (`TextClock`)** con formato oficial (`dd MMM, hh:mm:ss a`), que corre localmente con el reloj del teléfono (funciona 100% sin internet).
  - Foto oficial institucional, nombre completo, matrícula en badge `#0284C7`, carrera y campus.
  - Generación offline de **Código QR oficial** y **Código de barras lineal (Code 128)** con ZXing (`BarcodeGenerator.kt`).
  - **Modo Pantalla Completa (`CredentialFullscreenActivity`):** Eleva automáticamente el brillo de la pantalla al 100% para escaneo en torniquetes/bibliotecas y mantiene la pantalla encendida.
  - Persistencia permanente en `SessionManager.saveStudentCredential` (nunca se borra al estar sin conexión).
  - Diálogo de edición para personalizar la carrera/licenciatura mediante click sostenido en la credencial.

### ¿Qué falta por hacer / Tarea activa?
1. **Subida real de entregas multipart a Eminus:**
   - Endpoint: `POST https://eminus.uv.mx/eminusapi/api/actividad/adjuntarEntrega?idCurso={c}&idActividad={a}&idActividadEntregas={e}`
   - Confirmación: `POST https://eminus.uv.mx/eminusapi/api/Actividad/InsertaActividadEntrega`
2. **Foros / Exámenes — apertura in-app:** Actualmente muestran dialog informativo. Podría mejorarse con WebView al portal institucional de Eminus.
3. **Planes futuros (Solicitados por el usuario):**
   - **Buzón de sugerencias / comentarios en Perfil:** Agregar una caja de comentarios / feedback en la pantalla de Perfil (`ProfileFragment`) para que los usuarios puedan enviar ideas, sugerencias y mejoras directamente.
   - **Contador / Telemetría de usuarios activos:** Implementar un mecanismo anónimo y ligero para monitorear cuántas personas están usando la app realmente día a día (ej. ping telemetry o endpoint analítico).

---

## 🗺️ 2. MAPA MAESTRO DE ARCHIVOS (UBICACIÓN EXACTA)

| Componente | Archivo | Responsabilidad Principal |
|---|---|---|
| **Núcleo & Sesión** | `core/Constants.kt` | URLs base de APIs (`eminusapi8`, `eminusapi`, estáticos), endpoints y keys de SharedPreferences. |
| | `core/SessionManager.kt` | Manejo seguro de SharedPreferences: token JWT, matrícula (`getUserId`), expiración y logout. |
| **Modelos de Datos** | `data/model/Course.kt` | Modelo de curso, thumbnail público, color de tarjeta y estado activo/cerrado. |
| | `data/model/Activity.kt` | Item de actividad en lista de curso, ponderación, fecha límite y estado. |
| | `data/model/ActivityDetail.kt` | Detalle completo de actividad, instrucciones HTML, `estaCerrada`, `estaCalificada`. |
| | `data/model/ActivityAttachment.kt` | Adjunto del profesor (`idArchivosActividades`, `nombreOriginal`, `peso`, `getDownloadUrl`). |
| | `data/model/StudentDelivery.kt` | Datos de entrega del alumno: `idActividadEntrega`, `descEntrega`, `descRevision`, lista de archivos. |
| | `data/model/DeliveryAttachment.kt` | Archivo entregado por alumno (`idArchivosEntregas`, `nombreOriginal`, `peso`, `getDownloadUrl`). |
| **Cliente de Red** | `data/api/EminusApiClient.kt` | OkHttpClient con interceptores Bearer y cookies. Métodos para todas las llamadas API y descarga binaria a caché (`downloadFile`). |
| **Autenticación** | `ui/auth/LoginWebViewActivity.kt` | WebView SSO institucional, extracción de token en `localStorage` y fix de bucle infinito. |
| **Pantalla Principal** | `ui/main/MainActivity.kt` | BottomNavigationView M3 con navegación FragmentTransaction (`Courses`, `Notifications`, `Profile`). |
| **Detalle Actividad** | `ui/courses/ActivityDetailActivity.kt` | Visualización de instrucciones, bloqueo de entregas, lista de adjuntos del profe y del alumno, feedback con imágenes, selector de archivo. |
| **Visor en Línea** | `ui/viewer/FileViewerActivity.kt` | Orquestador de visualización in-app (PDF, DOCX, PPTX, imágenes, texto). |
| | `ui/viewer/PdfPageAdapter.kt` | Adapter de `PdfRenderer` para scroll vertical fluido de páginas PDF. |
| | `ui/viewer/OfficeDocumentRenderer.kt` | Fallback para formatos legacy binarios (`.doc`, `.ppt`, `.xls`). |
| **Librerías Webview** | `assets/viewer/docx_viewer.html` | Plantilla HTML + `docx-preview.min.js` para renderizar Word con diseño real. |
| | `assets/viewer/pptx_viewer.html` | Plantilla HTML + `PptxViewJS.min.js` + `chart.umd.min.js` para renderizar PowerPoint con diapositivas reales y swipe. |
| | `assets/viewer/jszip.min.js` | Dependencia de descompresión OpenXML en cliente. |
| **Layouts Clave** | `res/layout/activity_detail.xml` | Pantalla de detalle de actividad (tarjetas de adjuntos, entrega, retroalimentación). |
| | `res/layout/activity_file_viewer.xml` | Layout del visor con Toolbar, `RecyclerView` PDF, `WebView` Office y `ImageView`. |
| | `res/layout/item_attachment.xml` | Ítem para cada archivo adjunto con botones "Ver en línea" y "Descargar". |

---

## 🗺️ 2b. ARCHIVOS DEL MÓDULO AVISOS (creados en sesión Nov 2026)

| Componente | Archivo | Responsabilidad |
|---|---|---|
| **Modelos** | `data/model/ExamItem.kt` | Examen: `idExamen`, `titulo`, `valor`, fechas, `calificacion`, vars mutables `idCurso`/`nombreCurso`. Props: `estaCalificado`, `estaVencido`. |
| | `data/model/ForumItem.kt` | Foro: `idForo`, `titulo`, `descripcion`, `totalComentarios`, `nuevosComentarios`, `calificacion`, vars mutables `idCurso`/`nombreCurso`. |
| | `data/model/MessageItem.kt` | Item en lista de mensajes: `idMensaje`, `asunto`, `remitente`, `fecha`, `leido`, `tieneAdjuntos`. |
| | `data/model/MessageDetail.kt` | Detalle completo + `MessageSender`, `MessageRecipient`, `MessageAttachment`. Método `getDownloadUrl(idCurso, idMensaje)`. |
| | `data/model/CourseActivityItem.kt` | Wrapper de `Activity` con `courseId`, `courseName`, `courseColor`. |
| **API Client** | `data/api/EminusApiClient.kt` | Métodos añadidos: `getExams(courseId)`, `getForums(courseId)`, `getMessages(folderId)`, `getMessageDetail(messageId)`. |
| **Pantallas** | `ui/courses/AllActivitiesActivity.kt` | Actividades de todos los cursos activos. Filtros: Todas/Pendientes/Entregadas/Calificadas. Click → `ActivityDetailActivity`. |
| | `ui/exams/ExamsActivity.kt` | Exámenes de todos los cursos con exámenes. Filtros: Todos/Disponibles/Calificados. Click → dialog informativo. |
| | `ui/forums/ForumsActivity.kt` | Foros de todos los cursos con foros. Filtros: Todos/Con aportes nuevos. Badge de nuevos comentarios. |
| | `ui/messages/MessagesActivity.kt` | Bandeja de mensajes (Recibidos/Enviados). `MessageAdapter` decodifica HTML entities con `HtmlCompat`. |
| | `ui/messages/MessageDetailActivity.kt` | Detalle de mensaje con cuerpo HTML, adjuntos con botones Descargar/Ver. |
| **Credencial UV** | `data/model/StudentCredential.kt` | Modelo de la credencial: matrícula, nombre, apellidos, carrera, campus, vigencia, foto, qrCode y barCode. |
| | `util/BarcodeGenerator.kt` | Generador offline de QR (512x512) y Código de barras Code 128 usando ZXing core nativo. |
| | `ui/profile/CredentialFullscreenActivity.kt` | Pantalla completa de la credencial con brillo al 100%, reloj en vivo y layout institucional. |
| | `res/layout/activity_credential_fullscreen.xml` | Layout con tarjeta oficial UV, degradado verde, QR y barras para escáneres. |

---

## 🌐 3. ARQUITECTURA DE APIS Y ENDPOINTS DE EMINUS 4

Eminus 4 no tiene una sola API; está dividido en **dos backends y un servidor de archivos**:

### 3.1. Las Tres URLs Base
1. **API v8:** `https://eminus.uv.mx/eminusapi8/api/` (`Constants.API_BASE_URL`)
2. **API Global:** `https://eminus.uv.mx/eminusapi/api/` (`Constants.API_GLOBAL_URL`)
3. **Repositorio Estático Público:** `https://eminus.uv.mx/eminusapi/` (sin auth requerida)
   - Fotos de perfil: `https://eminus.uv.mx/eminusapi/profiles/{avatarHash}.png`
   - Portadas de cursos: `https://eminus.uv.mx/eminusapi/thumbnail/{ruta}`
   - Sellos/stickers Froala: `https://eminus.uv.mx/eminusapi/froala/...`

### 3.2. Tabla de Endpoints Comprobados

| Acción | Método | URL Base | Endpoint | Auth | Notas |
|---|---|---|---|---|---|
| Lista de Cursos | GET | v8 | `Course/getAllCourses` | Bearer | Array de cursos inscritos. |
| Actividades de Curso | GET | Global | `Actividad/getActividadesEstudiante/{idCurso}` | Bearer | **Nota:** No usar `Activity/getActivities` de v8 (da 400). |
| Detalle de Actividad | GET | Global | `Actividad/getActividadEstudiante/{idCurso}/{idActividad}` | Bearer | Retorna instrucciones y `archivosActsConTamano` del profesor. |
| Estado de Entrega Alumno | GET | v8 | `Activity/getActUsuarioRevision/{idCurso}/{idActividad}/{idUsuario}` | Bearer | **Clave:** Retorna `archivosEntregaConTamano`, calificación y feedback docente. |
| Descargar Adjunto Profesor | GET | Global | `actividad/descargaActividadArchivo/{idCurso}/{idActividad}/{idArchivo}` | Bearer | Descarga binaria con token. |
| Descargar Archivo Entregado | GET | Global | `actividad/descargaEntregaArchivo/{idCurso}/{idActividad}/{idArchivosEntregas}` | Bearer | Descarga binaria de lo que entregó el alumno. |
| Notificaciones | GET | Global | `Global/NotificacionesUsuario` | Bearer | Contadores de mensajes, tareas, foros. |
| Perfil de Usuario | GET | Global | `Usuario/getUsuario` | Bearer | Datos personales, correo, campus. |
| Subir Adjunto de Entrega | POST | Global | `actividad/adjuntarEntrega?idCurso={c}&idActividad={a}&idActividadEntregas={e}` | Bearer | Multipart con clave `file`. |
| Confirmar Entrega | POST | Global | `Actividad/InsertaActividadEntrega` | Bearer | JSON con payload de confirmación. |
| **Lista de Exámenes** | GET | Global | `Examen/getExamenesEst/{idCurso}` | Bearer | 200 con array o 204 sin exámenes. Filtrar cursos con `isActivo() \|\| examenes > 0`. |
| **Lista de Foros** | GET | Global | `Foro/getForos/{idCurso}/0` | Bearer | 200 siempre (array vacío si no hay). Filtrar cursos con `isActivo() \|\| foros > 0`. |
| **Lista de Mensajes** | GET | Global | `centromensajes/GetListadoMensajes/{folderId}/0` | Bearer | folderId=1 Recibidos, folderId=2 Enviados. |
| **Detalle de Mensaje** | GET | Global | `centromensajes/GetMensajeDetalle/{idMensaje}` | Bearer | Incluye cuerpo HTML, remitente, lista de adjuntos. |
| **Descargar Adjunto Mensaje** | GET | Global | `centromensajes/descargaCentroMensajesArchivo/{idCurso}/{idMensaje}/{idArchivosMensajes}` | Bearer | idCurso viene de `remitente.idCurso` en el detalle. |

---

## 🎨 4. FUNCIONAMIENTO DEL VISOR DE DOCUMENTOS OFFICE (DISEÑO REAL)

Para evitar la apariencia de texto plano que el usuario descartó:

### DOCX (`docx_viewer.html`):
1. `FileViewerActivity` descarga el archivo a `cacheDir/temp_docs/{nombre}`.
2. Lee los bytes y los codifica en Base64 (`Base64.NO_WRAP`).
3. Carga `file:///android_asset/viewer/docx_viewer.html` en `webOfficeViewer`.
4. En `onPageFinished`, invoca `renderDocxBase64(base64Data)`.
5. `docx-preview.min.js` decodifica a `ArrayBuffer` y renderiza el documento completo con CSS de hoja de papel, respetando fuentes, alineación, tablas e imágenes incrustadas.

### PPTX (`pptx_viewer.html`):
1. `FileViewerActivity` lee los bytes del PPTX en Base64.
2. Carga `file:///android_asset/viewer/pptx_viewer.html`.
3. Requiere `jszip.min.js`, `chart.umd.min.js` y `PptxViewJS.min.js`.
4. En `onPageFinished`, invoca `renderPptxBase64(base64Data)`.
5. `PptxViewJS` crea una instancia `PPTXViewer` sobre un `<canvas>` HTML5.
6. Permite navegar entre diapositivas completas con botones "◀ Anterior" / "Siguiente ▶" y gestos táctiles de deslizamiento (swipe horizontal).

---

## 🔒 5. REGLAS DE NEGOCIO: CIERRE DE ACTIVIDADES

En `ActivityDetail.kt` la propiedad calculada `estaCerrada` gobierna la interfaz:
```kotlin
val estaCerrada: Boolean
    get() {
        if (calificacion != null) return true // Ya calificada
        if (estado == 2 || estado == 3 || estadoEntrega == 2 || estadoEntrega == 3) return true
        val fechaTerminoMillis = parseIsoToMillis(fechaTermino) ?: return false
        return System.currentTimeMillis() > fechaTerminoMillis
    }
```
- **Si `estaCerrada == true`:**
  - Se ocultan `btnSelectFile`, `btnSubmitDelivery` y `layoutSelectedFile`.
  - Se hace visible `layoutClosedNotice` con título y descripción contextual (verde si fue calificada con su nota, rojo si venció el plazo).
- **Si `estaCerrada == false`:**
  - Se habilitan los botones de selección y entrega.
  - El botón adapta su texto dinámicamente: "Seleccionar archivo para entrega…" (si es nueva) o "Modificar archivo de entrega…" (si ya tiene entrega previa).

---

## 🛠️ 6. COMANDOS ÚTILES PARA EL ENTORNO (COPIAR Y PEGAR)

Ruta del ejecutable ADB en la máquina del usuario:
`$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe`

```powershell
# Compilar e instalar rápidamente en el dispositivo Xiaomi/Poco (d79c00ce)
cd BodrioNus-Native
.\gradlew assembleDebug 2>&1 | Select-Object -Last 4; & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"

# Tomar screenshot y descargar para inspección visual
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell screencap -p /sdcard/screen.png
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" pull /sdcard/screen.png screen_check.png

# Ver logs de JavaScript en WebView de Office
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -d -s chromium

# Ver si el dispositivo sigue conectado
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
```
