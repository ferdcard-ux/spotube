# Integración del desarrollo original — 12 de noviembre de 2026

## Problema observado y solución

Se capturó la pantalla del Moto g22 antes de interactuar con ella: `build/device-tests/moto-g22/18-user-reported-problem.png`. El instalador rechazaba Spotify 1.1.0 porque declaraba API 0.1.0 y la aplicación implementaba 0.0.1.

Se integró `team-spotube/spotube`, rama `master`, hasta [5768ad7568665130bdbc057348bc2b9e1cf8cc38](https://github.com/team-spotube/spotube/commit/5768ad7568665130bdbc057348bc2b9e1cf8cc38): 44 commits exclusivos del upstream respecto del fork. La actualización incluye el contrato real de plugins 0.1.0, restricciones de cookies por dominio, caché de sufijos públicos y errores de permisos. No se cambió solamente el número de versión para eludir la comprobación.

El complemento público [Spotify 1.1.0](https://github.com/sonic-liberation/spotube-plugin-spotify/blob/main/spotube_plugin_spotify/build.gradle.kts) declara acceso de cookies a `spotify.com`. El teléfono mostró sus tres permisos y permitió instalarlo. Captura posterior: `24-spotify-api-010-review.png`, en el mismo directorio de capturas. La jerarquía de interfaz `27-spotify-installed-confirmed.xml` registra Home cargando el proveedor Spotify y solicitando iniciar sesión, con el botón de DJ Livi visible; el PNG de esa misma toma aún muestra el estado transitorio de carga.

## Conservación del fork

Antes de fusionar se creó la rama local `integration/upstream-2026-11-12` y el commit `d167b6b2`, que conserva las cuatro funciones de reproducción y sus pruebas. La rama original `feature/android-tv` permanece en `74572134`. El respaldo adicional está en `build/upstream-integration-backup/working-tree-before.zip`, con parche y manifiesto junto al ZIP. No se publicaron cambios al remoto.

Se conservaron Android TV, perfiles, inicio mediante QR, control remoto, gapless, crossfade, Automix y DJ Livi. Resoluciones principales:

- La migración de base de datos mantiene el directorio propio de cada perfil.
- El foco de Android TV se trasladó del selector de plugins eliminado al nuevo selector.
- El nuevo motor VLC de iOS conserva dos motores para las transiciones y una sola sesión multimedia para los controles del sistema.
- Las salas Jam suspenden crossfade, Automix y las presentaciones del DJ mientras comparten una línea temporal. Las preferencias se mantienen y las funciones vuelven a estar disponibles al salir.

Otras mejoras originales incorporadas: listas y colas más eficientes, caché de streaming y portadas, rediseño del gestor de plugins, nuevo servidor y seguridad de Jam, selección de actualizaciones mediante SemVer, versión 6.0.0, correcciones de Linux, reproducción en segundo plano de iOS y empaquetado Homebrew.

## Verificación

- Compilaron `assembleStableDebug`, `assembleNightlyDebug` y `assembleNightlyDebugAndroidTest` con el código integrado.
- `plugin_interfaces:jvmTest`: 5 pruebas aprobadas.
- Suite JVM de la aplicación: 265 pruebas, 256 aprobadas y 9 fallidas. Pasaron los casos de transiciones (6), integración nativa de transiciones (1), seguridad y sincronización de Jam, cookies, sufijos públicos y actualizaciones.
- Ocho fallos corresponden a las mismas pruebas de interfaz observadas antes de integrar: `JamLayoutTest`, `LibraryToolbarTest`, cuatro de `MediaLibraryUiTest` y dos de `LocalMediaFoldersEditorTest`.
- El caso nativo de escritorio `shuffle does NOT restart current track` falla antes de activar shuffle: la posición no supera 100 ms tras la espera inicial de 500 ms, aunque el estado es PLAYING. Falló también al ejecutarlo aisladamente. La implementación JVM del reproductor no cambió respecto de `d167b6b2`; no se considera una regresión demostrada ni una prueba aprobada. Las pruebas anteriores no llegaban a esta aserción por falta de archivos de audio.
- Para ejecutar los 26 casos nativos antiguos se generaron cinco WAV temporales. No forman parte del cambio. Los resultados completos se conservaron en `build/upstream-integration-backup/full-jvm-results/` y `full-jvm-summary.json`.

El APK Nightly 6.0.0-nightly.47 se actualizó en el Moto g22 mediante ADB, transporte 3. Stable sigue instalado con su firma y sus datos originales. Los artefactos finales están en `composeApp/build/outputs/apk/{stable,nightly}/debug/`.

Las cuatro pruebas nativas se repitieron sobre ese APK integrado y aprobaron: gapless (7.051 ms de ejecución), crossfade con controles de sesión multimedia (11.152 ms), Automix (4.290 ms) y DJ (11.441 ms). Estos tiempos son la duración de cada prueba, no latencias de transición. Evidencia: [resultados JSON](device-tests/moto-g22-upstream-2026-11-12.json). Se restauraron los ajustes y el historial, se limpiaron los audios temporales y se retiró únicamente el APK auxiliar de pruebas. Spotify permaneció instalado y activo después de la prueba.

## Comprobación posterior con Spotify autenticado

Después de que el usuario inició sesión directamente en el Moto g22, Home cargó recomendaciones y una playlist mostró canciones, artistas, portadas y duraciones. Se inició streaming real y Android confirmó PLAYING, avance de posición y búfer de red. No se leyeron ni se copiaron credenciales.

Pausa y reanudación respondieron mediante las teclas multimedia de Android. DJ Livi utilizó el historial de esa escucha, recibió recomendaciones del proveedor y creó un bloque de cuatro canciones, comprobado visualmente en la cola. La tarjeta de Home mostró el estado de presentación y un guion basado en el historial. Después comenzó la canción recomendada. Siguiente llevó a la segunda recomendación, que también inició reproducción.

Con DJ desactivado y crossfade configurado en cinco segundos, se buscó cerca del final de una canción y se observó el cambio automático a la siguiente. La posición inicial publicada para la canción entrante fue aproximadamente tres segundos, consistente con un inicio anticipado. El muestreo de la sesión no mide las ganancias de los motores ni certifica cinco segundos completos de mezcla. Android publicó un estado IDLE aislado durante el intercambio; no se puede deducir de él un silencio audible. Los campos de posición de dumpsys son instantáneas de eventos, no un reloj actualizado en cada consulta.

Se probó también Anterior y se dejó la reproducción pausada. Ajustes finales: gapless activado, fundido de cinco segundos, Automix y DJ desactivados. Se conservan la sesión, la cola real y el historial de las escuchas realizadas. Las capturas solicitadas y los registros de diagnóstico permanecen en el directorio local ignorado `build/device-tests/moto-g22/`; no se incorporan al repositorio datos musicales de la sesión privada. Las imágenes y los XML se toman secuencialmente y pueden representar momentos distintos de una animación.

## Límites

La suite completa aún tiene fallos; esta integración no certifica ausencia de toda regresión. iOS requiere compilación y pruebas en macOS; aquí solamente se revisó su adaptación. Android TV y las salas Jam necesitan además una comprobación interactiva entre dispositivos. No se realizó escucha humana ni análisis PCM para certificar huecos de exactamente 0 ms o alineación musical de beats. El catálogo y las recomendaciones reales de Spotify sí se comprobaron después de iniciar sesión. Automix con música real y beats conocidos, y gapless real con crossfade en cero, siguen pendientes de una comprobación específica; sus casos instrumentados con datos conocidos aprobaron.
