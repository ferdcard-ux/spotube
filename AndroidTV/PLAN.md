# Spotube — Plan Técnico Completo: Compatibilidad 100% Android TV

> **Módulo:** `:AndroidTV` — directorio `/AndroidTV` (capa separada de `:composeApp`, `:plugin_interfaces`, `:js_plugin_example`)
> **Tipo:** Librería Android (`com.android.library`) con actividad Leanback propia. Un solo APK, dos actividades (`MainActivity` móvil + `MainActivityTv`), misma `applicationId`.
> **Stack TV:** Jetpack Compose TV (`androidx.tv:tvfoundation`, `androidx.tv:tvmaterial`) — **no** Leanback Views. Media3 ExoPlayer reutilizado de `:composeApp`.
> **Estado de hoy:** Sin soporte TV intencional. Este plan es el camino a 100% (descubrible + navegable por D-pad + legible a 10 pies + publicable en Play TV).
> **Reglas que no se rompen:** `gradle/libs.versions.toml` como única fuente de versiones, strings vía `strings.xml` + `stringResource(Res.string.*)`, ViewModels dueños de estado (los Composables TV son renderers), no `HttpClient(...)` directo (inyectar `HttpClientFactory`), no `ktor-client-cio`, no tocar `SwingMainDispatcherFactory`, Gradle wrapper solamente.

---

## 1. Objetivo y alcance

### 1.1 Objetivo
Que Spotube sea instalable, descubrible, navegable y usable al 100% en Android TV (Leanback) con mando a distancia (D-pad), sin regresiones en móvil/Desktop/iOS, y publicable en Play Console con filtro Digital TV.

### 1.2 Definición de "100%"
- Aparece en el home de Android TV (categoría `LEANBACK_LAUNCHER`) y en Play TV.
- Toda superficie principal es operable sin puntero/táctil: foco espacial 2D correcto, foco inicial por destino, foco restaurado al volver, scroll-into-view.
- Texto legible a 3 m (≥24sp cuerpo), targets ≥56dp, contenido dentro de overscan-safe area.
- Playback con teclas de medios (Media3) y foreground service funciona; foco de audio correcto.
- Búsqueda y filtros operables sin teclado físico (teclado TV o voz).
- Sin barras/scrollbars/touch-only flows que bloqueen el progreso.
- Pasa la matriz de verificación del §12 y CI del §13.

### 1.3 Fuera de alcance (explícito)
- Migrar toda la UI a Compose TV (solo pantallas TV en `/AndroidTV`).
- Reescribir `PlaybackService` / `AudioPlayer` (ya TV-correctos, §3.3).
- TV channels (`tvprovider`) y `BrowseFragment` suggestions en v1 (opcionales, Fase 9, no bloquean lanzamiento).
- Android Auto / Automotive (ya presente vía `car.app`, no se toca).
- Fork de `Paths` / permisos más allá de lo descrito en Fase 8.

---

## 2. Estado actual (evidencia verificada)

| Área | Evidencia | Conclusión |
|---|---|---|
| Build | `composeApp/build.gradle.kts:179-183` `androidTarget()` única; `minSdk 28 / targetSdk 37 / compileSdk 37` (`libs.versions.toml:17-19`) | OK para TV, sin flavor TV |
| Manifest | `composeApp/src/androidMain/AndroidManifest.xml:50-58` solo `LAUNCHER`; sin `banner` (líneas 36-44), sin `uses-feature leanback/touchscreen` | No descubrible en TV, filtrado por Play |
| Deps | 0 hits `androidx.tv` en todo el repo; `libs.versions.toml:157-159` solo `media3-common/exoplayer/session` | Sin TV Compose |
| Detección | `Platform.kt:20-42` sin `AndroidTV`, sin `isTelevision`/`UiModeManager` | En TV cae en rama móvil |
| Shell | `AppShell.kt:87-294`: `useSidebar` (≥840dp) o `CompactPlayerOverlay` (bottom-sheet swipe). `AppShellViewModel.kt:48` `sidebarMinWidth = 840.dp` | No hay tercer modo TV |
| Foco | 0 `onPreviewKeyEvent`, 0 `bringIntoViewRequester`, 0 `focusRestorer`. Solo `focusGroup()` en `MediaCardSurface.kt:73` y un `FocusRequester` de search en `LibraryScreen.kt:254` | D-pad no fiable |
| Gestos | Drag-scroll (`VerticalScrollbar.kt:33`), swipe player (`AppShell.kt:335-350`), drag-reorder (`PlayerQueueContent.kt:78`), pull-refresh (`HomeScreen.kt:209`), `combinedClickable` (`TrackList.kt:31`) | Bloquean sin puntero |
| 10-foot | `AppTheme.kt:150-164` cuerpo 16sp / label 11sp; targets 32-44dp; `enableEdgeToEdge()` sin overscan (`MainActivity.kt:34`) | Ilegible / recortado |
| Ingreso texto | IME-only (`MillenniumTextField`), sin teclado TV ni voz | Search/Library inoperables |
| Reutilizable | Media3 1.10.1 + `PlaybackService.kt` + `AudioPlayer.android.kt`; `currentWindowWidthBreakpoint()`; `InputMode.Touch` check en `PlayableCard.kt:99` | Base que se reutiliza |

