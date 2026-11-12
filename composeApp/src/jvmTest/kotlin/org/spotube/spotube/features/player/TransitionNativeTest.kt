package org.spotube.spotube.features.player

import co.touchlab.kermit.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.spotube.spotube.core.audioplayer.*
import org.spotube.spotube.modules.settings.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class TransitionNativeTest {
    @Test fun vlcPreparesAndPromotesTheSecondDeck() = runBlocking {
        val directory = Files.createTempDirectory("spotube-transition-test").toFile()
        val wav = File(directory, "silence.wav")
        // PCM generado localmente: la prueba no requiere canciones privadas ni acceso a la red.
        val dataBytes = 44_100 * 2 * 3
        val buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + dataBytes).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(44_100).putInt(88_200)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(dataBytes)
        wav.writeBytes(buffer.array())
        val nativeDir = File("build/vlc-natives/windows-x64")
        val previousResources = System.getProperty("compose.application.resources.dir")
        if (nativeDir.isDirectory) System.setProperty("compose.application.resources.dir", nativeDir.absolutePath)
        stopKoin()
        startKoin { modules(module { factory { (tag: String?) -> Logger.withTag(tag ?: "test") } }) }
        val settings = object : UserSettingsSource {
            override val userSettings = MutableStateFlow(UserSettings(crossfadeSeconds = 1))
        }
        var player: TransitionAudioPlayer? = null
        try {
            val controller = TransitionAudioPlayer(AudioPlayer(Unit), { AudioPlayer(Unit) }, settings)
            player = controller
            val first = MediaItem("First", "Test", "", 3.seconds, "", wav.absolutePath)
            val second = first.copy(title = "Second")
            controller.setVolume(0f)
            controller.load(listOf(first, second), true, 0)
            withTimeout(15_000) {
                controller.currentMediaItemFlow.first { it?.title == "Second" }
                controller.playerStateFlow.first { it == PlayerState.PLAYING }
            }
            assertEquals("Second", controller.currentMediaItemFlow.value?.title)
            assertEquals(0f, controller.volumeFlow.value)
        } finally {
            player?.dispose()
            stopKoin()
            if (previousResources == null) System.clearProperty("compose.application.resources.dir")
            else System.setProperty("compose.application.resources.dir", previousResources)
            wav.delete()
            directory.delete()
        }
    }
}
