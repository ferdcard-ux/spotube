# Ajustes de DJ y Mix

## Comportamiento

- Las claves se conservan por proveedor en el dispositivo. Cambiar entre OpenRouter, Gemini y Groq restaura su clave y modelo. Eliminar una clave afecta solo al proveedor seleccionado.
- Android usa AES-GCM y una clave no exportable de Android Keystore; los archivos cifrados están excluidos del respaldo. Windows usa DPAPI del usuario. macOS/iOS usan Keychain; Linux usa Secret Service mediante `secret-tool`, sin alternativa en texto plano.
- La activación, el nombre y la configuración pública del DJ están en Ajustes → Reproducción. Activarlo habilita el botón inferior sin interrumpir la música actual.
- Cada toque en DJ solicita un set nuevo de seis canciones distintas. Una petición nueva cancela la anterior. La cola se reemplaza cuando las canciones están disponibles; si no se resuelven al menos seis pistas, se conserva la cola anterior y se informa del error.
- La voz seleccionada saluda al inicio del set y se despide cuando termina la última pista. No presenta cada canción. El set termina después de la despedida; tocar DJ inicia otro.
- Las candidatas provienen del historial, de las playlists guardadas, de la cola y de descubrimientos del proveedor de música. La IA opcional elige identificadores reales entre esas candidatas; no inventa canciones. Los anuncios usan plantillas verificables y la voz nativa.
- Mix pertenece a las playlists y a Canciones favoritas. Su selección se guarda por lista. No activa DJ, no habla y no utiliza sus credenciales. Los estados distinguen Mix desactivado, activado y reproduciendo.
- El DJ muestra preparación, reproducción y voz mediante su botón y una tarjeta de estado con ondas. El botón nunca abre configuración.

## Motores de voz

- **Voz del dispositivo** (Android TTS / AVSpeech / voz del sistema operativo): valor predeterminado y respaldo cuando Gemini no responde.
- **Gemini** (en línea): voz expresiva con la clave de Gemini de la sección de IA. Se elige una de sus 30 voces o la automática; el audio se prepara por adelantado. Si falla, habla la voz del dispositivo y el aviso aparece una sola vez (5 s).
- En «Voz del DJ» se elige la voz concreta de cada motor; la del dispositivo lista las voces instaladas en el teléfono. «Probar la voz» reproduce una muestra.
- Piper (voz local) se retiró el 2026-11-13: sus voces sonaban mal y el motor aumentaba el APK de unos 212 MB a 346 MB.

### Propuesta para OpenVoice autoalojado

OpenVoice no fija un protocolo HTTP interoperable para clientes. Para mantener Spotube independiente de una implementación concreta, se propone un adaptador del servidor compatible con `POST /v1/audio/speech`:

- Solicitud JSON con `input`, `voice`, `language`, `speed` y `response_format: "wav"`; respuesta de audio `audio/wav` PCM.
- URL del servidor, voz y credencial opcional configurables por el usuario. No se enviará texto al servidor hasta que OpenVoice esté seleccionado explícitamente.
- HTTPS por defecto; un endpoint HTTP de LAN requerirá habilitación explícita. Las credenciales se guardarán en el almacén seguro nativo.
- Si el servidor seleccionado no responde, habla la voz del dispositivo. El contrato queda como propuesta, pendiente de confirmación antes de implementar el cliente.

## Cómo probar

1. En Ajustes → Reproducción, activar IA opcional. Introducir una clave y modelo para un proveedor y pulsar Guardar configuración. Cambiar de proveedor, configurar otro y volver al primero: debe recuperar su configuración. Cerrar y abrir la aplicación: deben conservarse. Borrar la clave del proveedor seleccionado y comprobar que las otras siguen presentes.
2. Activar DJ desde Ajustes. En cualquier pantalla, tocar el botón inferior DJ: debe preparar seis canciones, saludar una sola vez y empezar a reproducir. Siguiente/anterior no deben producir presentaciones individuales.
3. Tocar DJ mientras reproduce: debe preparar otro set, reemplazar la cola y dar una nueva bienvenida. Varios toques durante la preparación deben conservar solo la petición más reciente.
4. Reproducir la última canción del set hasta su final: debe escucharse una despedida única, sin añadir automáticamente recomendaciones de reproducción infinita.
5. Con DJ habilitado, abrir una playlist, activar Mix y reproducirla. No debe escucharse voz. Comprobar el estado del botón Mix y los controles de reproducción al pausar/reanudar.
6. Probar sin historial pero con playlists guardadas: deben poder alimentar el set. Si el proveedor no proporciona seis pistas válidas, debe mostrarse un error sin reproducir un bloque corto.