---

## 3. Decisiones arquitectónicas (por qué esta forma)

### 3.1 Módulo separado `/AndroidTV` como `com.android.library` (decisión principal)
- **Pros:** separación física real (lo pedido), `androidx.tv:*` Android-only no contamina `commonMain` (iOS/Desktop intactos), un solo APK (misma `applicationId`, mismo `PlaybackService`, misma firma, DataStore compartido), Play TV descubre vía merger del manifest de la librería.
- **Dirección de dependencia:** `:composeApp` → `:AndroidTV`. Nunca al revés (librería → app no es válido en AGP). `:AndroidTV` es UI pura Android/Jetpack-TV, no conoce `commonMain`.
- **Puente:** el código TV compartido que necesita acceso a pantallas/ViewModels de `commonMain` vive en `:composeApp/androidMain` (que sí puede importar `org.spotube.tv.*`) y delega a `:AndroidTV`. Lo puramente TV (shell, foco, safe-area, tipografía, tarjetas TV) vive en `/AndroidTV` y es consumido desde `androidMain`.

```
:composeApp
  (commonMain)  Home/Library/Search/Settings ViewModels + pantallas compartidas
  (androidMain) MainActivity (LAUNCHER) ──► delega si isTelevision() ──► MainActivityTv
                PlaybackService (Media3)   (declarado en app manifest, visible para ambas activitities)
                Bridges.kt                 (adaptadores commonMain ↔ TV)
       │ implementation(project(":AndroidTV"))
       ▼
:AndroidTV  (com.android.library, org.spotube.tv)
  MainActivityTv (LEANBACK_LAUNCHER)
  TvShell / TvRail / TvTopTabs / TvMiniPlayer / TvPlayerDialog
  TvFocusManager / focus extensions / bringIntoView
  TvSafeArea / TvTypography / TvTheme wrapper
  TvCards / TvRows / TvGrid / TvKeyboard
  (NO business logic, NO ViewModels propios; todo estado en ViewModels de :composeApp)
```

### 3.2 Dos actividades en un APK (no `applicationId` separado)
- `MainActivity` = `LAUNCHER` (móvil). `MainActivityTv` = `LEANBACK_LAUNCHER` (TV).
- Mismo `applicationId = org.spotube.spotube`. Sin duplicar firma/DataStore/Play listing. El launcher de TV muestra la segunda; el de móvil la primera.
- **Alternativa descartada:** `applicationId` separado (`org.spotube.spotube.tv`) — duplicaría firma, listing y DataStore; se revisitara solo si Play exige listings distintos.

### 3.3 No bifurcar playback
`PlaybackService` + `AudioPlayer` (Media3 ExoPlayer, audio focus, noisy handling) ya son la pila TV. `:AndroidTV` **no** crea servicio propio. En Fase 9 solo se amplía `MediaBrowseHelper` para browsable TV (hierarchy para la fila "Recently played", no para canales).

### 3.4 `expect/actual` mínimo y justificado
AGENTS.md: no `expect/actual` para adaptividad pura Compose/Material3. TV sí lo justifica (necesita `UiModeManager`/`PackageManager`, APIs de plataforma). Un solo `expect fun isTelevision(): Boolean` + `LocalIsTv` en `commonMain`; actuals en `androidMain` (vía `UiModeManager`) y stub `false` en `jvmMain`/`iosMain`.

### 3.5 TV Compose solo en `/AndroidTV` (nunca en `commonMain`)
`androidx.tv:tvfoundation`/`tvmaterial` son artefactos Android-only. Van en dependencias de `androidMain` de `:composeApp` (transitivas vía `:AndroidTV`) o en `:AndroidTV/build.gradle.kts`. **Prohibido** en `commonMain`/`mobileMain` (rompe iOS/Desktop).

---

## 4. Estructura de directorio propuesta

