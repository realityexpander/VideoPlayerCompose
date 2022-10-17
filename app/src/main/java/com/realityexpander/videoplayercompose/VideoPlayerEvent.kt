package com.realityexpander.videoplayercompose

sealed class VideoPlayerEvent {
    object Idle : VideoPlayerEvent()
    object onLoadVideoExternalFiles : VideoPlayerEvent()
}
