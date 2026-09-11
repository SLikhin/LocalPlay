package com.localplay.app.data.scanner

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Wraps a [ContentObserver] registration for [MediaStore.Audio.Media.EXTERNAL_CONTENT_URI]
 * as a cold [Flow].
 *
 * Each time MediaStore signals a change (e.g. a new file is added or deleted),
 * the flow emits [Unit]. The caller is responsible for debouncing and
 * triggering a rescan via [com.localplay.app.data.repository.SongRepository.scan].
 *
 * ### Lifecycle
 * The observer is registered when the flow is collected and automatically
 * unregistered when collection is cancelled (e.g. the `ViewModel` is cleared).
 *
 * ### Usage in a ViewModel
 * ```kotlin
 * init {
 *     observeMediaStoreChanges(contentResolver)
 *         .debounce(500)
 *         .onEach { repository.scan() }
 *         .launchIn(viewModelScope)
 * }
 * ```
 */
fun observeMediaStoreChanges(contentResolver: ContentResolver): Flow<Unit> =
    callbackFlow {
        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        contentResolver.registerContentObserver(uri, /* notifyForDescendants = */ true, observer)
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.distinctUntilChanged()