```
AndroidTV/                                  ← NUEVO (módulo :AndroidTV)
├── build.gradle.kts                        # com.android.library + Compose + tv deps
├── src/main/
│   ├── AndroidManifest.xml                 # banner + LEANBACK_LAUNCHER + features (merger)
│   ├── java/org/spotube/tv/
│   │   ├── MainActivityTv.kt               # Activity Leanback, entry TV
│   │   ├── TvApp.kt                        # Compose root TV (theme + shell)
│   │   ├── shell/
│   │   │   ├── TvShell.kt                 # Rail izquierdo + top tabs + contenido + mini player
│   │   │   ├── TvRail.kt                   # Rail 80-96dp, filas ≥56dp, foco
│   │   │   ├── TvTopTabs.kt                # Tabs Library/Settings (cuando aplique)
│   │   │   └── TvMiniPlayer.kt             # Barra inferior compacta (no bottom-sheet)
│   │   ├── focus/
│   │   │   ├── TvFocusManager.kt           # FocusRequester por destino + focusRestorer
│   │   │   ├── TvFocus.kt                  # Extensiones Modifier: bringIntoView, focusGroupRows
│   │   │   └── TvKeys.kt                   # onPreviewKeyEvent helpers (dirección, back, media)
│   │   ├── theme/
│   │   │   ├── TvTypography.kt             # ≥24sp cuerpo, ≥32sp títulos
│   │   │   └── TvSafeArea.kt               # Overscan-safe padding (48dp/27dp)
│   │   ├── components/
│   │   │   ├── TvCards.kt                  # PlayableCard TV (foco-driven, no hover)
│   │   │   ├── TvRows.kt                   # LazyRow TV con bringIntoView
│   │   │   ├── TvGrid.kt                   # LazyVerticalGrid TV (Adaptive 280dp / Fixed 4)
│   │   │   ├── TvPlayerDialog.kt           # Dialog fullscreen player (seek D-pad)
│   │   │   ├── TvQueueDialog.kt            # Dialog queue (↑/↓ para mover, no drag)
│   │   │   ├── TvKeyboard.kt               # Keyboard TV / voz (search, filtros)
│   │   │   └── TvEmptyState.kt             # Empty/error states 10-foot
│   │   └── navigation/
│   │       └── TvNavigation.kt             # Scene NavKey + InitialFocus per destination
│   └── res/
│       ├── drawable-xxxhdpi/ic_banner.png  # 320×180 (banner Leanback)
│       ├── values/dimens.xml               # overscan, rail width, min target
│       └── values/strings_tv.xml?          # NO: usar strings.xml de :composeApp (regla AGENTS.md)
└── PLAN.md                                 # este archivo (o README.md)
```

> **Convención de nombres:** Paquete `org.spotube.tv`. Actividad `org.spotube.tv.MainActivityTv`. Recursos: `ic_banner` requerido por Play TV.

---

## 5. Fase 0 — Manifest, banner y descubribilidad (bloqueante, ~1 día)

### 5.1 Tareas
- [x] Crear `/AndroidTV/src/main/AndroidManifest.xml` con `android:banner="@drawable/ic_banner"`, `<uses-feature leanback>`, `<uses-feature touchscreen required="false">` y la activity Leanback. El merger lo lleva al APK final de `:composeApp`.
- [x] Generar `res/drawable-xxxhdpi/ic_banner.png` 320×180 (arte Spotube sobre fondo oscuro, sin texto pequeño). También se añadió `drawable-xhdpi/ic_banner.png` 320×180 para paneles que piden el banner en densidad baja.
- [x] En `composeApp/src/androidMain/AndroidManifest.xml`: mantener `LAUNCHER`, **no** declarar `LEANBACK_LAUNCHER` ahí (vive en `:AndroidTV` para no duplicar). Añadir `<uses-feature touchscreen required="false"/>` a nivel app para que Play no filtre TV sin táctil.
- [x] `MainActivityTv` en librería con `screenOrientation="landscape"`, `configChanges="orientation|keyboardHidden|screenSize|screenLayout|smallestScreenSize|uiMode"` y `theme` TV (sin status bar).
- [x] `values/dimens.xml`: `tv_overscan_h = 48dp`, `tv_overscan_v = 27dp`, `tv_rail_width = 96dp`, `tv_min_target = 56dp`.

### 5.2 Código (ejemplo, adaptar a merger real)
```xml
<!-- AndroidTV/src/main/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <uses-feature android:name="android.software.leanback" android:required="false" />
  <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
  <application android:banner="@drawable/ic_banner">
    <activity
        android:name=".MainActivityTv"
        android:exported="true"
        android:label="@string/app_name"
        android:screenOrientation="landscape"
        android:configChanges="orientation|keyboardHidden|screenSize|screenLayout|smallestScreenSize|uiMode"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
      <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
      </intent-filter>
    </activity>
  </application>
</manifest>
```
```xml
<!-- composeApp/src/androidMain/AndroidManifest.xml (app) -->
<uses-feature android:name="android.software.leanback" android:required="false" />
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
```

### 5.3 Verificación
```powershell
.\gradlew.bat :composeApp:assembleStableDebug
adb shell dumpsys package org.spotube.spotube | Select-String -Pattern "leanback|LEANBACK|banner|touchscreen"
# Esperado: FEATURE_LEANBACK presente, banner resuelto, activity LEANBACK_LAUNCHER listada
```

---

## 6. Fase 1 — Esqueleto del módulo y dependencias (~0.5 día)

### 6.1 Tareas
- [x] `settings.gradle.kts`: añadir `include(":AndroidTV")`.
- [x] `gradle/libs.versions.toml`: añadir versiones y librerías (fuente única, §3.5). No hardcodear versiones en `build.gradle.kts`.
- [x] `/AndroidTV/build.gradle.kts`: `com.android.library` + `org.jetbrains.kotlin.android` + Compose; `namespace = "org.spotube.tv"`, `compileSdk/minSdk/targetSdk` heredados de `libs.versions.toml` (no nuevos valores).
- [x] `:composeApp/build.gradle.kts` (`androidMain`): `implementation(project(":AndroidTV"))`.
- [x] Esqueleto: `MainActivityTv` que solo hace `setContent { TvApp() }` y `TvApp` placeholder con `Text("TV")` (string en `strings.xml`, no hardcodeado). El placeholder final no usa texto: `TvApp` solo pinta tokens + `TvBridge`, y el contenido real lo instala `MyApplication` (ver §3.1).
- [x] Mismo `applicationId`: la librería **no** define `applicationId` (solo `namespace`).

