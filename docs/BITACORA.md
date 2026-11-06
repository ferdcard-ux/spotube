# Bitácora de Desarrollo — Spotube

> **Propósito:** Registro cronológico de todas las implementaciones, correcciones y decisiones técnicas del desarrollo.
>
> **Alcance:** la bitácora es el registro íntegro, **incluidos los intentos fallidos, las ideas descartadas y lo que quedó sin verificar**. Si el proyecto tiene un archivo aparte de notas de release (p. ej. `RELEASE_NOTES.md`), ese es el resumen publicable y la bitácora no lo sustituye: se remite a él, no se duplica.
> **Proyecto:** Spotube — fork independiente. Documentación y planeación en `AndroidTV/PLAN.md`, `docs/BITACORA.md` (este archivo).
> **Propósito del fork:** Añadir compatibilidad 100% Android TV mediante módulo separado `:AndroidTV` (capa `/AndroidTV`, paquete `org.spotube.tv`). Se mantiene intacto el núcleo móvil/Desktop/iOS de `:composeApp`.
> **Firma / BUILD:** **Este fork es independiente al desarrollo original Spotube.** Todos los artefactos de BUILD deben llevar **firma propia del fork**, bajo instrucciones previas al BUILD. No se reutiliza la firma, el `applicationId` de firma ni la identidad de release del proyecto original sin instrucción explícita del propietario. Antes de cualquier BUILD se debe confirmar: keystore propio (`local.properties` / `gradle.properties` vía `-P` / secreto CI), `applicationId`, `versionCode`, `versionName` y canal (`stable`/`nightly`) del fork.

---

## Cómo usar esta plantilla

Copia este archivo como `BITACORA.md` en el proyecto nuevo y sustituye:

- `<Nombre del Proyecto>` por el nombre real.
- Los `<...>` de cada sección por el contenido concreto.
- Elimina los bloques de ejemplo (cajas `>`) y las secciones que no apliquen.
- La numeración de secciones es correlativa (`1.`, `2.`, `3.`, ...) según avanza el desarrollo; agrega entradas nuevas al final, antes de `## Historial de Versiones`.
- Si una entrada **corrige** a otra anterior, no la borres: decláralo en `Corrige`. Las entradas históricas describen lo que se creía saber en ese momento y siguen siendo ciertas para esa fecha.
- No versiona archivos de sesión, volcados ni capturas temporales (`.session/`, `session-*.md`, `*.hprof`, PNG de trabajo).

### Los tres bloques que toda entrada lleva

Estos no son opcionales. Se añadieron porque su ausencia ha producido los errores de método más caros: cambios que declaraban estar verificados sin serlo, y entornos de prueba modificados en silencio.

| Bloque | Pregunta que responde | Por qué es obligatorio |
|---|---|---|
| `### Alcance del cambio` | ¿Qué toca y qué queda deliberadamente fuera? | Evita que un cambio para una plataforma rompa la otra sin querer |
| `No verificado / atajos de prueba` | ¿Qué **no** se comprobó y con qué atajos se forzó el escenario? | Un atajo no declarado convierte una prueba en una afirmación falsa |
| `### Cambios residuales en el entorno` | ¿Qué se dejó modificado en el dispositivo, servidor o red? | El entorno queda como estaba o no, y hay que decirlo |

### Qué cuenta como «verificado»

No todas las verificaciones valen lo mismo. Declara cuál de estas es la que hiciste:

| Nivel | Significado |
|---|---|
| **Verificado en hardware** | Observado en un dispositivo real, con la evidencia recogida |
| **Verificado por código** | Solo por lectura, compilación o prueba unitaria; **sin** observación en dispositivo |
| **No verificado** | Sin comprobar; problema abierto |

> Regla que ha costado caro más de una vez: **verificar la premisa antes que el resultado**. Antes de medir «el dispositivo reproduce», comprobar que el dispositivo reproduce. Una medición hecha sobre un estado de error es real y no concluye nada.

### Tipos de entrada

Se usa un bloque `###` por cada sección según el tipo de entrada:

| Tipo de entrada | Sub-bloques recomendados |
|---|---|
| Bug / diagnóstico | `Problema`, `Causa Raíz`, `Solución Aplicada`, `Verificación`, `Versión publicada` |
| Hotfix | `Contexto`, `Causa Raíz`, `Diagnóstico`, `Fix aplicado (archivo)`, `Verificación en hardware`, `Versión publicada`, `Notas de build` |
| Funcionalidad | `Implementación`, `Resultado verificado`, `Versión publicada` |
| Decisión técnica / nota | `Problema`, `Hallazgo`, `Decisión` (o `Receta`) |
| **Condición de entorno** | `Síntoma`, `Medición por variable`, `Receta`, `Alcance y límites` |
| **Registro de verificación** | `Qué se comprobaba`, `Métodos`, `Resultado`, `Qué no se pudo comprobar` |

Cada entrada termina con `---` para separarla de la siguiente.

---

## 1. Decisión técnica / nota — Creación de `/AndroidTV/PLAN.md` y constitución del fork independiente

### Problema
`<situación que motivó la nota>`
El fork de Spotube no tenía documentación de plan de trabajo para Android TV ni registro de bitácora integral. Se necesitaba:
1. Un plan técnico completo, público y versionable en el repo, con capa TV separada físicamente (`/AndroidTV`).
2. Una plantilla de bitácora adaptada a este repo para registrar desde ahora todos los trabajos (incl. intentos fallidos).
3. Dejar constancia explícita de que **el fork es independiente** y que todo BUILD debe hacerse con **firma propia bajo instrucciones previas**.

### Hallazgo
> Evidencia que revela el mecanismo real detrás del problema.
- Auditoría previa del repo (ver §2 y `AndroidTV/PLAN.md` §2): 0 soporte TV intencional. Manifest sin `LEANBACK_LAUNCHER`/`banner`, 0 `androidx.tv`, 0 `isTelevision`, 0 foco D-pad (`onPreviewKeyEvent`/`bringIntoViewRequester`/`focusRestorer` ausentes), gestos-only (drag-scroll, swipe-player, drag-reorder, pull-refresh, `combinedClickable`).
- Base reutilizable verificada por código: Media3 1.10.1 + `PlaybackService.kt` + `AudioPlayer.android.kt` (TV-correctos, no bifurcar); `currentWindowWidthBreakpoint()` mapea 960dp TV a `Expanded`.
- Decisión arquitectónica firme: módulo separado `:AndroidTV` como `com.android.library` (paquete `org.spotube.tv`), dos actividades en un APK (`MainActivity` `LAUNCHER` + `MainActivityTv` `LEANBACK_LAUNCHER`), Jetpack Compose TV (`tvfoundation`/`tvmaterial`) solo en Android, `expect fun isTelevision()` con actual `UiModeManager`.
- Firma: `composeApp/build.gradle.kts` usa `signingConfigs.release` desde `local.properties` (`release.signing.*`) y `buildTypes.release/debug` con firma release. El fork NO debe asumir que ese keystore es el propio: cada BUILD exige instrucción previa del propietario.

### Decisión / Receta
1. Crear `/AndroidTV/PLAN.md` (plan técnico completo: fases 0–10, matriz DoD V1–V12, estimaciones, riesgos, archivos a crear/tocar, primer PR vertical slice).
2. Crear `docs/BITACORA.md` desde `TEMPLATE_BITACORA.md`, adaptada a Spotube:
   - Mantener los 3 bloques obligatorios (`Alcance`, `No verificado`, `Cambios residuales`) y niveles de verificación.
   - Mantener recetas Android §5.1/§5.2 de la plantilla (condiciones de entorno).
   - Añadir §6 con recetas de firma del fork (ver abajo).
3. Antes de cualquier `assemble*`, `bundle*` o `package*`: pedir y registrar las instrucciones de firma del fork en la entrada correspondiente (`Notas de build` + `Historial de Versiones`), incluyendo:
   - keystore/firma propia y su origen (local vs CI),
   - `applicationId` resultante (debe coincidir con el del fork, no con el original si se decidió separar),
   - `versionCode` / `versionName` y canal (`stable`/`nightly`),
   - comando exacto ejecutado y hash/artefacto generado.
4. No tocar código de producto en esta entrada (solo documentación).
5. Registrar el inicio en `Historial de Versiones` (entrada sin publicación).

> Nota: si aplica, documenta trucos del entorno (puertos ADB que cambian, claves de sistema, etc.).
- N/A en esta entrada. Sin cambios de entorno.

### Corrige
> Si esta entrada deja obsoleto algo escrito antes, cita exactamente qué sección y por qué queda obsoleta. No borres la entrada anterior: corrígela desde aquí.
- No corrige a sección anterior (primera entrada de la bitácora).

### Alcance del cambio
- Toca: documentación (`AndroidTV/PLAN.md`, `docs/BITACORA.md`). Sin cambios de código, sin cambios de build, sin cambios de manifest antes de las Fases 0/1 del plan.
- Fuera de alcance: implementación TV (Fases 0–10), firma/BUILD, migración de sesión, publicación.

### No verificado / atajos de prueba
- Plan verificado por código/lectura del repo (§2 evidencias con archivo y línea). **No verificado en hardware**: ningún emulador TV, ningún `dumpsys`/D-pad, ninguna compilación `:AndroidTV` (módulo aún no creado).
- Atajo usado: auditoría por subagente explore + lecturas directas (`build.gradle.kts`, `AndroidManifest.xml`, `libs.versions.toml`, `Platform.kt`, `MainActivity.kt`, `App.kt`, `AppShell.kt`, `AppTheme.kt`, `NavigationState.kt`). Pendiente de validación: la totalidad de `AndroidTV/PLAN.md` cuando se implemente.

### Cambios residuales en el entorno
- `<ajuste o archivo>`: `<dónde se cambió>` → `<cómo volver al estado original>`
- `AndroidTV/PLAN.md`: creado en `/AndroidTV/` — permanente (documento del proyecto).
- `docs/BITACORA.md`: creado en `/docs/` — permanente. Plantilla original en `C:\Users\migue\Downloads\TEMPLATE_BITACORA.md` **sin modificar**.
- Ningún ajuste de dispositivo, servidor, red, SDK, emulador o `local.properties`. Ningún cambio de firma/BULD.

---

## 2. Auditoría inicial Android TV — compatibilidad del desarrollo

### Problema
¿El desarrollo actual tiene compatibilidad de uso en Android TV?

### Causa Raíz (resumen)
Ausencia sistémica de soporte TV intencional, no un solo bug:

1. `composeApp/src/androidMain/AndroidManifest.xml:50-58`: solo `LAUNCHER`; sin `LEANBACK_LAUNCHER`, sin `banner` (36-44), sin `uses-feature leanback`/`touchscreen required=false`.
2. `gradle/libs.versions.toml`: 0 `androidx.tv` (sin `tvfoundation`/`tvmaterial`/`leanback`); `minSdk 28/targetSdk 37` OK.
3. `Platform.kt:20-42`: sin `AndroidTV`, sin `isTelevision`/`UiModeManager`; en TV cae en rama móvil.
4. `AppShell.kt:87-294`: `useSidebar` (≥840dp) o `CompactPlayerOverlay` swipe — no hay tercer modo TV.
5. Foco: 0 `onPreviewKeyEvent`, 0 `bringIntoViewRequester`, 0 `focusRestorer`; solo `focusGroup()` en `MediaCardSurface.kt:73` y un `FocusRequester` en `LibraryScreen.kt:254`.
6. Gestos: drag-scroll (`VerticalScrollbar.kt:33`), swipe-player (`AppShell.kt:335-350`), drag-reorder (`PlayerQueueContent.kt:78`), pull-refresh (`HomeScreen.kt:209`), `combinedClickable` (`TrackList.kt:31`).
7. 10-foot: `AppTheme.kt:150-164` cuerpo 16sp / label 11sp; targets 32-44dp; `enableEdgeToEdge` sin overscan (`MainActivity.kt:34`).
8. Ingreso texto IME-only, sin teclado TV ni voz.

### Alcance del cambio
- Toca: solo diagnóstico (lectura de código y config). Sin modificaciones de código.
- Fuera de alcance: plan, implementación, firma/BUILD (el plan está en §1 y `AndroidTV/PLAN.md`).

### Solución Aplicada
- Auditoría de compatibilidad Android TV con reporte estructurado (manifest, SDK/deps, detección, shell, foco, listas, 10-foot, reutilizable, gaps G1–G15 y roadmap mínimo). Detalle completo en `AndroidTV/PLAN.md` §2–§3.

### Verificación
- **Verificado por código** (lectura + grep exhaustive `leanback|isTelevision|UiModeManager|tvfoundation|focusRequester|onPreviewKeyEvent|BringIntoView|WindowSizeClass|overscan|touchscreen` → sin hits TV): reportado en §2 con archivo y línea.
- Emulador TV / TV real: **no** probado (no hay APK TV).

### No verificado / atajos de prueba
- **Por código / pendiente de validar a mano**: toda la tabla de gaps G1–G15 y la matriz DoD V1–V12 de `AndroidTV/PLAN.md` §16. Hasta implementar Fases 0–10 y probar en hardware TV, el “100%” es un plan, no una afirmación de producto.

### Cambios residuales en el entorno
- Ninguno. Solo lectura. Subagente explore read-only, sin edición de código ni de entorno.

---

## 3. Condiciones de proyecto Android — instalación y verificación

> **Cuándo usar esta sección:** en proyectos Android, cópiala y complétala con tus propias medidas. Las
> condiciones de instalación varían **por fabricante y por versión de ROM**, y las de verificación
> varían **por versión de herramienta**. Lo de abajo está medido, no deducido: vuelve a medirlo en
> cada dispositivo antes de darlo por válido, porque una receta que «funcionaba» en otro proyecto
> puede fallar en el tuyo sin explicación aparente.

### 3.1 <Condición de entorno — instalación del APK en ROMs que restringen la instalación lateral>

#### Síntoma

`<mensaje completo, p. ej. INSTALL_FAILED_USER_RESTRICTED: Install canceled by user>`

#### Medición por variable
> Una prueba por cada vía, cambiando **una sola variable** cada vez. Sin aislar variables no se
> sabe qué es imprescindible y qué es accesorio, y la receta que se documente será falsa.

| Vía | `<ajuste del sistema>` | Resultado |
|---|---|---|
| `<vía A>` | `<sin ajuste>` | `<resultado>` |
| `<vía A>` | `= <valor>` | `<resultado>` |
| `<vía B sin flag>` | `= <valor>` | `<resultado>` |
| `<vía B con flag>` | `<sin ajuste>` | `<resultado>` |
| `<vía B con flag>` | `= <valor>` | **`<resultado correcto>`** |

#### Receta

> Solo las condiciones que la medición demostró **imprescindibles**. Si dos bastan, las dos van:
> documentar solo una produce recipes que fallan sin explicación.

```bash
<variable de entorno>
<comando de preparación>
<comando de instalación>
<comando de limpieza>   # no dejar artefactos en el dispositivo
```

#### Alcance y límites

- Medido en `<dispositivo, versión de ROM, versión de Android>`; **re-verificar en cada uno**.
- El ajuste `<nombre>` **persiste** entre reinicios, pero se pierde si se desactiva desde la
  interfaz. Indica si queda activo tras las pruebas y cómo revertirlo.
- Vías que **siguen bloqueadas** aunque el ajuste esté activo: `<...>`. Anotarlo evita volver a
  intentarlas.
- Ajustes que **no** deben tocarse: `<...>`, con el motivo.

---

### 3.2 <Condición de entorno — métodos para obtener resultados verificables en pruebas de ejecución>

#### Qué se comprobaba
`<objetivo de la verificación, en una línea>`

#### Métodos
> Ninguna herramienta da la verdad por sí sola: lo que la da es **triangular**, contrastando al
> menos dos vías independientes. Anota para cada método la trampa que falló en la práctica.

| Vía | Para qué sirve | Trampa medida |
|---|---|---|
| `<herramienta>` | `<uso>` | `<qué se reportó mal o cómo falló>` |
| `<herramienta>` | `<uso>` | `<...>` |
| `<fuente de verdad externa>` | `<uso>` | `<...>` |

#### Reglas de verificación

1. **Cuidado con los patrones de coincidencia.** Una búsqueda amplia casa también con el valor
   vacío del campo que se quiere excluir y produce un veredicto falso. Anota el patrón correcto.
2. **Una salida vacía no es evidencia de ausencia.** Si una herramienta no devuelve nada,
   contrástalo con otra antes de concluir que algo no existe.
3. **Verificar la premisa antes que el resultado.** Antes de medir «funciona», comprobar que la
   condición previa se cumple.
4. **Triangular contra el mundo real.** Cuando exista un canal de estado del propio sistema
   (endpoint, consulta, log con marca de tiempo), es más fiable que la inspección de la interfaz.

