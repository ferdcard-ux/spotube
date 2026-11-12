package org.spotube.spotube.features.dj

import org.spotube.spotube.core.audioplayer.QueueCollectionEntry

const val DJ_SET_SIZE = 6

/** La voz pertenece al bloque, nunca a las pistas individuales ni a una playlist. */
class DjSetLifecycle {
    private var id: String? = null
    private var introduced = false
    private var ended = false
    var intro: String? = null
        private set
    var outro: String? = null
        private set
    fun begin(setId: String, intro: String, outro: String) { id = setId; introduced = false; ended = false; this.intro = intro; this.outro = outro }
    fun owns(collection: QueueCollectionEntry?) = collection is QueueCollectionEntry.DjSet && collection.id == id
    fun introduction(collection: QueueCollectionEntry?): Boolean {
        if (!owns(collection) || introduced || ended) return false
        introduced = true
        return true
    }
    fun ending(collection: QueueCollectionEntry?): Boolean {
        if (!owns(collection) || !introduced || ended) return false
        ended = true
        return true
    }
    fun clear() { id = null; introduced = false; ended = false; intro = null; outro = null }
}