### 6.2 `libs.versions.toml` (añadir, versiones a fijar en primer build)
```toml
# [versions]
androidx-tv-foundation = "1.0.0-alpha14"   # fijar a la estable vigente al implementar
androidx-tv-material   = "1.0.0-alpha14"

# [libraries]
androidx-tv-foundation = { module = "androidx.tv:tvfoundation", version.ref = "androidx-tv-foundation" }
androidx-tv-material   = { module = "androidx.tv:tvmaterial", version.ref = "androidx-tv-material" }
```
> Nota: JetBrains Compose Multiplatform no publica `androidx.tv:*`. Por eso viven solo en Android. Si `tvmaterial` resulta demasiado acoplado a Material3, el MVP puede usar solo `tvfoundation` + componentes Millennium escalados (decisión documentada en Fase 4).

### 6.3 `/AndroidTV/build.gradle.kts` (esqueleto)
```kotlin
plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid) // o kotlinMultiplatform según AGP 9.1.0; documentar elección
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}
android {
    namespace = "org.spotube.tv"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
dependencies {
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    // Solo lo necesario para UI; ninguna dependencia de negocio
}
```

### 6.4 Verificación
```powershell
.\gradlew.bat :AndroidTV:assembleDebug :composeApp:assembleStableDebug :composeApp:check
```

---

## 7. Fase 2 — Detección TV y enrutamiento (~1 día)

### 7.1 Tareas
- [x] `Platform.kt` (commonMain): `expect fun isTelevision(): Boolean` + `val LocalIsTv = compositionLocalOf { false }` + `fun rememberIsTv() = ...`.
- [x] Actuals: `androidMain` → `UiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION` (fallback `hasSystemFeature(FEATURE_TELEVISION)`); `jvmMain`/`iosMain` → `false`.
- [x] `MainActivity` (móvil, `:composeApp/androidMain`): al iniciar, si `isTelevision()` entonces `startActivity(Intent(this, MainActivityTv::class.java)); finish()`. (Defensa en profundidad aunque el launcher ya rutee bien.)
- [x] `MainActivityTv` (`/AndroidTV`): `enableEdgeToEdge()` **solo** con `TvSafeArea` (Fase 5), `setContent { TvApp() }`, `startService(PlaybackService)` igual que `MainActivity` (o reutilizar helper compartido `startPlaybackService()` en `androidMain`). El inicio real lo hace `TvBridge`/`initTvRuntime` (FileKit, NewPipe, `PlaybackService`).
- [x] `TvApp`: `CompositionLocalProvider(LocalIsTv provides true) { SpotubeTheme(...) { TvShell(...) } }`. Reutiliza `SpotubeTheme(userSettings)` de `commonMain` para no duplicar tema. El `LocalIsTv provides true` y el shell viven en `TvShellHost` (`androidMain`), no en `TvApp`: la librería no puede ver `SpotubeTheme` ni los ViewModels.

### 7.2 Código puente (en `:composeApp/androidMain`, no en librería)
```kotlin
expect fun isTelevision(): Boolean
// androidMain actual:
actual fun isTelevision(): Boolean {
    val ctx: Context = GlobalContext.get().get<Context>()
    val uiMode = ctx.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}
```

### 7.3 Verificación
- Emulador TV: log `isTelevision=true`, se abre `MainActivityTv` sin pasar por móvil.
- Móvil/emulador teléfono: `isTelevision=false`, comportamiento idéntico al actual.

---

## 8. Fase 3 — Tema TV, tipografía y overscan (~1–2 días)

### 8.1 Tareas
- [x] `/AndroidTV/.../theme/TvTypography.kt`: `TvTypography` con cuerpo ≥24sp, título ≥32sp, label ≥20sp (escalado sobre `AppDefaultTypography`, no reemplazo global). Seleccionado vía `LocalIsTv`. Se llama `TvType` en el código para no chocar con `Typography`.
- [x] `/AndroidTV/.../theme/TvSafeArea.kt`: `Modifier.tvSafeArea()` = `padding(horizontal = 48.dp, vertical = 27.dp)` (5% overscan) + `WindowInsets.displayCutout` cuando aplique. Aplicar en raíz de `TvShell`, **dentro** de `enableEdgeToEdge`. Implementado como padding fijo de overscan; no se consume `WindowInsets.displayCutout` todavía.
- [x] `dimens.xml`: constantes TV centralizadas (rail, targets, grid cell, foco ring).
- [x] Strings TV nuevos (`tv_*`, etc.) en `composeApp/src/commonMain/composeResources/values/strings.xml` vía `stringResource(Res.string.*)`. **Prohibido** texto hardcodeado en Composables TV (regla AGENTS.md).
- [x] Foco visual: anillo de foco ≥3dp accent, escala 1.04 en tarjeta enfocada, halo en `TvCards` (estilo `MediaCardSurface` pero driven por `isFocused`, no hover).

