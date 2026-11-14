# 1. MIX.

**Spotify Mix** funciona mediante una combinación de análisis de señal de audio y algoritmos de mezcla. A nivel técnico, para implementarlo en una app se necesitan estos componentes:

**Análisis de pista (por track)**
- **Detección de BPM y beat grid**: se detecta el tempo y se marca la cuadrícula de beats (downbeats en verde). Esto es esencial para alinear transiciones.
- **Detección de tono (Camelot Key)**: se asigna una clave Camelot (1–12A/B) para evaluar compatibilidad armónica entre pistas.
- **Waveform**: se extrae la forma de onda para visualizar picos de energía y definir el punto óptimo de transición.

**Motor de transición**
- **Beat matching**: si dos pistas tienen BPM distintos (ej. 120 vs 128), se estira temporalmente una hasta igualar la otra durante la transición, y luego se devuelve a su BPM original tras ~16 compases.
- **Longitud de transición**: se define en compases (2, 4 u 8 bars); solo se permite cuando hay un beat grid claro.
- **Curvas de mezcla** (se aplican simultáneamente sobre las dos pistas):
  - **Volumen**: crossfade, overlap, fade in/out, cut, center cut.
  - **EQ 3-bandas**: fade independiente de graves/medios/agudos, o "bass swap" (cambio de graves en el centro o al final).
  - **Filtros**: low-pass / high-pass con sweep (barrido) sobre la transición.
- **Presets** que combinan las tres curvas: *Fade, Rise, Blend, Wave, Melt, Slam, Dissolve, Surge, Afterglow*.

**Modo Auto (algoritmo)**
- Analiza BPM, clave y estructura de ambas pistas para elegir automáticamente longitud, preset y curvas. La lógica exacta no está documentada públicamente, pero se basa en compatibilidad de tempo (idealmente ≤10 BPM de diferencia) y proximidad en la rueda Camelot (±1 número o cambio A↔B).

**Smart Reorder**
- Reordena la playlist por BPM y clave para minimizar el esfuerzo de transición.

**Arquitectura mínima para replicarlo en una app**

```
┌─────────────────────────────────────────────────┐
│  1. Preprocesamiento (offline / server)         │
│     • Beat detection (librerías: librosa, madmom)│
│     • Key detection (Chromaprint, Essentia)      │
│     • Waveform extraction                        │
├─────────────────────────────────────────────────┤
│  2. Motor de mezcla (client-side, DSP)          │
│     • Time-stretching (WSOLA / Phase Vocoder)   │
│     • Crossfade + EQ 3-band + Biquad filters    │
│     • Curvas paramétricas (volumen/EQ/filtro)   │
├─────────────────────────────────────────────────┤
│  3. UI de edición                               │
│     • Waveform dual con beat markers            │
│     • Drag para alinear downbeats               │
│     • Presets + sliders para curvas             │
└─────────────────────────────────────────────────┘
```

