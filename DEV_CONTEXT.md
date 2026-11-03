# Bodrio-Nus v2.0 (Arquitectura Nativa) - Contexto Técnico del Proyecto

> **Documento para Desarrolladores y Asistentes de IA**
>
> Este archivo es el **punto de entrada obligatorio** para cualquier IA o desarrollador que retome el proyecto.
> Léelo completo antes de tocar cualquier archivo.
>
> ### Instrucciones para IAs que trabajen en este proyecto
> - **Actualiza este archivo al terminar cada movimiento de trabajo.** Mueve tareas al estado correcto, agrega bugs encontrados, decisiones tomadas y cambios de arquitectura.
> - Usa ADB para compilar, instalar y tomar capturas directamente en el dispositivo conectado (ver sección 7).
> - Antes de hacer cambios al login, revisa la sección 3.3 — hay bugs resueltos que NO deben revertirse.
> - El archivo `activity_login.xml` y `LoginWebViewActivity.kt` han sido modificados varias veces; leerlos antes de editar.
> - Si vas a cambiar el `AndroidManifest.xml`, revisa que `LoginWebViewActivity` siempre tenga `windowSoftInputMode="adjustPan"`.

---

## 1. Antecedentes y Motivación
- **Versión 1.x (`Eminus4`):** Cliente híbrido monolítico basado en `WebView` dentro de `MainActivity.kt` (>1800 líneas). Inyectaba CSS y JS para adaptar la web de Eminus 4 a móvil. Lento, alto consumo de RAM, frágil ante cambios del DOM web.
- **Versión 2.0 (`BodrioNus-Native`):** Reescritura desde cero con arquitectura modular nativa Android. La WebView se usa **únicamente para el login SSO institucional**. Todo lo demás es nativo consumiendo la API REST de Eminus 4 con JWT.

---

## 2. Compatibilidad con la App Existente (v1.x)
Para sustituir la v1.x sin desinstalar en los dispositivos de estudiantes:
- **`applicationId`:** `com.pako.eminus4` (idéntico a v1.x).
- **Firma Digital:** Mismo keystore (`app/bodrio.keystore`), alias `bodrio`, clave `android`.
- **`versionCode`:** `5` (superior al `4` de v1.x).
- **`versionName`:** `"2.0.0"`.
- **Auto-Actualización:** `AppUpdater.kt` apunta a GitHub Releases de `PakoFX/Bodrio-Nus`.

---

## 3. Ingeniería Inversa de Eminus 4 (La API Descubierta)

### 3.1. Arquitectura del Servidor
- **Frontend:** SPA Angular Material en `https://eminus.uv.mx/eminus4/`
- **Backend API Base:** `https://eminus.uv.mx/eminusapi8/api/`
- **Auth:** `Authorization: Bearer <JWT>`

### 3.2. Flujo de Autenticación Híbrida
1. Usuario ingresa credenciales en el WebView cargando `https://eminus.uv.mx/eminus4/`.
2. Angular guarda el JWT en `localStorage` del WebView tras autenticación exitosa.
3. Kotlin ejecuta `evaluateJavascript` para extraer el token.
4. Token se persiste en `SessionManager` (SharedPreferences).
5. Se cierra el WebView y se lanza `CoursesActivity`.

#### Claves en `localStorage`:
| Clave | Tipo | Descripción |
|---|---|---|
| `accessToken` | String | Token JWT Bearer |
| `userName` | String | Nombre completo del alumno |
| `userId` / `userIdentity` | String | Matrícula (ej. `zs24022198`) |
| `userEmail` | String | Correo UV |
| `userCampus` | String | Campus universitario |
| `avatar` | String | Ruta foto de perfil |
| `tokenExpiration` | String ISO | Fecha de caducidad del token |
| `userRoles` | JSON Array | Roles (ej. `["STUDENT"]`) |

### 3.3. Bug Resuelto: Bucle de Sesión Expirada ⚠️ NO REVERTIR
**Problema:** JWT expirado en WebView → hook JS lo detectaba → regresaba a `CoursesActivity` → 401 → loop infinito.

**Solución en `LoginWebViewActivity`:**
- `cookieManager.removeAllCookies()` + `webView.clearCache(true)` + `localStorage.clear()` al detectar sesión expirada.
- Flag `waitingForFreshLogin = true` bloquea captura del token viejo hasta que el usuario pase por `default.aspx` o se detecte un token distinto al expirado.

### 3.4. Endpoints Descubiertos
| Endpoint | Método | Descripción |
|---|---|---|
| `Course/getAllCourses` | GET | Lista todos los cursos del usuario |
| `Activity/getActivities/{idCurso}` | GET | Actividades de un curso |
| `Notification/NotificacionesUsuario` | GET | Notificaciones del usuario |
| `Usuario/getUsuario` | GET | Perfil de usuario |

