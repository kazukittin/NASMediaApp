@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ponchi.nasmedia

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class AudioPlaybackRequest(
    val server: String,
    val username: String,
    val password: String,
    val folderPath: String,
    val paths: List<String>,
    val initialIndex: Int
)

internal object AudioPlaybackRequestStore {
    @Volatile var request: AudioPlaybackRequest? = null
}

class AudioPlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(5_000L)
            .setSeekForwardIncrementMs(30_000L)
            .build()
        session = MediaSession.Builder(this, player).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_LOAD_PLAYLIST) loadRequest()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun loadRequest() {
        val request = AudioPlaybackRequestStore.request ?: return
        serviceScope.launch {
            // プライバシー設定がONのときは通知・ロック画面に実ジャケットを出さない
            val hideArtwork = getSharedPreferences(PRIVACY_PREFERENCES, MODE_PRIVATE)
                .getBoolean(HIDE_AUDIO_ARTWORK_KEY, false)
            val artwork = if (hideArtwork) {
                null
            } else runCatching {
                loadPreferredAudioCoverBitmap(
                    this@AudioPlaybackService,
                    request.server,
                    request.username,
                    request.password,
                    request.folderPath
                )?.toArtworkData()
            }.getOrNull()
            val albumTitle = request.folderPath.substringAfterLast('\\').ifBlank { "Voice" }
            val sources = request.paths.map { path ->
                val factory = SmbVideoDataSource.Factory(
                    request.server, request.username, request.password, "Voice", path
                )
                val metadata = MediaMetadata.Builder()
                    .setTitle(path.substringAfterLast('\\').substringBeforeLast('.'))
                    .setAlbumTitle(albumTitle)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .apply {
                        artwork?.let {
                            setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        }
                    }
                    .build()
                val item = MediaItem.Builder()
                    .setMediaId(path)
                    .setUri(Uri.Builder().scheme("smb").authority("Voice")
                        .encodedPath(path.replace('\\', '/')).build())
                    .setMediaMetadata(metadata)
                    .build()
                ProgressiveMediaSource.Factory(factory).createMediaSource(item)
            }
            withContext(Dispatchers.Main) {
                player.setMediaSources(sources, request.initialIndex, 0L)
                player.prepare()
                player.play()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onDestroy() {
        serviceScope.cancel()
        session.release()
        player.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_LOAD_PLAYLIST = "com.ponchi.nasmedia.LOAD_AUDIO_PLAYLIST"
    }
}

private fun Bitmap.toArtworkData(): ByteArray = ByteArrayOutputStream().use { output ->
    compress(Bitmap.CompressFormat.JPEG, 90, output)
    output.toByteArray()
}