### 8.2 Verificación
- Screenshot TV 1080p: ningún elemento crítico en los 48dp/27dp externos; texto legible en captura a tamaño real; `adb shell uiautomator dump` confirma jerarquía con foco.

---

## 9. Fase 4 — Sistema de foco D-pad (núcleo, 1–2 sprints)

### 9.1 Tareas
- [ ] `/AndroidTV/.../focus/TvFocusManager.kt`: un `FocusRequester` por destino top-level (`Home/Search/Library/Settings`), `rememberSaveable` del último foco por ruta, `focusRestorer()` en `NavDisplay` entries (`NavigationState.kt:120-139`) para que al hacer pop se restaure el foco anterior. **Parcial:** existe `TvFocusManager` (mapa clave→`FocusRequester`) y `rememberTvFocusManager()`, pero todavía no se conecta a `NavDisplay` ni persiste el último foco por ruta.
- [x] `/AndroidTV/.../focus/TvFocus.kt`: extensiones `Modifier.tvFocusGroup()`, `Modifier.tvBringIntoView(requester)`, `Modifier.focusProperties { canFocus = true }` para items; cada `LazyRow`/`LazyColumn`/`LazyVerticalGrid` recibe `bringIntoViewRequester` mapeado por índice. **Parcial:** las extensiones existen y se usan en rail, teclado y cola; las filas/grids de las pantallas compartidas aún no se migraron.
- [ ] `/AndroidTV/.../focus/TvKeys.kt`: `onPreviewKeyEvent` en raíz del shell: `DirectionUp` en primera fila → rail; `DirectionDown` en rail → contenido; `MediaPlayPause`/`MediaNext`/`MediaPrevious` delegados a Media3 (no interceptar salvo `/AndroidTV` player); `Back` (sistema) → `navigator.pop()`; long-press `DpadCenter` abre menú de opciones (reemplaza `combinedClickable`). **Parcial:** `tvMediaKeys` está montado en la raíz del shell y `tvDirectionKeys` cubre seek/queue; el long-press no está implementado. Los bordes del rail ya cancelan la salida (`focusProperties`: ↑ en el primero, ↓ en el último, ← en todos; ver `docs/BITACORA.md` §9); → entra al contenido por búsqueda espacial.
- [ ] `TvNavigation.kt`: foco inicial por destino (ej. Home: primera tarjeta del primer row; Library grid: primera celda; Settings: primer item). **Parcial:** el archivo solo define `TvRailItem`/`TvTabItem`; no hay foco inicial por destino.
- [ ] Sin regresiones: en móvil el foco no cambia (solo `:AndroidTV` lo instala). No verificable sin compilar `:composeApp` (ver §22).

### 9.2 Verificación
```powershell
# D-pad smoke
adb shell input keyevent KEYCODE_DPAD_RIGHT
adb shell input keyevent KEYCODE_DPAD_LEFT
adb shell input keyevent KEYCODE_DPAD_UP
adb shell input keyevent KEYCODE_DPAD_DOWN
adb shell input keyevent KEYCODE_DPAD_CENTER
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_MEDIA_PLAY_PAUSE
# OK = foco visible siempre, nunca fuera de pantalla, nunca atrapado
```

---

## 10. Fase 5 — Shell TV y navegación (~1 sprint)

### 10.1 Tareas
- [x] `/AndroidTV/.../shell/TvShell.kt`: layout `Row` (rail + contenido + mini player inferior). **No** `UnstyledBottomSheet`, **no** `MillenniumBottomNavigationBar` (80dp) ni swipe-player.
- [x] `TvRail.kt`: rail izquierdo 80-96dp, items ≥56dp, icono+label 20sp (reutiliza datos `sidebarTabs` de `App.kt:104-151` vía puente en `androidMain`; la librería no importa `commonMain`). Se usan `bottombarTabs` (Home, Search, Library, Settings) resueltos con `stringResource` en `TvShellHost`.
- [x] `TvTopTabs.kt`: dentro de Library/Settings, tabs superiores focusables (reemplaza `LibraryTabs` `LazyRow` táctil). En Library se alimenta con `LibraryTab.entries` y `libraryState.onTabSelected`.
- [x] `TvMiniPlayer.kt`: barra persistente (arte, título, controles play/next/prev con foco), sin swipe; `OK` abre `TvPlayerDialog`.
- [x] `TvPlayerDialog.kt`: fullscreen Dialog (no BottomSheet): seek con `←/→` (±10s) vía Media3 `seekTo`, controles con foco, `Back` cierra.
- [x] `TvQueueDialog.kt`: Dialog (no `QueueSheet` drawer): lista con foco, reordena con D-pad (invoca `viewModel.onMove`), `Back` cierra; ocultar drag-handle. **Desviación:** `←`/`→` reordena y `↑`/`↓` mantiene el foco (no `↑/↓` como decía el plan); `Menu` elimina, `OK` reproduce.
- [ ] `AlternativeTrackSheet` → Dialog TV equivalente (mismo contenido, foco grupado). Pendiente: las pantallas compartidas siguen abriendo su diálogo actual.
- [ ] `LyricsScreen` TV: lista con foco, auto-scroll con `bringIntoView`, tamaño 10-foot. Pendiente: no se implementó una vista de letras TV.
- [ ] Onboarding (`Routes.Welcome`): flujo TV propio no bloqueante (login/keyboard si aplica; si no aplica en TV, bypassear a `Home`). **Parcial:** `TvShellHost` deja Welcome a pantalla completa (con `LocalIsTv` provisto) y el shell aparece al llegar a Home. En TV el foco inicial va al botón principal. No hay onboarding específico de TV, y el panel de pestañas de `MillenniumTabGroup` es un paso de foco sin indicador visual.