**Headers requeridos en todas las peticiones:**
```
Authorization: Bearer <accessToken>
Accept: application/json, text/plain, */*
User-Agent: Mozilla/5.0 (Linux; Android 14) ... Mobile Safari/537.36
Referer: https://eminus.uv.mx/eminus4/page/course/list
Cookie: <cookies del CookieManager del WebView>
```

---

## 4. Estructura del Código

```text
com.pako.eminus4/
│
├── core/
│   ├── Constants.kt             # URLs, endpoints, claves de SharedPreferences
│   └── SessionManager.kt        # Gestión de tokens y perfil (SharedPreferences)
│
├── data/
│   ├── model/
│   │   ├── Course.kt            # Modelo curso + helpers (isActivo, getColorHexa, getThumbnailUrl)
│   │   ├── CourseResponse.kt    # Wrapper de respuesta API cursos
│   │   ├── Activity.kt          # Modelo actividad + helpers (getEmojiTipo, getPorcentaje)
│   │   └── UserProfile.kt       # Perfil del estudiante
│   └── api/
│       └── EminusApiClient.kt   # OkHttp con interceptor Bearer + Cookies
│
├── ui/
│   ├── splash/
│   │   └── SplashActivity.kt    # Router: verifica sesión → CoursesActivity o LoginWebViewActivity
│   ├── auth/
│   │   └── LoginWebViewActivity.kt  # WebView SSO + extracción JWT + fix bucle expirado
│   └── courses/
│       ├── CoursesActivity.kt       # Lista de cursos con chips Activos/Todos
│       ├── CourseAdapter.kt         # ListAdapter + DiffUtil para tarjetas de cursos
│       ├── CourseDetailActivity.kt  # Detalle curso + actividades (collapsing toolbar)
│       └── ActivityAdapter.kt       # ListAdapter para items de actividades
│
├── AppUpdater.kt                # Auto-actualizador vía GitHub Releases API
└── EminusApp.kt                 # Application Class
```

---

## 5. Hoja de Ruta

### ✅ Completado
1. Estructura del proyecto `BodrioNus-Native` independiente
2. Firma con `bodrio.keystore` (compatible con v1.x para actualizaciones silenciosas)
3. `core/`: `SessionManager`, `Constants`
4. `data/model/`: `Course`, `CourseResponse`, `Activity`, `UserProfile`, `NotificationSummary`
5. `data/api/`: `EminusApiClient` (getAllCourses, getActivities, getNotifications, getUserProfile)
6. `ui/splash/`: `SplashActivity` como launcher limpio hacia `MainActivity`
7. `ui/auth/`: `LoginWebViewActivity` con extracción de token y fix del bucle de sesión expirada
8. `ui/courses/`: `CourseAdapter` con tarjetas de color dinámico y thumbnails nativos
9. `ui/courses/`: `CourseDetailActivity` con collapsing toolbar y lista de actividades
10. `ui/courses/`: `ActivityAdapter` con calificaciones, fechas y estados
11. **Thumbnails de cursos (RESUELTO sesión 2026-11-03):** Se descubrió mediante ingeniería inversa en `main.js` de Eminus 4 que la propiedad `REPOSITORYIMAGES` apunta a `https://eminus.uv.mx/eminusapi/` (no a `eminusapi8`). Los thumbnails se sirven como estáticos públicos sin requerir cookies o Bearer token.
12. **Bottom Navigation Bar (sesión 2026-11-03):** 
    - Creado `MainActivity.kt` como orquestador con `BottomNavigationView` Material 3 (color acento `#00897B`).
    - Navegación instantánea mediante FragmentTransactions (`add` + `show`/`hide`), conservando el estado y scroll de cada pestaña.
