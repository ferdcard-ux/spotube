# Transiciones y DJ Livi

Pruebas posteriores en un teléfono físico: [informe del moto g22](device-tests/moto-g22-2026-11-12.md).

La implementación respeta los source sets de Kotlin Multiplatform. El equivalente de `src/features/player/` y `src/features/dj/` está en `composeApp/src/commonMain/kotlin/org/spotube/spotube/features/`; las implementaciones nativas están en las mismas rutas de `androidMain`, `jvmMain` e `iosMain`. No se añadieron dependencias ni servicios de pago.

## Archivos

- `features/player/TransitionAudioPlayer.kt`: dos reproductores nativos, cola pública, precarga, fundido, volumen maestro, repetición y controles.
- `features/player/TransitionPlan.kt`: curva equal-power y selección de mezcla según BPM.
- `features/dj/DjService.kt`: historial por perfil en DataStore, recomendaciones en bloques de hasta cuatro canciones, guiones y coordinación de voz.
- `features/dj/DjCard.kt`: ViewModel y tarjeta de Home con animación durante la voz.
- `features/dj/NativeSpeech.kt` y sus implementaciones: voz de Android, AVSpeechSynthesizer en iOS, voz del sistema en escritorio. Windows usa System.Speech, macOS usa say y Linux usa Speech Dispatcher si está instalado.
- `androidMain/.../features/player/SharedAudioFocus.kt`: un foco de audio compartido para permitir el solapamiento de ambos reproductores.
- Modificados: `AudioPlayer.kt`, los tres `AudioPlayer.<plataforma>.kt`, `AudioPlayerModels.kt`, `DeviceAudioPlayerQueue.kt`, los cuatro `Modules*.kt`, `PlaybackService.kt`, `AndroidManifest.xml`, `HomeScreen.kt`, `SettingsModels.kt`, `SettingsViewModel.kt`, `PlaybackSection.kt`, los recursos de texto generales y españoles y `plugin_interfaces/.../MetadataTrackModels.kt`.
- Pruebas: `commonTest/.../features/player/TransitionAudioPlayerTest.kt` y `jvmTest/.../features/player/TransitionNativeTest.kt`.

## Cómo probar

1. **Gapless:** en Ajustes → Reproducción, activar reproducción sin pausas y poner fundido en 0. Reproducir dos canciones locales sin silencio grabado. Al faltar cinco segundos se prepara el segundo motor; al terminar se inicia desde cero. Comparar con gapless desactivado. Repetir con streaming y con red lenta.
2. **Crossfade:** poner el fundido en 6 segundos. Reproducir dos canciones, escuchar los últimos seis segundos de la primera y comprobar que ambas se solapan. Cambiar el volumen maestro durante el fundido; probar pausa/reanudar, siguiente, anterior, búsqueda, eliminar o mover la siguiente canción y repetición. Con 0 se desactiva el solapamiento. En canciones cortas se limita a la mitad de la duración.
3. **Automix:** activar Automix y reproducir una playlist. El proveedor debe informar `bpm` en ambas canciones: por ejemplo, 100 y 105. Una diferencia estrictamente menor de 10 selecciona una mezcla de ocho segundos y ajusta el tempo entrante. Con diferencia de 10 o más, sin BPM o fuera de playlist se utiliza el fundido configurado. La alineación requiere además `beatOffsetMs` (posición real del primer beat) en ambas canciones; sin ese dato no se inventa una fase rítmica. La entrada se alinea al primer beat disponible dentro de la ventana de mezcla, no al último beat de toda la canción: esperar hasta ese último beat sería incompatible con ocho segundos de solapamiento.
4. **DJ Livi:** escuchar al menos diez segundos de varias canciones de streaming. En Home, pulsar «Activar DJ Livi». Se solicitan hasta cuatro recomendaciones al proveedor y se presenta cada canción antes de reproducirla. Verificar la tarjeta y su onda durante la voz, pausa/siguiente durante una presentación y desactivación del modo. Al llegar a la última canción se solicita otro bloque. El historial conserva las últimas veinte escuchas; artistas y género favorito se derivan solo de datos disponibles. Si no hay historial, recomendaciones o voz instalada se informa en pantalla. La voz tiene un límite de espera de quince segundos para no bloquear la música.

El modo DJ desactiva los fundidos entre canciones mientras intercala presentaciones. El ajuste de DJ también se encuentra en Ajustes → Reproducción. Todos los ajustes sobreviven al reinicio.

## Verificación y límites

```powershell
.\gradlew.bat :composeApp:jvmTest --tests "org.spotube.spotube.features.player.Transition*Test"
.\gradlew.bat :composeApp:assembleStableDebug
.\gradlew.bat :composeApp:check
```

La prueba nativa genera un WAV temporal silencioso para verificar precarga y cambio de motor en VLC. Las pruebas comunes comprueban conservación de potencia, BPM inválido, precarga a cinco segundos, pausa y búsqueda durante el fundido e invalidación de la siguiente canción.

Verificación realizada el 12 de noviembre de 2026 en Windows: las seis pruebas enfocadas pasaron (cinco comunes y una nativa de VLC), y `:composeApp:assembleStableDebug` terminó correctamente. APK: `composeApp/build/outputs/apk/stable/debug/composeApp-stable-debug.apk`. La suite JVM completa ejecutó 186 pruebas y reportó 34 fallos fuera de las pruebas nuevas; 26 fallan en la preparación por ausencia de los archivos de audio requeridos. No se certificó la continuidad auditiva ni se ejecutó iOS.

Esta capa reduce la espera mediante precarga, pero el cambio entre dos salidas nativas se coordina mediante eventos y corutinas; **no garantiza continuidad exacta a nivel de muestra ni 0 ms en todos los dispositivos**. La aceptación auditiva queda pendiente de pruebas en hardware. No se analiza ni se recorta el silencio grabado en el archivo; tampoco se detectan beats a partir de PCM. Los proveedores actuales pueden omitir BPM, género y posición del beat, en cuyo caso se aplican las alternativas descritas.

iOS necesita compilación y pruebas en macOS. El `check` global de este entorno falla al resolver `composewebview:webview-compose-iossimulatorarm64:unspecified`. La suite JVM completa también contiene pruebas que requieren cinco archivos de audio externos en `src/jvmTest/resources/test-audio/`, además de fallos en pruebas de interfaz; las pruebas enfocadas evitan esos requisitos ajenos a las transiciones.

Referencias de las APIs nativas: [velocidad sin cambiar pitch en Media3](https://developer.android.com/reference/androidx/media3/common/Player.html), [algoritmos de tiempo y tono en Apple](https://developer.apple.com/documentation/avfoundation/time-pitch-algorithm-settings), [voz del sistema en Windows](https://learn.microsoft.com/en-us/dotnet/api/system.speech.synthesis.speechsynthesizer.speak).
