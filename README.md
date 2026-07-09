# WoWUpdateMonitor v3.1

App Android que monitorea actualizaciones de builds de World of Warcraft en servidores de Blizzard y envía notificaciones push a teléfonos registrados.

## Qué hace

1. Un **Cloudflare Worker** consulta la API de Blizzard cada pocos minutos.
2. Si detecta un cambio de build (número de versión, build config), manda **FCM** a todos los teléfonos Android registrados.
3. La app Android recibe la notificación, la muestra y la guarda en historial tipo chat.
4. Si hay una nueva versión de la app misma, muestra actualización obligatoria y descarga/instala la APK automáticamente.

## Arquitectura

```
┌──────────────────┐      ┌─────────────────────┐      ┌──────────────┐
│  Blizzard API    │◄─────│  Cloudflare Worker   │─────►│  Firebase    │
│  (US/EU/CN/KR/TW)│      │  (worker.js)         │      │  FCM         │
└──────────────────┘      │                      │      └──────┬───────┘
                          │  /fetch              │             │
                          │  /check              │             ▼
                          │  /register-token     │      ┌──────────────┐
                          │  /unregister-token   │      │  Android App │
                          │  /app-version        │      │  (v3.1)      │
                          │  /notify-app-update  │      │              │
                          └─────────────────────┘      │  - Historial │
                                                       │  - Auto-     │
                                                       │    updater   │
                                                       └──────────────┘
```

## Componentes

### Cloudflare Worker (`worker.js`)

- **Lenguaje:** JavaScript (Cloudflare Workers)
- **Almacenamiento:** Cloudflare KV (`WOW_KV`)
- **Endpoints:**
  - `/fetch` — Consulta builds actuales de Blizzard (US, EU, CN, KR, TW)
  - `/check` — Compara con builds guardados, detecta cambios, manda FCM + Telegram
  - `/register-token` — Registra token FCM del teléfono
  - `/unregister-token` — Elimina token
  - `/app-version` — Lee `version.json` desde GitHub para sistema de auto-update
  - `/notify-app-update` — Envía FCM data-only a todos los teléfonos para avisar nueva versión de app
  - `/test-fcm` — Envía notificación de prueba
- **FCM:** Usa JWT con Service Account de Firebase. Firma con RS256.
- **Telegram:** Envía resumen de cambios a canal de Telegram.

### Android App (`app/`)

- **Lenguaje:** Kotlin
- **UI:** XML layouts + ViewBinding
- **Base de datos:** Room (SQLite) para historial de versiones
- **Notificaciones:** Firebase Cloud Messaging (FCM)
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34 (Android 14)

#### Estructura

```
app/src/main/java/com/wowmonitor/app/
├── WowMonitorApp.kt          # Application class, notification channel
├── data/
│   ├── AppDatabase.kt        # Room database
│   ├── ChangeDetector.kt     # Detecta cambios en versiones
│   ├── Models.kt             # Data classes (VersionEntry, etc.)
│   ├── NetworkMonitor.kt     # Monitorea conectividad de red
│   └── VersionDao.kt         # Room DAO para versiones
├── service/
│   ├── AlarmReceiver.kt      # AlarmManager para polling periódico
│   ├── AppUpdater.kt         # Auto-updater: lee version.json, descarga APK, abre instalador
│   ├── BootReceiver.kt       # Re-registra alarmas al reiniciar teléfono
│   ├── MonitorScheduler.kt   # Programa alarmas de monitoreo
│   ├── MyFirebaseMessagingService.kt  # Recibe FCM, guarda historial, muestra notificación
│   ├── NotificationHelper.kt # Crea y muestra notificaciones Android
│   └── RegionPrefs.kt        # Preferencias de regiones seleccionadas
└── ui/
    ├── HistoryAdapter.kt     # RecyclerView para historial de versiones
    ├── MainActivity.kt       # Pantalla principal: versiones, regiones, notificaciones, auto-update
    └── VersionAdapter.kt     # RecyclerView para versiones de juegos
```

#### Layouts

```
app/src/main/res/layout/
├── activity_main.xml         # Layout principal con ScrollView
├── card_game.xml             # Card de juego (nombre + región + build)
├── dialog_versions.xml       # Diálogo para seleccionar regiones
├── item_history.xml          # Item de historial de cambios
├── item_version.xml          # Item de versión en lista
├── section_notifications.xml # Chat de notificaciones (ScrollView + LinearLayout)
└── section_regions.xml       # Chips de selección de regiones
```

#### Funcionalidades principales