### 10.2 Verificación
- Recorrer Home → Search → Library → Settings solo con D-pad; subir al rail y bajar al contenido sin atajos; abrir player y queue con foco visible y cerrar con `Back`.

---

## 11. Fase 6 — Pantallas TV (Home, Search, Library, TrackList) (~2 sprints)

### 11.1 Home
- [ ] `/AndroidTV/.../components/TvRows.kt`: cada sección = `LazyRow` TV con `bringIntoView`, snap suave sin fling táctil; `Coverflow.kt:62-108` (tap-zones invisibles) → fila focusable con `←/→` = `scrollToItem`. **Parcial:** `TvRow` existe en la librería, pero `HomeScreen` sigue renderizando sus propias filas.
- [ ] `FeaturedCarousel.kt`: play button 56dp, foco ring, sin hover-only overlay. Pendiente.
- [ ] `TvCards.kt`: `PlayableCard` TV — overlay play/add-queue visible cuando `isFocused` (extiende `PlayableCard.kt:99 InputMode.Touch` con `InputMode.Key`/`LocalIsTv`); tamaños 200→280dp ancho. **Parcial:** `TvCard` existe y es focus-driven, pero las pantallas compartidas aún usan `MediaCardSurface`.

### 11.2 Library / TrackList
- [ ] `TvGrid.kt`: `GridCells.Adaptive(280dp)` o `Fixed(4)` (reemplaza `160dp` de `LibraryMediaCollection.kt:67`). **Parcial:** `TvGrid` existe, sin migrar `LibraryMediaCollection`.
- [ ] `TrackList` TV: filas ≥56dp, columnas índice/álbum ya presentes en `Expanded` (`TrackList.kt:215-218`) se mantienen; opciones vía long-press D-pad (no `combinedClickable`). Pendiente.
- [ ] Drag-scroll (`VerticalScrollbar.kt`) y pull-refresh (`HomeScreen.kt:209`): eliminados en TV (foco + `bringIntoView` los reemplazan). Pendiente en pantallas compartidas; el shell TV ya no usa bottom-sheets ni swipe-player.

### 11.3 Search
- [ ] `TvKeyboard.kt`: `androidx.tv` `Keyboard` como opción A; opción B voz `RecognizerIntent.EXTRA_VOICE_SEARCH` si teclado TV es frágil. Mantener IME para táctil. **Parcial:** `TvKeyboard` propio (Compose Foundation, D-pad) inyectado en `SearchScreen` vía `LocalTvSearchKeyboard`; `androidx.tv` no expone un `Keyboard` y la búsqueda por voz no se implementó.
- [ ] `SearchBar` TV: foco inicial en campo, `DpadCenter` abre teclado, `Back` cierra teclado primero y navega después. **Parcial:** el teclado se muestra al entrar y un botón `tv_keyboard_show` lo reabre; falta foco inicial explícito y el manejo de `Back` en dos pasos. `showTvKeyboard` vive en el Composable (desviación de la regla ViewModel-owned, pendiente de mover a `SearchScreenViewModel`).
- [x] `SearchScreen.kt:191 isExpanded = isDesktop && Expanded` → en TV usar `LocalIsTv` para layout expandido (no depende de `isDesktop`).

### 11.4 Todos
- [x] ViewModels sin cambios de firma (reutilizados). Interfaz entre `commonMain` y TV es solo datos de UI (`UiState`) + callbacks. La lógica de foco vive en `/AndroidTV` (presentación), no en ViewModels.
- [ ] Adaptive dialogs (`AdaptiveDropdownBottomSheet`/`AdaptiveDialogBottomSheet`): en TV forzar rama `DropdownMenu`/`Dialog` (nunca `ModalBottomSheet` swipe), con `focusGroup` e foco inicial en primer item. **Parcial:** ambos fuerzan la rama grande con `LocalIsTv.current`; falta `focusGroup` y foco inicial.

---

## 12. Fase 7 — Gestos eliminados y permisos / almacenamiento (1 sprint)

### 12.1 Tabla gesto → reemplazo TV
| Gesto hoy | Archivo | Reemplazo TV |
|---|---|---|
| Drag-scroll filas | `VerticalScrollbar.kt:33`, `HomeScreen.kt:74` | Foco + `bringIntoViewRequester` |
| Swipe bottom-sheet player | `AppShell.kt:335-350` | `TvMiniPlayer` + `TvPlayerDialog` |
| Drag-reorder queue | `PlayerQueueContent.kt:336` | `↑/↓` en `TvQueueDialog` |
| Pull-to-refresh | `HomeScreen.kt:209` | Botón foco “Refresh” (si se necesita) |
| `combinedClickable` long-press | `TrackList.kt:31` | Long-press D-pad `TvKeys` |
| Hover cards / hover text | `MediaCardSurface.kt:44`, `PlayerArtistsText.kt:48` | `isFocused` |

