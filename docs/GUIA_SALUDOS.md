## Especificación técnica: Saludos tipo "DJ Personal"

### 1. Tipos de intervención

| Tipo | Duración | Extensión (palabras) | Momento |
|---|---|---|---|
| **Introducción completa** | ~15 s | 30–40 | Inicio de sesión, cambio de género/estado de ánimo |
| **Reconocimiento breve** | ~3–5 s | 10–15 | Confirmación de petición, cierre de sesión |

### 2. Estructura de la introducción completa

```
[saludo informal] + [presentación del DJ (nombre, "personalidad")] + [justificación de la selección musical] + [transición a audio]
```

Ejemplo de plantilla:

```
"{saludo_casual}. {presentación_dj}. {razón_contextual}. {cierre_breve}"
```

- `saludo_casual`: "Hola, hola, ¿qué tal?" / "¡Qué onda!" / "Hey, ¿cómo vas?"
- `presentación_dj`: "Soy (NOMBRE DJ), tu DJ personal" / "Aquí tu DJ de hoy"
- `razón_contextual`: "Veo que te gusta el reggaetón, así que arrancamos con eso" / "Como es viernes por la tarde, te pongo algo relajado"
- `cierre_breve`: "Aquí va" / "Dale, a escuchar"

### 3. Variables de contexto a inyectar

| Variable | Fuente | Ejemplo de uso |
|---|---|---|
| `hora_del_día` | sistema | "Buenos días" / "Buenas tardes" / "Buenas noches" |
| `día_semana` | sistema | "Es viernes, toca fiesta" |
| `gustos_históricos` | perfil del usuario | "Veo que siempre pones indie" |
| `estado_de_ánimo` | input del usuario | "Para romantizar el trabajo" |
| `género_solicitado` | input del usuario | "Te pongo reggaetón" |

### 4. Tono y estilo

- **Registro:** informal, cercano, conversacional (tú, no usted).
- **Voz:** primera persona singular ("yo soy tu DJ").
- **Longitud máxima:** 40 palabras (hard cap) para no interrumpir la escucha.
- **Prohibido:** formalidades, listas, datos técnicos de audio, más de 2 oraciones sin pausa.
- **Idioma:** el mismo del usuario (detectar en primera interacción).

### 5. Reglas de flujo

```
on_session_start:
    → generar introducción completa (30-40 palabras)
    → reproducir audio

on_genre_change:
    → generar introducción completa (30-40 palabras)
    → reproducir audio

on_user_request:
    → generar reconocimiento breve (10-15 palabras)
    → reproducir audio

on_session_end:
    → generar reconocimiento breve (10-15 palabras)
    → detener audio
```

### 6. Opción de "menos comentarios" (feature flag)

Si el usuario activa `verbose: false`:

```
on_session_start:
    → "¡Va, aquí tienes {género}!" (10-15 palabras)
    → reproducir audio

on_genre_change:
    → "Perfecto, {género}." (5-10 palabras)
    → reproducir audio

on_user_request:
    → (silencio, solo audio)

on_session_end:
    → "Hasta luego." (2-3 palabras)
```

### 7. Restricciones de implementación

- **TTS:** sintetizar a ~130–150 palabras/min para que 30–40 palabras ≈ 15 s.
- **Latencia:** generar texto < 500 ms antes de iniciar TTS.
- **Fallback:** si el LLM falla, usar una plantilla estática pre-escrita por género/hora.
- **No repetir** el mismo saludo en sesiones consecutivas (mantener un log de últimas 5 frases).

