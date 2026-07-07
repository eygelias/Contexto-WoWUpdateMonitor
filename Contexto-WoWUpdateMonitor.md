# Contexto WoWUpdateMonitor

Última actualización de contexto: 2026-07-07

## Objetivo del proyecto

WoWUpdateMonitor es una app Android para avisar rápido cuando Blizzard actualiza builds de World of Warcraft. El flujo usa Cloudflare Worker + Firebase Cloud Messaging (FCM) para enviar notificaciones a teléfonos Android.

## Preferencias del dueño

- Usuario: Carlos Ramos / Ely.
- Idioma: español.
- Quiere instrucciones paso a paso, directas, sin múltiples opciones.
- Quiere mantener una sola APK vigente en escritorio.
- Cada cambio importante de ahora en adelante debe actualizar este repo de contexto: `Contexto-WoWUpdateMonitor`.

## Repositorios GitHub

### Releases de APK

Repo:

```text
https://github.com/eygelias/WoWUpdateMonitor-Releases
```

Release actual:

```text
https://github.com/eygelias/WoWUpdateMonitor-Releases/releases/tag/v3.1
```

APK actual:

```text
https://github.com/eygelias/WoWUpdateMonitor-Releases/releases/download/v3.1/WoWUpdateMonitor-v3.1-auto-updater.apk
```

`version.json` actual:

```text
https://raw.githubusercontent.com/eygelias/WoWUpdateMonitor-Releases/main/version.json
```

Contenido esperado:

```json
{
  "versionCode": 4,
  "versionName": "v3.1",
  "appName": "WoWUpdateMonitor",
  "apkName": "WoWUpdateMonitor-v3.1-auto-updater.apk",
  "apkUrl": "https://github.com/eygelias/WoWUpdateMonitor-Releases/releases/download/v3.1/WoWUpdateMonitor-v3.1-auto-updater.apk",
  "required": true,
  "message": "Actualización obligatoria disponible"
}
```

## Archivos locales importantes

Proyecto Android:

```text
C:\Users\ELY\Desktop\WoWUpdateMonitor
```

Worker Cloudflare local:

```text
C:\Users\ELY\Desktop\WoWUpdateMonitor\worker.js
```

APK vigente en escritorio:

```text
C:\Users\ELY\Desktop\WoWUpdateMonitor-v3.1-auto-updater.apk
```

WowPushSender:

```text
C:\Users\ELY\Desktop\WowPushSender
C:\Users\ELY\Desktop\wow-push-sender.html
C:\Users\ELY\Desktop\WowPushSender.apk
```

## Cloudflare Worker

Worker público:

```text
https://orange-meadow-c3f6.eygelias.workers.dev
```

Endpoints importantes:

```text
/fetch
/check
/test-fcm
/register-token
/unregister-token
/app-version
/notify-app-update
```

`/app-version` lee `version.json` desde GitHub.

`/notify-app-update` debe enviar aviso solo por FCM a teléfonos Android. No debe enviar Telegram.

Cambio pendiente/manual si no se hizo deploy: pegar `C:\Users\ELY\Desktop\WoWUpdateMonitor\worker.js` en Cloudflare y hacer Deploy. La versión local del worker manda FCM como data-only para que la app pueda guardar el historial cuando recibe notificaciones.

## Firebase / FCM

La app usa FCM para recibir notificaciones. El Worker hace broadcast a tokens registrados.

Regla importante:

- Para que la app guarde historial, el Worker debe enviar `title` y `body` dentro de `data`, no solo como `notification`.
- `app_update` usa data payload para que `MyFirebaseMessagingService` guarde la info y muestre actualización.

## Telegram

Canal/chat usado por el Worker para avisos de WoW:

```text
-1004240877348
```

Bot conocido:

```text
@updatewow_bot
```

Importante: botón `Avisar nueva versión` de WowPushSender NO debe enviar Telegram. Solo FCM a teléfonos Android.

## Versión actual de Android app

Versión vigente:

```text
versionCode = 4
versionName = 3.1
APK = WoWUpdateMonitor-v3.1-auto-updater.apk
```

Build verificado:

```text
BUILD SUCCESSFUL
APK Android válido, ~6.7 MB
SHA256: a335537e86c7ebfb5a133eeb2635106249e9c1926434886aa28e32725eb7a031
```

## Cambios implementados hasta v3.1