#### Correlación temporal
> Cuando el resultado dependa de una latencia, la forma fiable de medirla es **reconstruir la línea
> de tiempo** con marcas de tiempo de dos fuentes independientes, en lugar de comparar con un
> umbral supuesto.

| Hora | Fuente | Evento |
|---|---|---|
| `<HH:MM:SS.mmm>` | `<teléfono / servidor / dispositivo>` | `<evento>` |
| `<HH:MM:SS.mmm>` | `<teléfono / servidor / dispositivo>` | `<evento>` |

#### Resultado
`<conclusión con la medida concreta, no con una impresión>`

#### Qué no se pudo comprobar
> Todo lo que quedó fuera, y por qué. Es la parte más importante del registro.

- `<escenario>`: `<por qué no se comprobó>`
- `<vía real de usuario>`: `<pendiente de validación manual por bloqueo de la automatización>`

#### Cambios residuales en el entorno
- `<ajuste, archivo o servicio>`: `<dónde se cambió>` → `<cómo revertirlo>`

---

## 4. Condiciones del fork — firma y BUILD independiente

### Problema
El fork es independiente al desarrollo original Spotube. Los artefactos de BUILD deben llevar firma propia bajo instrucciones previas al BUILD. La firma existente en `composeApp/build.gradle.kts` (`signingConfigs.release` → `local.properties` `release.signing.*`) no debe asumirse como propia del fork.

### Hallazgo
- `composeApp/build.gradle.kts:405-437`: `signingConfigs.release` lee `release.signing.storeFile/storePassword/keyAlias/keyPassword` de `local.properties`; `release` y `debug` se firman con esa config si existe (debug comparte release key si configurada).
- `gradle.properties` (repo): `nightly=true` por defecto y ajustes de toolchain/Gradle. Cambiar canal/versionado altera `versionCode`/`versionName`/`applicationId` por flavor (`stable`/`nightly`, `bundleId = org.spotube.spotube`).
- Play TV (`AndroidTV/PLAN.md` §15) usa la misma `applicationId` y keystore del APK (single-APK, dos actividades). Firmar TV con otra key rompe updates y DataStore; por eso la firma es del **APK**, no de la activity.

### Decisión / Receta
1. Antes de **cualquier** BUILD (`assemble*`, `bundle*`, `package*`, `create*`, `packageGraalvm*`), pedir al propietario las instrucciones de firma del fork y registrarlas en la entrada correspondiente:
   - keystore propio: ubicación, alias, y si es local o CI (nunca hardcodear contraseñas en el repo),
   - `applicationId` resultante y canal,
   - `versionCode` / `versionName`,
   - comando exacto y artefacto/hash generado.
2. Validar que `local.properties` no contenga credenciales del proyecto original reutilizadas sin instrucción. Si las contiene, detener el BUILD y pedir instrucciones.
3. No publicar artefactos firmados con la firma del proyecto original bajo el nombre del fork.

### Corrige
- Añade una capa de restricción previa a cualquier BUILD. No corrige entradas anteriores como falsas; las declara incompletas en materia de firma hasta que se registren las instrucciones.

---

## 5. Funcionalidad — Implementación Android TV, Fases 0–7 (parcial)

### Implementación
Base TV según `AndroidTV/PLAN.md`; las casillas de cada fase marcan lo hecho y anotan **Parcial** donde aplica.

- **Módulo `:AndroidTV`** (`com.android.library`, `namespace = "org.spotube.tv"`): `TvBridge`, `MainActivityTv` (`LEANBACK_LAUNCHER`), `TvApp`, tema (`TvDimens`, `TvTypography`, `TvTheme`, `TvSafeArea`), foco (`TvFocus`, `TvFocusManager`, `TvKeys`), navegación, componentes (`TvButton`, `TvCard`, `TvRow`, `TvGrid`, `TvEmptyState`, `TvMiniPlayer`, `TvPlayerDialog`, `TvQueueDialog`, `TvKeyboard`) y shell (`TvShell`, `TvRail`, `TvTopTabs`). Banners `ic_banner.png` (xhdpi y xxxhdpi), `dimens.xml`, `themes.xml`, manifest con `leanback`/`touchscreen` opcionales.
- **Build:** `include(":AndroidTV")`; catálogo con `androidx-tv-foundation` 1.0.0, `androidx-tv-material` 1.1.0 (declaradas, sin uso en UI para no duplicar tema) y plugin `kotlinAndroid`; raíz con `alias(libs.plugins.kotlinAndroid) apply false`; `:composeApp` `androidMain` → `implementation(project(":AndroidTV"))`.
- **`:composeApp`:**
  - Detección: `Platform.kt` (`expect isTelevision`, `LocalIsTv`, `rememberIsTv`); actual Android en `core/platform/AndroidTvDetection.kt` (`UiModeManager` + `FEATURE_LEANBACK`); JVM/iOS → `false`.
  - Entrada: `AppShellHost` (`expect/actual`), Android ramifica a `tv/TvShellHost.kt`; `App.kt` usa `AppShellHost`. `MyApplication` instala `TvBridge`; `MainActivity` redirige a `MainActivityTv` en TV conservando el intent.
  - `TvShellHost`: rail desde `bottombarTabs`, tabs de Library vía `LibraryState`, mini player, diálogos de player (seek ±10 s) y cola (`PlayerQueueContentViewModel.onMove`), bypass de Welcome.
  - Pantallas compartidas: `SearchScreen` (layout expandido en TV + teclado TV por `LocalTvSearchKeyboard`), `LibraryScreen` (oculta sus tabs en TV), `Adaptive{Dropdown,Dialog}BottomSheet` (rama diálogo forzada en TV).
  - Permisos/rutas: `Paths.android.kt` usa Downloads de la app en TV; `LocalMediaPermission.android.kt` no pide permiso en TV.
  - Strings `tv_*` en `strings.xml`.
- **Desviación del plan:** en `TvQueueDialog` `←/→` reordena y `↑/↓` mueve el foco (el plan decía `↑/↓` reordena).

### Resultado verificado
- **Verificado por código (compilación):** `:AndroidTV:compileDebugKotlin` BUILD SUCCESSFUL, pero **solo** con un `compileSdk = 36` temporal (ya revertido) y `--configure-on-demand --no-configuration-cache`. Con el `compileSdk = 37` real falla igual que el resto (ver Cambios residuales).
- **`:composeApp`: no compilado.** Revisión estática de los archivos tocados, sin compilación ni `:composeApp:check`.
- **No verificado en hardware.** Ni emulador TV ni TV real.

### Versión publicada
- Ninguna. Sin BUILD firmado: siguen pendientes las instrucciones de firma del fork (§4).

### Alcance del cambio
- Toca: módulo nuevo `/AndroidTV`, wiring Android de `:composeApp`, y cambios mínimos en `commonMain` guardados por `LocalIsTv` (en móvil/Desktop/iOS `LocalIsTv` es `false` y la rama previa se conserva).
- Fuera de alcance: `PlaybackService`/`AudioPlayer`, `HttpClientFactory`, `SwingMainDispatcherFactory`, `plugin_interfaces`, `js_plugin_example`, CI/release, firma.
- Pendiente del plan (marcado `[ ]` o **Parcial** en `PLAN.md`): foco restaurado por ruta y foco inicial por destino (Fase 4), atajos rail↔contenido y long-press D-pad, `AlternativeTrackSheet`/Lyrics/onboarding TV (Fase 5), migración de filas/grids/cards de Home y Library a componentes TV (Fase 6), ruta SAF para medios locales (Fase 7), Fases 8–10 completas.

### No verificado / atajos de prueba
- Sin compilar `:composeApp`, cualquier error de tipos en `TvShellHost.kt`, `SearchScreen.kt` o `LibraryScreen.kt` sigue sin detectar. Es el primer paso en cuanto se desbloquee el entorno.
- `SearchScreen` guarda `showTvKeyboard` en el Composable (`remember`), lo que se desvía de la regla «ViewModels own all state». Pendiente de mover a `SearchScreenViewModel`.
- `LocalMediaPermission.android.kt` vuelve antes de los `remember` en TV. Es estable porque la detección TV no cambia en un proceso, pero es un retorno condicional dentro de un Composable.
- Editar la cola mientras hay un reorden pendiente en `TvQueueDialog` deja el buffer desactualizado (caso no tratado).
- La matriz DoD V1–V12 sigue sin probar.

### Cambios residuales en el entorno
- `compileSdk = 36` temporal en `AndroidTV/build.gradle.kts`: **revertido** al catálogo (`libs.versions.android.compileSdk`).
- Bloqueo 1 (sin cambio aplicado): `cargo`/`rustc` no están en el PATH; `:composeApp` no configura (Gobley necesita Rust para los bindings `uniffi`, p. ej. `DiscordRpcClient`).
- Bloqueo 2 (sin cambio aplicado): el SDK local tiene `platforms/android-37` registrado como `platforms;android-37.0` (`package.xml`), y AGP 9.1.0 con `compileSdk = 37` falla con `Failed to find target with hash string 'android-37'`. Reproducido el 2026-11-03 con `:AndroidTV:compileDebugKotlin`.
- Ningún cambio en dispositivos, emuladores, `local.properties` ni firma.

---

## 6. Condición de entorno — SDK 37 solo existe como `android-37.0`

### Síntoma
`:AndroidTV:compileDebugKotlin` (y cualquier tarea Android) falla con `Failed to find target with hash string 'android-37'`, aunque `platforms/android-37` está instalado.

### Medición por variable
- `sdkmanager --list` (2026-11-03): el repositorio de Google **no** publica `platforms;android-37`; solo `android-37.0`, `37.1`, `37.2` y betas de `37.2`. El instalado declara `path="platforms;android-37.0"` y `<api-level>37.0</api-level>` en `package.xml`.
- AGP 9.1.0 con `compileSdk = 37` busca el hash `android-37`, así que reinstalar el SDK no lo arregla.
- `compileSdkVersion("android-37.0")` → BUILD SUCCESSFUL (prueba descartada: saca la versión del catálogo).
- `compileSdk = 37` + `compileSdkMinor = 0` → BUILD SUCCESSFUL.

### Receta
- `gradle/libs.versions.toml`: nuevo `android-compileSdkMinor = "0"` junto a `android-compileSdk`.
- `AndroidTV/build.gradle.kts` y `composeApp/build.gradle.kts`: `compileSdkMinor = libs.versions.android.compileSdkMinor.get().toInt()` debajo de `compileSdk`.

### Alcance y límites
- **Verificado por código (compilación):** `:AndroidTV:compileDebugKotlin` BUILD SUCCESSFUL con el SDK 37.0 real y la receta final. Esto reemplaza la validación con `compileSdk = 36` temporal de §5.
- `:composeApp` tiene la misma línea, pero sigue sin configurarse por falta de Rust (Bloqueo 1 de §5), así que ahí no está verificado.
- Si se sube `android-compileSdk` (p. ej. a 37.2), hay que ajustar `android-compileSdkMinor` a la vez.

### Corrige
- §5, «Cambios residuales en el entorno», Bloqueo 2: queda resuelto con esta receta. No era un SDK mal instalado; era el nombre del paquete frente a AGP.

### Alcance del cambio
- Toca solo la configuración de build (catálogo y dos `build.gradle.kts`). Ningún código de producto.

### No verificado / atajos de prueba
- Compilado con `--configure-on-demand --no-configuration-cache` para no configurar `:composeApp`, que aún no configura sin Rust.

### Cambios residuales en el entorno
- Ninguno. Las dos pruebas temporales en `AndroidTV/build.gradle.kts` se sustituyeron por la receta final; el SDK local no se tocó.

---

## 7. Registro de verificación — Primer BUILD de `:composeApp` con la capa TV

### Qué se comprobaba
Que `:composeApp:assembleStableDebug` compile con todo el código TV de §5 y que el APK resultante declare la entrada Android TV.

### Métodos
1. Entorno preparado:
   - Rust 1.99.1 (`stable-x86_64-pc-windows-msvc`), instalado por el propietario.
   - Gobley añade solo los targets Android de Rust.
2. `plugin_interfaces/build.gradle.kts`: se le aplicó la receta de §6. Su DSL KMP (`androidLibrary`) no tiene `compileSdkMinor`, así que se usa `compileSdk { version = release(37) { minorApiLevel = 0 } }`, con los dos valores del catálogo. Es un cambio solo de build, no de la API de plugins.
3. `dev.nucleusframework:composewebview:0.1.0-SNAPSHOT` (no está en Maven Central), siguiendo el `README.md`:
   - Clonado `KRTirtho/ComposeNativeWebview` en el commit fijado por CI (`b11ac748`) en `C:\Users\migue\DEV\ComposeNativeWebview`.
   - Publicado con `gradlew publishToMavenLocal -x :webview-compose:verifyPublicationCoordinates`.
4. `gradlew :composeApp:assembleStableDebug`: el primer intento falló con 4 errores de compilación, todos en código TV:
   - `AndroidTvDetection.kt`: importaba `android.content.UiModeManager`. Corregido a `android.app.UiModeManager`.
   - `TvShellHost.kt`: faltaba `import org.spotube.spotube.App` (el archivo vive en el subpaquete `tv`).
5. Segundo intento: **BUILD SUCCESSFUL** (3 m 4 s), sin warnings en archivos TV.
6. Inspección del APK con `aapt2 dump xmltree` y `apksigner verify`.

### Resultado
- **Verificado por código (compilación + inspección del APK):**
  - Se genera `composeApp/build/outputs/apk/stable/debug/composeApp-stable-debug.apk` (~212 MB debug, 4 ABIs).
  - Manifest final: `package="org.spotube.spotube"`, `uses-feature` `android.software.leanback` y `android.hardware.touchscreen` con `required=false`, `android:banner` presente, `org.spotube.tv.MainActivityTv` con `LEANBACK_LAUNCHER`.
- **Firma:** `CN=Android Debug` (keystore debug por defecto de esta máquina; no hay `local.properties`). SHA-256 del certificado `220b40972e6f2039bdb09dd103ab25910ef726918a9f0cccf225928ec8f0d2c8`. **No** es la firma del proyecto original ni la del fork. Es un artefacto solo de verificación: no se distribuye (§4).

### Qué no se pudo comprobar
- Ejecución: el APK no se instaló en emulador TV ni en TV real. La matriz DoD V1–V12 sigue sin probar.
- `:composeApp:check` y los targets JVM/iOS no se ejecutaron. Los cambios en `commonMain` (`SearchScreen`, `LibraryScreen`, `Adaptive*`, `Platform`, `AppShellHost`) solo están compilados para Android.
- BUILD firmado del fork: sigue pendiente de instrucciones (§4).

### Alcance del cambio
- Código: dos correcciones de import en archivos TV de `androidMain`.
- Build: `plugin_interfaces/build.gradle.kts` (versión menor del SDK).
- Fuera de alcance: firma, release, CI.

### No verificado / atajos de prueba
- `verifyPublicationCoordinates` se omitió al publicar `composewebview`. En Windows no se construyen los targets iOS y la tarea lo detecta. Los metadatos publicados en `mavenLocal` apuntan a módulos iOS inexistentes; no afecta al build Android/JVM, pero esa copia local **no sirve para iOS**.
- `buildNativeWindows` de `composewebview` compiló la DLL x64. La ARM64 falló (`LNK1104 libcpmt.lib`, faltan librerías ARM64 de MSVC) y el script lo trata como aviso. No afecta al APK Android.
- La sesión del agente define `NoDefaultCurrentDirectoryInExePath=1`, que impide a `cmd` encontrar `build.bat`. Se quitó solo para ese proceso. En una terminal normal no ocurre.

