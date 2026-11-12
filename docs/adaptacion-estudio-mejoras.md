# Adaptación del estudio al motor nativo de Spotube

Se revisó `ESTUDIO_MEJORAS.md` contra la implementación KMP y las fuentes oficiales el 12 de noviembre de 2026. El documento de estudio se conserva sin modificaciones. Esta entrega implementa una primera adaptación del motor nativo y un selector LLM limitado a candidatos reales; no implementa todavía el sistema DSP completo ni el agente de herramientas descritos allí.

## Implementado en este incremento

| Función | Comportamiento |
|---|---|
| Curva de crossfade | Ajustes → Reproducción permite potencia constante, lineal o suave. La curva elegida se fija al comenzar cada mezcla. Potencia constante sigue siendo el valor predeterminado. |
| Duración musical | Automix permite 2, 4 u 8 compases bajo la convención explícita 4/4. Solo utiliza esa duración con BPM compatibles y marcadores de beat válidos en ambas pistas. Sin marcadores conserva ocho segundos; sin BPM compatibles conserva el crossfade normal. |
| Retorno de tempo | Tras promocionar el motor entrante, recupera su velocidad original progresivamente durante 16 compases de 4/4, medidos sobre el reloj de esa pista. Una pausa congela el proceso. Buscar, entrar en Jam o fijar una velocidad manual cancela la recuperación. |
| Relojes del fundido | Convierte los avances de ambos archivos según sus velocidades para calcular el progreso de mezcla. |
| Nombre del DJ | Campo editable en Ajustes → Reproducción, hasta 24 caracteres. El panel, la descripción accesible del botón y el estado de voz muestran el nombre configurado. Se conserva Livi por compatibilidad hasta que el usuario elija otro nombre. |
| Acceso DJ | Se retira el acceso de Home y se coloca un botón circular con ondas y las letras DJ entre Biblioteca y Ajustes. Abre un panel sobre la pantalla actual, con presentaciones, estado y configuración. |
| Controles | Botones de transporte circulares y sin texto, con sus descripciones accesibles; aro metálico y acento luminoso para reproducir. Mix utiliza una cápsula cromada, siguiendo la referencia enviada por el usuario. |
| Mix en playlists | Visible en todas las pantallas de playlist, incluidas canciones guardadas. Guarda una preferencia independiente por lista y funciona como excepción al ajuste global de Automix, sin reordenar ni modificar la playlist del proveedor. Se suspende durante Jam. |
| IA opcional | OpenRouter, Gemini o Groq con clave y modelo elegidos por el usuario. Desactivada por defecto; si falla conserva la recomendación local y el TTS del sistema. |

No se agregaron bibliotecas. La voz sigue utilizando el TTS del sistema. Los nuevos ajustes tienen valores predeterminados compatibles con los archivos de preferencias anteriores. Solo se contacta con un servicio de IA si el usuario lo activa y proporciona su clave y modelo.

La configuración de IA se encuentra exclusivamente en Ajustes → Reproducción. Las claves se conservan por proveedor en el almacén seguro nativo del dispositivo; las preferencias públicas se guardan en DataStore. Cambiar de proveedor conserva su clave y modelo. OpenRouter solo admite `openrouter/free` o identificadores terminados en `:free`; Gemini y Groq requieren introducir un modelo vigente y respetan las cuotas de la cuenta del usuario. El botón inferior DJ inicia un set nuevo; no abre configuración. Véanse los [ajustes actuales de DJ y Mix](dj-mix-ajustes.md).

La petición comparte hasta 20 canciones candidatas ya filtradas por las listas de bloqueo y el texto escrito por el usuario. No comparte credenciales musicales ni el historial completo. La respuesta debe seleccionar seis IDs existentes y distintos. Se rechazan planes inválidos y se usa la alternativa local después de errores, límites o treinta segundos de espera. Si no hay seis pistas válidas, no se carga un set incompleto. Cambiar la configuración durante una petición invalida su respuesta. No se ejecuta código devuelto por el modelo. La voz conserva plantillas verificables y solo abre y cierra cada bloque; nunca presenta las pistas de Mix. Ajustes distingue errores de autenticación, permisos, cuotas, proveedor, red, espera y respuesta. Tocar DJ vuelve a solicitar un set sin borrar las claves.

La referencia de beat disponible identifica fase de pulsos, no una detección automática de downbeats ni de métrica. Elegir 4/4 no detecta la métrica de la canción. La duración se limita a la mitad de la pista saliente y el inicio se aproxima al siguiente beat; pistas cortas o preparación tardía pueden reducir la mezcla efectiva.