### v3.0

Primera versión con auto-updater:

- Recibe FCM `app_update`.
- Guarda datos de actualización.
- Consulta `/app-version` al abrir.
- Si hay versión mayor, muestra diálogo obligatorio.
- Descarga APK desde GitHub dentro de la app.
- Abre instalador Android.
- Agrega permiso `REQUEST_INSTALL_PACKAGES`.
- Agrega `FileProvider` para abrir APK descargada.

### v3.1

Reparación de sección notificaciones:

- Se eliminó desplegable de “Últimas notificaciones”.
- Se cambió a vista tipo chat.
- Última actualización aparece abajo.
- Hay scroll vertical para subir/bajar.
- Mantener presionado mensaje abre menú `Eliminar`.
- Elimina solo el mensaje seleccionado.
- Historial se guarda oldest -> newest para comportarse como chat.

Archivos tocados:

```text
app/src/main/res/layout/section_notifications.xml
app/src/main/java/com/wowmonitor/app/service/MyFirebaseMessagingService.kt
app/src/main/java/com/wowmonitor/app/ui/MainActivity.kt
app/build.gradle.kts
worker.js
```

## Detalles técnicos de notificaciones tipo chat

`MyFirebaseMessagingService.saveNotification()` ahora guarda historial en orden viejo -> nuevo.

`getNotifications()` devuelve lista en ese orden.

`deleteNotification(context, index)` borra mensaje por índice.

`MainActivity.loadNotifications()`:

- Llena `notifChat` dinámicamente.
- Usa `ScrollView` `notifScroll`.
- Al final ejecuta scroll al fondo con `scroll.fullScroll(View.FOCUS_DOWN)`.
- Cada burbuja tiene long press con `PopupMenu` → `Eliminar`.

`section_notifications.xml` ahora usa:

```text
ScrollView @id/notifScroll
LinearLayout @id/notifChat
```

## Compilación Android

Comando usado en esta máquina:

```bash
export JAVA_HOME="/c/Users/ELY/android-build/jdk-17.0.19+10"
export ANDROID_SDK_ROOT="/c/Users/ELY/android-build/android-sdk"
"$JAVA_HOME/bin/java" -cp "C:/Users/ELY/Desktop/WoWUpdateMonitor/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain -p "C:/Users/ELY/Desktop/WoWUpdateMonitor" assembleDebug --no-daemon
```

Salida APK:

```text
C:\Users\ELY\Desktop\WoWUpdateMonitor\app\build\outputs\apk\debug\app-debug.apk
```

Luego copiar a:

```text
C:\Users\ELY\Desktop\WoWUpdateMonitor-v3.1-auto-updater.apk
```

## WowPushSender

App separada para enviar avisos manuales.

Agregado:

- Botón `Leer versión GitHub`.
- Botón `Avisar nueva versión`.

Botón `Avisar nueva versión` llama:

```text
https://orange-meadow-c3f6.eygelias.workers.dev/notify-app-update
```

No debe mandar Telegram.

## Flujo recomendado para distribuir

1. Enviar v3.0 o v3.1 manualmente a amigos.
2. Ellos instalan APK una vez.
3. Desde v3.0 en adelante la app puede recibir aviso de actualización.
4. En WowPushSender tocar `Leer versión GitHub`.
5. Si muestra v3.1, tocar `Avisar nueva versión`.
6. Teléfonos reciben FCM y descargan/instalan nueva APK desde GitHub.

## Reglas para futuros cambios

1. Trabajar siempre sobre última versión: v3.1 o superior.
2. Mantener solo una APK vigente en escritorio.
3. Cada cambio debe:
   - modificar código local,
   - compilar,
   - verificar APK,
   - subir release a GitHub si cambia APK,
   - actualizar `version.json`,
   - actualizar este contexto en repo `Contexto-WoWUpdateMonitor`.
4. No guardar secretos en este contexto.
5. No incluir tokens, claves Firebase, credenciales Cloudflare ni claves privadas.

## Estado final antes de crear repo de contexto

- APK v3.1 creada.
- Release v3.1 subido.
- `version.json` apunta a v3.1.
- Escritorio limpio: solo queda `WoWUpdateMonitor-v3.1-auto-updater.apk`.
- Falta confirmar manualmente si Worker nuevo ya fue deployado en Cloudflare.
