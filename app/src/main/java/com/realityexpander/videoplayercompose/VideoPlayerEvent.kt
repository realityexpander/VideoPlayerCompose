package com.realityexpander.videoplayercompose

sealed class VideoPlayerEvent {
    object onLoadVideoExternalFiles : VideoPlayerEvent()
}