**Monitoreo de versiones:**
- Consulta Worker periódicamente via AlarmManager
- Muestra versiones actuales por juego y región
- Historial de cambios con timestamps

**Notificaciones tipo chat (v3.1):**
- Última actualización aparece abajo (estilo WhatsApp)
- Scroll vertical para ver mensajes viejos
- Mantener presionado → menú "Eliminar"
- Historial persistido en SharedPreferences (JSON array)
- Máximo 20 notificaciones guardadas

**Auto-updater (v3.0+):**
- Al abrir app, consulta `/app-version` del Worker
- Compara `versionCode` con el instalado
- Si hay versión mayor → diálogo obligatorio
- Descarga APK desde GitHub dentro de la app
- Botón "Instalar" → abre instalador Android (`REQUEST_INSTALL_PACKAGES`)
- Usa `FileProvider` para compartir APK al instalador

**Selección de regiones:**
- US, EU, CN, KR, TW
- Chips Material3 para seleccionar/deseleccionar
- Se envían al Worker al registrar token

### WowPushSender (`wow-push-sender.html`)

- **Tipo:** Página HTML/JS standalone
- **Uso:** Interfaz web para enviar notificaciones manuales FCM
- **Funciones:** Enviar título+body, leer versión GitHub, avisar nueva versión
- **NO forma parte de la app Android** — es herramienta interna del administrador

## Flujo de notificación

```
1. Worker hace GET a API de Blizzard (/fetch)
2. Worker compara con última versión en KV
3. Si hay cambio:
   a. Actualiza KV con nueva versión
   b. Lee tokens FCM registrados
   c. Envía FCM a cada token con título + body
   d. Envía resumen a Telegram
4. App Android recibe FCM en MyFirebaseMessagingService
5. Guarda notificación en historial (SharedPreferences)
6. Muestra notificación Android nativa
7. Si type=app_update → guarda datos para auto-update
```

## Flujo de auto-update

```
1. App abre → consulta /app-version
2. Worker lee version.json desde GitHub
3. App compara versionCode local vs remoto
4. Si remoto > local:
   a. Muestra AlertDialog obligatorio
   b. Descarga APK con HttpURLConnection
   c. Cambia botón a "Instalar"
   d. Al tocar → Intent con FileProvider → instalador Android
5. Si version.json no existe o error → no muestra nada
```

## Cómo construir desde código fuente

### Requisitos

- JDK 17
- Android SDK (platforms/android-34, build-tools/34.0.0)
- Cuenta Firebase con `google-services.json`

### Pasos

```bash
# 1. Clonar repo
git clone https://github.com/eygelias/Contexto-WoWUpdateMonitor.git
cd Contexto-WoWUpdateMonitor

# 2. Crear local.properties
echo "sdk.dir=C:\\Users\\ELY\\android-build\\android-sdk" > local.properties

# 3. Agregar google-services.json de Firebase Console
# Copiar a app/google-services.json

# 4. Construir
./gradlew assembleDebug

# 5. APK en
# app/build/outputs/apk/debug/app-debug.apk
```

### Variables de entorno (Windows)

```bash
export JAVA_HOME="/c/Users/ELY/android-build/jdk-17.0.19+10"
export ANDROID_SDK_ROOT="/c/Users/ELY/android-build/android-sdk"
```

## Permisos Android

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

## Dependencias principales

```
Firebase BOM 32.7.0
Firebase Messaging KTX
Room 2.6.1 (con KSP)
Material 1.11.0
RecyclerView 1.3.2
CardView 1.0.0
WorkManager 2.9.0
ConstraintLayout 2.1.4
```

## Versión actual

| Campo | Valor |
|---|---|
| versionCode | 4 |
| versionName | 3.1 |
| APK | WoWUpdateMonitor-v3.1-auto-updater.apk |
| SHA256 | a335537e86c7ebfb5a133eeb2635106249e9c1926434886aa28e32725eb7a031 |

## Changelog

### v3.1
- Chat de notificaciones (última abajo, scroll, eliminar con long press)
- FCM data-only para guardar historial en segundo plano

### v3.0
- Auto-updater: descarga e instala nueva APK desde la app
- Diálogo obligatorio si hay nueva versión
- FileProvider para instalación de APK

### v2.0
- Selección de regiones (US/EU/CN/KR/TW)
- Historial de cambios
- AlarmManager para polling periódico
- BootReceiver para re-registrar alarmas

### v1.0
- Monitoreo básico de versiones
- FCM notifications
- Cloudflare Worker + Firebase

## Licencia

Código abierto para uso personal y educativo.