### Cambios residuales en el entorno
- `C:\Users\migue\DEV\ComposeNativeWebview`: clon nuevo, fuera del repo. Incluye `nuget.exe`, el paquete NuGet `Microsoft.Web.WebView2` 1.0.2210.55 y las DLL en `webview-compose/src/jvmMain/resources/nucleus/native/win32-x64/`. → Revertir: borrar la carpeta.
- `%USERPROFILE%\.m2\repository\dev\nucleusframework\` (`composewebview`, `-android`, `-jvm`, `-wasm-js`) → Revertir: borrar esa carpeta.
- Rust: instalado por el propietario (`rustup`). Los targets Android de Rust los añadió Gobley (`rustup target add`) → Revertir con `rustup target remove <triple>`.
- Daemons de Gradle detenidos con `--stop` (sin efecto persistente).

---

## 8. Registro de verificación — Primera ejecución en emulador Android TV + fix de anillo de foco

### Qué se comprobaba
Que el APK debug de §7 se instale, se lance desde la entrada Leanback y sea navegable con D-pad.

### Métodos
- Emulador `MirraTV_TV` (`sdk_google_atv_x86`, Android TV API 34, x86, features `leanback`, `leanback_only`, `type.television`), en `emulator-5554`.
- Instalación con `adb -s emulator-5554 install -r`. Lanzamiento con `monkey -c android.intent.category.LEANBACK_LAUNCHER`.
- D-pad simulado con `adb shell input keyevent DPAD_*`. Capturas con `screencap`. Foco medido con `uiautomator dump` (nodo `focused="true"`). Crashes con `logcat -b crash`.
- Había conectados por adb una TV real (`Caixun_4K_RATV3`) y un teléfono (`23028RA60L`); **no** se tocaron, todo con `-s emulator-5554`.

### Resultado
- **Verificado en emulador TV (no en hardware real):**
  - `pm query-activities LEANBACK_LAUNCHER` devuelve `org.spotube.tv.MainActivityTv`. Al lanzarla queda como `topResumedActivity`, sin entradas en `logcat -b crash` en ningún momento de la sesión.
  - Welcome a pantalla completa (diseño de §5). Con D-pad: ↓ deja el foco invisible (área desplazable) y → llega a `Skip`; OK salta Welcome y el skip se guarda (el segundo arranque entra directo al shell).
  - Shell TV: rail (Home, Search, Library, Settings), aviso `tv_hint_dpad`, estado vacío «No metadata plugin selected» (esperado en instalación limpia).
  - Rail ↑/↓ + OK navega. Settings abre como diálogo con foco en el botón cerrar (rama forzada de `AdaptiveDialogBottomSheet`).
  - Library: `TvTopTabs` (Playlists/Albums/Artists/Local Tracks) visibles y tabs propias ocultas. → pasa del rail al contenido (`Install a plugin`, con desplazamiento automático); ↑ recorre la barra (icono de búsqueda) hasta las pestañas, que se desplazan con el foco; ← vuelve al rail.
- **Bug encontrado y corregido:** en `TvRail`, `TvTopTabs`, `TvCard` y `TvButton` (×2), `.onFocusChanged` iba **después** de `.clickable`, así que no observaba el nodo enfocable: `focused` nunca cambiaba y el anillo de foco y la escala no se pintaban. Se movió antes de `.clickable`. Tras recompilar y reinstalar, el anillo y la escala se ven en el rail y las pestañas.
- **Bug visual corregido:** `border(width = 0.dp)` dibuja una línea de 1 px en Compose, así que todos los ítems sin foco tenían contorno. Ahora el color es `Color.Transparent` sin foco (`TvRail`, `TvTopTabs`, `TvButton` ×2, `TvQueueDialog`). Verificado en captura.

### Qué no se pudo comprobar
- Search: sin plugin de metadatos la pantalla muestra el estado vacío, así que el campo y el teclado TV (`LocalTvSearchKeyboard`) **no se ejercitaron**.
- Reproducción, mini player, `TvPlayerDialog`, `TvQueueDialog` y teclas de medios: requieren contenido (plugin o música local).
- Hardware TV real, overscan, TalkBack, 4K.

### Alcance del cambio
- Código: solo `:AndroidTV` (`shell/TvRail.kt`, `shell/TvTopTabs.kt`, `components/TvCard.kt`, `components/TvButton.kt`, `components/TvQueueDialog.kt`): orden de modificadores y color de borde. Sin cambios en `:composeApp`.

### No verificado / atajos de prueba
- Teclas inyectadas por `adb input keyevent`, no por un mando físico.
- Problemas observados y **no corregidos** (pendientes del plan, Fases 4–5):
  - Welcome no tiene foco inicial claro: ↓ lleva el foco a un nodo invisible.
  - ↑ desde Home, el ítem superior del rail, salta a la pestaña Playlists del contenido en vez de quedarse. Faltan los límites rail↔contenido (`focusProperties`), ya marcados como pendientes en `PLAN.md` §9.
  - En Library, `TvTopTabs` comparte fila con el aviso de D-pad y la cuarta pestaña se corta («Local Trac»).
  - El ítem seleccionado del rail no se distingue cuando tiene el foco (el estilo de foco sustituye al de seleccionado).

### Cambios residuales en el entorno
- Emulador `MirraTV_TV` arrancado con `-no-snapshot-save` (no guarda estado al cerrarse). Tiene instalado `org.spotube.spotube` (debug) con el skip de Welcome guardado → Revertir: `adb -s emulator-5554 uninstall org.spotube.spotube`.
- `/sdcard/ui.xml` en el emulador (volcado de `uiautomator`) → borrar con `adb -s emulator-5554 shell rm /sdcard/ui.xml`.

---

## 9. Bug / diagnóstico — Fallos de foco D-pad detectados en §8

### Problema
Cuatro fallos observados en el emulador (§8, «No verificado»):
1. Welcome arranca sin foco claro.
2. ↑ desde el primer ítem del rail salta al contenido.
3. Las pestañas de Library se cortan («Local Trac»).
4. El ítem del rail con foco no se distingue del seleccionado.

### Causa Raíz
1. Welcome no pedía foco inicial. Además, en TV `TvShellHost` renderizaba Welcome **antes** de proveer `LocalIsTv`, así que ahí `LocalIsTv` valía `false`.
2. `TvRail` no tenía límites de foco: la búsqueda espacial del D-pad salía por el borde superior hacia el nodo más cercano del contenido.
3. `TvShell` ponía `TvTopTabs` y el aviso de D-pad en la misma `Row`; el aviso le quitaba ancho a las pestañas.
4. En `RailEntry`, el estilo de foco (`onBackground`, fondo `background`) sustituía al de seleccionado (`accent`).

### Solución Aplicada
1. `TvShellHost.kt`: la rama de Welcome también provee `LocalIsTv provides true`. `WelcomeScreen.kt`: en TV, un `FocusRequester` en el botón principal (Let's Go/Done) pide foco al entrar. En móvil y escritorio no cambia nada (`LocalIsTv` es `false`).
2. `TvRail.kt`: `focusProperties` en cada entrada: `left = Cancel` siempre, `up = Cancel` en la primera y `down = Cancel` en la última.
3. `TvShell.kt`: el aviso pasa a su propia línea, alineado a la derecha y con `maxLines = 1`; `TvTopTabs` ocupa todo el ancho debajo.
4. `TvRail.kt`: el seleccionado conserva el texto en `accent` aunque tenga foco, y su fondo sube a `accent` 28 % (16 % sin foco). El foco sigue marcado por el anillo y la escala.

### Verificación
- **Verificado en emulador TV** (`MirraTV_TV`, API 34), tras `assembleStableDebug` (BUILD SUCCESSFUL), `install -r` y `pm clear` (solo en el emulador, para volver a ver Welcome):
  1. Welcome arranca con el foco en Let's Go (`uiautomator`: `[1649,932][1864,1028]`, anillo visible).
  2. ↑ y ← en Home, y ↓ en Settings, no mueven el foco (mismo nodo enfocado antes y después).
  3. Library muestra las 5 pestañas completas, Downloads incluida.
  4. Home y Library, con foco y seleccionados, muestran texto acento + fondo tintado + anillo; un ítem solo enfocado (Settings) muestra texto blanco.
- Sin entradas en `logcat -b crash`.

### Alcance del cambio
- `:AndroidTV`: `shell/TvRail.kt`, `shell/TvShell.kt`.
- `:composeApp`: `tv/TvShellHost.kt` (androidMain) y `modules/welcome/WelcomeScreen.kt` (commonMain, protegido por `LocalIsTv`).

### No verificado / atajos de prueba
- En Welcome, ↑/↓ pasa por el **panel de contenido** de la pestaña (`[56,314][1864,886]`), que es enfocable pero no tiene indicador visual. Lo crea `MillenniumTabGroup` (librería externa `org.spotube.millennium` 0.1.1) y Welcome no puede quitarlo. Cuesta una pulsación extra; no bloquea.
- Al entrar al shell tras Welcome no hay foco inicial: la primera tecla lo coloca. El foco inicial por destino sigue pendiente (`PLAN.md` §9).
- Warning nuevo de compilación: `PackageManager.FEATURE_TELEVISION` está deprecado (`AndroidTvDetection.kt:36`). Es inocuo porque `FEATURE_LEANBACK` ya cubre el caso; pendiente de limpiar.
- Sin hardware real ni mando físico.

### Cambios residuales en el entorno
- Emulador: `pm clear org.spotube.spotube` y luego Welcome saltado de nuevo. La app sigue instalada (ver §8 para desinstalar).

---

## 10. Bug / diagnóstico — El foco D-pad se queda atrapado en emergentes de Settings → Plugins

### Problema
Reportado por el propietario: en Settings → Plugins el foco se queda pegado a ciertos elementos emergentes y no deja navegar al resto.

### Causa Raíz
1. **Desplegables que se abren con ↑/↓.** Las cuatro tarjetas de plugin por defecto (`DefaultAbilityPluginGrid.kt`) son disparadores de `AdaptiveDropdownBottomSheet`. En TV toma la rama `MillenniumDropdownMenu`, que se apoya en `UnstyledDropdownMenu` de `com.composables:composeunstyled` 2.10.0. Ese ancla registra un `onPreviewKeyEvent` que **abre el menú con `DirectionDown`, `DirectionUp`, `Enter` o `Spacebar`** (patrón *combobox* de escritorio). Lo confirma el bytecode de `DropdownMenuKt$UnstyledDropdownMenu$4$1` (`getDirectionDown` / `getDirectionUp` → `onExpandedChange(true)`). En TV ↑/↓ son las teclas de navegación: al pasar por una tarjeta se abría su emergente, que es un popup modal y retenía el foco hasta pulsar Back.
2. **Foco invisible en las tarjetas.** `AbilityPluginTile` usa `clickable` sin indicador de foco. Sobre el degradado no se ve qué tarjeta tiene el foco, así que la navegación parece no responder.

### Diagnóstico
- Emulador `MirraTV_TV` + `uiautomator dump`: con ↓ desde la tarjeta NewPipe, el foco pasa primero a ningún nodo y, en la pulsación siguiente, aparece el emergente `[992,515][1392,680]` (selector de plugin de audio), sin haber pulsado OK. A partir de ahí ↑/↓/←/→ no lo sacan; solo Back.

### Solución Aplicada
1. `AdaptiveDropdownBottomSheet.kt` (commonMain, **solo con `LocalIsTv`**): `onPreviewKeyEvent` en el `Box` contenedor. Las teclas *preview* llegan al padre antes que al ancla de la librería, así que ↑/↓ (KeyDown) hacen `focusManager.moveFocus(Up/Down)` y se consumen. OK (`DirectionCenter`) sigue abriendo el menú. Cubre **todos** los desplegables adaptativos de la app en TV, no solo Plugins.
2. `DefaultAbilityPluginGrid.kt` (`AbilityPluginTile`): anillo de foco blanco de 3 dp **solo en TV** (`onFocusChanged` antes de `clickable`, `border` condicional).

### Verificación
- **Verificado en emulador TV** (BUILD SUCCESSFUL, `install -r`):
  - ↓/↑ recorren Metadata/NewPipe → LRCLib/Scrobble → Install a Plugin (con desplazamiento) sin abrir ningún emergente. ← vuelve al menú lateral de Settings.
  - OK sobre NewPipe abre su selector; Back lo cierra y el foco vuelve a la tarjeta (`[1209,195][1832,491]`).
  - El anillo blanco se ve sobre la tarjeta enfocada (captura de LRCLib).
  - Sin entradas en `logcat -b crash`.

### Alcance del cambio
- `composeApp/src/commonMain/.../core/ui/component/AdaptiveDropdownBottomSheet.kt` y `.../modules/plugin/components/DefaultAbilityPluginGrid.kt`. Ambos cambios dependen de `LocalIsTv`: móvil, escritorio e iOS no cambian.
- Sin cambios en librerías externas (Millennium, composeunstyled).

### No verificado / atajos de prueba
- Dentro del menú abierto, ↑/↓ lo maneja la librería (no se tocó). Solo se probó con menús de un elemento (NewPipe; «No plugins available»).
- **No corregido:** el emergente aparece en la misma posición (`[992,515]`) sea cual sea la tarjeta, no anclado a ella. Parece un cálculo de ancla de Millennium/composeunstyled dentro del `Dialog` de Settings; no se investigó.
- El resto de pantallas con `AdaptiveDropdownBottomSheet` (menús de pista, álbum, etc.) reciben el mismo cambio, pero no se probaron en emulador porque necesitan contenido (plugin de metadatos).
- Otros elementos de Settings sin indicador de foco propio no se revisaron uno por uno.

### Cambios residuales en el entorno
- Ninguno nuevo. La app debug sigue instalada en el emulador (ver §8).

---

## 11. Bug / diagnóstico — Los menús emergentes no se abren junto a su disparador en TV

### Problema
Pendiente de §10: en Settings → Plugins, el selector de cada tarjeta aparecía siempre en la misma X, no bajo la tarjeta que lo abría.

### Causa Raíz
- `MillenniumDropdownMenu` → `UnstyledDropdownMenu` (`composeunstyled` 2.10.0) dibuja el panel dentro de un `Modal`. En Android, `Modal` es un `ComponentDialog` propio (`Modal_androidKt.PlatformModal`) con el tema `@style/Modal` de la librería (`parent=""`, sin tamaño de ventana).
- Esa ventana se queda en `wrap_content`. `dumpsys window` con el emergente abierto: `(wrapxwrap) gr=CENTER … frame=[520,0][1400,1080]`, es decir, 880 px de ancho centrados en una pantalla de 1920.
- La librería calcula la posición del panel en coordenadas de pantalla completa y luego lo ajusta para que quepa en su ventana. La Y coincide porque la ventana ocupa toda la altura; la X queda limitada por el borde derecho: 1400 − 400 (panel de 200 dp) − 8 (margen) = **992**, la X observada en todas las tarjetas.

### Diagnóstico
X e Y del emergente por tarjeta, medidas con `uiautomator`:

| Tarjeta | Emergente |
|---|---|
| Metadata `[562,195]` | `[992,515]` |
| NewPipe `[1209,195]` | `[992,515]` |
| Scrobble `[1209,425]` | `[992,745]` |
| LRCLib `[562,425]` | `[992,745]` |

La Y sigue a la tarjeta (borde inferior + 24 px); la X no.

### Solución Aplicada
- Se sobrescribe el estilo de la librería **solo en TV**, con el calificador de modo UI `-television` (los recursos de la app tienen prioridad sobre los de una dependencia con el mismo nombre):
  - `composeApp/src/androidMain/res/values-television/composeunstyled_modal.xml`: `<style name="Modal" parent="Modal.Base">` con `android:windowMinWidthMajor` y `windowMinWidthMinor` = `100%`. `DecorView` aplica ese mínimo a las ventanas `wrap_content`, así que la ventana ocupa toda la pantalla, que es lo que la librería supone.
  - `values-television-v36/composeunstyled_modal.xml`: lo mismo más `android:windowAnimationStyle=@style/Animation.HideIme`. El modo UI tiene más precedencia que el nivel de API, así que sin esta variante el override taparía la animación que la librería define en `values-v36`.
- Va en `:composeApp` y no en `:AndroidTV`: entre dos librerías, la prioridad de un override de recursos depende del orden de dependencias y no es fiable. Solo el módulo de la app lo garantiza.

### Verificación
- **Verificado en emulador TV** (BUILD SUCCESSFUL, `install -r`). Cada emergente se abre bajo su tarjeta, alineado con su borde izquierdo + 8 px:

  | Tarjeta | Emergente |
  |---|---|
  | Metadata `[562,195]` | `[570,515]` |
  | NewPipe `[1209,195]` | `[1217,515]` |
  | Scrobble `[1209,425]` | `[1217,745]` |
  | LRCLib `[562,425]` | `[570,745]` |

  Captura con el selector de NewPipe bajo su tarjeta. `dumpsys window` ya no muestra la ventana `wrapxwrap`. Sin entradas en `logcat -b crash`.

### Alcance del cambio
- Solo recursos Android con calificador `television`. Ningún cambio de código; teléfonos y tablets usan el estilo original de la librería.
- Afecta a **todo** `Modal` de `composeunstyled` en TV (desplegables, diálogos y bottom sheets de Millennium): todos pasan a ventana de ancho completo, que es como la librería los posiciona.

### No verificado / atajos de prueba
- Otros `Modal` en TV (diálogos de Millennium, menús de pistas) no se probaron uno a uno con este cambio. El diálogo de Settings no usa este `Modal`: es la ventana `fillxwrap` de Compose (`Window #3` en el `dumpsys`) y se ve igual.
- La variante `-v36` no se ejercitó: el emulador es API 34.
- **Posible fallo upstream sin tocar:** en tablets Android en horizontal (no TV), la misma ventana `wrap_content` podría desplazar los menús igual. No se comprobó, y el override no se aplica fuera de TV. Es candidato a reportarse a `composeunstyled`.

### Cambios residuales en el entorno
- Ninguno nuevo (ver §8).

