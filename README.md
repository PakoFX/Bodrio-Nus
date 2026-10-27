# Bodrio-Nus 🎓📱

Cliente móvil no oficial y optimizado para **Eminus 4** de la Universidad Veracruzana (UV), diseñado para transformar la experiencia de escritorio en una aplicación Android nativa, fluida y moderna.

![Android](https://img.shields.io/badge/Android-14%2B-green.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)
![UV](https://img.shields.io/badge/UV-Eminus%204-blue.svg)

---

## ✨ Características Principales

- 📱 **Rediseño Móvil Responsivo:** Inyección dinámica de CSS y hooks para SPA (Angular) que transforman el buscador en una pastilla unificada, reestructuran las tarjetas de cursos (`mat-card`) y adaptan filtros a pantallas táctiles.
- 🔒 **Escala Nativa 1:1:** Bloqueo de gestos de zoom accidentales para mantener la interfaz sólida y firme como una aplicación nativa.
- 🧭 **Barra Flotante de Emergencia:** Navegación integrada (`[ ◀ | ⌂ Eminus | ▶ | 🔄 ]`) para nunca quedar atrapado en redirecciones, enlaces de Microsoft 365, visores externos o descargas.
- 📥 **Gestor de Descargas con Sesión:** Descarga directa de archivos (PDFs, DOCX, XLSX) en segundo plano mediante `HttpURLConnection` propagando las cookies institucionales activas y abriéndolos con visores del sistema (`FileProvider`).
- 📤 **Soporte para Subida de Tareas:** Integración con el selector de archivos del sistema Android (`onShowFileChooser`) para adjuntar entregas y tareas sin problemas.
- 💾 **Persistencia de Sesión:** Manejo de `CookieManager` con sincronización en `onPause` para no tener que iniciar sesión a cada momento.
- 🎨 **Identidad Visual UV:** Ícono adaptativo con birrete UV y esquema de colores institucional.

---

## 🛠️ Tecnologías

- **Lenguaje:** Kotlin
- **Plataforma:** Android SDK (API 34 / Android 14 HyperOS)
- **Componentes:** AndroidX, Material Design Components, WebView, FileProvider
- **Estilos:** CSS3 adaptativo con soporte para selectores modernos (`:has()`, flexbox, touch-action)

---

## 📦 Compilación

Para compilar el APK de depuración:

```bash
./gradlew assembleDebug
```

El archivo generado estará disponible en:
`app/build/outputs/apk/debug/app-debug.apk`

---

Desarrollado para la comunidad universitaria de la UV.
