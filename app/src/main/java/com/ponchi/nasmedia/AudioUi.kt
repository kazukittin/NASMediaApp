@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ponchi.nasmedia

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay

@Composable
internal fun AudioPlayerScreen(
    playlist: AudioPlaylist,
    server: String,
    username: String,
    password: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentIndex by remember(playlist) { mutableIntStateOf(playlist.initialIndex) }
    var volume by remember(playlist) { mutableStateOf(1f) }
    var coverBitmap by remember(playlist) { mutableStateOf<Bitmap?>(null) }
    val audioState = remember {
        context.getSharedPreferences(AUDIO_STATE_PREFERENCES, Context.MODE_PRIVATE)
    }
    var playbackSpeed by remember(playlist) {
        mutableStateOf(loadAudioSpeed(audioState, playlist.folderPath))
    }
    var bookmarks by remember(playlist) {
        mutableStateOf(loadAudioBookmarks(audioState, playlist.folderPath))
    }
    var sleepMinutes by remember(playlist) { mutableIntStateOf(0) }
    var sleepEndOfFolder by remember(playlist) { mutableStateOf(false) }
    var sleepDeadline by remember(playlist) { mutableStateOf(0L) }
    var sleepRemaining by remember(playlist) { mutableStateOf(0L) }
    var sleepBaseVolume by remember(playlist) { mutableStateOf(1f) }
    var wasNewRequest by remember { mutableStateOf(false) }
    val controllerFuture = remember(playlist, server, username, password) {
        val newRequest = AudioPlaybackRequest(
            server,
            username,
            password,
            playlist.folderPath,
            playlist.paths,
            playlist.initialIndex
        )
        // ミニプレイヤーから同じプレイリストを開き直した場合は読み込み直さず、再生位置を維持する
        wasNewRequest = AudioPlaybackRequestStore.request != newRequest
        if (wasNewRequest) {
            AudioPlaybackRequestStore.request = newRequest
            ContextCompat.startForegroundService(
                context,
                Intent(context, AudioPlaybackService::class.java)
                    .setAction(AudioPlaybackService.ACTION_LOAD_PLAYLIST)
            )
        }
        MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        ).buildAsync()
    }
    var connectedPlayer by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(controllerFuture) {
        controllerFuture.addListener(
            { connectedPlayer = runCatching { controllerFuture.get() }.getOrNull() },
            ContextCompat.getMainExecutor(context)
        )
        onDispose { MediaController.releaseFuture(controllerFuture) }
    }
    val player: Player = connectedPlayer ?: run {
        Surface(modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        return
    }
    LaunchedEffect(playlist.folderPath) {
        coverBitmap = runCatching {
            loadPreferredAudioCoverBitmap(
                context,
                server,
                username,
                password,
                playlist.folderPath
            )
        }.getOrNull()
    }
    LaunchedEffect(player) {
        player.setPlaybackSpeed(playbackSpeed)
        if (wasNewRequest && playlist.resumePositionMs > 0L) {
            var attempts = 0
            while (attempts < 40 && player.playbackState == Player.STATE_IDLE) {
                delay(250L)
                attempts++
            }
            runCatching { player.seekTo(playlist.resumePositionMs) }
        }
        var lastSavedIndex = -1
        var lastSavedBucket = -1L
        while (true) {
            currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0L } ?: 0L
            isPlaying = player.isPlaying
            volume = player.volume
            val bucket = position / 5_000L
            if (currentIndex != lastSavedIndex || bucket != lastSavedBucket) {
                lastSavedIndex = currentIndex
                lastSavedBucket = bucket
                playlist.paths.getOrNull(currentIndex)?.let { trackPath ->
                    saveAudioQueue(
                        audioState, playlist.folderPath, currentIndex, position, trackPath
                    )
                }
            }
            if (sleepDeadline > 0L) {
                val remaining = sleepDeadline - System.currentTimeMillis()
                sleepRemaining = remaining.coerceAtLeast(0L)
                if (remaining <= 0L) {
                    player.pause()
                    player.volume = sleepBaseVolume
                    sleepDeadline = 0L
                    sleepMinutes = 0
                } else if (remaining <= 30_000L) {
                    player.volume = sleepBaseVolume * (remaining / 30_000f)
                }
            } else if (sleepEndOfFolder) {
                val isLastTrack = currentIndex >= playlist.paths.lastIndex
                if (isLastTrack && duration > 0L) {
                    val remainingMs = duration - position
                    sleepRemaining = remainingMs.coerceAtLeast(0L)
                    if (remainingMs in 0..30_000L) {
                        player.volume = sleepBaseVolume * (remainingMs / 30_000f)
                    }
                }
            }
            delay(250L)
        }
    }
    BackHandler(onBack = onBack)
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    clearAudioQueue(audioState, playlist.folderPath)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            val index = player.currentMediaItemIndex.coerceAtLeast(0)
            playlist.paths.getOrNull(index)?.let { trackPath ->
                saveAudioQueue(
                    audioState,
                    playlist.folderPath,
                    index,
                    player.currentPosition.coerceAtLeast(0L),
                    trackPath
                )
            }
            player.removeListener(listener)
        }
    }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap!!.asImageBitmap(),
                        contentDescription = "作品画像",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("作品画像なし", style = MaterialTheme.typography.titleLarge)
                }
            }
            Text(
                playlist.paths.getOrNull(currentIndex)?.substringAfterLast('\\').orEmpty(),
                style = MaterialTheme.typography.titleLarge
            )
            Slider(
                value = position.coerceAtMost(duration).toFloat(),
                onValueChange = { position = it.toLong() },
                onValueChangeFinished = { player.seekTo(position) },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatPlaybackTime(position))
                Text(formatPlaybackTime(duration))
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { player.seekToPreviousMediaItem() },
                    enabled = currentIndex > 0) { Text("|◀") }
                TextButton(onClick = { player.seekTo((player.currentPosition - 15_000L).coerceAtLeast(0L)) }) {
                    Text("↶15")
                }
                Button(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                    Text(if (isPlaying) "一時停止" else "再生")
                }
                TextButton(onClick = { player.seekTo(player.currentPosition + 15_000L) }) {
                    Text("15↷")
                }
                TextButton(onClick = { player.seekToNextMediaItem() },
                    enabled = currentIndex < playlist.paths.lastIndex) { Text("▶|") }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("音量")
                Slider(
                    value = volume,
                    onValueChange = { volume = it; player.volume = it },
                    modifier = Modifier.weight(1f)
                )
            }
            Text("再生速度", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                    FilterChip(
                        selected = playbackSpeed == speed,
                        onClick = {
                            playbackSpeed = speed
                            player.setPlaybackSpeed(speed)
                            saveAudioSpeed(audioState, playlist.folderPath, speed)
                        },
                        label = { Text("×$speed") }
                    )
                }
            }
            Text("スリープタイマー", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(10, 20, 30, 60).forEach { minutes ->
                    FilterChip(
                        selected = sleepMinutes == minutes,
                        onClick = {
                            sleepMinutes = minutes
                            sleepEndOfFolder = false
                            sleepBaseVolume = player.volume
                            sleepDeadline = System.currentTimeMillis() + minutes * 60_000L
                        },
                        label = { Text("${minutes}分") }
                    )
                }
                FilterChip(
                    selected = sleepEndOfFolder,
                    onClick = {
                        sleepEndOfFolder = true
                        sleepMinutes = 0
                        sleepDeadline = 0L
                        sleepBaseVolume = player.volume
                    },
                    label = { Text("フォルダ最後") }
                )
                if (sleepMinutes > 0 || sleepEndOfFolder) {
                    TextButton(onClick = {
                        sleepMinutes = 0
                        sleepEndOfFolder = false
                        sleepDeadline = 0L
                        player.volume = sleepBaseVolume
                    }) { Text("解除") }
                }
            }
            if (sleepDeadline > 0L) {
                Text(
                    "あと ${formatPlaybackTime(sleepRemaining)} で停止します",
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (sleepEndOfFolder) {
                Text(
                    "このフォルダの最後で停止します",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text("しおり", style = MaterialTheme.typography.titleSmall)
            Button(
                onClick = {
                    playlist.paths.getOrNull(currentIndex)?.let { trackPath ->
                        bookmarks = addAudioBookmark(
                            audioState,
                            playlist.folderPath,
                            AudioBookmark(currentIndex, position, trackPath)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("この位置にしおりを追加")
            }
            bookmarks.forEach { bookmark ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { player.seekTo(bookmark.trackIndex, bookmark.positionMs) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "${bookmark.trackPath.substringAfterLast('\\')
                                .substringBeforeLast('.')} ${formatPlaybackTime(bookmark.positionMs)}"
                        )
                    }
                    TextButton(
                        onClick = {
                            bookmarks = removeAudioBookmark(
                                audioState, playlist.folderPath, bookmark
                            )
                        }
                    ) { Text("削除") }
                }
            }
        }
    }
}