---

## 12. Bug / diagnóstico — En TV no se puede salir de Ajustes

### Problema
Reportado por el propietario: la app no permite salir de la ventana de configuración.

### Causa Raíz
- `SettingsDialog` se cierra con `onDismiss = { navigatorCommands.pop(Routes.Settings) }`. Lo invocan la X y Back (`onDismissRequest` del diálogo).
- `NavigationCommands` es un canal (`navigationCommandFlow` / `navigationPopCommandFlow`) para que código no composable (view models, diálogos, controlador del WebView) navegue. **Solo lo consumía un `LaunchedEffect` dentro de `AppShell`**, el shell de móvil y escritorio.
- En TV, `AppShellHost` usa `TvShellHost` en lugar de `AppShell`, y nadie recogía el canal: el `pop` (y cualquier `navigateTo`, p. ej. `onLyrics`) se encolaba y se perdía. Es un defecto de §5, no de §10–§11.

### Diagnóstico
- Emulador: con Ajustes abierto, ni Back ni OK sobre la X cambian nada (2 ventanas de la app antes y después; foco fijo en la X `[1748,69][1844,165]`).
- `grep navigationPopCommandFlow`: un único consumidor, `AppShell.kt:115`.

### Solución Aplicada
- Nuevo `core/navigation/NavigationCommandsEffect.kt` (commonMain): el mismo bloque `LaunchedEffect` que tenía `AppShell`, movido sin cambios de lógica, con KDoc que obliga a que todo shell lo aloje.
- `AppShell.kt` llama a `NavigationCommandsEffect(navigator, navigationState)` en lugar del bloque inline. El comportamiento en móvil y escritorio no cambia.
- `TvShellHost.kt` lo llama al principio, antes de la rama de Welcome, como `AppShell`.

### Verificación
- **Verificado en emulador TV** (BUILD SUCCESSFUL, `install -r`, conservando los datos del propietario en el emulador: idioma español y 4 plugins):
  - Ajustes abierto (2 ventanas) → Back → 1 ventana; vuelve a Inicio con el foco en «Ajustes» del rail.
  - Ajustes → OK sobre la X → 1 ventana.
  - Ajustes → emergente de plugin abierto (3 ventanas) → Back (2: se cierra el emergente) → Back (1: se cierra Ajustes).
  - Sin entradas en `logcat -b crash`.
- Móvil y escritorio: **no** probados en ejecución. El cambio en `AppShell` es una extracción literal y compila (`compileStableDebugKotlinAndroid`); JVM e iOS no se compilaron.

### Alcance del cambio
- `composeApp`: `core/navigation/NavigationCommandsEffect.kt` (nuevo), `modules/shell/AppShell.kt`, `tv/TvShellHost.kt`.

### No verificado / atajos de prueba
- Otros comandos que ahora sí llegan en TV (p. ej. `navigateTo(Routes.Lyrics)`) no se ejercitaron.
- **Observado, no corregido:** las 11 cadenas `tv_*` solo existen en `values/strings.xml`; ninguno de los 20 idiomas las tiene, así que el aviso de D-pad sale en inglés con la app en español. Además, `values-es` tiene 758 de 811 cadenas: las que ya faltaban en upstream también salen en inglés (p. ej. «No metadata plugin selected»).
- **Observado, no corregido:** en el rail, «Biblioteca» se trunca a «Bibliote…» (ancho del rail y tamaño de fuente TV).

### Cambios residuales en el entorno
- Ninguno nuevo (ver §8). Los datos de la app en el emulador (idioma, plugins) son del propietario y no se tocaron.

---

## 13. Funcionalidad — Traducción al español de las cadenas TV

### Implementación
- `composeApp/src/commonMain/composeResources/values-es/strings.xml`: las 11 cadenas `tv_*` añadidas al final, bajo `<!-- Android TV -->`, igual que en `values/strings.xml`. Se respetan la terminación CRLF y la UTF-8 del archivo, y el vocabulario ya usado en ese idioma («canción» para *track*, «Buscar», «Ajustes»).
- Equivalencias: «Retroceder/Avanzar 10 segundos», «Usa el D-pad para moverte, OK para seleccionar y Atrás para volver», «Izquierda/derecha mueve la canción, OK la reproduce, Menú la quita», «Usa el D-pad para escribir», «Abrir/Cerrar teclado», «Borrar», «Retroceso», «Espacio», «Buscar».

### Resultado verificado
- XML válido (`minidom`). BUILD SUCCESSFUL.
- **Verificado en emulador TV** con la app en español: `uiautomator` muestra el aviso «Usa el D-pad para moverte, OK para seleccionar y Atrás para volver», que cabe en una línea sobre el contenido.
- Las cadenas de teclado y cola no se vieron en pantalla: necesitan Search con un plugin de metadatos activo, o la cola con reproducción.

### Alcance del cambio
- Solo `values-es`. Los otros 19 idiomas siguen sin cadenas `tv_*` y caen al inglés.
- No se tocaron las 53 cadenas que ya faltaban en `values-es` en upstream (p. ej. `no_metadata_plugin_description_select`, que sigue en inglés en Inicio).

### No verificado / atajos de prueba
- Traducción revisada por el agente, sin revisión de un hablante nativo.

### Cambios residuales en el entorno
- Ninguno nuevo.

---

## 14. Prueba — Recorrido D-pad completo con cuenta de Spotify

### Alcance
Emulador `MirraTV_TV` (1920×1080, API 34), APK debug de §13, app en español con el plugin Spotify iniciado por el usuario. Solo D-pad (`adb input keyevent`), teclas multimedia y OK largo; foco leído con `uiautomator`. Sin cambios de código.

### Funciona
- Rail: las 4 entradas, con anillo de foco y sin salirse por los bordes.
- Inicio: secciones y tarjetas con anillo; ↑/↓ entre secciones.
- Biblioteca: pestañas, barra «Tu biblioteca» (buscar, crear, vista lista/cuadrícula) y cuadrícula/lista de playlists.
- Playlist: botón atrás, añadir, me gusta, Reproducir, Aleatorio y el play de la cabecera. Las filas de canciones avanzan con ↑/↓.
- Reproducción: OK en el play de la cabecera reproduce la playlist (MediaSession `PLAYING`). Las teclas `MEDIA_PLAY_PAUSE`, `MEDIA_NEXT` y `MEDIA_PREVIOUS` funcionan.
- Mini reproductor: anterior, play/pausa, siguiente y cola, con anillo.
- Cola (`TvQueueDialog`): foco inicial en la canción actual, ←/→ reordena, ↑ llega a la X y Atrás devuelve el foco al botón de cola.
- Reproductor expandido: OK en play/pausa funciona y Atrás lo cierra.
- Ajustes: barra lateral, filas con anillo, interruptores con OK, diálogo Paleta (muestras, Cancelar/Guardar) y X.

### Fallos encontrados
| # | Pantalla | Fallo | Gravedad |
|---|---|---|---|
| 1 | Reproductor expandido | `tvDirectionKeys` en la raíz convierte ←/→ en ±10 s, así que el foco no sale de play/pausa. Anterior, siguiente, ±10 s y cola no se alcanzan con el D-pad. | Alta |
| 2 | Reproductor expandido | Los botones ±10 s llevan texto largo («Retroceder 10 segundos»). La fila se desborda y «Avanzar 10 segundos» y Cola quedan fuera de pantalla. | Alta |
| 3 | Ajustes (Idioma, País, Formato y Calidad de streaming…) | `SelectionSettingCard` en ancho «Medium+»: OK en la fila solo pone `isDialogOpen`, y ese diálogo solo se pinta en pantallas estrechas. El botón desplegable de la derecha no recibe foco. No se pueden cambiar. | Alta |
| 4 | Ajustes (nombre del dispositivo, puerto proxy y otras `TextInputSettingCard`) | El campo inline no recibe foco y OK en la fila no hace nada en ancho «Medium+». No se pueden editar. | Alta |
| 5 | Buscar | En 1080p el área de contenido mide ≈270 dp. El `TvKeyboard` (título, aviso, consulta y 6 filas de ≥56 dp) no cabe: las teclas no se ven, solo la caja de consulta. Las pestañas y los resultados quedan fuera de pantalla. | Alta |
| 6 | Buscar | El campo de búsqueda abre el IME del sistema al recibir foco y ↓ no sale de él. Tras ENTER, ← lleva a un botón aplastado (≈25 px) sin texto visible. No se pudo ver ningún resultado. | Alta |
| 7 | Playlist / álbum (`TrackList`) | El campo «Filtrar» abre el IME del sistema al pasar el foco. Atrapa ↓ hasta que se cierra con Atrás. | Alta |
| 8 | Playlist (`TrackListRow`) | Las filas no tienen indicador de foco. «⋯», el enlace de álbum y los artistas no se alcanzan: → desde una fila salta a la cabecera. Opciones de canción inaccesibles. | Alta |
| 9 | Inicio | OK en una categoría («Made For You»…) pierde el foco (ningún nodo enfocado) y no se ve que abra nada. El siguiente Atrás sale de la app. | Media |
| 10 | Navegación | Al abrir una playlist, al pulsar el play de la cabecera y al volver con Atrás, el foco cae en el rail («Inicio») en lugar de quedarse en el contenido o volver a la tarjeta de origen. | Media |
| 11 | Mini reproductor | La zona de título y artista recibe foco sin indicador visible. | Media |
| 12 | Inicio (carrusel) | ←/→ no mueven el carrusel: el foco queda en la tarjeta central. | Baja |
| 13 | Arranque | Sin foco inicial al abrir la app (ya anotado en §9). | Baja |
| 14 | Rail | ← desde el contenido cae en la entrada del rail más cercana en vertical, no en la seleccionada. | Baja |

### No verificado / atajos de prueba
- No probados: álbum, artista, hoja de opciones de canción (inaccesible, fallo 8), eliminar de la cola con Menú, Lista negra, Jam, Caché, Descargas, enlaces externos de Apoyo, inicio de sesión o webview.
- El texto escrito con `adb input text` perdió una letra («shkira»). Es una carrera con el IME del emulador, no un fallo atribuible a la app.
- Se usó `uiautomator` para leer el foco. En listas que se desplazan, la fila enfocada puede tener los mismos límites tras moverse (fallo 8 incluido).

### Cambios residuales en el entorno
- Activar «Permitir control remoto» lanzó el permiso del sistema «dispositivos cercanos», y un OK de la prueba lo concedió sin querer. Se revirtió: interruptor apagado, `pm revoke … NEARBY_WIFI_DEVICES` y flags `user-set`/`user-fixed` limpiados. Android cerró la app al revocar y se volvió a abrir; la reproducción en curso se perdió.
- La cola se reordenó durante la prueba y se restauró al orden original (KESI, Pepas, Espresso, WATATI…).
- Queda «shkira» en el historial de búsqueda del emulador.

---

## 15. Bug / diagnóstico — Corrección de los 8 fallos graves de §14

### Problema
Fallos 1–8 de §14: reproductor expandido inalcanzable, desplegables y campos de Ajustes inutilizables, Buscar sin teclado ni resultados visibles, campos de texto que atrapan el foco y opciones de canción inaccesibles.

### Causa Raíz
- **Reproductor (1, 2):** `tvDirectionKeys` en la raíz del diálogo convertía ←/→ en ±10 s para todo el diálogo. Los ±10 s eran `TvButton` con texto largo y desbordaban la fila.
- **Desplegables (3):**
  - `SelectionSettingCard` en ancho «Medium+» solo marcaba `isDialogOpen`, y ese diálogo solo se pinta en ancho estrecho.
  - Además, el panel de `UnstyledDropdownMenu` (composeunstyled 2.10.0) se queda con el foco en TV: ↑/↓ no llegan a los elementos. Su manejador interno (`DropdownMenuPanel$3$1`: `moveFocus(Next)` / `requestFocus` del primer elemento) existe, pero en el emulador nunca avanzó.
- **Campos (4, 6, 7):** un campo de texto enfocado abre el IME y se traga ↑/↓. `TextInputSettingCard` ponía el campo inline como `trailing` de una fila que ya es el objetivo de foco.
- **Buscar (5):** el `TvKeyboard` se pintaba inline sobre los resultados. En 1080p (960×540 dp) el área de contenido no le deja sitio.
- **Filas (8):** `MillenniumListTile` no pinta foco en las filas de canción, y «⋯», álbum y artistas no son objetivos de D-pad.

### Solución Aplicada
- **`:AndroidTV`**
  - `TvPlayerDialog`: quitado `tvDirectionKeys` de la raíz. La barra de progreso es ahora un objetivo de foco propio, con anillo y `contentDescription` (`tv_seek_bar`): ←/→ solo buscan con ella enfocada. ±10 s son `TvIconButton` (`FeatherIcons.RotateCcw` / `RotateCw`, nuevos campos de `TvPlayerIcons`).
  - `tvMediaKeys`: acepta `MEDIA_REWIND` y `MEDIA_FAST_FORWARD`.
  - `TvKeyboardDialog`: el teclado a pantalla completa, con foco inicial en «q» y desplazamiento vertical de respaldo.
- **commonMain**
  - `TvTextFieldGate`: en TV el campo queda detrás de una compuerta enfocable con anillo. OK empieza a editar (abre el IME); ↑/↓ o Atrás salen. Fuera de TV pinta el campo igual que antes.
    - Aplicado a Buscar, «Filtrar» de `TrackList`, diálogo de `TextInputSettingCard`, Lista negra, código de Jam, filtro de Biblioteca, buscador de «Añadir a playlist», formulario de playlist (nombre y descripción), «Añadir canciones», URL de plugin y temporizador de apagado.
    - No aplicado a `PlayerQueueContent` ni a `AdaptiveBottomSheetContent`: TV no los usa.
  - `TvRowKeys.kt`:
    - `tvFocusRing`: anillo de foco reutilizable.
    - `tvRowKeys`: anillo, OK = clic, OK mantenido o Menú = opciones. Detecta el KeyDown repetido; consume el KeyUp para no reproducir.
    - Aplicados a `TrackListRow` y a los elementos de `TrackOptionsBottomSheet`.
  - `TvMenuDialog`: en TV, `AdaptiveDropdownBottomSheet` muestra las opciones en un diálogo propio de filas enfocables, con foco inicial en la opción seleccionada. Sustituye al menú de la librería, que no se podía recorrer.
  - `AdaptiveDropdownBottomSheet`:
    - `expanded` / `onExpandedChange` opcionales (estado elevado).
    - El interceptor de ↑/↓ de §10 solo actúa con el menú cerrado.
  - `SelectionSettingCard`: en ancho «Medium+» la fila abre el desplegable. `TextInputSettingCard`: en TV usa el diálogo, como en ancho estrecho.
  - `SearchScreen`:
    - El teclado TV ya no se abre por defecto. «Abrir teclado» está en la misma fila que el campo y abre `TvKeyboardDialog`.
    - En TV se quita la `ApplicationMainBar` vacía.
    - En TV ya no se llama a `clearFocus()`, que dejaba el mando sin foco.
- **Cadenas** (`values` y `values-es`):
  - Nueva `tv_seek_bar`.
  - `tv_hint_dpad` acortada y con «mantén OK: opciones»: «D-pad: moverte · OK: elegir · Mantén OK: opciones · Atrás: volver». La versión larga se cortaba.

### Verificación
**Verificado en emulador TV** (`assembleStableDebug` OK, `install -r`, cuenta Spotify del usuario):
- **Reproductor:** ←/→ recorren los 6 controles y todos caben. ↑ lleva a la barra de progreso (anillo), donde ←/→ buscan ≈10 s (00:58 → 01:10). ↓ vuelve a play/pausa. Cola abre desde el reproductor y Atrás devuelve el foco.
- **País:**
  - OK abre `TvMenuDialog` con foco en «Bangladesh (BD)», la opción actual.
  - ↓↓ lleva el anillo a «Burkina Faso». Atrás cierra sin cambios. OK sobre la actual la reaplica (sigue «Bangladesh»).
- **Puerto del proxy:** OK abre el diálogo. El campo se recorre sin IME (anillo) hasta Cancelar, y Cancelar cierra.
- **Filtros:** en Lista negra, OK abre el IME y Atrás sale. En «Filtrar» de la playlist, pasar no abre el IME; OK sí; Atrás / ↓ salen.
- **Filas de canción:** anillo visible. Mantener OK abre la hoja de opciones sin reproducir. ↓ recorre opciones con anillo. Atrás devuelve el foco a la fila.
- **Buscar:**
  - Con el teclado TV se escribió «shakira» y «Buscar» mostró los resultados. Las filas de resultados tienen anillo y la hoja de opciones también se abre desde ellas.
  - Con el IME del sistema: OK en el campo, escribir, Atrás y ↓ salen a las pestañas.
- Sin crashes (`logcat -b crash` vacío).
- `:composeApp:compileKotlinJvm` OK.