## Precisiones del estudio

- La fórmula suave del estudio intercambia las direcciones de entrada y salida. Aquí A empieza en 1 y termina en 0; B empieza en 0 y termina en 1, para las tres curvas. La curva suave utiliza `A = cos²(πx/2)` y `B = sin²(πx/2)`. Conserva suma de ganancias, no potencia constante.
- La potencia constante mantiene la suma de cuadrados de las ganancias. Eso no equivale a normalizar sonoridad, evitar saturación en cualquier señal o garantizar ausencia de un cambio perceptible.
- Dos salidas nativas precargadas no equivalen a un único búfer PCM con continuidad exacta entre muestras. El coordinador actual no promete un empalme exacto ni elimina silencios grabados intencionadamente.
- La compensación de retardo del codificador depende del formato, metadatos y decodificador. No se debe recortar una cantidad fija en milisegundos a todos los archivos ni asumir que todo FLAC corresponde a un álbum continuo.
- La separación asíncrona del LLM evita que su petición tenga que bloquear el audio; por sí sola no hace que la latencia del audio sea cero.

## Trabajo que requiere otra implementación

1. **Gapless con un solo motor/salida PCM:** evaluar primero la cola nativa de Media3 para el modo sin mezcla y mantener el estado público de Spotube al pasar de medio. La API de playlists de ExoPlayer ofrece transiciones nativas; hay que verificar también el proxy de streaming y los metadatos de delay/padding. Para una garantía a nivel de muestra hará falta medir PCM de salida con fixtures conocidos. [Playlists de Media3](https://developer.android.com/media/media3/exoplayer/playlists), [AudioSink](https://developer.android.com/reference/androidx/media3/exoplayer/audio/AudioSink).
2. **Análisis musical:** obtener PCM mediante APIs nativas, procesar fuera del hilo de audio y almacenar BPM, confianza, pulsos, downbeats, clave y waveform por versión del archivo. No dibujar ondas decorativas ni inventar claves cuando no haya análisis. Después se podrá implementar reordenación armónica y edición visual con datos reales.
3. **EQ, filtros y presets:** introducir procesamiento por motor antes de la suma, definir automatizaciones y validar consumo y saturación. No ofrecer nombres de presets de Spotify con un crossfade idéntico detrás. Spotify documenta estilos, BPM, clave y ajustes de volumen/EQ, pero eso no publica su algoritmo DSP completo. [Presentación oficial de Mix](https://newsroom.spotify.com/2025-08-19/mix-your-favorite-playlists-seamlessly-by-adding-your-own-transitions/).
4. **Agente DJ con herramientas:** ampliar el selector acotado a búsquedas y planes de sesión mediante un catálogo explícito de herramientas, presupuestos de rondas y validación de cada llamada. El selector de esta entrega no hace búsquedas arbitrarias ni promete sesiones de duración exacta. La persistencia de claves, si se necesita, deberá utilizar un almacén seguro nativo por plataforma; esta entrega evita guardarlas.

Los nombres de modelos y cuotas del estudio no se incorporan como constantes: disponibilidad, precio y límites dependen del proveedor, modelo y cuenta. Consultar [OpenRouter](https://openrouter.ai/collections/free-models), [cuotas de Gemini](https://ai.google.dev/gemini-api/docs/rate-limits) y [cuotas de Groq](https://console.groq.com/docs/rate-limits) al configurar la integración. Tampoco se reemplaza el TTS nativo por herramientas Python del estudio, que no son una integración KMP nativa.

## Cómo comprobarlo

1. En Ajustes → Reproducción elegir cada curva y una duración de fundido mayor que cero. Reproducir dos pistas consecutivas y comprobar los controles durante la mezcla. La opción cero sigue desactivando el crossfade normal.
2. Activar Automix y elegir 2, 4 u 8 compases. Con datos de prueba a 120 BPM y marcadores de beat en ambas pistas, las ventanas objetivo son 4, 8 y 16 segundos, respectivamente, antes de límites de pista y alineación. Con datos ausentes no debe inventarse una rejilla.
3. Con BPM cercanos, comprobar que la pista entrante mantiene el tempo ajustado al terminar la mezcla y vuelve gradualmente a su tempo original. Pausar/reanudar y fijar una velocidad manual debe conservar el control del usuario.
4. Cambiar el nombre del DJ y abrir su panel desde la barra inferior para comprobar título, descripción accesible y estado de presentación. El emblema mantiene las letras DJ. El nombre no modifica el historial ni la selección musical.
5. Abrir playlists distintas y canciones guardadas. Activar Mix en una y comprobar que la otra conserva su elección. La preferencia corresponde al reproductor de este dispositivo; no se envía al protocolo de control remoto.
6. Abrir DJ desde la barra inferior y comprobar sus ondas, el panel y el estado de voz. El acceso de activación ya no está en Home.
7. Activar IA sin clave debe conservar la alternativa local. Para una prueba real, introducir la clave directamente en el teléfono, seleccionar proveedor/modelo vigente y activar DJ para generar un nuevo bloque. Una clave se borra al cambiar de proveedor; «Borrar clave y desactivar IA» restablece la configuración local. No enviar claves por chat ni guardarlas en archivos del repositorio.

## Archivos principales

- `features/player/TransitionPlan.kt` y `TransitionAudioPlayer.kt`: curvas, temporización y tempo.
- `features/player/PlaylistMixViewModel.kt` y `PlaylistMixButton.kt`: preferencia de mezcla por playlist.
- `features/player/ChromePlaybackControls.kt`: controles circulares y cápsula Mix.
- `features/dj/DjEmblem.kt` y `DjNavigationControl.kt`: emblema y panel desde la navegación inferior.
- `features/dj/DjAiPlanner.kt`, `DjAiSession.kt` y `DjAiConfiguration.kt`: selección por IA, configuración persistente por proveedor y formulario de clave enmascarada; los almacenes nativos conservan las claves cifradas.
- `features/dj/DjCard.kt` y `DjService.kt`: presentación, nombre y coordinación con el selector opcional.
- `modules/settings/`, `modules/shell/`, `core/ui/component/Collection*`, pantallas de playlist/guardadas y `DeviceAudioPlayerQueue.kt`: integración con Ajustes, navegación, controles y cola existente.
- Recursos EN/ES en `composeResources/`: textos y accesibilidad.
- `commonTest/.../TransitionAudioPlayerTest.kt` y `DjAiPlannerTest.kt`: regresiones del motor y validación de respuestas externas.

## Verificación realizada

Se compilaron Stable y Nightly en Windows con Gradle wrapper. Las 43 pruebas enfocadas aprobaron: 27 de cola, 10 de transiciones, una de transición nativa VLC, tres de respuestas/configuración de IA, una de recursos fuera de Compose y una del gesto de expansión del reproductor. No se repitió la suite completa, que conserva los fallos previos documentados en el informe de integración.

En el Moto g22 se actualizó Nightly mediante ADB conservando la cuenta y la cola. Se comprobó la ubicación del DJ entre Biblioteca y Ajustes, la apertura del panel, el formulario de los tres proveedores sin introducir claves, Mix desactivado en una playlist mientras otra mantenía su selección y la conservación de ese estado al volver a la primera. El nombre temporal Nova apareció en el panel y el botón después de guardarlo desde Ajustes. Se restauró después el nombre anterior.

La revisión visual motivó un ajuste del tamaño del emblema y del contraste de los iconos sobre el fondo metálico. Las capturas y XML de estas comprobaciones quedan en el directorio local ignorado `build/device-tests/moto-g22/`, sin incorporarse al repositorio.

El APK final de Nightly está en `composeApp/build/outputs/apk/nightly/debug/composeApp-nightly-debug.apk`, SHA-256 `1CD89D4EBD548C929C66E50D5F8F5B9FA6C996FCC6AE2B1CF0BB6FBF878FDBB9`, y se instaló mediante actualización conservando los datos. También se generó `composeApp/build/outputs/apk/stable/debug/composeApp-stable-debug.apk`; Stable conserva su instalación original y su firma. Al finalizar las pruebas se mantienen los valores que el usuario tenía durante la revisión: gapless activo, crossfade de ocho segundos, Automix activado, DJ desactivado y nombre Livi. El usuario puede cambiar el nombre desde Ajustes.

No se probaron llamadas reales a OpenRouter/Gemini/Groq porque no se proporcionaron claves; las pruebas usan respuestas sintéticas para verificar que no se aceptan IDs inventados, repetidos, incompletos ni respuestas malformadas. El audio en hardware no se certificó de nuevo a nivel de muestra ni se hizo análisis auditivo de beats. iOS requiere validación en macOS. El estudio completo de PCM, análisis musical y DSP sigue pendiente en los puntos indicados arriba.

La comprobación posterior con la clave de OpenRouter introducida por el usuario y la ampliación del diagnóstico se registran en [Validación de OpenRouter](openrouter-validacion-2026-11-12.md).
