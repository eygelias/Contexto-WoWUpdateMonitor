# Contexto WoWUpdateMonitor

Contexto técnico e historial del proyecto WoWUpdateMonitor.

## Contenido

- `Contexto-WoWUpdateMonitor.md` — Documento maestro de contexto del proyecto
- `app/` — Código fuente Android (WoWUpdateMonitor)
- `worker.js` — Cloudflare Worker
- `wow-push-sender.html` — WowPushSender (interfaz web para enviar notificaciones)
- `build.gradle.kts` — Configuración Gradle raíz
- `settings.gradle.kts` — Settings Gradle
- `gradle.properties` — Propiedades Gradle

## Cómo construir APK

1. Instalar JDK 17 + Android SDK (platforms/android-34, build-tools/34.0.0)
2. Crear `local.properties` con `sdk.dir=<ruta-al-sdk>`
3. Agregar `google-services.json` en `app/`
4. Ejecutar: `./gradlew assembleDebug`
5. APK en: `app/build/outputs/apk/debug/app-debug.apk`

## Versión actual

- **v3.1** — Chat de notificaciones + auto-updater
- **versionCode**: 4