### Alcance del cambio
- Los fallos medios y bajos de §14 (9–14) siguen abiertos. En el foco al abrir una playlist hubo una mejora parcial: cae en el botón atrás de la cabecera en vez del rail.
- `TvMenuDialog` sustituye en TV a **todos** los desplegables adaptativos (Plugins, ordenar, acciones masivas, ajustes). En TV se pierde el filtro de texto de las listas largas, como País: se recorren con ↓.

### No verificado / atajos de prueba
- No se probaron: Idioma (cambiaría la app), Formato y Calidad de streaming, menú Menú (KEYCODE_MENU) sobre una fila, ordenar y acciones masivas de `TrackList`, Jam, formularios de playlist, URL de plugin, temporizador, ni las hojas de Biblioteca.
- iOS sin compilar. `:composeApp:check` no ejecutado.
- `adb input text` sigue perdiendo letras con el IME del emulador («ab» de «abc»).
- `TvTextFieldGate` y `tvRowKeys` guardan estado de foco local en el composable (bandera de edición, tecla mantenida). Es mecánica de foco de un control, no estado de pantalla, pero se desvía de la regla de «ViewModels own ALL state».

### Cambios residuales en el entorno
- Durante la verificación se abrieron sin querer los diálogos informativos «Apoyar a LRCLib Lyrics» y «Plugin details» (Spotify), y se cerraron con su X. No se tocó «Cerrar sesión».
- «shakira» y «ab» quedan en el historial de búsqueda del emulador. País: se reaplicó el mismo valor («Bangladesh»).

---

## 16. Bug / diagnóstico — Corrección de los 6 fallos menores de §14

### Problema
Fallos 9–14 de §14: categoría de Inicio que pierde el foco, foco que cae en el rail al navegar o reproducir, zona de título del mini reproductor sin anillo, carrusel que no se mueve con ←/→, app sin foco inicial y ← que entra al rail por la entrada más cercana.

### Causa Raíz
- **Categoría (9):**
  - El chip cambia de estilo al seleccionarse y suelta el foco.
  - Además, `HomeScreen` volvía arriba del todo (`scrollToItem(0)`). En TV solo se veía el carrusel y no lo que el chip acababa de abrir.
- **Foco al navegar y al arrancar (10, 13):**
  - Ninguna pantalla pedía foco al mostrarse, así que la siguiente tecla empezaba desde la esquina superior izquierda (el rail).
  - Los botones con estado de carga (`loading`, «Reproducir»…) se deshabilitan mientras cargan, sueltan el foco y Android se lo da al primer elemento de la ventana.
- **Mini reproductor (11):** la columna de título era `clickable` sin indicador de foco.
- **Carrusel (12):**
  - Las tarjetas laterales se superponen a la central, así que la búsqueda 2D del D-pad no las alcanza.
  - El envoltorio clicable de `CoverflowItem` y las dos zonas táctiles de los bordes de `Coverflow` recibían el foco sin anillo.
  - `CoverflowState` notifica `-1` al deseleccionar. El índice local quedaba en `-1` y la tarjeta central se pintaba atenuada.
- **Rail (14):** sin regla de entrada, la búsqueda 2D elige la entrada más cercana en vertical.

### Solución Aplicada
- **`:AndroidTV`**
  - `TvRail`: el rail es un `focusGroup` con `onEnter`. Al entrar con ← el foco va a la entrada seleccionada.
  - `TvMiniPlayer`: la zona de título tiene anillo de foco y `contentDescription` (`labels.openPlayer`).
- **commonMain**
  - `core/navigation/TvEntryFocus.kt` (nuevo): decorador de `NavEntry` que solo se añade en TV, después del de estado guardable.
    - Cada pantalla es un grupo de foco con `focusRestorer` y pide foco al mostrarse. Si aún está cargando, reintenta durante 5 s hasta que haya algo enfocable.
    - Guarda el hijo enfocado antes de cada tecla, porque `focusRestorer` solo lo guarda al salir del grupo y abrir otra pantalla con OK no sale.
  - `tvKeepFocusWhileBusy` (en `TvRowKeys.kt`): mientras el botón carga, un objetivo de foco exterior sostiene el foco y luego lo devuelve al botón. Aplicado a Reproducir, Aleatorio y Añadir a la cola de `CollectionDetails`, y al play de `CollectionView`.
  - `HomeScreen`:
    - En TV no se vuelve arriba al elegir categoría.
    - El chip elegido recupera el foco (`FocusRequester` por género, LazyRow con `key = id`).
  - `FeaturedCarousel`:
    - En TV, ←/→ mueven el carrusel una tarjeta y el foco la sigue (índice TV propio). En los extremos la tecla no se consume y el foco sale con normalidad.
    - Las tarjetas tienen anillo.
    - Se ignora el `-1` de deselección (afecta a todas las plataformas: la tarjeta central ya no se atenúa).
  - `Coverflow` / `CoverflowItem`: `tvNotFocusable()` quita el foco en TV al envoltorio y a las zonas de borde, que solo sirven para el tacto.

### Verificación
**Verificado en emulador TV** (`assembleStableDebug` OK, `install -r`, cuenta Spotify del usuario):
- **Arranque (13):** el foco cae en la tarjeta central del carrusel, con anillo.
- **Carrusel (12):**
  - →, → y ← mueven una tarjeta cada vez; un registro temporal confirmó los índices 10 → 11, ya retirado.
  - El anillo sigue a la tarjeta central.
- **Categorías (9):**
  - OK en «Made For You» la selecciona, conserva el foco y muestra «Uniquely yours» debajo.
  - → recorre los chips y la fila se desplaza.
- **Navegación (10):**
  - Al abrir «Daily Mix 2» el foco cae en el botón atrás.
  - Al volver con Atrás, el foco regresa a la sección de origen, pero a la tarjeta vecina («Daily Mix 1»): ver «No verificado».
  - OK en «Reproducir» de «Happy Hits!»: suena la playlist y el foco se queda en el botón (ahora «Pausar»). Antes saltaba a «Inicio».
- **Mini reproductor (11):** ← desde «anterior» muestra el anillo sobre título y artista.
- **Rail (14):** ← desde la zona de título (abajo) entra por «Inicio» (seleccionada), no por «Ajustes» (la más cercana).
- Sin crashes (`logcat -b crash` sin `FATAL`). `:composeApp:compileKotlinJvm` OK.

### Alcance del cambio
- `TvEntryFocus` hace que, en TV, abrir cualquier pantalla (también desde el rail) mueva el foco al contenido.
- `tvKeepFocusWhileBusy` solo cubre los botones de cabecera de colección. Otros botones con `loading` (play de `PlayableCard` / `PlayableListTile` / `LikedTracksCard`) y el botón Seguir, que cambia de composable al pulsarlo, pueden seguir soltando el foco.

### No verificado / atajos de prueba
- Volver con Atrás restaura la sección pero no siempre la tarjeta exacta: la fila horizontal vuelve a su desplazamiento inicial y el foco cae en la tarjeta de la misma posición visible. Se probaron claves estables en filas y secciones de Inicio y no cambiaron nada, así que se retiraron.
- Los 5 s de reintento de `TvEntryFocus` podrían quitar el foco del rail si el usuario llega a él mientras la pantalla todavía carga. No observado.
- No probados: Aleatorio y Añadir a la cola con carga, `CollectionView` plegado, el carrusel en sus extremos, pantallas fuera de Inicio y playlist (Biblioteca, Ajustes, Buscar) con el nuevo foco inicial.
- iOS sin compilar. `:composeApp:check` no ejecutado.
- `tvKeepFocusWhileBusy`, el chip con foco devuelto y el índice TV del carrusel guardan estado de foco local en el composable. Se desvía de «ViewModels own ALL state» igual que §15.

### Cambios residuales en el entorno
- Se reprodujeron «Daily Mix 2» y luego «Happy Hits!». La cola anterior (Espresso) quedó sustituida y la reproducción se dejó en pausa.
- Una secuencia de teclas sin verificar el foco pulsó OK en Inicio sin abrir nada visible. Pudo cambiar la categoría seleccionada, que no se guarda entre sesiones.

---

## 17. Funcionalidad / bug — Atajos D-pad, botones internos, escala, paleta y Letra

### Problema
Petición del usuario (8 puntos):
1. No había forma rápida de llegar al mini reproductor ni de volver arriba.
2. Los botones pequeños dentro de tarjetas y filas («+», play, «⋯», enlaces de álbum y artista) no eran alcanzables.
3. Los textos de ayuda D-pad ocupaban espacio.
4. Carátulas y tarjetas salían recortadas.
5. En el diálogo de paleta, los colores no mostraban foco y el foco escapaba al fondo.
6. En Inicio, el foco no volvía al carrusel desde abajo.
7. La Letra no estaba accesible en TV.
8. Analizar multiusuario.

### Causa Raíz
- (2) La búsqueda espacial de Compose nunca pasa de un contenedor enfocable a algo dentro de sus propios límites, así que los botones internos de tarjetas y filas no eran candidatos.
- (4) Las pantallas están dimensionadas para teléfono. En 1080p (densidad 2), el área de contenido era más baja que una tarjeta (230 dp) más su cabecera, y la línea de ayuda le quitaba unos 30 dp más.
- (5) `MillenniumDialog` (`composeunstyled`) se dibuja en la ventana de la pantalla, no en una ventana propia, y abre con el foco en su botón cerrar. Además, el anillo de acento era invisible sobre una muestra del mismo color de acento.
- (7) El shell TV nunca mostró un botón de Letra, aunque la ruta `Routes.Lyrics` existe.
- (6) No reproducido con un ↑ simple: desde «Albums by artists you follow», al final de la lista, ↑ sube sección a sección hasta el carrusel. El recorrido automático del doble ↑ sí se detenía en las categorías, porque movía el foco mientras la lista aún se desplazaba.

### Solución Aplicada
- **Atajos (1):** `TvShell` cuenta dos pulsaciones de ↓ o ↑ dentro de 400 ms, ignorando la repetición por tecla mantenida.
  - Doble ↓ enfoca play/pausa del mini reproductor (`TvMiniPlayer.playPauseFocusRequester`).
  - Doble ↑ enfoca la pestaña seleccionada si hay pestañas (`TvTopTabs.selectedFocusRequester`). Si no las hay, recorre el contenido hacia arriba, un elemento cada 4 fotogramas, con un reintento a los 12 fotogramas. Mientras dura el recorrido, `onExit` + `cancelFocusChange()` impide que el foco salga del contenido.
  - El parámetro `miniPlayer` de `TvShell` recibe ahora el `FocusRequester`.
- **Botones internos (2):** nuevo `Modifier.tvInnerActions(entry)` en `TvRowKeys.kt`. En el contenedor enfocado, → entra a sus botones (`entry` o `FocusDirection.Enter`), y ← desde el botón más a la izquierda vuelve al contenedor (`onExit`).
  - Aplicado en `MediaCardSurface`, que cubre `PlayableCard`, `AvatarCard` y `LikedTracksCard`.
  - Aplicado en `TrackListRow`, donde → entra en «⋯».
  - `tvRowKeys` solo actúa si la fila misma tiene el foco, para no robar el OK a sus botones.
  - Los enlaces de álbum, artista y «+N artistas» tienen `tvFocusRing`.
- **Ayuda (3):** eliminadas `tv_hint_dpad`, `tv_queue_reorder_hint` y `tv_keyboard_hint` (en y es), junto con su uso en `TvShell`, `TvKeyboard` (título → `tv_keyboard_search`) y `TvQueueDialog`.
- **Escala (4):** `TvShell` dibuja el contenido con una densidad menor cuando su área mide menos de 440 dp de alto: escala = alto / 440, con un mínimo de 0,75. El rail y el mini reproductor no cambian.
- **Paleta (5):**
  - Nuevo `Modifier.tvFocusTrap()`, una `focusGroup` con `onExit { cancelFocusChange() }`, aplicado a un `Column` que envuelve todo el contenido del diálogo.
  - El foco inicial pasa a la paleta actual tras 20 fotogramas, después de que el diálogo enfoque su X.
  - En TV, la muestra enfocada lleva un anillo `onSurface` con 6 dp de separación.
- **Letra (7):** botón Letra (`Iconsax.IconsaxMusic`, cadena `player_lyrics`) en el mini reproductor y en el reproductor a pantalla completa. Cierra el reproductor y hace `navigator.navigate(Routes.Lyrics)`.

### Verificación
Emulador `MirraTV_TV` 1080p, APK `assembleStableDebug`:
- **Doble ↓:** desde «Popular radio» enfoca play/pausa del mini reproductor.
- **Doble ↑:** desde el final de Inicio llega al carrusel. En Biblioteca, desde la segunda fila de tarjetas, llega a la pestaña «Playlists».
- **Inicio con ↑ simple:** sube hasta el carrusel desde el final de la lista y desde una columna desplazada a la derecha.
- **Tarjetas:** en «Daily Mix 1», → enfoca «+», ↓ el play, ← vuelve a la tarjeta y → → pasa a «Daily Mix 2».
- **Cabecera de playlist:** «+» y corazón alcanzables con ↑ y ←.
- **Filas de canción:** → enfoca «⋯» y ← vuelve a la fila.
- **Escala:**
  - Inicio se dibuja a 0,84: el carrusel se ve entero, con el anillo completo.
  - Biblioteca se dibuja a 0,75: 5 columnas con título visible.
  - La línea de ayuda ya no aparece.
- **Paleta:** abre con foco y anillo blanco en Lima. ← lo mueve a Naranja. 5 × ← y 3 × ↑ no salen del diálogo. Atrás cierra el diálogo y luego Ajustes.
- **Letra:** el botón del mini reproductor abre la Letra sincronizada de «lovely», con botón atrás enfocado.
- Sin crashes. `:composeApp:compileKotlinJvm` OK.

### Alcance del cambio
- **Escala:** reduce todo el contenido de las pantallas en TV (texto incluido), no solo las carátulas. A 0,75 el texto pequeño es menos legible a distancia. Los umbrales son las constantes `MinContentHeightDp` y `MinContentScale` de `TvShell.kt`.
- **Tarjetas en filas horizontales:** pasar de una tarjeta a la siguiente con → cuesta ahora dos pulsaciones cuando la tarjeta tiene botones (la primera entra en «+»).
- **Doble ↓:** dos ↓ rápidos ya no bajan dos elementos; saltan al mini reproductor. Lo mismo con ↑.
- **Paleta:** el `Column` que envuelve el diálogo existe en todas las plataformas. La trampa de foco solo actúa en TV.

### No verificado / atajos de prueba
- **Punto 6:** el bloqueo en «Recently played» no se reprodujo, porque esa sección no apareció en la categoría «All». Puede depender de otra categoría o de otro contenido.
- **Doble ↑ sin pestañas:** probado solo en Inicio. Las pantallas sin lista perezosa no se probaron.
- **Enlaces de canción:** no se comprobó a ojo el anillo nuevo en álbum y artista. En la prueba de la fila, el foco pasó por ellos sin indicador; el anillo se añadió después y no se volvió a probar.
- **Otros `MillenniumDialog`:** los otros 9 usos siguen sin trampa de foco.
- iOS sin compilar. `:composeApp:check` no ejecutado.
- **Estado de foco local:** `tvInnerActions`, `tvFocusTrap` y el contador del doble pulso guardan estado de foco en el composable, igual que §15–16.

### Cambios residuales en el entorno
- Se abrieron la playlist «Composiciones (éxitos) de Luis Egurrola», la Letra y el diálogo de paleta, este último cerrado sin guardar. No se reprodujo nada y la cola («lovely») no cambió.
- Se instaló el APK debug varias veces sobre la app existente. Los datos y la sesión se conservan.

---

## 18. Análisis — Multiusuario (varias cuentas o perfiles)

### Estado actual
- Toda la persistencia es global por instalación: archivos DataStore en `getApplicationDataDirPath()` (`core/db/Database.kt`):
  - `spotube.preferences_pb` (ajustes)
  - `spotube_plugins.preferences_pb`
  - `_matched_tracks`
  - `_audio_player_queue`
  - `_local_media`
  - `_blacklist`
  - `_window`
- **Credenciales de los plugins:** se guardan con `PersistedStorageAPI` en `spotube_plugins.preferences_pb`, con clave `"<pluginId>:<clave>"` (`RealPersistedStorageAPI`). No hay dimensión de usuario.
- **Cookies del WebView de login:** son de proceso. En Android, `CookieManager` es único y el login empieza con `removeAllCookies` (`PlatformWebviewConfig.android.kt`).
- **Estado en memoria:** Koin crea un único `Database`, repositorios y estados (`LibraryState`, cola, reproductor) para toda la app, y hay una sola instancia de cada plugin de Zipline.

