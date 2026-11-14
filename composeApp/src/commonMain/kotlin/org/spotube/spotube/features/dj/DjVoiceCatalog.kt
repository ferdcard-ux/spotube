package org.spotube.spotube.features.dj

import org.spotube.spotube.modules.settings.DjVoiceGender

/** Estilo con el que Google describe cada voz prediseñada de Gemini. */
enum class GeminiVoiceStyle {
    BRIGHT, UPBEAT, INFORMATIVE, FIRM, EXCITABLE, YOUTHFUL, BREEZY, EASY_GOING, BREATHY, CLEAR, SMOOTH,
    GRAVELLY, SOFT, EVEN, MATURE, FORWARD, FRIENDLY, CASUAL, GENTLE, LIVELY, KNOWLEDGEABLE, WARM,
}

data class GeminiVoice(val name: String, val style: GeminiVoiceStyle, val gender: DjVoiceGender)

/** Las 30 voces prediseñadas de Gemini TTS (todas hablan español). */
val GEMINI_VOICES: List<GeminiVoice> = listOf(
    GeminiVoice("Puck", GeminiVoiceStyle.UPBEAT, DjVoiceGender.MALE),
    GeminiVoice("Fenrir", GeminiVoiceStyle.EXCITABLE, DjVoiceGender.MALE),
    GeminiVoice("Sadachbia", GeminiVoiceStyle.LIVELY, DjVoiceGender.MALE),
    GeminiVoice("Achird", GeminiVoiceStyle.FRIENDLY, DjVoiceGender.MALE),
    GeminiVoice("Zubenelgenubi", GeminiVoiceStyle.CASUAL, DjVoiceGender.MALE),
    GeminiVoice("Umbriel", GeminiVoiceStyle.EASY_GOING, DjVoiceGender.MALE),
    GeminiVoice("Algieba", GeminiVoiceStyle.SMOOTH, DjVoiceGender.MALE),
    GeminiVoice("Charon", GeminiVoiceStyle.INFORMATIVE, DjVoiceGender.MALE),
    GeminiVoice("Rasalgethi", GeminiVoiceStyle.INFORMATIVE, DjVoiceGender.MALE),
    GeminiVoice("Sadaltager", GeminiVoiceStyle.KNOWLEDGEABLE, DjVoiceGender.MALE),
    GeminiVoice("Iapetus", GeminiVoiceStyle.CLEAR, DjVoiceGender.MALE),
    GeminiVoice("Orus", GeminiVoiceStyle.FIRM, DjVoiceGender.MALE),
    GeminiVoice("Alnilam", GeminiVoiceStyle.FIRM, DjVoiceGender.MALE),
    GeminiVoice("Schedar", GeminiVoiceStyle.EVEN, DjVoiceGender.MALE),
    GeminiVoice("Enceladus", GeminiVoiceStyle.BREATHY, DjVoiceGender.MALE),
    GeminiVoice("Algenib", GeminiVoiceStyle.GRAVELLY, DjVoiceGender.MALE),
    GeminiVoice("Laomedeia", GeminiVoiceStyle.UPBEAT, DjVoiceGender.FEMALE),
    GeminiVoice("Zephyr", GeminiVoiceStyle.BRIGHT, DjVoiceGender.FEMALE),
    GeminiVoice("Autonoe", GeminiVoiceStyle.BRIGHT, DjVoiceGender.FEMALE),
    GeminiVoice("Leda", GeminiVoiceStyle.YOUTHFUL, DjVoiceGender.FEMALE),
    GeminiVoice("Aoede", GeminiVoiceStyle.BREEZY, DjVoiceGender.FEMALE),
    GeminiVoice("Callirrhoe", GeminiVoiceStyle.EASY_GOING, DjVoiceGender.FEMALE),
    GeminiVoice("Sulafat", GeminiVoiceStyle.WARM, DjVoiceGender.FEMALE),
    GeminiVoice("Despina", GeminiVoiceStyle.SMOOTH, DjVoiceGender.FEMALE),
    GeminiVoice("Erinome", GeminiVoiceStyle.CLEAR, DjVoiceGender.FEMALE),
    GeminiVoice("Pulcherrima", GeminiVoiceStyle.FORWARD, DjVoiceGender.FEMALE),
    GeminiVoice("Kore", GeminiVoiceStyle.FIRM, DjVoiceGender.FEMALE),
    GeminiVoice("Vindemiatrix", GeminiVoiceStyle.GENTLE, DjVoiceGender.FEMALE),
    GeminiVoice("Achernar", GeminiVoiceStyle.SOFT, DjVoiceGender.FEMALE),
    GeminiVoice("Gacrux", GeminiVoiceStyle.MATURE, DjVoiceGender.FEMALE),
)

/** Una voz instalada en el sistema (Android TTS, iOS). [id] es el identificador del motor. */
data class DjSystemVoice(val id: String, val languageTag: String, val needsNetwork: Boolean)