**Librerías útiles**: [FFmpeg](https://ffmpeg.org/) (time-stretch, filtros), [SoX](https://sox.sourceforge.net/), [librosa](https://librosa.org/) (Python, análisis), [Web Audio API](https://developer.mozilla.org/en-US/docs/Web/API/Web_Audio_API) (DSP en browser). El DSP de mezcla (crossfade + EQ + filtros biquad) es el núcleo; la detección de beat/key es lo que permite el modo Auto y la alineación visual.

# 2. CROSSFADER.

El **crossfader** es el componente más simple del motor de mezcla: mezcla dos señales en el tiempo aplicando curvas de ganancia opuestas.

## La matemática básica

Dado un parámetro de posición `t ∈ [0, 1]` (0 = 100% señal A, 1 = 100% señal B):

```
out[n] = gA(t) · A[n] + gB(t) · B[n]
```

El problema es **qué curvas** usar para `gA` y `gB`.

## Curvas disponibles

| Curva | `gA(t)` | `gB(t)` | Comportamiento |
|---|---|---|---|
| **Lineal** | `1 − t` | `t` | Dip de **−3 dB** en el centro (señales no correlacionadas) |
| **Equal-power** | `cos(πt/2)` | `sin(πt/2)` | Potencia constante: `gA² + gB² = 1`, **sin dip** |
| **Log / S-curve** | `sin²(πt/2)` | `cos²(πt/2)` | Percepción más "natural", dip menor |

La clave: para señales **no correlacionadas** (dos canciones distintas, el caso típico de Spotify Mix) se usa **equal-power**, porque la potencia combinada se mantiene en 1 en todo momento. En el punto medio ambas señales están a **0.707** (−3 dB) y la suma de potencias sigue siendo 1.

## Pseudocódigo de implementación

```python
import numpy as np

def crossfade(signal_a, signal_b, duration_samples):
    """
    signal_a: numpy array (última parte de la pista A)
    signal_b: numpy array (primera parte de la pista B)
    duration_samples: longitud del crossfade en samples
    """
    n = duration_samples
    t = np.linspace(0, 1, n)  # posición 0→1

    # Curvas equal-power
    gA = np.cos(t * np.pi / 2)
    gB = np.sin(t * np.pi / 2)

    # Mezcla
    out = gA * signal_a[:n] + gB * signal_b[:n]
    return out
```

## En el contexto de Spotify Mix

El crossfader en sí es trivial (dos multiplicaciones + una suma por sample). Lo que lo hace útil en una app tipo Spotify Mix es **dónde y cuándo** se aplica:

1. **Detección del punto de entrada**: se busca el primer downbeat de la pista B (usando el beat grid precalculado) para que el fade-in caiga en tiempo rítmico.
2. **Duración en compases**: el crossfade dura N compases (2, 4, 8), no un tiempo fijo en segundos.
3. **Time-stretch previo**: si el BPM difiere, se estira `signal_b` *antes* del crossfade para que los beats coincidan.
4. **Curvas combinadas**: el crossfader de volumen se aplica **simultáneamente** con las curvas de EQ 3-band y filtros biquad que definimos en la respuesta anterior.

En resumen: el crossfader es el "qué" (mezclar dos señales), pero el "cuándo" y el "cómo" (beat matching + curvas paramétricas) es lo que convierte un crossfade genérico en una transición musical coherente.

# 3. SIN PAUSAS.

## Reproducción sin pausas (Gapless Playback)

A diferencia del crossfader (que **mezcla** dos señales), la reproducción sin pausas garantiza que el **sample N de la pista A** sea inmediatamente seguido por el **sample 0 de la pista B**, sin silencio ni solapamiento. Es una transición **sample-accurate**.

### Los dos problemas a resolver

| Problema | Causa | Solución |
|---|---|---|
| **Silencio artificial** | Los codecs lossy (MP3, AAC) añaden *encoder delay* al inicio y *padding* al final por la transformada MDCT | Leer metadata del archivo y recortar esos samples |
| **Latencia de cambio de pista** | El player debe abrir, decodificar y bufferizar la siguiente pista antes de que termine la actual | Pre-buffering en un segundo thread/decoder |

### 1. Compensación de encoder delay y padding

Cada formato lo resuelve con metadata embebida:

| Formato | Dónde se almacena | Ejemplo |
|---|---|---|
| **MP3 (LAME)** | LAME tag (extensión del Xing header, en la primera frame) | `enc_delay = 576`, `enc_padding = 1152` |
| **AAC / M4A** | Tag `iTunSMPB` (36 bytes) | `encoder_delay`, `encoder_padding` |
| **Ogg Vorbis** | `granulepos` en cada página Ogg (64-bit, sample absoluto) | El decoder compara samples decodificados vs. granulepos |
| **FLAC** | Nativo — no hay padding, las pistas son contiguas en un solo stream | No requiere compensación |

La fórmula de longitud real:

```
true_length = raw_length − encoder_delay − padding
```

### 2. Pre-buffering (el núcleo del mecanismo)

```
┌──────────────────────────────────────────────────────────────┐
│  Thread de reproducción (render loop)                         │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  Ring buffer → Audio Output (hardware)                  │  │
│  │  Nunca se detiene entre pistas                          │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  Thread de decodificación (prefetch)                         │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  1. Abrir pista N+1                                    │  │
│  │  2. Leer metadata gapless (delay, padding)             │  │
│  │  3. Decodificar y recortar primer tramo                │  │
│  │  4. Escribir en ring buffer DESPUÉS del último sample  │  │
│  │     de la pista N (splice exacto)                      │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

El punto crítico: el **handoff ocurre entre samples dentro del ring buffer**, no a nivel de stream. El output audio nunca se cierra ni se reinicia.

### 3. Pseudocódigo del splice

```python
# En el producer del ring buffer:
def produce_samples():
    if current_track_remaining() <= PREFETCH_THRESHOLD:
        # Pre-decode next track
        next_pcm = decode_next_track()
        next_pcm = trim_padding(next_pcm)  # remove encoder_delay + padding

    if current_track_exhausted():
        # Splice: append next track's first samples
        # directly after the last sample of current track
        ring_buffer.append(next_pcm[:CHUNK])
        current_track = next_track
```

### 4. Diferencia clave vs. crossfader

| | Crossfader | Gapless |
|---|---|---|
| **Objetivo** | Mezcla artística (DJ) | Fidelidad al original |
| **Solapamiento** | Sí (2–8 compases) | **No** — 0 samples de overlap |
| **Curvas** | Equal-power, log, etc. | Ninguna — corte exacto |
| **Uso típico** | Spotify Mix, DJ apps | Álbums de rock progresivo, clásica, suites |

### 5. En el contexto de tu app (Spotify Mix)

En una app como Spotify Mix, ambos mecanismos **coexisten**:

- **Modo Gapless** (reproducción estándar): splice exacto, sin crossfade.
- **Modo Mix / Crossfade**: el crossfader se aplica *antes* del splice, y el padding se recorta igualmente para que el crossfade empiece en el punto musical correcto (no en silencio artificial).

```
Pista A (recortada)  ──┐
                       ├──→ Crossfade (N compases) ──→ Pista B (recortada)
Pista B (recortada)  ──┘
```

La compensación de encoder delay es **obligatoria en ambos modos**, porque de lo contrario el crossfade empezaría ~13 ms tarde (con LAME) y sonaría como un "bump" audible.


# 4. AI DJ

## DJ Livi — AI DJ con proveedores gratuitos

### Arquitectura: Agente con Tool Calling

El patrón que usan los proyectos de referencia (DJMate, melodi-ai, dj-agent) es el mismo: **el LLM no genera música ni elige canciones directamente** — decide *qué buscar* y *cómo ordenar*, y delega la ejecución en herramientas (tools) que tu app ya tiene.

```
┌─────────────────────────────────────────────────────────────────┐
│  DJ Livi (LLM Agent)                                            │
│                                                                 │
│  "Quiero una sesión de 45 min, empezar suave y subir energía"   │
│       │                                                         │
│       ▼                                                         │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  LLM (OpenRouter / Gemini / Groq)                        │   │
│  │  Decide: intención, filtros, orden, transiciones         │   │
│  │  → Emite tool calls en JSON                              │   │
│  └──────────────────────────────────────────────────────────┘   │
│       │                                                         │
│       ▼  Tool Calls                                             │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  search_tracks(genre, bpm_range, key, energy, mood)      │   │
│  │  get_track_metadata(track_id)                            │   │
│  │  build_set(ordered_ids, transition_presets)              │   │
│  │  get_crossfade_params(track_a, track_b)                  │   │
│  │  announce(text)  ← TTS para intro de pista               │   │
│  └──────────────────────────────────────────────────────────┘   │
│       │                                                         │
│       ▼  Resultados                                             │
│  LLM escribe la "voz" del DJ + devuelve el set planificado     │
└─────────────────────────────────────────────────────────────────┘
```

### Configuración multi-proveedor con API Keys

```python
# config.yaml
dj_livi:
  providers:
    - name: openrouter
      base_url: "https://openrouter.ai/api/v1"
      api_key: "${OPENROUTER_API_KEY}"
      model: "nvidia/nemotron-3-ultra-550b-a55b:free"
      priority: 1
    - name: gemini
      base_url: "https://generativelanguage.googleapis.com/v1beta"
      api_key: "${GEMINI_API_KEY}"
      model: "gemini-3.5-flash-lite"
      priority: 2
    - name: groq
      base_url: "https://api.groq.com/openai/v1"
      api_key: "${GROQ_API_KEY}"
      model: "llama-3.3-70b-versatile"
      priority: 3

  fallback: "keyword_parser"   # si todos fallan
  max_tool_rounds: 10
```

```python
# llm_client.py — multiplexor con failover
class LLMClient:
    def __init__(self, providers: list[ProviderConfig]):
        self.providers = sorted(providers, key=lambda p: p.priority)

    async def chat(self, messages, tools=None) -> str:
        for provider in self.providers:
            try:
                return await self._call(provider, messages, tools)
            except (RateLimitError, APIError, TimeoutError):
                continue
        # Fallback: parser de keywords sin LLM
        return self._keyword_fallback(messages)
```

### Límites gratuitos (Nov 2026)

| Proveedor | Modelo free | RPM | RPD | Notas |
|---|---|---|---|---|
| **OpenRouter** | `nemotron-3-ultra:free`, `gemma-4-31b:free`, `openrouter/free` | 20 | 50 (1 000 con $10 crédito) | Una sola key para todos los modelos |
| **Google Gemini** | `gemini-3.5-flash-lite`, `gemini-3.8-flash` | 5–15 | 20–500 | Por proyecto, reset a medianoche PT |
| **Groq** | `llama-3.3-70b-versatile` | 30 | 14 400 | El más generoso para prototipo |

> **Nota**: Lyria 3 (generación de música) **no es gratuita** en ninguno de los tres. DJ Livi no genera audio — solo planifica y explica.

### Tool schemas (lo que el LLM puede invocar)

```python
TOOLS = [
    {
        "name": "search_tracks",
        "description": "Buscar pistas en la biblioteca local",
        "parameters": {
            "genre": "string (opcional)",
            "bpm_min": "int", "bpm_max": "int",
            "key": "string (Camelot, ej '8A')",
            "energy": "float 0-1",
            "mood": "string",
            "limit": "int"
        }
    },
    {
        "name": "build_set",
        "description": "Ordenar pistas y asignar transiciones",
        "parameters": {
            "track_ids": "array[int]",
            "transition_duration_bars": "int (2|4|8)",
            "preset": "string (fade|rise|blend|wave|melt|slam|dissolve|surge|afterglow)"
        }
    },
    {
        "name": "get_crossfade_params",
        "description": "Calcular parámetros de crossfade entre dos pistas",
        "parameters": {
            "track_a": "int", "track_b": "int"
        }
    },
    {
        "name": "announce",
        "description": "Generar texto para anuncio TTS del DJ",
        "parameters": {
            "track_title": "string",
            "artist": "string",
            "context": "string (intro, transition, outro)"
        }
    }
]
```

### System prompt (núcleo)

```text
Eres Livi, un DJ de música electrónica. Tu biblioteca tiene {n} pistas
con metadata: título, artista, BPM, clave Camelot, energía (0-1), mood,
duración.

Reglas:
1. NUNCA inventes títulos o artistas. Solo usa IDs que devuelva search_tracks.
2. Al planificar un set, verifica compatibilidad:
   - ΔBPM ≤ 10 entre pistas consecutivas
   - Clave Camelot: misma, ±1, o cambio A↔B
   - Energía: curva ascendente o descendente coherente
3. Usa build_set con el preset de transición más adecuado al cambio de energía.
4. Para cada transición, genera un announce breve (máx 15 palabras).
5. Si la búsqueda devuelve < 3 resultados, relaja filtros y reintenta (máx 2).

Formato de respuesta: JSON con "set" (array de pistas ordenadas),
"transitions" (array de presets), "announcements" (array de textos).
```

### Integración con el motor de mezcla

```python
# dj_livi.py — orquestación completa
class DJLivi:
    def __init__(self, library, crossfader, llm_client):
        self.library = library        # índice local (BPM, key, waveform)
        self.crossfader = crossfader  # motor DSP de la respuesta anterior
        self.llm = llm_client

    async def plan_set(self, user_request: str) -> SetPlan:
        # 1. LLM decide filtros y orden
        response = await self.llm.chat(
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": user_request}
            ],
            tools=TOOLS
        )

        # 2. Parsear tool calls
        plan = parse_tool_calls(response)

        # 3. Validar: BPM diff, Camelot, duración total
        self._validate(plan)

        # 4. Calcular crossfades concretos (samples, curvas)
        for i in range(len(plan.tracks) - 1):
            plan.transitions[i] = self.crossfader.compute(
                track_a=plan.tracks[i],
                track_b=plan.tracks[i+1],
                preset=plan.transitions[i].preset,
                bars=plan.transitions[i].bars
            )

        return plan