### Opciones
1. **Perfiles con cambio rápido (recomendada).** Un solo perfil activo, como «Cambiar de cuenta» en Spotify:
   - Cada perfil tiene su subcarpeta `profiles/<id>/` para los DataStore por usuario: plugins (sesión), cola, lista negra y, si se quiere, ajustes.
   - `matched_tracks`, `local_media` y las cachés pueden seguir compartidos.
   - Un selector de perfil en Ajustes (y opcionalmente al arrancar) guarda el perfil activo en un DataStore global y reinicia la actividad o el proceso. Así se recrean Koin, los plugins y el reproductor sin tocar cada singleton.
   - Al cambiar, se vacían las cookies del WebView. Basta si el plugin de Spotify guarda su sesión en `PersistedStorage`; si depende de cookies vivas, habría que guardarlas y restaurarlas por perfil.
   - Esfuerzo medio:
     - `Database`/`Paths` con perfil activo.
     - Migración de los datos actuales a un perfil «Principal».
     - UI del selector (TV y móvil).
     - Prueba de que el plugin de Spotify vuelve a la sesión correcta.
2. **Varias cuentas abiertas a la vez.** Exige varias instancias de plugin y estado aislado por cuenta en memoria. Choca con el diseño de singletons, con un único reproductor y con el contrato de plugins. Esfuerzo alto y poco beneficio en TV, donde solo se reproduce una cuenta a la vez.
3. **Perfiles de Android (multiusuario del sistema).** Ya funciona sin cambios en dispositivos que lo permiten, porque cada usuario del sistema tiene sus propios datos. Muchos Android TV no lo exponen.

### Riesgos y puntos abiertos
- **Sesión del plugin:** hay que confirmar en el código del plugin de Spotify (fuera de este repo) dónde guarda la sesión. Si la guarda en cookies, la opción 1 necesita guardarlas y restaurarlas por perfil.
- **Migración:** si una instalación existente no pasa sus datos al perfil «Principal», el usuario pierde la sesión.
- **Jam y control remoto por LAN:** usan identidad de dispositivo y habría que decidir si van por perfil.
- Sin cambios de código en esta sección.

---

## 19. Funcionalidad — Escala configurable, foco en diálogos, «Ir al álbum» y perfiles

### Problema
Petición del usuario:
1. Elegir la escala TV desde Ajustes → Apariencia, en una lista de valores seguros.
2. Revisar los otros 9 `MillenniumDialog` por la misma pérdida de foco que la paleta.
3. Implementar perfiles (opción 1 de §18), con un botón de usuario encima de «Inicio».
4. En las opciones de canción, «Ir al álbum» quedaba fuera de pantalla.

### Causa Raíz
- **(2)** Los 9 diálogos se dibujan en la ventana de la pantalla, igual que la paleta (§17). Sin trampa de foco, las flechas podían llevar el foco al fondo. Los que no tienen botón cerrar (`ErrorDisplay`, el selector de dispositivo y los de `PlayerOptionDialogs`) se abrían con el foco todavía en el fondo.
- **(4)** La lista de `TrackOptionsBottomSheet` no se podía desplazar. En una TV de 1080p, la última opción quedaba bajo el borde y el foco no la alcanzaba.

### Solución Aplicada
- **Escala:**
  - `UserSettings.tvContentScale` vale 0 (automático) o uno de 75, 80, 85, 90, 95 o 100 (`TV_CONTENT_SCALE_OPTIONS`).
  - Nueva fila «Tamaño del contenido (TV)» en Apariencia, solo visible si `isTelevision()`.
  - `TvShellHost` pasa el valor como `TvShell.contentScale`; con null se mantiene el cálculo automático de §17.
- **Diálogos:**
  - Nuevo componente `TvDialogFocus` (`core/ui/component`): un `Column` que en TV aplica `tvFocusTrap`. Si a los 20 fotogramas nada del diálogo tiene el foco, lo lleva al primer elemento.
  - Envuelve el contenido de los 10 `MillenniumDialog`:
    - `AdaptiveDialogBottomSheet` (que cubre todos sus usos)
    - `ErrorDisplay`
    - `PlayDestinationPicker`
    - `InteractiveSettingCards` (×2)
    - `LocalMediaFoldersEditor`
    - `PlayerOptionDialogs` (×2)
    - `Support`
    - la paleta
  - El cambio se hizo con un script que respeta el anidado de llaves.
- **«Ir al álbum»:** la columna de la hoja de opciones usa `verticalScroll`.
- **Perfiles:**
  - `core/profile/ProfileStore`: un JSON leído de forma síncrona (`spotube_profiles.json`) con la lista y el perfil activo.
    - El perfil «Principal» usa la carpeta de datos actual, así que no hace falta migrar nada.
    - Cada perfil nuevo usa `profiles/<id>/`.
  - `Database` toma de ese directorio solo los DataStore por usuario: `spotube_plugins` (sesiones de los plugins), cola y lista negra. Ajustes, plugins instalados, `matched_tracks`, medios locales y cachés siguen compartidos.
  - `restartApplication()` (expect/actual): en Android vacía las cookies del WebView, relanza la actividad Leanback o normal y termina el proceso. En escritorio e iOS devuelve false y la pantalla pide reabrir la app.
  - `ProfilesViewModel` y `ProfilesScreen` (ruta `Routes.Profiles`):
    - cambiar de perfil (OK en la fila);
    - añadir (diálogo con `TvTextFieldGate`);
    - renombrar y eliminar, con confirmación; el perfil activo y «Principal» no se pueden eliminar;
    - «Cuentas de este perfil» abre Plugins.
  - `TvRail.topItem`: el botón de usuario encima de «Inicio» muestra el nombre del perfil activo.
  - Cadenas `profiles_*` en inglés y español.

### Verificación
Emulador `MirraTV_TV` 1080p:
- **«Ir al álbum»:** en «KESI - Remix» de «Happy Hits!», Menú abre las opciones y ↓ llega a «Ir al álbum» con anillo.
- **Escala:** la lista muestra Automático y 75–100 %. Con 90 %, Inicio se redibuja más grande. Se devolvió a Automático.
- **Paleta y Ajustes:** siguen con el foco atrapado (§17). Los otros diálogos solo se compilaron; no se abrieron uno a uno en TV.
- **Botón de perfil:** «Principal» aparece encima de «Inicio» y ↑ desde «Inicio» llega a él.
- **Añadir perfil:** el diálogo trae campo, Cancelar y Guardar. Se creó «Prueba» con el teclado del sistema.
- **Cambio a «Prueba»:** la app se reinició sola. El perfil arrancó sin sesión («Sign in to continue»), sin cola y con «Prueba» en el rail.
- **Vuelta a «Principal»:** Inicio cargó con la sesión de Spotify y la cola anterior («Esa Mirada») intacta.
- **Eliminar «Prueba»:** con confirmación. `files/profiles/` queda vacía y `spotube_profiles.json` vuelve a `{}`.
- **Inicio:** ↑ desde «Recently played», con dos secciones vacías encima, sube a las categorías.
- Sin crashes. `:composeApp:assembleStableDebug` y `:composeApp:compileKotlinJvm` OK.

### Alcance del cambio
- **Datos por perfil:**
  - Las sesiones de todos los plugins, la cola y la lista negra pasan a ser por perfil.
  - Ajustes (idioma, tema, escala, plugin por defecto) siguen siendo comunes.
  - Las descargas y el caché de audio también.
- **Cambio de perfil en Android:** cierra el proceso, lo que detiene la reproducción y el servicio. También borra las cookies del WebView, que son las de login de los plugins: el siguiente inicio de sesión empieza limpio.
- **Escritorio e iOS:** guardan el perfil elegido, pero hay que reabrir la app a mano. No tienen botón propio: la ruta existe pero solo se llega desde el rail TV.
- **Cambiar la escala:** con Ajustes abierto, la ventana de Ajustes vuelve a la sección Plugins.

### No verificado / atajos de prueba
- **Diálogos:** no se abrieron uno a uno en TV.
- **Foco al cerrar un diálogo** (perfil añadido o eliminado): cae en el rail, no en el botón que lo abrió.
- **Renombrar perfil:** no se probó.
- **Distinción del perfil activo:** apenas se distingue por el color de fondo.
- **Sesión del plugin de Spotify:** sobrevive a la vuelta a «Principal», lo que indica que la guarda en `PersistedStorage`. No se revisó el código del plugin.
- **Inicio:** las secciones sin elementos («Popular with listeners of…», «Shows you might like») se muestran solo con su título.
- iOS sin compilar. `:composeApp:check` no ejecutado.

### Cambios residuales en el entorno
- Se creó y se eliminó el perfil «Prueba»; el reinicio dejó la app en «Principal».
- La escala se puso en 90 % y se devolvió a Automático.
- Se abrieron «Happy Hits!» y las opciones de «KESI - Remix» sin elegir ninguna. No se reprodujo nada.
- El cambio de perfil vació las cookies del WebView del emulador. La sesión de Spotify siguió activa.

---

## 20. Configuración — Firma release propia del fork y primer APK release

### Problema
El usuario pidió compilar el release firmado con un keystore propio, con una estrategia de firma segura y persistente. El procedimiento completo está en `docs/SIGNING.md`.

### Estado previo
- **Build:** `composeApp/build.gradle.kts` leía `release.signing.*` de `composeApp/local.properties`, que no existía, y firmaba también el debug con la clave release cuando estaba configurada.
- **CI:** `spotube-release-binary.yml` monta la firma desde los secretos `KEYSTORE`, `KEYSTORE_PASSWORD`, `KEYSTORE_KEY_ALIAS` y `KEYSTORE_KEY_PASSWORD`.
- **Keystores:** ninguno de Spotube en el equipo ni en Git.
- **Instalaciones existentes:**
  - Emulador: firmado con la clave debug de Android (SHA-256 `220b4097…d2c8`).
  - moto g22: Spotube oficial (`CN=Kingkor Roy Tirtho`, SHA-256 `38da7c14…4711`). El fork no puede actualizar esta instalación.

### Decisiones del usuario
- Crear un keystore nuevo, porque no existía una identidad del fork: `%USERPROFILE%\.keystores\spotube\spotube.keystore`, alias `spotube`.
- Mantener `applicationId` `org.spotube.spotube`.
- El debug vuelve a la clave debug.

### Solución Aplicada
- **Lectura de la firma:** `build.gradle.kts` la toma de las variables `SPOTUBE_SIGNING_*` (un solo proceso) o de `local.properties` (CI).
- **Fallo explícito:** cualquier tarea `assemble|bundle|package|install|sign*Release` falla con un mensaje claro si falta el keystore, el alias o la contraseña. Tampoco hay vuelta silenciosa a la clave debug.
- **Debug:** usa siempre la clave debug.
- **`.gitignore`:** añade `*.jks`, `*.keystore`, `*.p12`, `*.pfx` y `*.pepk`. Se comprobó que no hay ninguno rastreado.
- **`scripts/signing/create-keystore.ps1`:**
  - crea el keystore PKCS12 (RSA 4096) y se niega si ya existe;
  - restringe la carpeta y el archivo al usuario (`icacls`, sin herencia);
  - `keytool` pide las contraseñas ocultas.
- **`scripts/signing/build-release.ps1`:**
  - pide la contraseña con `Read-Host -AsSecureString` y la pasa solo como variables del proceso;
  - las borra al terminar y libera la memoria;
  - compila con `--no-configuration-cache`;
  - verifica con `apksigner` y compara la huella con `docs/SIGNING.md`.
  - Primero no reconocía el formato `V2 Signer:` de `apksigner`; se corrigió.
- **`docs/SIGNING.md`:** ruta, identidad pública, compilación, verificación, respaldo y restauración, cambio de contraseña con copia previa y verificación, e impacto en el CI.
- En Windows, los scripts se ejecutan con `powershell -ExecutionPolicy Bypass -File …`, que no cambia la política del sistema.

### Verificación
- **Sin firma:** `:composeApp:assembleStableRelease -m` falla con «Android release signing is not configured; missing: …».
- **Keystore:**
  - creado por el usuario en su terminal;
  - titular `CN=Fernando Cardenas, OU=Unknown, O=ferdcard-dev, L=Unknown, ST=Unknown, C=CO`;
  - SHA-256 `A6:36:90:09:FA:B7:8D:B5:B1:2A:4D:4D:77:FC:A2:52:E1:D6:A9:EB:77:0E:DB:63:04:DB:49:61:AA:FC:34:A2`;
  - permisos solo para `ASUS-GAMING\migue`, fuera del repositorio y sin rastrear en Git.
- **APK:** `composeApp/build/outputs/apk/stable/release/composeApp-stable-release.apk` (76,9 MB), compilado con el script en la terminal del usuario (`BUILD SUCCESSFUL in 6m 43s`).
- **Firma:** `apksigner verify --print-certs` da «Verifies», firma v2, un firmante, con el certificado del fork. La SHA-256 coincide con la del keystore y con `docs/SIGNING.md`.
- **Secretos:** no existe `composeApp/local.properties` ni ningún archivo con contraseñas en el repositorio.

### Alcance del cambio
- **Instalación:** el APK release no se instala encima del Spotube oficial ni de los APK debug, que tienen otra firma. Hay que desinstalarlos antes, y se pierden sus datos locales.
- **CI:** si los secretos `KEYSTORE*` del repositorio no contienen este keystore, los APK del CI tendrán otra identidad y no actualizarán los compilados en local. No se modificaron ni el flujo ni los secretos.
- **Firma v2 únicamente:** la elige AGP, sin v1, por el `minSdk` del proyecto.

### No verificado / atajos de prueba
- **Respaldo:** el respaldo externo del keystore lo hace el usuario; no se comprobó.
- **Cambio de contraseña:** no se ejecutó (el keystore es nuevo y la contraseña la eligió el usuario). El procedimiento está documentado.
- **APK release:** no se instaló ni se publicó, por indicación del usuario.
- **Nightly:** no se compiló el nightly release (`-Task :composeApp:assembleNightlyRelease`).