13. **Pestaña Cursos (`CoursesFragment`):** Migrado desde Activity a Fragment con saludo dinámico ("¡Hola, Francisco!"), chips de filtrado y carga de imágenes.
14. **Pestaña Avisos (`NotificationsFragment`):** Consume `Global/NotificacionesUsuario`, mostrando tarjetas para Actividades, Exámenes, Foros y Mensajes no leídos.
15. **Pestaña Perfil (`ProfileFragment`):** Credencial digital institucional con foto de perfil servida desde `/eminusapi/profiles/...`, matrícula, campus, correo UV, correo alterno, estado del token JWT, botón para buscar actualizaciones (`AppUpdater`) y diálogo de confirmación para cerrar sesión.
16. **Detalle de Curso y Actividades (RESUELTO sesión 2026-11-03):** El endpoint institucional real para estudiantes es `GET https://eminus.uv.mx/eminusapi/api/Actividad/getActividadesEstudiante/{idCurso}` (servido por `API_GLOBAL_URL`, no por `eminusapi8` que arrojaba error 400 "Falta idCurso"). La respuesta es un array JSON directo con `titulo`, `calificacion`, `valor`, `fechaTermino` y `estadoEntrega`. Mapeado exitosamente en `Activity.kt`, `EminusApiClient.kt` y `ActivityAdapter.kt`.
17. **Pantalla de Detalle de Actividad y Descarga de Adjuntos (`ActivityDetailActivity`) (sesión 2026-11-03):**
    - Creada `ActivityDetailActivity` con Material 3 Toolbar respetando WindowInsets / notch.
    - Metadatos en tiempo real: título, valor/calificación con badge dinámico, fechas (inicio, límite y entrega).
    - Renderizado limpio de instrucciones en HTML institucional (`HtmlCompat` + links interactivos).
    - Detección y listado de archivos adjuntos del docente (`archivosActsConTamano`) con icono dinámico según extensión (`.pdf`, `.pptx`, `.docx`, etc.) y descarga directa vía `DownloadManager` institucional contra `actividad/descargaActividadArchivo/{idCurso}/{idActividad}/{idArchivo}`.
    - Área de entrega con selector nativo Storage Access Framework (`ActivityResultContracts.GetContent()`), vista previa del archivo seleccionado y botón de envío.

### 🔄 En Proceso / Pausado
- **Sistema de Envío Multipart de Entregas:** Implementar el upload multipart en `EminusApiClient` (`/actividad/adjuntarEntrega`) y confirmación (`/InsertaActividadEntrega`).

### 🔲 Pendiente
- [ ] Subida multipart a Eminus (`/actividad/adjuntarEntrega`)
- [ ] Confirmación y modificación de entrega (`/InsertaActividadEntrega` y `/ModificarEntrega`)
- [ ] Manejo proactivo de token expirado (verificar `tokenExpiration` al iniciar antes de hacer llamadas)
- [ ] Caché local de cursos y avisos (Room DB o JSON) para modo sin red / offline
- [ ] Animaciones de transición entre Activities (Shared Element Transition)
- [ ] Soporte landscape / pantallas grandes
- [ ] Build de release firmado y publicación en GitHub Releases

---

## 9. Arquitectura de Endpoints y Repositorios Eminus 4 (Actualizado 2026-11-03)

Tras análisis de `main.js` del cliente Angular de la UV, se descubrió la separación arquitectónica de APIs:
1. **API v8 (`https://eminus.uv.mx/eminusapi8/api/`):**
   - Maneja el listado principal de cursos del usuario (`Course/getAllCourses`).
   - Requiere Bearer token.
2. **API Global / v1 (`https://eminus.uv.mx/eminusapi/api/`):**
   - Maneja notificaciones globales (`Global/NotificacionesUsuario`), perfil de estudiante (`Usuario/getUsuario`) y actividades de un curso por estudiante (`Actividad/getActividadesEstudiante/{idCurso}`).
   - Detalle de actividad para estudiante: `GET Actividad/getActividadEstudiante/{idCurso}/{idActividad}`.
   - Requiere Bearer token.
3. **Repositorio Estático (`https://eminus.uv.mx/eminusapi/`):**
   - Servidor de archivos estáticos públicos para fotos de perfil (`/profiles/{hash}.png`) y portadas de cursos (`/thumbnail/{ruta}`). No requiere tokens.

---

## 10. Flujo de Entregas y Adjuntos Descubierto en Eminus 4

Endpoints de entrega analizados en `main.js`:
- `GET Actividad/getActividadEstudiante/{idCurso}/{idActividad}`: Retorna detalles de la actividad, fechas epoch, calificación y descripción HTML.
- `POST actividad/descargaActividadArchivo`: Descarga archivos adjuntos puestos por el profesor.
- `POST actividad/adjuntarEntrega`: Sube un archivo adjunto para la entrega del estudiante (multipart).
- `POST Actividad/InsertaActividadEntrega`: Registra la entrega final de la actividad.
- `POST Actividad/ModificarEntrega`: Modifica una entrega ya enviada previamente.
- `PUT actividad/eliminaArchivoEntrega`: Quita un archivo adjunto de la entrega.
- `POST actividad/descargaEntregaArchivo`: Descarga los archivos entregados por el estudiante.

---

## 6. Notas de Arquitectura

### Por qué OkHttp directo (no Retrofit)
Control total sobre cookies y headers específicos del sistema UV. Migrar a Retrofit si la API crece en complejidad.

### Por qué sin ViewModel / LiveData
Arquitectura simplificada intencionalmente para un proyecto pequeño. Migrar a MVVM si el equipo crece o si se agrega caché.

