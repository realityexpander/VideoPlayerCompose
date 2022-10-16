package com.realityexpander.videoplayercompose

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    val player: Player,  // injected from VideoPlayerModule
    private val metaDataReader: MetaDataReader  // injected from VideoPlayerModule
): ViewModel() {

    private val videoUris = savedStateHandle.getStateFlow("videoUris", emptyList<Uri>())

    // Convert the Uri to VideoItem to get the name for the list in the UI
    val videoItems = videoUris.map { uris ->
        uris.map { uri ->
            VideoItem(
                contentUri = uri,
                mediaItem = MediaItem.fromUri(uri),
                name = metaDataReader.getMetaDataFromUri(uri)?.fileName
                    ?: if (MediaItem.fromUri(uri).mediaMetadata.displayTitle != null)
                        MediaItem.fromUri(uri).mediaMetadata.displayTitle.toString()
                    else
                        MediaItem.fromUri(uri).localConfiguration?.uri?.pathSegments?.last()
                            ?: "Unknown"
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000), // only run collector when there are subscribers
        emptyList()
    )

    init {
        player.prepare() // setup the player

        addVideoUri(Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"))
        addVideoUri(Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"))
        addVideoUri(Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"))
        addVideoUri(Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"))
        addVideoUri(Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4"))
        addVideoUri(Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4"))
        addVideoUri(Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4"))
        addVideoUri(Uri.parse("http://techslides.com/demos/sample-videos/small.mp4"))
    }

    fun addVideoUri(uri: Uri) {
        savedStateHandle["videoUris"] = videoUris.value + uri
        player.addMediaItem(MediaItem.fromUri(uri))
    }

    fun playVideo(uri: Uri) {
        player.setMediaItem(
            videoItems.value.find {
                it.contentUri == uri
            }?.mediaItem ?: return
        )
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}