### Cambios residuales en el entorno
- Nueva carpeta `%USERPROFILE%\.keystores\spotube\` con `spotube.keystore`, con acceso solo para el usuario.
- APK release en `composeApp/build/outputs/apk/stable/release/`.
- Copias temporales de los APK instalados (para leer su firma) en la carpeta temporal de la sesión, fuera del repositorio.

### Instalación en el Redmi Note 12 (petición posterior del usuario)
- **Dispositivo:** `23028RA60L`, Android 15, ADB inalámbrico.
- **Estado previo:** tenía el Spotube oficial 6.0.0-beta.0, firmado por `CN=Kingkor Roy Tirtho` (SHA-256 `38da7c14…4711`), instalado el 2026-11-02.
- **Desinstalación:** con autorización expresa del usuario se desinstaló el oficial. Se borraron su sesión de Spotify, ajustes, cola, descargas y lista negra.
- **Primer intento:** HyperOS lo canceló (`INSTALL_FAILED_USER_RESTRICTED`) hasta activar «Instalar vía USB» y aprobar el aviso en el teléfono.
- **Segundo intento:** `composeApp-stable-release.apk` (versionCode 46, `minSdk` 28, `targetSdk` 37) se instaló bien.
- **Arranque:** `MainActivity` queda en primer plano mostrando la bienvenida «Música, sin ataduras.». Sin entradas en `logcat -b crash`.
- **Pendiente para el usuario en el teléfono:** completar la bienvenida e iniciar sesión de nuevo en Spotify.

### Instalación en la TV física Caixun (petición posterior del usuario)
- **Dispositivo:** `Caixun_4K_RATV3` («Sgamer»), Android 14, 1920×1080, solo 32 bits (`armeabi-v7a`).
- **Estado previo:** estaba en `Asleep` y se despertó con `KEYCODE_WAKEUP`. No tenía Spotube.
- **Librerías nativas:** el APK release incluye las 6 de `armeabi-v7a` (`libcompose_app.so`, `libquickjs.so`, `libjnidispatch.so`, entre otras). Android eligió `primaryCpuAbi=armeabi-v7a`.
- **Instalación:** `adb install` correcta, sin aviso en pantalla.
- **Arranque:** el lanzador Leanback abre `MainActivityTv` con la bienvenida y el foco en «¡Vamos!». Sin entradas en `logcat -b crash`.
- **Observación:** en esta TV el texto de la bienvenida bajo «Música, sin ataduras.» quedaba cortado por el borde inferior de la tarjeta. Se corrigió en §21.
- **Pendiente para el usuario:** completar la bienvenida e iniciar sesión en Spotify desde la TV.

---

## 21. Bug — Bienvenida cortada en la TV física

### Problema
En la TV Caixun, la descripción bajo «Música, sin ataduras.» quedaba fuera de la tarjeta de bienvenida. El D-pad no podía desplazar ese texto, porque no es enfocable.

### Causa Raíz
- La TV tiene la fuente del sistema al 130 % (`font_scale` 1.3) y densidad 320 (960 × 540 dp).
- La bienvenida se dibuja fuera de `TvShell`, así que no le aplicaba la escala de contenido de §17.
- Esa escala solo consideraba la altura disponible, no el tamaño de fuente.

### Solución Aplicada
- **`TvFitContent`:** nuevo componente en `AndroidTV/.../shell/TvFitContent.kt` que concentra la escala.
  - Escala automática = (alto / alto mínimo) / max(`fontScale`, 1), acotada entre 0,75 y 1.
  - Respeta la escala fija elegida en Ajustes.
- **`TvShell`:** lo usa para el contenido, con un mínimo de 440 dp.
- **`TvShellHost`:** envuelve también la bienvenida (`Routes.Welcome`) con un mínimo de 640 dp (`WELCOME_MIN_HEIGHT_DP`). La lectura del ajuste `tvContentScale` se adelantó para que sirva también aquí.

### Verificación
- `:composeApp:compileStableDebugKotlinAndroid` OK.
- El release lo compiló el usuario con `build-release.ps1`. La firma es la del fork (SHA-256 `a6369009…34a2`).
- En la TV Caixun se instaló como actualización con `adb install -r`, sin desinstalar.
- La página «Bienvenida» muestra el texto completo («…Para que lo hagas tuyo.») y «¡Vamos!» dentro de la tarjeta.
- La página «Apoyo» muestra los tres botones de donación, la nota y «Siguiente» completos.
- Sin entradas en `logcat -b crash`.

### Alcance del cambio
- **Compensación de fuente:** se aplica a todas las pantallas TV. En TVs con fuente del sistema grande, el contenido se reduce más (hasta el 75 %).
- **Bienvenida:** en TV también puede quedar reducida si no cabe.

### No verificado / atajos de prueba
- **Bienvenida:** no se revisaron las páginas «Plugins» y «Guía» en la TV.
- **Otras pantallas:** no se revisaron en la TV Caixun con la fuente al 130 %. La app sigue en la bienvenida, sin sesión.
- iOS sin compilar. `:composeApp:check` no ejecutado.

### Cambios residuales en el entorno
- APK release actualizado en la TV Caixun.
- La bienvenida quedó en la página «Apoyo» tras pulsar «¡Vamos!» una vez. No se completó ni se inició sesión.

---

## 22. Funcionalidad — Inicio de sesión en la TV con código QR

### Problema
El usuario pidió iniciar sesión en Spotify desde la TV escaneando un código QR con el teléfono, en lugar de escribir con el D-pad en el login web.

### Causa Raíz / análisis
- **Sesión del plugin:** el plugin de Spotify (`8384910b`, «Sonic Liberation») inicia sesión con el WebView, lee la cookie `sp_dc` y guarda su sesión en `PersistedStorage` con la clave `8384910b:credentials`.
- **Id del plugin:** sale de `MurmurHash3(name:author)`, así que es igual en todos los dispositivos.
- **Emparejamiento oficial:** Spotify no ofrece a terceros su emparejamiento de TV (`spotify.com/pair`).

### Decisión del usuario
- **Mecanismo:** «Transferir sesión». El teléfono, con este fork instalado y la sesión iniciada, envía su sesión a la TV, cifrada con una clave que solo va en el QR.
- **Dónde aparece:** al pulsar «Iniciar sesión» en la TV.
- **Descartado:** el «teclado remoto», que enviaba la contraseña por HTTP.

### Solución Aplicada
- **Protocolo** (`core/qrlogin/QrLogin.kt`):
  - La TV escucha temporalmente en `0.0.0.0` (puerto libre, con `startServerWithPortFallback`) y muestra el QR `http://<ip>:<puerto>/qr/<sesión>#k=<clave>`.
  - La sesión es de 128 bits y la clave AES de 256 bits. La clave va en el fragmento, que el navegador no envía al servidor.
  - `GET` devuelve una página que pasa el enlace a la app como `intent://qr-login…;scheme=spotube` (Android) o `spotube://qr-login?…`.
  - El teléfono envía con `POST` la carga `QrLoginPayload` (id del plugin y sus entradas), cifrada con AES-256-GCM y con la sesión como dato asociado.
  - La TV acepta un único envío:
    - 403 si la clave no es válida, 413 si pasa de 64 KB, 409 si el plugin no está instalado;
    - con una carga válida, guarda las entradas y se reinicia (`restartApplication`).
- **Plataforma:** `QrLoginPlatform` (expect/actual).
  - `androidJvmMain`: `javax.crypto`, `SecureRandom`, IPv4 privada con `NetworkInterface` y QR con ZXing `core` 3.5.3 (nueva dependencia en el catálogo).
  - iOS: no soportado.
- **TV:** `QrLoginReceiveViewModel` y `QrLoginScreen` (ruta `Routes.QrLogin`).
  - Muestra el QR dibujado en `Canvas`, los pasos, el estado y el botón «Iniciar sesión en esta TV», que abre el login web de siempre.
  - `MetadataAuthViewModel.startLogin()` abre el QR en TV y el login web en el resto de plataformas. Lo usan `MetadataPluginGuard` y `ErrorDisplay`.
- **Teléfono:**
  - `MainActivity` acepta `spotube://qr-login` (`VIEW` / `BROWSABLE`).
  - `QrLoginInbox` se registra como `ExternalUriHandler.listener`.
  - `QrLoginSendHost` (en la raíz de `App`) pide confirmación («¿Enviar tu sesión de Spotify a la TV en …?»), cifra y envía las entradas del plugin de metadatos seleccionado. Si no hay sesión, lo indica.
- **Navegación (fallo previo):** `Lyrics`, `SavedTracks`, `RemoteControl`, `JamSettings`, `Profiles` y `QrLogin` no estaban registradas en el `polymorphic(NavKey)` de `NavigationState`. Pasar la app a segundo plano con una de ellas abierta la cerraba (`SerializationException`); salió al probar el QR.
- **`build-release.ps1`:** vuelve a pedir la contraseña si llega vacía, hasta 3 veces.
- **Cadenas:** `qr_login_*` en inglés y español.

### Verificación
- **Emulador (debug, perfil sin sesión «Perfil 2»):**
  - «Sign in» abre el QR y el QR se decodifica (OpenCV).
  - `GET` de la página: 200, `text/html`, contiene `intent://qr-login`.
  - `POST` con clave aleatoria: 403. `POST` de 70 KB: 413.
  - El enlace `spotube://qr-login` abre el diálogo de confirmación. Con «Enviar» y sin sesión responde «Inicia sesión en Spotube en este teléfono…».
  - Después se volvió a «Principal».
- **Dispositivos reales (release firmado del fork, SHA-256 `a6369009…34a2`):**
  - TV Caixun (192.168.1.13) y Redmi (192.168.1.8) en la misma Wi-Fi.
  - El usuario escaneó el QR con el Redmi y confirmó.
  - La TV registró `QR login: stored 1 entries for plugin 8384910b`, se reinició y abrió Inicio con la cuenta: recomendaciones cargadas y reproducción en curso.
- Sin crashes. `:composeApp:compileStableDebugKotlinAndroid`, `:composeApp:compileKotlinJvm` y `assembleStableDebug` OK.

### Alcance del cambio
- **TV:** «Iniciar sesión» abre el QR en lugar del login web. El login web sigue en el botón «Iniciar sesión en esta TV».
- **Qué se transfiere:** la TV recibe la misma sesión del teléfono, no una nueva. Si la sesión del teléfono se cierra o caduca, la de la TV puede dejar de funcionar.
- **Puerto en la red:** mientras la pantalla del QR está abierta, la TV escucha en un puerto de la red local.
- **Navegación:** el registro de las 6 rutas evita cierres en todas las plataformas.

### No verificado / atajos de prueba
- **Sin Spotube en el teléfono:** no se probó qué ve el teléfono si escanea sin tener Spotube (debería quedarse en la página con el botón «Abrir Spotube»).
- **Escritorio:** no se probó como emisor ni como receptor (compila).
- **Perfiles:** no se probaron transferencias con un perfil distinto de «Principal» en la TV.
- La página del QR no se probó en navegadores distintos del que usó el Redmi.
- iOS sin compilar. `:composeApp:check` no ejecutado.

### Cambios residuales en el entorno
- **TV Caixun:** sesión de Spotify iniciada (copiada del Redmi) y reproducción en curso.
- **Redmi:** release actualizado. Se instaló desde `Descargas/Spotube-fork-release.apk`, porque HyperOS bloquea `adb install` y `pm install` sin aprobación en pantalla. El archivo sigue en Descargas.
- **Emulador:** release debug actualizado; «Perfil 2» se usó para la prueba y se volvió a «Principal».
- **Equipo:** se instaló `opencv-python-headless` para decodificar el QR desde capturas.

---

## 23. Funcionalidad / bug — Control remoto, pantalla encendida, perfiles en todas las plataformas y TV a pantalla completa

### Problema
Petición del usuario:
1. **Control remoto:**
   - La cola del teléfono no marcaba la pista que sonaba en la TV.
   - El teléfono mostraba «Spotube-a6f91d» en lugar del nombre configurado.
2. **Pantalla encendida:** el salvapantallas de la TV entraba mientras sonaba música. Se pidió un interruptor para mantener la pantalla encendida.
3. **Perfil «Principal»:** no se podía renombrar ni eliminar.
4. **Perfiles fuera de la TV:** botón también en móvil y escritorio.
5. **Márgenes en la TV** (añadido durante el trabajo): la app dejaba márgenes vacíos alrededor.

### Causa Raíz
- **(1) Cola:** `RemoteControlHandler` enviaba `QueueUpdated` solo al conectar y tras cada orden del teléfono. Un cambio de pista en la TV (fin de la canción, mando, cola editada allí) no llegaba al teléfono, que seguía marcando el índice viejo.
- **(1) Nombre:** `RemoteControlService` registraba el anuncio NSD una sola vez. Un cambio posterior de `remoteControlDeviceName` no lo rehacía, porque el bucle no hace nada si ya hay un servicio anunciado.
- **(2)** Ni `keepScreenOn` ni `FLAG_KEEP_SCREEN_ON` existían en la app.
- **(3)** `ProfileStore.remove` excluía el id `main` y la UI ocultaba el lápiz si el nombre era nulo.
- **(5)** `TvShell` aplicaba `tvSafeArea()` (48 × 27 dp de margen contra el overscan), innecesario en TVs actuales.

### Solución Aplicada
- **Cola remota:** dentro de la sesión, un colector de `combine(queueFlow, currentQueueEntryFlow).distinctUntilChanged().drop(1)` llama a `broadcastQueue`.
- **Nombre remoto:** `advertisedFor` guarda el nombre y el puerto anunciados. Si cambian, se llama a `stopAdvertising()` y se vuelve a registrar.
- **Pantalla encendida:**
  - `UserSettings.keepScreenOn: Boolean?`; null = activo en TV, inactivo en el resto.
  - `App` aplica `Modifier.keepScreenOn()` a la raíz, de Compose y multiplataforma.
  - Interruptor «Mantener la pantalla encendida» en Apariencia, solo en Android.
- **Perfil principal:**
  - Se puede renombrar.
  - Se puede eliminar si no es el activo y queda otro perfil. Se borran solo sus archivos propios del directorio compartido (`ProfileStore.PROFILE_FILES`).
  - La lista ya no vuelve a crear «main» si se eliminó.
  - `canDelete` = no activo y más de un perfil.
- **Perfiles en móvil y escritorio:**
  - Móvil: botón de usuario en la cabecera de Inicio (pantallas pequeñas).
  - Escritorio: entrada con el nombre del perfil activo en el pie de `AppSidebar`. `ProfilesUiState.activeProfile` alimenta estos botones.
  - El escritorio se reinicia solo al cambiar de perfil (`ProcessHandle` + `ProcessBuilder` + `exitProcess`).
- **TV a pantalla completa:** se quitó `tvSafeArea()` de la raíz del shell. El rail llega al borde y el contenido usa márgenes de 24 dp a los lados, 12 dp arriba y 16 dp bajo el mini reproductor.

### Verificación
- **Compilación:** `:composeApp:compileStableDebugKotlinAndroid` y `:composeApp:compileKotlinJvm` OK.
- **Emulador (debug):**
  - pantalla completa;
  - ventana con `fl=KEEP_SCREEN_ON`;
  - interruptor visible y activo por defecto en Apariencia;
  - «Principal» con lápiz y sin papelera (es el activo), y «Perfil 2» con papelera;
  - renombrar «Principal» guarda `{"id":"main","name":"Principal"}`.
- **TV Caixun** (release firmado, instalado con `adb install -r`):
  - ventana con `KEEP_SCREEN_ON`;
  - pantalla completa;
  - log `Advertising remote control service 'TV-Android' on port 14769`.
- **Redmi:** release instalado por el usuario desde Descargas.
  - Dispositivos y el control remoto muestran «TV-Android» y «Playing on TV-Android».
  - La cola marcaba «Sobredosis (feat. Ozuna)». Tras `KEYCODE_MEDIA_NEXT` en la TV, sin tocar el teléfono, marcó «Propuesta Indecente».
  - La TV siguió en `PLAYING`.
  - La cabecera de Inicio muestra el botón de perfil.
- Sin crashes.

### Alcance del cambio
- **Escritorio:** al cambiar de perfil se abre otra instancia y la actual se cierra.
- **Pantalla encendida en teléfonos:** desactivada por defecto. Si se activa, solo actúa con la app en pantalla.
- **TV:** todas las pantallas del shell ocupan la pantalla completa.

### No verificado / atajos de prueba
- **Eliminar «Principal»:** no se probó, porque borraría la sesión del emulador.
- **Escritorio:** el botón de perfil de la barra lateral y el reinicio automático solo se compilaron.
- **Conexiones abiertas:** con una conexión ya abierta, el nombre nuevo puede no verse hasta reconectar o volver a Dispositivos.
- **Textos sin traducir** (anteriores a este trabajo): «Connected», «Open remote», «Playing on…» y «Sign in to continue».
- iOS sin compilar. `:composeApp:check` no ejecutado.

### Cambios residuales en el entorno
- **TV Caixun:** release actualizado y una pista adelantada con `KEYCODE_MEDIA_NEXT`.
- **Redmi:** release actualizado. `Descargas/Spotube-fork-release.apk` sigue ahí. Se navegó hacia atrás desde el control remoto hasta Inicio.
- **Emulador:** debug actualizado. El nombre «Principal» quedó guardado explícitamente.

---

## 24. Funcionalidad — Dispositivo activo (control remoto tipo Spotify Connect), fases 1–3

### Problema
Con el teléfono conectado a la TV:
- Mucho de lo que se tocaba en el teléfono sonaba en el propio teléfono.
- La TV mantenía su cola aislada y no se podía añadir contenido con normalidad.

El usuario pidió que, mientras haya conexión, todo vaya al dispositivo conectado, y un botón en el reproductor para cambiar de dispositivo. El planteamiento se aprobó antes de implementar.

### Causa Raíz
- **Sin dispositivo activo:** `RemotePlaybackController` preguntaba el destino en cada acción con el selector «¿Dónde reproducir?».
- **Accesos que reproducían siempre en local:** `TrackCard`, `SearchScreen.playSingleTrack`, la pista del `FeaturedCarousel` y la rama de «Reproducir a continuación» cuando la pista ya estaba en la cola local.
- **Reproductores locales:** el reproductor del teléfono solo controlaba el reproductor local.
- **Fallo previo:** `RemoteControlCommand.Play` no llevaba la pista inicial. Tocar una canción concreta de una playlist o álbum con destino TV empezaba desde la primera.

### Solución Aplicada
- **Fase 1 — dispositivo activo:**
  - `core/remote/PlaybackDeviceManager` mantiene `activeDevice` (`Local` / `Remote(nombre)`). Al conectar pasa a `Remote` y pausa el reproductor local; al desconectar vuelve a `Local`.
  - `RemotePlaybackController.request()` ya no muestra el selector: envía al dispositivo activo.
  - Nuevo `playTracks(tracks, startIndex)`, que usan `TrackCard`, `SearchScreen` y el carrusel.
  - La rama «ya está en la cola local» solo se aplica si el activo es el teléfono (Álbum, Artista, Playlist, Guardadas, Buscar).
  - Los archivos locales (`LocalFolderViewModel`) cambian a «este dispositivo» y pausan la TV.
- **Protocolo:**
  - `Play.startTrack` (opcional).
  - `LoadQueue(tracks, startIndex, positionMs, autoPlay)`.
  - `RemoteQueueEntry.track` (opcional), con la metadata completa para poder tomar la cola.
  - El host aplica `LoadQueue` y salta a la posición con `seekWhenReady`.
  - Los campos nuevos tienen valor por defecto, así que la conexión sigue funcionando entre versiones; las funciones nuevas solo actúan si los dos dispositivos tienen esta versión.
