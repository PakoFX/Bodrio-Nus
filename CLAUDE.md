# Guía para Claude Code

Consulta siempre [DEV_CONTEXT.md](./DEV_CONTEXT.md) y [AGENTS.md](./AGENTS.md) antes de realizar cualquier acción.

- **NO hagas commits ni push** sin autorización explícita.
- **NO escanees archivos innecesariamente**: Toda la arquitectura, endpoints y mapa de archivos están en `DEV_CONTEXT.md`.
- **Compilar e instalar**: `.\gradlew assembleDebug 2>&1 | Select-Object -Last 4; & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"`