### Token JWT
Duración ~2 horas (campo `exp`). Actualmente se detecta la expiración en tiempo de ejecución con el 401. La mejora pendiente es verificar `tokenExpiration` al inicio.

### ⚠️ Usar WindowCompat.setDecorFitsSystemWindows(false) rompe el WebView
Se intentó en sesión 2026-10-28 para manejar insets del notch — causó pantalla negra. El approach correcto es `fitsSystemWindows="true"` en el layout XML, sin tocar el window desde código.

---

## 7. Despliegue y Pruebas (ADB)

**ADB:** `C:\Users\Pako\AppData\Local\Android\Sdk\platform-tools\adb.exe`
**Último dispositivo:** `d79c00ce`

```powershell
# Compilar
.\gradlew assembleDebug

# Instalar (conserva datos)
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"

# Compilar + instalar en un comando
.\gradlew assembleDebug 2>&1 | Select-Object -Last 4; & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"

# Tomar captura de pantalla
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell screencap -p /sdcard/screen.png
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" pull /sdcard/screen.png screen_local.png

# Ver dispositivos conectados
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
```

> **Nota:** `LoginWebViewActivity` tiene `exported="false"` — no se puede lanzar directo con ADB. Para probar el login el usuario debe abrir la app desde el teléfono o cerrar sesión manualmente.

---

## 8. Historial de Cambios Recientes

| Fecha | Cambio | Archivo(s) |
|---|---|---|
| 2026-10-28 | Toolbar oscuro → header blanco minimalista sin título | `activity_login.xml` |
| 2026-10-28 | Barra progreso: 4dp verde neón → 3dp verde `#2E7D32` | `activity_login.xml` |
| 2026-10-28 | `adjustPan` para evitar que el teclado desplace el WebView | `AndroidManifest.xml` |
| 2026-10-28 | `overScrollMode="never"` para bloquear scroll fuera de límites | `activity_login.xml` |
| 2026-10-28 | `fitsSystemWindows="true"` para respetar notch/status bar | `activity_login.xml` |
| 2026-10-28 | CSS inyectado en `onPageFinished` para llenar altura completa | `LoginWebViewActivity.kt` |
| 2026-10-28 | Creado `bg_dot_accent.xml` (oval verde, actualmente sin uso tras quitar el header) | `res/drawable/` |
| 2026-10-28 | Chip "NORMAL" → "ACTIVO"/"CERRADO" con color dinámico | `CourseAdapter.kt` |
| 2026-10-28 | Creados drawables para chips de estado de cursos | `bg_chip_activo.xml`, `bg_chip_cerrado.xml` |
| 2026-10-28 | Coil inicializado en `EminusApp` con OkHttpClient + interceptor auth | `EminusApp.kt` |
| 2026-10-28 | `CourseAdapter` simplificado: ya no recibe token (Coil lo maneja globalmente) | `CourseAdapter.kt`, `CoursesActivity.kt` |
| 2026-10-28 | Endpoint `3.4` actualizado: añadido campo `thumbnail` en modelo `Course` | `Course.kt` |
| 2026-11-03 | Descubierto origen de imágenes: REPOSITORYIMAGES (`/eminusapi/`) sin requerir auth | `Course.kt`, `Constants.kt` |
| 2026-11-03 | Creado `MainActivity` con `BottomNavigationView` Material 3 (Cursos · Avisos · Perfil) | `MainActivity.kt`, `activity_main.xml` |
| 2026-11-03 | Migrado cursos a `CoursesFragment` con saludo formateado limpio | `CoursesFragment.kt`, `fragment_courses.xml` |
| 2026-11-03 | Implementada pantalla de avisos con métricas de tareas, foros, exámenes y msjs | `NotificationsFragment.kt`, `fragment_notifications.xml` |
| 2026-11-03 | Implementada credencial de estudiante en perfil con foto real y acciones | `ProfileFragment.kt`, `fragment_profile.xml` |
| 2026-11-03 | Actualizados endpoints `Global/NotificacionesUsuario` y `Usuario/getUsuario` | `EminusApiClient.kt`, `UserProfile.kt`, `NotificationSummary.kt` |
| 2026-11-03 | Corregido endpoint de actividades: `Actividad/getActividadesEstudiante` (resuelve HTTP 400) | `EminusApiClient.kt`, `Activity.kt`, `ActivityAdapter.kt`, `Constants.kt` |
| 2026-11-03 | Implementada pantalla de detalle de actividad (`ActivityDetailActivity`) con HTML, adjuntos y selector | `ActivityDetailActivity.kt`, `activity_detail.xml`, `ActivityAttachment.kt` |