- **Fase 2 — interfaz:**
  - `PlaybackDeviceViewModel` alimenta `PlaybackDeviceButton` (solo con conexión; resaltado si el activo es la TV). Abre «Reproducir en» con «Este dispositivo» y la TV.
  - El botón está en el mini reproductor local, el reproductor expandido, el reproductor de escritorio, el control remoto y el mini reproductor remoto.
  - `RemoteFloatingPlayer`: carátula, título, «Reproduciendo en …», progreso, reproducir/pausar, siguiente y botón de dispositivo. Al tocarlo abre el control remoto.
  - Con la TV activa, `AppShell` usa:
    - `RemoteFloatingPlayer` en móvil (barra) y en escritorio (en lugar de `AppLargePlayer`);
    - `RemoteControlScreen(onCollapse)` como reproductor expandido, con flecha para minimizar.
- **Fase 3 — transferencia:**
  - **Al pasar a la TV:** se envía `LoadQueue` con las pistas en streaming de la cola local, el índice actual y la posición, y se pausa el teléfono.
  - **Al pasar al teléfono:** se carga localmente la cola de la TV (con `RemoteQueueEntry.track`) en la misma pista, se salta a su posición y se envía `Pause` a la TV.
  - `seekWhenReady` espera a que la recarga reinicie la posición (< 5 s) y haya duración. La primera versión esperaba un cambio de `currentMediaItemFlow`, que no se emite si se recarga la misma pista (`MediaItem` es una `data class`).
- **Cadenas:** `playback_device_*` en inglés y español.

### Verificación
Release firmado (SHA-256 `a6369009…34a2`) en la TV Caixun (`adb install -r`) y en el Redmi (instalado desde Descargas):
- **Conexión:** el mini reproductor del teléfono pasó a ser remoto («Decidete — Reproduciendo en TV-Android», con botón de dispositivo). El control remoto muestra el botón de dispositivo.
- **Buscar:** tocar «Inevitable» (Shakira) en el teléfono la hizo sonar en la TV, y el mini reproductor remoto la reflejó.
- **Añadir a la cola:** «⋯ → Añadir a la cola» con «Antología» llegó como `addTrackToQueue` a la TV y apareció al final de su cola en el control remoto.
- **TV → teléfono:** el selector «Reproducir en» marcaba TV-Android. Con «Este dispositivo», la TV quedó `PAUSED` en 115 856 ms y el teléfono cargó «Inevitable» en 115 462 ms. El mini reproductor volvió a ser el local.
- **Teléfono → TV:** desde el botón del mini reproductor local, la TV reprodujo «Inevitable» y el teléfono volvió al mini reproductor remoto. La TV empezó en el segundo 6, no en la posición del teléfono (~2 min): era el fallo de la misma pista, corregido después.
- **Re-verificación con el arreglo** (nuevo release en ambos), filtrando `dumpsys media_session` por `package=org.spotube.spotube`:
  - TV → teléfono: la TV de Spotube quedó `PAUSED` en 22 260 ms y el teléfono reproducía.
  - Teléfono → TV: el teléfono iba por 68 895 ms y quedó `PAUSED` en 74 013 ms. A los 12 s la TV estaba en `PLAYING` en 79 931 ms, es decir, retomó en la misma posición.
- **Lecturas de estado anteriores:** la primera sesión de `dumpsys media_session` de la TV es la de KDE Connect, que refleja la música del teléfono. Las cifras de estado de la TV de §23 y del primer intento de §24 pueden ser de esa sesión, no de Spotube.
- **Compilación:** con el arreglo de posición, `compileStableDebugKotlinAndroid` y `compileKotlinJvm` OK.
- Sin crashes.

### Alcance del cambio
- **Sin selector:** con conexión ya no se pregunta «¿Dónde reproducir?»; manda el dispositivo activo.
- **Al conectar:** se pausa lo que sonara en el teléfono.
- **Archivos locales:** al transferir no viajan. Reproducirlos con la TV activa cambia a este dispositivo.
- **Fase 4 pendiente:** que la TV controle el teléfono. En la TV no hay botón de dispositivo.

### No verificado / atajos de prueba
- **Escritorio:** la barra remota no se probó.
- **Otros accesos no probados en dispositivo:**
  - reproducir una playlist o álbum desde una canción concreta;
  - el carrusel;
  - las tarjetas de canción;
  - el reproductor expandido remoto (deslizar hacia arriba);
  - archivos locales con la TV activa.
- **Corte de conexión:** no se probó el regreso a «este dispositivo».

### Cambios residuales en el entorno
- **TV:** suena «Las de la Intuición» (Shakira), devuelta desde el teléfono en la prueba final. La cola de la prueba anterior tenía «Antología» al final.
- **Redmi:** queda en el control remoto. Se buscó «Shakira» y se abrió y cerró la página del artista por un toque fuera de sitio.

---

## 25. Build — Versión de escritorio para Windows y preparación de Linux

### Problema
Compilar la versión de escritorio para Windows en este equipo y preparar la de Linux, que se compilará en otro equipo clonando el repositorio.

### Decisiones del usuario
- **Windows:** instalador `.exe` **sin firma Authenticode** por ahora, verificable por su SHA-256.
- **Linux:** solo `.deb`.

### Solución Aplicada
- **Windows:**
  - `:composeApp:packageGraalvmExe -Pnightly=false`, con `GITHUB_TOKEN` (de `gh auth token`) solo en ese proceso, para el límite de la API al buscar GraalVM.
  - ComposeNativeWebview ya estaba en `~/.m2`, con `compose_webview_windows.dll` y `WebView2Loader.dll`. Visual Studio Build Tools y Rust ya estaban instalados.
- **Linux:** `scripts/linux/build-deb.sh` (Debian/Ubuntu). Pide confirmación antes de instalar:
  - paquetes `apt` (compilación, GTK/WebKitGTK, `patchelf`, JDK 21 y librerías de ejecución);
  - Rust;
  - el SDK de Android (command-line tools 19.0, `platforms;android-37`, `build-tools;37.0.0`; muestra las licencias).
  - Después compila ComposeNativeWebview en el commit fijado por el CI (lo lee de `spotube-release-binary.yml`) con su librería de Linux, lo publica en `~/.m2`, ejecuta `packageGraalvmDeb` y muestra la ruta y el SHA-256.
- **Guía:** `docs/BUILD-DESKTOP.md`.

### Verificación
- **Compilación:** `BUILD SUCCESSFUL in 7m 29s`.
- **Instalador:** `composeApp/build/compose/binaries/main/graalvm-exe/spotube-6.0.0-win-x64.exe`.
  - Tamaño: 60 385 769 bytes.
  - SHA-256: `2F2E28E3F9AB0EF02DE962B049DA5054484E1A68252666C823641F95F0E1CB57`.
  - `Get-AuthenticodeSignature`: `NotSigned` (esperado).
- **Prueba de arranque:** se ejecutó `graalvm-app/org.spotube.spotube/spotube.exe` sin instalar nada. A los 20 s seguía en marcha con la ventana «Spotube» (~200 MB) y se cerró.

### Alcance del cambio
- **Instalador sin firma:** SmartScreen mostrará «editor desconocido» al instalarlo.
- **Script de Linux:** solo se ha escrito, no se ha ejecutado. Pide `sudo` para `apt` y descarga Rust y las herramientas del SDK de Android de sus sitios oficiales.

### No verificado / atajos de prueba
- **Instalador de Windows:** no se instaló ni se probó dentro de la app (reproducción, WebView de login).
- **Script de Linux:** no se ejecutó. En particular, no se comprobó que la URL de las command-line tools ni la publicación de ComposeNativeWebview funcionen en ese equipo.

### Cambios residuales en el entorno
- GraalVM CE descargado por Nucleus en `~/.gradle/nucleus/graalvm`.
- Se lanzó y cerró una vez el binario nativo. Puede haber creado su carpeta de datos de escritorio, perfil «Principal», sin sesión.

---

## 27. Autenticación externa en Linux

### Verificación
- Se instaló en este equipo el `.deb` de la compilación más reciente.
- La autenticación en la ventana externa funciona: se completó el inicio de sesión en una cuenta de Spotify.

### Compilación
- `./gradlew :composeApp:check` no terminó correctamente. Gradle no pudo resolver `composewebview:webview-compose-iossimulatorarm64:unspecified`, requerido por `dev.nucleusframework:composewebview:0.1.0-SNAPSHOT` en `iosSimulatorArm64TestCompileKlibraries`.
- Este fallo impidió completar la validación agregada; no invalida la prueba funcional del `.deb` instalado.

---

## 28. Ventana de Spotube agrupada con el lanzador de Plank

### Problema
- Al abrir Spotube desde el lanzador anclado en Plank, la ventana aparecía como un icono separado.
- La ventana X11 reportó `WM_CLASS = "spotube", "Spotube"`, pero el `.desktop` del `.deb` tenía `StartupWMClass=org-spotube-spotube-MainKt`, que no coincidía.

### Solución aplicada
- La configuración Linux en `composeApp/build.gradle.kts` ahora declara `startupWMClass = "Spotube"`, para que el `.desktop` generado por Gradle coincida con la clase real de la ventana.
- La plantilla `packaging/linux/org.spotube.spotube.desktop` también declara `StartupWMClass=Spotube`.

### Verificación
- `./gradlew :composeApp:tasks --all --no-configuration-cache`: `BUILD SUCCESSFUL`; la DSL de empaquetado quedó configurada correctamente.
- `./gradlew :composeApp:packageGraalvmDeb --no-configuration-cache`: `BUILD SUCCESSFUL in 1h 48m 47s`.
- Artefacto: `composeApp/build/compose/binaries/main/graalvm-deb/spotube-6.0.0-linux-amd64.deb` (59 066 704 bytes), SHA-256 `2cd51bf38f9125b7e791034e239957f590a7f3a90025daaeec59db09f736b231`.
- El `.desktop` dentro del `.deb` generado contiene `StartupWMClass=Spotube`.
- El usuario instaló el `.deb` en este equipo y confirmó que la ventana se agrupa correctamente con el lanzador anclado en Plank.

---

## Historial de Versiones

| Fecha | Versión | Cambios |
|---|---|---|
| 2026-11-03 | — (sin publicación) | Inicio: auditoría Android TV (§2), plan técnico `AndroidTV/PLAN.md` y bitácora `docs/BITACORA.md` (§1), constitución de firma independiente (§4). Solo documentación, sin código de producto ni BUILD |
| 2026-11-03 | — (sin publicación) | Implementación Android TV Fases 0–7 parcial (§5): módulo `:AndroidTV` compilado solo con `compileSdk` 36 temporal; `:composeApp` sin compilar (falta Rust, SDK 37 no reconocido por AGP). Sin BUILD firmado |
| 2026-11-03 | — (sin publicación) | `compileSdkMinor = 0` desde el catálogo (§6): `:AndroidTV` compila contra el SDK 37.0 real. Sigue faltando Rust para `:composeApp` |
| 2026-11-03 | — (sin publicación) | Primer BUILD `:composeApp:assembleStableDebug` exitoso con capa TV (§7): 2 imports TV corregidos, manifest con `LEANBACK_LAUNCHER` verificado. APK firmado con keystore debug solo para verificación, no distribuible |
| 2026-11-03 | — (sin publicación) | Primera ejecución en emulador Android TV (§8): arranque Leanback sin crashes y navegación D-pad por rail, Settings y Library. Corregido el anillo de foco invisible (orden `onFocusChanged`/`clickable`) y el contorno de 1 px en 5 componentes `:AndroidTV` |
| 2026-11-03 | — (sin publicación) | Fallos de foco de §8 corregidos y verificados en emulador (§9): foco inicial en Welcome, límites del rail, pestañas de Library sin cortes, seleccionado distinguible con foco |
| 2026-11-03 | — (sin publicación) | Settings → Plugins (§10): ↑/↓ ya no abren los desplegables en TV (trampa de foco de `composeunstyled`) y las tarjetas de plugin muestran anillo de foco |
| 2026-11-03 | — (sin publicación) | Menús emergentes anclados a su disparador en TV (§11): override `-television` del estilo `Modal` de `composeunstyled` (ventana a ancho completo) |
| 2026-11-03 | — (sin publicación) | Salir de Ajustes en TV (§12): `NavigationCommandsEffect` compartido; `TvShellHost` ya aplica los `pop`/`navigateTo` que antes se perdían |
| 2026-11-03 | — (sin publicación) | Cadenas `tv_*` traducidas al español (§13) |
| 2026-11-03 | — (sin publicación) | Recorrido D-pad completo con cuenta de Spotify (§14): 14 fallos registrados, 8 de gravedad alta (reproductor expandido, desplegables y campos de Ajustes, Buscar, filtro y opciones de canción). Sin cambios de código |
| 2026-11-03 | — (sin publicación) | Los 8 fallos graves de §14 corregidos y verificados en emulador (§15): barra de progreso enfocable y ±10 s con icono, `TvMenuDialog` para desplegables, `TvTextFieldGate` en 12 campos, teclado TV a pantalla completa, filas de canción con anillo y opciones con OK mantenido |
| 2026-11-04 | — (sin publicación) | Los 6 fallos menores de §14 corregidos y verificados en emulador (§16): foco inicial y por pantalla (`TvEntryFocus`), foco sostenido en botones que cargan, carrusel con ←/→, categorías que conservan el foco, anillo en el mini reproductor y rail que entra por la sección seleccionada. Atrás vuelve a la sección de origen, no siempre a la tarjeta exacta |
| 2026-11-04 | — (sin publicación) | Atajos y ajustes TV (§17): doble ↓ al mini reproductor y doble ↑ a la cabecera, → entra a los botones de tarjetas y filas, sin textos de ayuda, contenido escalado para que quepa, foco visible y atrapado en el diálogo de paleta, botón Letra. Análisis de multiusuario (§18), sin código |
| 2026-11-04 | — (sin publicación) | Escala TV elegible en Apariencia, `TvDialogFocus` en los 10 diálogos Millennium, «Ir al álbum» alcanzable y perfiles locales con botón de usuario en el rail (§19). Cambio de perfil con reinicio verificado en emulador |
| 2026-11-04 | — (sin publicación) | Firma release propia del fork (§20): keystore en `~/.keystores/spotube` (SHA-256 `A6:36:…:34:A2`), build que falla sin firma, scripts de creación y compilación con contraseña oculta, `docs/SIGNING.md`. Primer `composeApp-stable-release.apk` firmado y verificado con `apksigner`, sin instalar |
| 2026-11-04 | — (sin publicación) | Bienvenida cortada en la TV física corregida (§21): `TvFitContent` reutilizable que compensa la fuente grande del sistema y se aplica también a la bienvenida. Release firmado instalado como actualización en la TV Caixun y verificado |
| 2026-11-04 | — (sin publicación) | Inicio de sesión en TV por QR (§22): el teléfono con sesión envía su sesión cifrada (AES-256-GCM, clave solo en el QR) a la TV, que la guarda y se reinicia. Verificado entre el Redmi y la TV Caixun con el release firmado. Corregido el cierre al pasar a segundo plano en 6 rutas sin registrar |
| 2026-11-04 | — (sin publicación) | Control remoto sincronizado: la cola marca la pista actual y el anuncio usa el nombre configurado. Pantalla encendida con interruptor (activo por defecto en TV). Perfil principal renombrable y eliminable; perfiles en móvil y escritorio. TV a pantalla completa (§23). Verificado entre la TV Caixun y el Redmi |
| 2026-11-04 | — (sin publicación) | Dispositivo activo para el control remoto, fases 1–3 (§24): todo lo que se reproduce va al dispositivo conectado, mini reproductor remoto, botón «Dispositivo» y transferencia de la cola y la posición en ambos sentidos. Verificado entre el Redmi y la TV; el salto a la posición al recargar la misma pista se corrigió después y falta comprobarlo |
| 2026-11-04 | — (sin publicación) | Versión de escritorio para Windows (§25): instalador GraalVM `spotube-6.0.0-win-x64.exe` sin firma Authenticode (SHA-256 `2F2E28E3…CB57`); el binario nativo arranca. Script `scripts/linux/build-deb.sh` y `docs/BUILD-DESKTOP.md` para el `.deb` en Linux, sin ejecutar |
| 2026-11-05 | — (sin publicación) | Autenticación externa en Linux (§27): con el `.deb` de la compilación más reciente instalado, se completó el inicio de sesión en Spotify. `:composeApp:check` quedó bloqueado por la dependencia `composewebview` de iOS Simulator no resuelta |
| 2026-11-06 | — (sin publicación) | Ventana agrupada con Plank (§28): `StartupWMClass` corregido a `Spotube` según el `WM_CLASS` real; `.deb` GraalVM compilado en 1 h 48 min, verificado por extracción y confirmado funcionando tras instalarlo |