### 12.2 Permisos y `Paths`
- [ ] `LocalMediaPermission.android.kt`: en TV no pedir `READ_MEDIA_AUDIO` como en móvil si no hay archivos locales; si los hay, usar SAF (`ACTION_OPEN_DOCUMENT_TREE`) + `MediaStore` grant. **Parcial:** en TV devuelve `isGranted = true` sin pedir permiso; la ruta SAF no existe.
- [x] `Paths.android.kt:36` `getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)` — en TV usar `getExternalFilesDir()` / app-specific (no existe Downloads compartido en muchos TV).
- [x] `POST_NOTIFICATIONS`: inerte en TV (sin shade), no bloqueante; no pedir en TV. Solo está declarado en el manifest; ningún código Kotlin lo solicita en runtime.
- [x] `FOREGROUND_SERVICE_MEDIA_PLAYBACK` se mantiene (línea 24 del manifest app).

---

## 13. Fase 8 — Media3 TV (mínimo necesario, 2–3 días)

### 13.1 Tareas
- [ ] Sin fork de `PlaybackService`/`AudioPlayer`. Verificar `setHandleAudioBecomingNoisy(true)`, ducking y foreground promotion en TV real (no solo emulador).
- [ ] `MediaBrowseHelper.kt`: exponer hierarchy browsable para TV (roots: Recent, Library Playlists/Albums/Artists) reutilizando la misma `MediaLibraryService` (mismo `intent-filter` ya declarado).
- [ ] Opcional v1: `tvprovider` channels (sugerencias). No bloquea lanzamiento; se deja hooks en `TvApp` sin UI visible hasta tener contenido.

---

## 14. Fase 9 — Accesibilidad y 10-foot final (~1 sprint)

- [ ] TalkBack/TV VoiceView: foco accesible, `contentDescription` en todos los controles focusables (strings vía `strings.xml`).
- [ ] Escalado de texto del sistema: no romper con `fontScale` 1.3 (usar `sp`, scroll con foco, no truncar a `1 línea` sin foco).
- [ ] Overscan test en TV 1080p y 4K (5%, 3.5% y 0%); foco ring nunca cortado.
- [ ] Contraste Millennium dark/light en TV: verificar `onBackground` legible a 3 m.
- [ ] Animaciones: springs de `AppShell.kt:346-348` y `haze` blur (`FeaturedCarousel.kt:278`) atenuados en TV (performance en SoC TV modesto); sin blur que tape foco.

---

## 15. Fase 10 — CI, Play y release (~2–3 días + revisión Play)

- [ ] `.github/workflows/release.yml`: añadir job `buildAndroidTv` (`:AndroidTV:assembleRelease` o `:composeApp:assembleStableRelease` que ya incluye merger), APK/AAB firmado con mismo keystore `KEYSTORE` de Android (secrets existentes).
- [ ] `packaging/`: no aplica a TV (solo desktop). Dejar nota explícita “TV se distribuye solo vía Play”.
- [ ] Play Console (nuevo listing Digital TV, mismo `applicationId`): banner 320×180, ícono 512, screenshots 16:9 (TV), descripción 10-foot, categoría Music, “controlado por D-pad”, clasificación de contenido.
- [ ] Pre-launch report: D-pad traversal completo, sin crashes en emuladores TV 1080p/4K, sin ANR en cold start.
- [ ] Rollout: 1% → 10% → 100% con monitor de crashes Play (misma app, filtro TV).

---

## 16. Matriz de verificación (DoD — todo debe pasar)

| # | Check | Cómo | Esperado |
|---|---|---|---|
| V1 | `LEANBACK_LAUNCHER` presente | `dumpsys package \| grep LEANBACK` | Sí, activity `MainActivityTv` |
| V2 | Banner resuelto | `aapt dump xmltree app.apk AndroidManifest.xml` + `dumpsys` | `ic_banner` válido |
| V3 | Touchscreen no requerido | `dumpsys package \| grep touchscreen` | `required=false` |
| V4 | Instalable en TV sin táctil | Emulador TV `adb install` | OK sin error |
| V5 | D-pad home→search→library→settings | `adb input keyevent KEYCODE_DPAD_*` | Foco visible siempre, scroll-into-view, sin trampas |
| V6 | Foco inicial y restaurado | Navegar a detalle y `Back` | Foco vuelve a la tarjeta de origen |
| V7 | Player seek/play/next/prev | Teclas `MEDIA_*` + `←/→` en player dialog | Media3 responde, no UI táctil |
| V8 | Queue reorder `↑/↓` | En `TvQueueDialog` | Reordena sin drag |
| V9 | Search sin teclado físico | Teclado TV / voz | Texto ingresable, resultados focusables |
| V10 | 10-foot legible | Captura 1080p a 100%, overscan 5% | Cuerpo ≥24sp, nada crítico fuera de safe area |
| V11 | Sin regresión móvil | `:composeApp:check` + smoke manual móvil | IDéntico a antes |
| V12 | Lint Play TV | Pre-launch report | 0 errores clase TV |

