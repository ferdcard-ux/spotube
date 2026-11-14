package org.spotube.spotube.features.dj

import kotlin.test.*
import org.spotube.spotube.core.audioplayer.QueueCollectionEntry

class DjSetLifecycleTest {
    @Test fun onlyTwoAnnouncementsAcrossSixSongsAndPreviousNext() {
        val lifecycle = DjSetLifecycle()
        val set = QueueCollectionEntry.DjSet("first")
        lifecycle.begin(set.id, "intro", "outro")
        assertTrue(lifecycle.introduction(set))
        repeat(DJ_SET_SIZE + 3) { assertFalse(lifecycle.introduction(set)) }
        assertTrue(lifecycle.ending(set))
        assertFalse(lifecycle.ending(set))
        assertFalse(lifecycle.introduction(set))
    }
    @Test fun playlistsAlbumsLikedSongsAndForeignSetsNeverSpeak() {
        val lifecycle = DjSetLifecycle()
        lifecycle.begin("owned", "intro", "outro")
        for (collection in listOf(null, QueueCollectionEntry.Playlist("p"), QueueCollectionEntry.Album("a"),
            QueueCollectionEntry.SavedTracks, QueueCollectionEntry.DjSet("foreign"))) {
            assertFalse(lifecycle.introduction(collection))
            assertFalse(lifecycle.ending(collection))
        }
    }
    @Test fun newSetWelcomesAgainAndInvalidatesPreviousSet() {
        val lifecycle = DjSetLifecycle()
        val first = QueueCollectionEntry.DjSet("one")
        val second = QueueCollectionEntry.DjSet("two")
        lifecycle.begin(first.id, "intro", "outro")
        assertTrue(lifecycle.introduction(first))
        lifecycle.begin(second.id, "intro", "outro")
        assertFalse(lifecycle.ending(first))
        assertTrue(lifecycle.introduction(second))
        lifecycle.clear()
        assertFalse(lifecycle.ending(second))
    }
}