```

### TTS para la "voz" del DJ

Para los `announce` (intros, transiciones), las opciones gratuitas:

| Opción | Modelo | Latencia | Coste |
|---|---|---|---|
| **Piper TTS** (local, CPU) | vits model ~60 MB | ~200 ms | $0 |
| **Gemini TTS** (API) | `gemini-2.5-flash-preview-tts` | ~1 s | $0 (free tier) |
| **Edge TTS** (Microsoft) | `edge-tts` (Python) | ~500 ms | $0 (no requiere key) |

El flujo: LLM genera texto → TTS lo convierte a WAV → se splicea en el ring buffer **antes** del crossfade (gapless, sin solapamiento).

### Resumen de la pila completa

```
┌─────────────────────────────────────────────────────────────┐
│  DJ Livi (agente LLM)                                       │
│  Proveedor: OpenRouter / Gemini / Groq (failover)           │
│  → Decide set, transiciones, announces                      │
├─────────────────────────────────────────────────────────────┤
│  Biblioteca local (Postgres + pgvector / SQLite FTS)        │
│  → search_tracks, get_metadata                              │
├─────────────────────────────────────────────────────────────┤
│  Motor DSP (de respuestas anteriores)                       │
│  → Crossfader, beat matching, EQ, filtros                   │
├─────────────────────────────────────────────────────────────┤
│  TTS (Piper / Edge / Gemini)                                │
│  → Anuncios de pista entre transiciones                     │
└─────────────────────────────────────────────────────────────┘
```

La clave de diseño: **el LLM es el cerebro, no los músculos**. Nunca toca samples ni calcula curvas — solo emite decisiones estructuradas (IDs, presets, texto) que tu motor DSP ya sabe ejecutar. Esto mantiene la latencia de audio a 0 ms (el DSP es determinista) y aísla la variabilidad del LLM (rate limits, timeouts) en un plano asíncrono que no bloquea la reproducción.