---

## 17. Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| `androidx.tv:tvfoundation` alpha inestable | API changes | Fijar versión en `libs.versions.toml`, aislar en `:AndroidTV`, no filtrar a `commonMain` |
| Foco atrapado / fuera de pantalla | Bloqueo UX | `focusGroup` por fila + `bringIntoView` por índice + tests D-pad obligatorios |
| Millennium no es TV-native | Componentes focusables pobres | `TvCards`/`TvRows` con foco-driven, no depender de hover; `tvmaterial` solo donde aporte |
| SoC TV modesto + `haze` blur | Jank | Atenuar blur/sombras en TV, perfiles en release |
| Un segundo activity duplica `MainActivity` init | Divergencia | Helper compartido `initAppCommon()` en `androidMain`; `FileKit`/`NewPipe`/`Paths`/`startService` idénticos |
| Play rechaza por banner/screenshots | Retraso lanzamiento | Banner 320×180 exacto, screenshots 16:9 TV desde emulador, checklist §15 |

---

## 18. Estimación y orden de trabajo (recomendado)

| Fase | Contenido | Esfuerzo |
|---|---|---|
| 0 | Manifest + banner + features | S (≤1 d) |
| 1 | Esqueleto `:AndroidTV` + deps + CI smoke | S (0.5 d) |
| 2 | Detección + routing dos activities | S (1 d) |
| 3 | Tema/Tipografía/SafeArea | S–M (1–2 d) |
| 4 | Foco D-pad (núcleo) | M–L (1–2 sprint) |
| 5 | Shell + player/queue dialogs | M (1 sprint) |
| 6 | Pantallas Home/Search/Library/TrackList | L (2 sprint) |
| 7 | Gestos eliminados + permisos/Paths | M (1 sprint) |
| 8 | Media3 TV mínimo | S (2–3 d) |
| 9 | Accesibilidad 10-foot | M (1 sprint) |
| 10 | CI/Play/release | S–M (2–3 d + review) |
| **Total** | **A 100% publicable** | **~4–6 sprint** (1 sprint = 2 semanas, 1 dev) |

> Orden no negociable: Manifest y foco antes que pantallas. Sin foco fiable, las pantallas no se pueden validar.

---

## 19. Archivos que se crean / tocan (resumen)

**Crear en `/AndroidTV`:** `build.gradle.kts`, `src/main/AndroidManifest.xml`, `MainActivityTv.kt`, `TvApp.kt`, `shell/*`, `focus/*`, `theme/*`, `components/*`, `navigation/*`, `res/drawable-xxxhdpi/ic_banner.png`, `res/values/dimens.xml`.

**Tocar en `:composeApp`:** `settings.gradle.kts` (+`include(":AndroidTV")`), `libs.versions.toml` (versiones TV), `composeApp/build.gradle.kts` (`androidMain: implementation(project(":AndroidTV"))`), `androidMain/AndroidManifest.xml` (`touchscreen required=false`), `Platform.kt` + actuals (`isTelevision`), `MainActivity.kt` (delegation), `App.kt` / `AppShell.kt` (delegación mínima, no fork), `strings.xml` (strings TV), `.github/workflows/release.yml` (job TV).

**No tocar:** `SwingMainDispatcherFactory`, `HttpClientFactory`, `PlaybackService` core, `plugin_interfaces`, `js_plugin_example`, `gradle/` más allá de `libs.versions.toml`.

---

## 20. Criterios de aceptación finales (100%)

- [ ] APK instalable en Android TV sin táctil y visible en TV home + Play TV.
- [ ] Todas las rutas top-level y detalle operables solo con D-pad, foco inicial y restaurado, sin pantallas con foco perdido.
- [ ] Cero flujos gesto-only en TV (tabla §12.1 completa).
- [ ] Texto ≥24sp, targets ≥56dp, overscan-safe.
- [ ] Search y filtros operables sin teclado físico.
- [ ] Playback con teclas de medios y foreground service verificado en hardware TV real.
- [ ] `:composeApp:check` + `:AndroidTV:assembleRelease` en verde, sin regresión móvil/Desktop/iOS.
- [ ] Matriz V1–V12 en verde y listing Play TV aprobado.

---

## 21. Primer PR recomendado (vertical slice)

1. Fase 0 + 1 + banner (APK con `MainActivityTv` “Hola TV” focusable).
2. Fase 2 (routing) + Fase 3 (safe area + tipografía placeholder).
3. Home row TV con 3 tarjetas focusables + D-pad smoke.
4. A partir de ahí, pantallas y player en orden §11.

El slice valida merger, deps, foco y Play TV antes de invertir en todas las pantallas.

---

*Generado a partir de auditoría del repo (ver §2, archivos con línea). Sin supuestos de proveedor/modelo; el nombre de presentación del servicio no afecta este plan.*