La versión anterior solo guardaba las claves en RAM: hay que introducirlas una vez en esta actualización para empezar a conservarlas de forma segura. Nunca introducir claves en el chat ni guardarlas en el repositorio.

## Archivos principales

- `features/dj/DjAiSession.kt`, `DjCredentialStore.kt` y sus implementaciones nativas: almacenamiento por proveedor y preferencias persistentes.
- `features/dj/DjService.kt`, `DjSetLifecycle.kt`, `DjAiPlanner.kt`: sets, recomendaciones y anuncios por bloque.
- `features/dj/DjNavigationControl.kt`, `DjCard.kt`, `DjAiConfiguration.kt`: acción DJ, estados y ajustes.
- `features/player/TransitionAudioPlayer.kt`, `PlaylistMixViewModel.kt`, `PlaylistMixButton.kt`: final de cola y estados de Mix.
- `core/audioplayer/AudioPlayerModels.kt`, `DeviceAudioPlayerQueue.kt`: contexto exclusivo de set y separación de recomendaciones infinitas.
- Ajustes, inyección de dependencias, AppShell y recursos de texto en inglés/español.

Las rutas de código anteriores se encuentran bajo `composeApp/src/<sourceSet>/kotlin/org/spotube/spotube/`.

## Validación

Las pruebas automatizadas cubren claves independientes, recreación de sesión, borrado selectivo, fallo de almacenamiento, validación de seis canciones, anuncios por bloque y aislamiento de playlists. La prueba nativa de credenciales usa exclusivamente claves ficticias en un espacio separado.

Validación del 12 de noviembre de 2026:

| Comprobación | Resultado |
| --- | --- |
| APK Nightly y APK de pruebas | Compilados e instalados en Moto g22 mediante ADB, sin desinstalar ni borrar datos |
| Pruebas JVM de DJ, credenciales y reproducción | 32 pruebas aprobadas; incluye una prueba con motores VLC reales y una de DPAPI en Windows |
| Android Keystore en Moto g22 | Cifrado, recreación del almacén, aislamiento de proveedores, borrado selectivo y detección de alteraciones aprobados |
| Sets reales del proveedor musical | Seis canciones distintas por set; tocar DJ de nuevo reemplaza el bloque |
| Voz y controles en Moto g22 | Bienvenida y despedida con tarjeta y ondas verificadas; siguiente, anterior y pausa sin presentaciones individuales |
| Mix con DJ habilitado | Playlist sin voz; estados desactivado, reproduciendo y pausado verificados |
| Persistencia de activación DJ | Conservada tras actualizar y reiniciar Nightly |
| Groq con clave real en el almacén nuevo | Selección validada y set de seis pistas generado antes y después de reiniciar Nightly; la interfaz mantuvo la clave enmascarada |
| OpenRouter con clave real en el almacén nuevo | Pendiente: el proveedor configurado en el dispositivo durante la validación fue Groq |
| Piper en Moto g22 (motor retirado) | Síntesis y reproducción aprobadas en `:piper` (PID separado); el proceso principal de Spotube permaneció activo durante 16,8 s |
| Género masculino en Moto g22 (con Piper, retirado) | Android TTS con género masculino seleccionado sintetizó y reprodujo mediante Piper en `:piper` (PID separado); Spotube siguió activo (17,4 s) |

APK: `composeApp/build/outputs/apk/nightly/debug/composeApp-nightly-debug.apk`.
SHA-256 del APK actual: `4b355f2d693a678df295281e9184aa1fb6e19f1b2794d06bef752b2e83fba527`.
Los registros y las capturas privadas de las pruebas permanecen fuera del contenido versionado.

La compilación y las pruebas de iOS/macOS requieren sus plataformas correspondientes; el código de esos almacenes no se puede validar desde Windows.
