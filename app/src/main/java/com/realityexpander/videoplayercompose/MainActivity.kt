package com.realityexpander.videoplayercompose

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.ui.PlayerView
import com.realityexpander.videoplayercompose.ui.theme.VideoPlayerComposeTheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.*
import java.util.*

// Saves captured video to app cache storage

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VideoPlayerComposeTheme {
                val viewModel = hiltViewModel<MainViewModel>()
                val videoItems by viewModel.videoItems.collectAsState()

                val context = LocalContext.current

                var videoUri by remember {
                    mutableStateOf<Uri?>(null)
                }

                // Open a video file picker
                val selectVideoLauncher =
                    rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent(),
                        onResult = { uri ->
                            uri?.let(viewModel::addVideoUriToPlayer)  // Add the video file to the player
                        }
                    )

                // Capture a video file, stored in app cache
                val captureVideoLauncher =
                    rememberLauncherForActivityResult(
                        contract = CaptureVideo(),
                        onResult = { success ->
                            if(success) {
                                videoUri?.let {
                                    viewModel.addVideoUriToPlayer(videoUri!!)
                                }
                            } else {
                                videoUri = null
                            }
                        },
                    )

                // Respond to lifecycle events
                LaunchedEffect(true) {
                    viewModel.events.collect { event ->
                        when (event) {
                            VideoPlayerEvent.onLoadVideoExternalFiles -> {
                                loadVideoExternalFiles(context) { videoFile ->
                                    viewModel.addVideoFileToPlayer(videoFile)
                                }
                            }
                            else -> {
                                println("Unhandled event: $event")
                            }
                        }
                    }
                }

                // Load the videos from the external files directory
                LaunchedEffect(true) {
                    // load video files from external storage for app
                    viewModel.loadVideoFilesFromAppExternalStorage()
                }

                // This `lifecycle` is used to pause/resume the video in the background
                var lifecycle by remember {
                    mutableStateOf(Lifecycle.Event.ON_CREATE)
                }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        lifecycle = event
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)

                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    AndroidView(
                        factory = { context ->
                            PlayerView(context).also {
                                it.player = viewModel.player
                            }
                        },
                        update = {
                            when (lifecycle) {
                                Lifecycle.Event.ON_PAUSE -> {
                                    it.onPause()
                                    it.player?.pause()
                                }
                                Lifecycle.Event.ON_RESUME -> {
                                    it.onResume()
                                }
                                else -> Unit
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16 / 9f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row {

                        IconButton(onClick = {
                            selectVideoLauncher.launch("video/mp4")
                        }) {
                            Icon(
                                imageVector = Icons.Default.FileOpen,
                                contentDescription = "Select video"
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))

                        IconButton(onClick = {
                            videoUri = ComposeFileProvider.getVideoUri(context)
                            captureVideoLauncher.launch(videoUri)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Capture video"
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))

                        Text(videoUri?.path
                            ?.split("/")
                            ?.last()
                            ?.truncateMiddle(18)
                            ?: "Ready to capture video"
                        )

                        if(videoUri != null) {

                            IconButton(onClick = {
                                videoUri?.let {
                                    moveTmpUriToMainStorage(videoUri!!, context) { newFile ->
                                        viewModel.removeVideoUriFromPlayer(videoUri!!)
                                        viewModel.addVideoFileToPlayer(newFile)
                                        videoUri = null
                                    }
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "Move video"
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(videoItems) { item ->
                            Text(
                                text = item.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .combinedClickable(
                                        onClick = {
                                            viewModel.playVideo(item.contentUri)
                                        },
                                        onLongClick = {
                                            viewModel.removeVideoUriFromPlayer(item.contentUri)
                                            removeVideoExternalFileByUri(
                                                context,
                                                item.contentUri
                                            )
                                        }
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

// Truncate string in the middle and add ellipsis
fun String.truncateMiddle(maxLength: Int, ellipses: Boolean = true): String {
    if (this.length <= maxLength) {
        return this
    }
    val middle = maxLength / 2
    return this.substring(0, middle) +
            (if (ellipses) "..." else "") +
            this.substring(this.length - middle)
}

// Allows the contentResolver to access the app cache storage
// From this tutorial: https://fvilarino.medium.com/using-activity-result-contracts-in-jetpack-compose-14b179fb87de
class ComposeFileProvider : FileProvider(
    //R.xml.filepaths
) {
    companion object {
        fun getVideoUri(context: Context): Uri {
            val directory = File(context.cacheDir, "videos")
            directory.mkdirs()

            val file = File(directory, "new_video.mp4")
            if(file.exists()) {
                file.delete()
            }

            // create a unique temporary file with unique name
            //val file = File.createTempFile(
            //    "video_",
            //    ".mp4",
            //    directory,
            //)
            val authority = context.packageName + ".fileprovider"

            return getUriForFile(
                context,
                authority,
                file,
            )
        }
    }
}

//fun loadAppVideoFilesFromCache(context: Context) {
//    val directory = File(context.cacheDir, "videos")
//    directory.mkdirs()
//
//    val files = directory.listFiles()
//    files?.forEach {
//        val uri = ComposeFileProvider.getVideoUri(context)
//        val file = File(uri.path!!)
//        file.copyTo(it, true)
//    }
//}

// Load from external storage - storage/self/primary/Movies/VideoPlayerCompose
fun loadVideoExternalFiles(context: Context, onFileLoaded: (File) -> Unit) {
    val appName = context.getString(R.string.app_name)
    val directoryName = "/$appName"
    val directory = File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MOVIES
        ).toString() + directoryName)
    directory.mkdirs()

    val files = directory.listFiles()
    files?.forEach {
        onFileLoaded(it)
    }
}

fun removeVideoExternalFileByUri(context: Context, deleteUri: Uri?) {
    if(deleteUri == null) return

    // Delete the external file
    try {
        val file = File(deleteUri.path!!)
        file.delete()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// move Uri file from cache to app external files - sdcard/Android/data/com.realityexpander.videoplayercompose/files/Movies
// Note: not selectable by the user.
fun moveTmpFileToAppMovies(fromUri: Uri, context: Context) {
    val inputStream = context.contentResolver.openInputStream(fromUri)!!
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
        fromUri.lastPathSegment ?:  "video.mp4")
    val outputStream = FileOutputStream(file)

    inputStream.copyTo(outputStream)
    inputStream.close()
    outputStream.close()

    context.contentResolver.delete(fromUri, null, null)
}

// Move cached URI file from cache to main storage - storage/self/primary/Movies/VideoPlayerCompose
// Note: Selectable by the user from file picker
fun moveTmpUriToMainStorage(fromUri: Uri, context: Context, onSuccessCallback: (movedFile: File) -> Unit) {
    val appName = context.getString(R.string.app_name) ?: BuildConfig.APPLICATION_ID.split(".").last()
    val directoryName = "/$appName"
    val defaultFileName =
            "video_" +
            UUID.randomUUID().toString().truncateMiddle(10, ellipses = false) +
            ".mp4"

    try {
        // Make VideoPlayerCompose directory if it doesn't exist
        val toDirectory =
            File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MOVIES
                ).toString() + directoryName
            )
        toDirectory.mkdirs()

        //val file = File(toDirectory, fromUri.lastPathSegment ?: defaultFileName) // use original file name
        val file = File(toDirectory, defaultFileName)
        val inputStream = context.contentResolver.openInputStream(fromUri)!!
        val outputStream = FileOutputStream(file)

        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()

        // Delete the cached Uri file
        context.contentResolver.delete(fromUri, null, null)

        onSuccessCallback(file)
    } catch (e: Exception) {
        Log.e("VideoPlayerCompose", "moveTmpUriToMainStorage->Error moving file: ${e.message}")
    }
}



