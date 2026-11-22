@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ponchi.nasmedia

import android.app.Activity
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.KeyEvent
import android.net.Uri
import android.content.ComponentName
import android.content.Intent
import android.content.Context
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.ponchi.nasmedia.ui.theme.NASMediaTheme
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File as LocalFile
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.EnumSet
import java.util.zip.ZipFile

class MainActivity : ComponentActivity() {
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // マンガリーダー表示中のみ音量キーでページ送りする
        val handler = MangaVolumeKeyHandler.handler
        if (handler != null &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            handler(keyCode == KeyEvent.KEYCODE_VOLUME_UP)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NASMediaTheme {
                NasConnectionScreen(cacheDirectory = cacheDir)
            }
        }
    }
}

internal data class MangaBook(
    val zipName: String,
    val cachedZip: LocalFile,
    val pageNames: List<String>,
    val charset: Charset
)

internal data class PhotoEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean
)

private data class PhotoGallery(
    val folderPath: String,
    val photoPaths: List<String>,
    val initialIndex: Int
)

internal data class VideoEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean
)

internal data class AudioEntry(val name: String, val path: String, val isDirectory: Boolean)
internal data class ResolvedAudioDirectory(
    val path: String,
    val entries: List<AudioEntry>
)
internal data class AudioPlaylist(
    val folderPath: String,
    val paths: List<String>,
    val initialIndex: Int,
    val resumePositionMs: Long = 0L
)

private data class DlsiteLibraryFilters(
    val rjOnly: Boolean = false,
    val circles: Set<String> = emptySet(),
    val voiceActors: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
    val releaseYears: Set<String> = emptySet()
) {
    val activeCount: Int
        get() = (if (rjOnly) 1 else 0) + circles.size + voiceActors.size +
            genres.size + releaseYears.size
}

private enum class DlsiteFilterCategory(val label: String) {
    CIRCLE("サークル"),
    VOICE_ACTOR("声優"),
    GENRE("ジャンル"),
    RELEASE_YEAR("発売年")
}

private val MANGA_FILTER_CATEGORIES = setOf(
    DlsiteFilterCategory.CIRCLE,
    DlsiteFilterCategory.GENRE,
    DlsiteFilterCategory.RELEASE_YEAR
)

private val AUDIO_FILTER_CATEGORIES = setOf(
    DlsiteFilterCategory.CIRCLE,
    DlsiteFilterCategory.VOICE_ACTOR,
    DlsiteFilterCategory.GENRE,
    DlsiteFilterCategory.RELEASE_YEAR
)

private data class DlsiteFilterCandidate(
    val displayName: String,
    val workInfo: DlsiteWorkInfo?
)

private enum class MediaSection(
    val title: String,
    val description: String
) {
    MANGA("マンガ", "ZIP作品を読む"),
    PHOTOS("写真", "写真を一覧・表示"),
    VIDEOS("ビデオ", "動画を再生"),
    AUDIO("音声", "作品ごとに再生")
}

internal data class RecentItem(
    val type: String,
    val key: String,
    val name: String,
    val timestamp: Long
)

internal enum class LibrarySort(val label: String) {
    NAME_ASC("名前順（昇順）"),
    NAME_DESC("名前順（降順）")
}

internal class DownloadControl {
    @Volatile var cancelled: Boolean = false
}

@Composable
private fun NasConnectionScreen(
    cacheDirectory: LocalFile,
    modifier: Modifier = Modifier
) {
    var server by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var mangaFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var mangaMetadata by remember { mutableStateOf<Map<String, DlsiteWorkInfo>>(emptyMap()) }
    var mangaFilters by remember { mutableStateOf(DlsiteLibraryFilters()) }
    var openedBook by remember { mutableStateOf<MangaBook?>(null) }
    var openedPhotoGallery by remember { mutableStateOf<PhotoGallery?>(null) }
    var isConnected by remember { mutableStateOf(false) }
    var selectedSection by remember { mutableStateOf<MediaSection?>(null) }
    var photoPath by remember { mutableStateOf("") }
    var photoEntries by remember { mutableStateOf<List<PhotoEntry>>(emptyList()) }
    var photoStatus by remember { mutableStateOf("") }
    var photoIsLoading by remember { mutableStateOf(false) }
    var videoPath by remember { mutableStateOf("") }
    var videoEntries by remember { mutableStateOf<List<VideoEntry>>(emptyList()) }
    var videoStatus by remember { mutableStateOf("") }
    var videoIsLoading by remember { mutableStateOf(false) }
    var openedVideoPath by remember { mutableStateOf<String?>(null) }
    var audioPath by remember { mutableStateOf("") }
    var audioEntries by remember { mutableStateOf<List<AudioEntry>>(emptyList()) }
    var audioStatus by remember { mutableStateOf("") }
    var audioIsLoading by remember { mutableStateOf(false) }
    var openedAudioPlaylist by remember { mutableStateOf<AudioPlaylist?>(null) }
    var status by remember { mutableStateOf("") }
    var connectionFailed by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var rememberLogin by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var hasSavedCredentials by remember {
        mutableStateOf(SecureNasCredentials.hasSaved(context))
    }
    val readingProgress = remember {
        context.getSharedPreferences(READING_PROGRESS_PREFERENCES, Context.MODE_PRIVATE)
    }
    val mangaCacheMetadata = remember {
        context.getSharedPreferences(MANGA_CACHE_PREFERENCES, Context.MODE_PRIVATE)
    }
    val recentPreferences = remember {
        context.getSharedPreferences(RECENT_PREFERENCES, Context.MODE_PRIVATE)
    }
    val videoProgressPreferences = remember {
        context.getSharedPreferences(VIDEO_PROGRESS_PREFERENCES, Context.MODE_PRIVATE)
    }
    val audioStatePreferences = remember {
        context.getSharedPreferences(AUDIO_STATE_PREFERENCES, Context.MODE_PRIVATE)
    }
    var recentItems by remember { mutableStateOf(loadRecentItems(recentPreferences)) }
    var mangaSort by remember { mutableStateOf(LibrarySort.NAME_ASC) }
    var mangaDownloading by remember { mutableStateOf(false) }
    var mangaDownloadPercent by remember { mutableStateOf(-1) }
    var mangaDownloadControl by remember { mutableStateOf<DownloadControl?>(null) }
    var mangaCacheSize by remember { mutableStateOf(0L) }
    val favoritesPreferences = remember {
        context.getSharedPreferences(FAVORITES_PREFERENCES, Context.MODE_PRIVATE)
    }
    var favorites by remember { mutableStateOf(loadFavorites(favoritesPreferences)) }
    var cachedMangaEntries by remember { mutableStateOf<List<CachedMangaEntry>>(emptyList()) }
    val privacyPreferences = remember {
        context.getSharedPreferences(PRIVACY_PREFERENCES, Context.MODE_PRIVATE)
    }
    var hideAudioArtwork by remember {
        mutableStateOf(privacyPreferences.getBoolean(HIDE_AUDIO_ARTWORK_KEY, false))
    }
    var mangaFavOnly by remember { mutableStateOf(false) }
    var mangaUnreadOnly by remember { mutableStateOf(false) }
    var mangaCachedOnly by remember { mutableStateOf(false) }

    fun refreshRecentItems() {
        recentItems = loadRecentItems(recentPreferences)
    }

    fun refreshFavorites() {
        favorites = loadFavorites(favoritesPreferences)
    }

    fun toggleFavoriteItem(type: String, key: String, name: String) {
        favorites = toggleFavorite(favoritesPreferences, type, key, name)
    }

    fun refreshMangaCacheSize() {
        scope.launch {
            mangaCacheSize = withContext(Dispatchers.IO) {
                mangaCacheSizeBytes(cacheDirectory)
            }
            cachedMangaEntries = withContext(Dispatchers.IO) {
                listCachedManga(cacheDirectory, mangaCacheMetadata)
            }
        }
    }

    fun openManga(fileName: String) {
        if (isLoading) return
        val control = DownloadControl()
        mangaDownloadControl = control
        mangaDownloading = true
        mangaDownloadPercent = -1
        isLoading = true
        status = "読み込み中…"
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    downloadAndOpenManga(
                        server = server.trim(),
                        username = username.trim(),
                        password = password,
                        zipName = fileName,
                        cacheDirectory = cacheDirectory,
                        cacheMetadata = mangaCacheMetadata,
                        onProgress = { downloaded, total ->
                            if (total > 0L) {
                                val percent = (downloaded * 100L / total).toInt()
                                if (percent != mangaDownloadPercent) {
                                    mangaDownloadPercent = percent
                                }
                            }
                        },
                        downloadControl = control
                    )
                }
            }.onSuccess {
                readingProgress.edit()
                    .putInt(pageCountKey(it.zipName), it.pageNames.size)
                    .apply()
                recordRecentItem(
                    recentPreferences, RECENT_TYPE_MANGA, it.zipName, it.zipName
                )
                refreshRecentItems()
                openedBook = it
                status = ""
                refreshMangaCacheSize()
            }.onFailure {
                status = if (control.cancelled) {
                    "ダウンロードをキャンセルしました"
                } else {
                    "ZIPを開けません"
                }
            }
            mangaDownloading = false
            mangaDownloadPercent = -1
            isLoading = false
        }
    }

    fun reloadMangaList() {
        if (isLoading) return
        isLoading = true
        status = "接続中…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    loadMangaZipNames(server.trim(), username.trim(), password)
                }
            }
            result.onSuccess { files ->
                mangaFiles = files
                status = if (files.isEmpty()) "MangaにZIPがありません" else ""
            }.onFailure { error ->
                status = classifyNasConnectionError(error)
            }
            isLoading = false
        }
    }

    fun openPhotoDirectory(path: String) {
        photoPath = path
        photoEntries = emptyList()
        photoStatus = ""
        photoIsLoading = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    loadPhotoEntries(server.trim(), username.trim(), password, path)
                }
            }.onSuccess { entries ->
                photoEntries = entries
                photoStatus = if (entries.isEmpty()) "空のフォルダです" else ""
            }.onFailure {
                photoStatus = "写真を読み込めません"
            }
            photoIsLoading = false
        }
    }

    fun openVideoDirectory(path: String) {
        videoPath = path
        videoEntries = emptyList()
        videoStatus = ""
        videoIsLoading = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    loadVideoEntries(server.trim(), username.trim(), password, path)
                }
            }.onSuccess { entries ->
                videoEntries = entries
                videoStatus = if (entries.isEmpty()) "空のフォルダです" else ""
            }.onFailure {
                videoStatus = "ビデオを読み込めません"
            }
            videoIsLoading = false
        }
    }

    fun openAudioDirectory(path: String, autoAdvance: Boolean = true) {
        audioPath = path
        audioEntries = emptyList()
        audioStatus = ""
        audioIsLoading = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                if (autoAdvance) {
                    resolveAudioDirectory(server.trim(), username.trim(), password, path)
                } else {
                    ResolvedAudioDirectory(
                        path = path,
                        entries = loadAudioEntries(
                            server.trim(), username.trim(), password, path
                        )
                    )
                }
            } }.onSuccess { directory ->
                audioPath = directory.path
                audioEntries = directory.entries
                audioStatus = if (directory.entries.isEmpty()) "空のフォルダです" else ""
            }.onFailure { audioStatus = "音声を読み込めません" }
            audioIsLoading = false
        }
    }

    fun openRecentItem(item: RecentItem) {
        when (item.type) {
            RECENT_TYPE_MANGA -> {
                selectedSection = MediaSection.MANGA
                openManga(item.key)
            }
            RECENT_TYPE_VIDEO -> {
                openedVideoPath = item.key
            }
            RECENT_TYPE_AUDIO -> {
                selectedSection = MediaSection.AUDIO
                openAudioDirectory(item.key)
            }
        }
    }

    LaunchedEffect(isConnected, selectedSection) {
        if (isConnected && selectedSection == null) {
            refreshRecentItems()
            refreshFavorites()
            refreshMangaCacheSize()
        }
    }

    fun selectSection(section: MediaSection?) {
        if (section == selectedSection) return
        selectedSection = section
        if (section == MediaSection.PHOTOS) openPhotoDirectory("")
        if (section == MediaSection.VIDEOS) openVideoDirectory("")
        if (section == MediaSection.AUDIO) openAudioDirectory("")
    }

    // ミニプレイヤー用に MediaController をこの画面で保持する（AudioPlayerScreen とは別接続）
    var miniController by remember { mutableStateOf<MediaController?>(null) }
    var miniTitle by remember { mutableStateOf("") }
    var miniIsPlaying by remember { mutableStateOf(false) }
    var miniHasItem by remember { mutableStateOf(false) }
    val miniControllerFuture = remember(context) {
        MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        ).buildAsync()
    }
    DisposableEffect(miniControllerFuture) {
        miniControllerFuture.addListener(
            { miniController = runCatching { miniControllerFuture.get() }.getOrNull() },
            ContextCompat.getMainExecutor(context)
        )
        onDispose { MediaController.releaseFuture(miniControllerFuture) }
    }
    LaunchedEffect(miniController) {
        val controller = miniController ?: return@LaunchedEffect
        while (true) {
            miniHasItem = controller.mediaItemCount > 0
            miniTitle = controller.currentMediaItem?.mediaMetadata?.title?.toString()
                ?: controller.currentMediaItem?.mediaId
                    ?.substringAfterLast('\\')?.substringBeforeLast('.')
                ?: ""
            miniIsPlaying = controller.isPlaying
            delay(500L)
        }
    }

    val bottomBar: @Composable () -> Unit = {
        if (isConnected) {
            Column {
                if (miniHasItem && openedAudioPlaylist == null) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    AudioPlaybackRequestStore.request?.let { request ->
                                        openedAudioPlaylist = AudioPlaylist(
                                            folderPath = request.folderPath,
                                            paths = request.paths,
                                            initialIndex = request.initialIndex
                                        )
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                miniTitle.ifBlank { "音声を再生中" },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall
                            )
                            TextButton(
                                onClick = {
                                    if (miniIsPlaying) miniController?.pause()
                                    else miniController?.play()
                                }
                            ) {
                                Text(if (miniIsPlaying) "一時停止" else "再生")
                            }
                        }
                    }
                }
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedSection == null,
                        onClick = { selectSection(null) },
                        icon = { Text("⌂") },
                        label = { Text("ホーム") }
                    )
                    MediaSection.entries.forEach { section ->
                        NavigationBarItem(
                            selected = selectedSection == section,
                            onClick = { selectSection(section) },
                            icon = {
                                Text(
                                    when (section) {
                                        MediaSection.MANGA -> "本"
                                        MediaSection.PHOTOS -> "画"
                                        MediaSection.VIDEOS -> "映"
                                        MediaSection.AUDIO -> "音"
                                    }
                                )
                            },
                            label = { Text(section.title) }
                        )
                    }
                }
            }
        }
    }

    fun connectToNas() {
        if (server.isBlank() || username.isBlank() || password.isBlank()) {
            status = "未入力の項目があります"
            return
        }
        isLoading = true
        connectionFailed = false
        mangaFiles = emptyList()
        status = "接続中…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    loadMangaZipNames(server.trim(), username.trim(), password)
                }
            }
            result.onSuccess { files ->
                if (rememberLogin) {
                    withContext(Dispatchers.IO) {
                        SecureNasCredentials.save(
                            context,
                            NasCredentials(server.trim(), username.trim(), password)
                        )
                    }
                    hasSavedCredentials = true
                } else if (hasSavedCredentials) {
                    withContext(Dispatchers.IO) { SecureNasCredentials.clear(context) }
                    hasSavedCredentials = false
                }
                mangaFiles = files
                isConnected = true
                selectedSection = null
                status = if (files.isEmpty()) "MangaにZIPがありません" else ""
            }.onFailure { error ->
                connectionFailed = true
                status = classifyNasConnectionError(error)
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        if (!hasSavedCredentials) return@LaunchedEffect
        val saved = withContext(Dispatchers.IO) {
            runCatching { SecureNasCredentials.load(context) }
        }.onFailure {
            withContext(Dispatchers.IO) { SecureNasCredentials.clear(context) }
            hasSavedCredentials = false
            status = "保存情報を削除しました"
        }.getOrNull() ?: return@LaunchedEffect
        server = saved.server
        username = saved.username
        password = saved.password
        rememberLogin = true
        connectToNas()
    }

    LaunchedEffect(mangaFiles) {
        mangaMetadata = emptyMap()
        mangaFiles.filter { findDlsiteProductId(it) != null }.forEach { fileName ->
            loadDlsiteWorkInfo(context, fileName)?.let { info ->
                mangaMetadata = mangaMetadata + (fileName to info)
            }
        }
    }


    openedAudioPlaylist?.let { playlist ->
        AudioPlayerScreen(
            playlist, server, username, password,
            onBack = { openedAudioPlaylist = null }, modifier = modifier
        )
        return
    }

    openedVideoPath?.let { video ->
        VideoPlayerScreen(
            videoPath = video,
            server = server,
            username = username,
            password = password,
            onBack = { openedVideoPath = null },
            modifier = modifier
        )
        return
    }

    openedPhotoGallery?.let { gallery ->
        PhotoViewerScreen(
            gallery = gallery,
            server = server,
            username = username,
            password = password,
            cacheDirectory = cacheDirectory,
            onBack = { openedPhotoGallery = null },
            modifier = modifier
        )
        return
    }

    openedBook?.let { manga ->
        MangaReaderScreen(
            book = manga,
            initialPage = savedPage(readingProgress, manga.zipName),
            onPageChanged = { page ->
                readingProgress.edit()
                    .putInt(pageKey(manga.zipName), page)
                    .apply()
            },
            onBack = { openedBook = null },
            modifier = modifier
        )
        return
    }

    if (isConnected && selectedSection == null) {
        val continueItems = recentItems.filter { item ->
            when (item.type) {
                RECENT_TYPE_MANGA -> readingProgress.contains(pageKey(item.key))
                RECENT_TYPE_VIDEO ->
                    videoProgressPreferences.getLong(videoProgressKey(item.key), 0L) > 0L
                else -> false
            }
        }
        MediaHomeScreen(
            server = server,
            hasSavedCredentials = hasSavedCredentials,
            continueItems = continueItems,
            recentItems = recentItems,
            readingProgress = readingProgress,
            videoProgress = videoProgressPreferences,
            mangaCacheSize = mangaCacheSize,
            favorites = favorites,
            onOpenFavorite = { favorite ->
                openRecentItem(
                    RecentItem(favorite.type, favorite.key, favorite.name, favorite.timestamp)
                )
            },
            cachedMangaEntries = cachedMangaEntries,
            onDeleteCachedManga = { zipName ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        deleteCachedManga(cacheDirectory, mangaCacheMetadata, zipName)
                    }
                    refreshMangaCacheSize()
                }
            },
            hideAudioArtwork = hideAudioArtwork,
            onToggleHideAudioArtwork = { enabled ->
                hideAudioArtwork = enabled
                privacyPreferences.edit()
                    .putBoolean(HIDE_AUDIO_ARTWORK_KEY, enabled)
                    .apply()
            },
            onOpenRecent = { item -> openRecentItem(item) },
            onClearMangaCache = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        clearMangaCache(cacheDirectory, mangaCacheMetadata)
                    }
                    refreshMangaCacheSize()
                }
            },
            onSelect = { section -> selectSection(section) },
            onForgetCredentials = {
                val hadSavedCredentials = hasSavedCredentials
                scope.launch {
                    withContext(Dispatchers.IO) { SecureNasCredentials.clear(context) }
                    hasSavedCredentials = false
                    rememberLogin = false
                    password = ""
                    isConnected = false
                    selectedSection = null
                    status = if (hadSavedCredentials) {
                        "保存情報を削除しました"
                    } else {
                        "切断しました"
                    }
                }
            },
            modifier = modifier,
            bottomBar = bottomBar
        )
        return
    }

    if (isConnected && selectedSection == MediaSection.PHOTOS) {
        PhotoBrowserScreen(
            currentPath = photoPath,
            entries = photoEntries,
            status = photoStatus,
            isLoading = photoIsLoading,
            server = server,
            username = username,
            password = password,
            cacheDirectory = cacheDirectory,
            onOpenFolder = ::openPhotoDirectory,
            onOpenPhoto = { selectedPhoto ->
                val photos = photoEntries.filterNot { it.isDirectory }
                openedPhotoGallery = PhotoGallery(
                    folderPath = photoPath,
                    photoPaths = photos.map { it.path },
                    initialIndex = photos.indexOfFirst { it.path == selectedPhoto.path }
                        .coerceAtLeast(0)
                )
            },
            onRefresh = { openPhotoDirectory(photoPath) },
            bottomBar = bottomBar,
            onBack = {
                if (photoPath.isEmpty()) {
                    selectedSection = null
                } else {
                    openPhotoDirectory(parentSmbPath(photoPath))
                }
            },
            modifier = modifier
        )
        return
    }

    if (isConnected && selectedSection == MediaSection.VIDEOS) {
        VideoBrowserScreen(
            currentPath = videoPath,
            entries = videoEntries,
            status = videoStatus,
            isLoading = videoIsLoading,
            onOpenFolder = ::openVideoDirectory,
            onOpenVideo = { entry ->
                recordRecentItem(
                    recentPreferences, RECENT_TYPE_VIDEO, entry.path, entry.name
                )
                refreshRecentItems()
                openedVideoPath = entry.path
            },
            onRefresh = { openVideoDirectory(videoPath) },
            bottomBar = bottomBar,
            onBack = {
                if (videoPath.isEmpty()) {
                    selectedSection = null
                } else {
                    openVideoDirectory(parentSmbPath(videoPath))
                }
            },
            modifier = modifier
        )
        return
    }

    if (isConnected && selectedSection == MediaSection.AUDIO) {
        AudioBrowserScreen(
            currentPath = audioPath,
            entries = audioEntries,
            status = audioStatus,
            isLoading = audioIsLoading,
            onOpenFolder = { entry -> openAudioDirectory(entry.path) },
            onOpenAudio = { selected ->
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    (context as? Activity)?.let { activity ->
                        ActivityCompat.requestPermissions(
                            activity,
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            1001
                        )
                    }
                }
                recordRecentItem(
                    recentPreferences,
                    RECENT_TYPE_AUDIO,
                    audioPath,
                    audioPath.substringAfterLast('\\').ifBlank { "Voice" }
                )
                refreshRecentItems()
                val tracks = audioEntries.filterNot { it.isDirectory }
                val trackPaths = tracks.map { it.path }
                val savedQueue = loadAudioQueue(audioStatePreferences, audioPath)
                    ?.takeIf { queue ->
                        queue.positionMs > 0L &&
                            queue.trackIndex in trackPaths.indices &&
                            trackPaths[queue.trackIndex] == queue.trackPath
                    }
                openedAudioPlaylist = AudioPlaylist(
                    folderPath = audioPath,
                    paths = trackPaths,
                    initialIndex = savedQueue?.trackIndex
                        ?: tracks.indexOfFirst { it.path == selected.path }.coerceAtLeast(0),
                    resumePositionMs = savedQueue?.positionMs ?: 0L
                )
            },
            onRefresh = { openAudioDirectory(audioPath, autoAdvance = false) },
            bottomBar = bottomBar,
            onBack = {
                if (audioPath.isEmpty()) selectedSection = null
                else openAudioDirectory(parentSmbPath(audioPath), autoAdvance = false)
            },
            modifier = modifier
        )
        return
    }

    if (isConnected && selectedSection != MediaSection.MANGA) {
        MediaPlaceholderScreen(
            section = selectedSection!!,
            onBack = { selectedSection = null },
            modifier = modifier
        )
        return
    }

    BackHandler(enabled = isConnected) { selectedSection = null }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        if (!isConnected) {
            Text("NAS Media", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("NASのIPアドレス") },
            placeholder = { Text("例：192.168.1.10") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next
            ),
            singleLine = true
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("専用ユーザー名") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            singleLine = true
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("パスワード") },
            visualTransformation = if (showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                TextButton(onClick = { showPassword = !showPassword }) {
                    Text(if (showPassword) "隠す" else "表示")
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                connectToNas()
            }),
            singleLine = true
        )

            Row(
                Modifier.fillMaxWidth().clickable(enabled = !isLoading) {
                    rememberLogin = !rememberLogin
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = rememberLogin,
                    onCheckedChange = { rememberLogin = it },
                    enabled = !isLoading
                )
                Text("ログイン情報を保存")
            }
            Button(
                onClick = {
                    focusManager.clearFocus()
                    connectToNas()
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("NASに接続")
            }
            if (connectionFailed && !isLoading) {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        connectToNas()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("再試行")
                }
            }
            if (hasSavedCredentials) {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                SecureNasCredentials.clear(context)
                            }
                            hasSavedCredentials = false
                            rememberLogin = false
                            password = ""
                            status = "保存したログイン情報を削除しました"
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存情報を削除")
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("マンガ", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { selectedSection = null }) {
                    Text("ホーム")
                }
            }
        }

        if (isLoading) {
            CircularProgressIndicator(Modifier.size(24.dp))
        }
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
        if (isConnected && mangaDownloading) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (mangaDownloadPercent >= 0) {
                        "ダウンロード中… $mangaDownloadPercent%"
                    } else {
                        "ダウンロード中…"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                if (mangaDownloadPercent >= 0) {
                    LinearProgressIndicator(
                        progress = { mangaDownloadPercent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                TextButton(onClick = { mangaDownloadControl?.cancelled = true }) {
                    Text("キャンセル")
                }
            }
        }

        if (isConnected && selectedSection == MediaSection.MANGA) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibrarySort.entries.forEach { sort ->
                    FilterChip(
                        selected = mangaSort == sort,
                        onClick = { mangaSort = sort },
                        label = { Text(sort.label) }
                    )
                }
            }
            DlsiteLibraryFilterControls(
                candidates = mangaFiles.map {
                    DlsiteFilterCandidate(it, mangaMetadata[it])
                },
                filters = mangaFilters,
                availableCategories = MANGA_FILTER_CATEGORIES,
                onFiltersChange = { mangaFilters = it }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mangaFavOnly,
                    onClick = { mangaFavOnly = !mangaFavOnly },
                    label = { Text("お気に入りのみ") }
                )
                FilterChip(
                    selected = mangaUnreadOnly,
                    onClick = { mangaUnreadOnly = !mangaUnreadOnly },
                    label = { Text("未読のみ") }
                )
                FilterChip(
                    selected = mangaCachedOnly,
                    onClick = { mangaCachedOnly = !mangaCachedOnly },
                    label = { Text("キャッシュ済みのみ") }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                val cachedMangaNames = cachedMangaEntries.map { it.zipName }.toSet()
                val filteredMangaFiles = mangaFiles.filter { fileName ->
                    matchesDlsiteLibraryFilter(
                        displayName = fileName,
                        workInfo = mangaMetadata[fileName],
                        filters = mangaFilters
                    ) && (!mangaFavOnly ||
                        isFavorite(favorites, RECENT_TYPE_MANGA, fileName)) &&
                        (!mangaUnreadOnly ||
                            savedPage(readingProgress, fileName) <= 0) &&
                        (!mangaCachedOnly || fileName in cachedMangaNames)
                }
                val sortedMangaFiles = when (mangaSort) {
                    LibrarySort.NAME_ASC ->
                        filteredMangaFiles.sortedWith { a, b -> compareNaturally(a, b) }
                    LibrarySort.NAME_DESC ->
                        filteredMangaFiles.sortedWith { a, b -> compareNaturally(b, a) }
                }
                if (sortedMangaFiles.isEmpty() && !isLoading) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                if (mangaFiles.isEmpty()) {
                                    "マンガがありません"
                                } else {
                                    "条件に合うマンガがありません"
                                }
                            )
                            Button(onClick = { reloadMangaList() }) {
                                Text("再読み込み")
                            }
                        }
                    }
                }
                items(sortedMangaFiles) { fileName ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isLoading) { openManga(fileName) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f)) {
                                MangaLibraryCardContent(
                                    fileName = fileName,
                                    cacheDirectory = cacheDirectory,
                                    progressLabel = savedProgressLabel(readingProgress, fileName)
                                )
                            }
                            val isFav = isFavorite(favorites, RECENT_TYPE_MANGA, fileName)
                            TextButton(
                                onClick = {
                                    toggleFavoriteItem(RECENT_TYPE_MANGA, fileName, fileName)
                                }
                            ) {
                                Text(if (isFav) "★" else "☆")
                            }
                        }
                    }
                }
            }
        if (isConnected) {
            bottomBar()
        }
        }
    }
}









@Composable
private fun MediaHomeScreen(
    server: String,
    hasSavedCredentials: Boolean,
    continueItems: List<RecentItem>,
    recentItems: List<RecentItem>,
    readingProgress: SharedPreferences,
    videoProgress: SharedPreferences,
    mangaCacheSize: Long,
    favorites: List<FavoriteItem>,
    onOpenFavorite: (FavoriteItem) -> Unit,
    cachedMangaEntries: List<CachedMangaEntry>,
    onDeleteCachedManga: (String) -> Unit,
    hideAudioArtwork: Boolean,
    onToggleHideAudioArtwork: (Boolean) -> Unit,
    onOpenRecent: (RecentItem) -> Unit,
    onClearMangaCache: () -> Unit,
    onSelect: (MediaSection) -> Unit,
    onForgetCredentials: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("NAS Media", style = MaterialTheme.typography.titleLarge)
                Text(
                    server,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (continueItems.isNotEmpty()) {
                item {
                    Text("続きから", style = MaterialTheme.typography.titleMedium)
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(continueItems) { item ->
                            Card(
                                modifier = Modifier
                                    .width(210.dp)
                                    .clickable { onOpenRecent(item) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        when (item.type) {
                                            RECENT_TYPE_MANGA -> "マンガ"
                                            RECENT_TYPE_VIDEO -> "ビデオ"
                                            else -> "音声"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        item.name.substringBeforeLast('.'),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    when (item.type) {
                                        RECENT_TYPE_MANGA -> {
                                            val pageCount = readingProgress.getInt(
                                                pageCountKey(item.key), 0
                                            )
                                            val page = readingProgress.getInt(
                                                pageKey(item.key), 0
                                            )
                                            savedProgressLabel(readingProgress, item.key)
                                                ?.let { label ->
                                                    Text(
                                                        label,
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            if (pageCount > 0) {
                                                LinearProgressIndicator(
                                                    progress = {
                                                        (page + 1).toFloat() / pageCount.toFloat()
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                        RECENT_TYPE_VIDEO -> {
                                            val position = videoProgress.getLong(
                                                videoProgressKey(item.key), 0L
                                            )
                                            if (position > 0L) {
                                                Text(
                                                    "続きから ${formatPlaybackTime(position)}",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MediaSection.entries.chunked(2).forEach { rowSections ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowSections.forEach { section ->
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(120.dp)
                                        .clickable { onSelect(section) }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            section.title,
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        Text(
                                            section.description,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                            if (rowSections.size == 1) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            if (recentItems.isNotEmpty()) {
                item {
                    Text("最近開いた", style = MaterialTheme.typography.titleMedium)
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recentItems) { item ->
                            Card(
                                modifier = Modifier
                                    .width(170.dp)
                                    .clickable { onOpenRecent(item) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        when (item.type) {
                                            RECENT_TYPE_MANGA -> "マンガ"
                                            RECENT_TYPE_VIDEO -> "ビデオ"
                                            else -> "音声"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        item.name.substringBeforeLast('.'),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (favorites.isNotEmpty()) {
                item {
                    Text("お気に入り", style = MaterialTheme.typography.titleMedium)
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(favorites) { item ->
                            Card(
                                modifier = Modifier
                                    .width(170.dp)
                                    .clickable { onOpenFavorite(item) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        when (item.type) {
                                            RECENT_TYPE_MANGA -> "マンガ"
                                            RECENT_TYPE_VIDEO -> "ビデオ"
                                            else -> "音声"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        item.name.substringBeforeLast('.'),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text("★ お気に入り", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            item {
                HorizontalDivider()
                Text("ストレージ", style = MaterialTheme.typography.titleMedium)
                Text(
                    "マンガキャッシュ：${formatFileSize(mangaCacheSize)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                cachedMangaEntries.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                entry.zipName.substringBeforeLast('.'),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                formatFileSize(entry.sizeBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { onDeleteCachedManga(entry.zipName) }) {
                            Text("削除")
                        }
                    }
                }
                TextButton(
                    onClick = onClearMangaCache,
                    enabled = mangaCacheSize > 0L
                ) {
                    Text("キャッシュをすべて削除")
                }
            }
            item {
                HorizontalDivider()
                Text("設定", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleHideAudioArtwork(!hideAudioArtwork) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = hideAudioArtwork,
                        onCheckedChange = { onToggleHideAudioArtwork(it) }
                    )
                    Column {
                        Text("音声のジャケットを隠す")
                        Text(
                            "通知・ロック画面に作品画像を表示しません（次に読み込むプレイリストから反映）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                TextButton(
                    onClick = onForgetCredentials,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (hasSavedCredentials) "保存情報を削除して切断" else "NASから切断")
                }
            }
        }
        bottomBar()
        }
    }
}

@Composable
private fun MediaPlaceholderScreen(
    section: MediaSection,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(section.title, style = MaterialTheme.typography.titleLarge)
            Button(
                onClick = onBack,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("ホームへ戻る")
            }
        }
    }
}

@Composable
private fun AudioBrowserScreen(
    currentPath: String,
    entries: List<AudioEntry>,
    status: String,
    isLoading: Boolean,
    onOpenFolder: (AudioEntry) -> Unit,
    onOpenAudio: (AudioEntry) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    var filters by remember(currentPath) { mutableStateOf(DlsiteLibraryFilters()) }
    var sort by remember(currentPath) { mutableStateOf(LibrarySort.NAME_ASC) }
    val favoritesPreferences = remember {
        context.getSharedPreferences(FAVORITES_PREFERENCES, Context.MODE_PRIVATE)
    }
    var favorites by remember { mutableStateOf(loadFavorites(favoritesPreferences)) }
    var favOnly by remember(currentPath) { mutableStateOf(false) }
    var metadataByPath by remember(currentPath) {
        mutableStateOf<Map<String, DlsiteWorkInfo>>(emptyMap())
    }
    LaunchedEffect(entries) {
        metadataByPath = emptyMap()
        entries.filter { it.isDirectory && findDlsiteProductId(it.name) != null }
            .forEach { entry ->
                loadDlsiteWorkInfo(context, entry.name)?.let { info ->
                    metadataByPath = metadataByPath + (entry.path to info)
                }
            }
    }
    BackHandler(onBack = onBack)
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("音声", style = MaterialTheme.typography.titleLarge)
                Row {
                    TextButton(onClick = onRefresh, enabled = !isLoading) { Text("再読込") }
                    TextButton(onClick = onBack) {
                        Text(if (currentPath.isEmpty()) "ホーム" else "戻る")
                    }
                }
            }
            LocationLabel("Voice", currentPath)
            if (isLoading) CircularProgressIndicator(Modifier.size(24.dp))
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
            DlsiteLibraryFilterControls(
                candidates = entries.map {
                    DlsiteFilterCandidate(it.name, metadataByPath[it.path])
                },
                filters = filters,
                availableCategories = AUDIO_FILTER_CATEGORIES,
                onFiltersChange = { filters = it }
            )
            LibrarySortControl(sort) { sort = it }
            FilterChip(
                selected = favOnly,
                onClick = { favOnly = !favOnly },
                label = { Text("お気に入りのみ") }
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filteredEntries = sortByName(
                    entries.filter { entry ->
                        matchesDlsiteLibraryFilter(
                            displayName = entry.name,
                            workInfo = metadataByPath[entry.path],
                            filters = filters
                        ) && (!favOnly || !entry.isDirectory ||
                            isFavorite(favorites, RECENT_TYPE_AUDIO, entry.path))
                    },
                    sort,
                    { it.isDirectory },
                    { it.name }
                )
                if (entries.isEmpty() && !isLoading) {
                    item {
                        EmptyLibraryState(
                            status.ifBlank { "空のフォルダです" },
                            onRefresh
                        )
                    }
                }
                items(filteredEntries) { entry ->
                    Card(Modifier.fillMaxWidth().clickable(enabled = !isLoading) {
                        if (entry.isDirectory) onOpenFolder(entry) else onOpenAudio(entry)
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (entry.isDirectory && findDlsiteProductId(entry.name) != null) {
                                    DlsiteAudioFolderContent(entry)
                                } else {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(entry.name, style = MaterialTheme.typography.titleMedium)
                                        Text(if (entry.isDirectory) "作品フォルダ" else "音声トラック",
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            if (entry.isDirectory) {
                                val isFav = isFavorite(favorites, RECENT_TYPE_AUDIO, entry.path)
                                TextButton(
                                    onClick = {
                                        favorites = toggleFavorite(
                                            favoritesPreferences,
                                            RECENT_TYPE_AUDIO,
                                            entry.path,
                                            entry.name
                                        )
                                    }
                                ) {
                                    Text(if (isFav) "★" else "☆")
                                }
                            }
                        }
                    }
                }
            }
            bottomBar()
        }
    }
}

@Composable
private fun DlsiteLibraryFilterControls(
    candidates: List<DlsiteFilterCandidate>,
    filters: DlsiteLibraryFilters,
    availableCategories: Set<DlsiteFilterCategory>,
    onFiltersChange: (DlsiteLibraryFilters) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    FilterChip(
        selected = filters.activeCount > 0,
        onClick = { showDialog = true },
        label = {
            Text(
                if (filters.activeCount == 0) "絞り込み" else "絞り込み（${filters.activeCount}件の条件）"
            )
        }
    )
    if (showDialog) {
        DlsiteFilterDialog(
            candidates = candidates,
            currentFilters = filters,
            availableCategories = availableCategories,
            onApply = {
                onFiltersChange(it)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

private fun matchesDlsiteLibraryFilter(
    displayName: String,
    workInfo: DlsiteWorkInfo?,
    filters: DlsiteLibraryFilters
): Boolean {
    if (filters.rjOnly && findDlsiteProductId(displayName) == null) return false
    if (filters.circles.isNotEmpty()) {
        val circleName = workInfo?.circleName ?: return false
        if (circleName !in filters.circles) return false
    }
    if (filters.voiceActors.isNotEmpty()) {
        val voiceActors = workInfo?.voiceActors ?: return false
        if (voiceActors.none(filters.voiceActors::contains)) return false
    }
    if (filters.genres.isNotEmpty()) {
        val genres = workInfo?.genres ?: return false
        if (genres.none(filters.genres::contains)) return false
    }
    val releaseYear = workInfo?.releaseDate?.let { Regex("\\d{4}").find(it)?.value }
    if (filters.releaseYears.isNotEmpty() && releaseYear !in filters.releaseYears) return false
    return true
}

@Composable
private fun DlsiteFilterDialog(
    candidates: List<DlsiteFilterCandidate>,
    currentFilters: DlsiteLibraryFilters,
    availableCategories: Set<DlsiteFilterCategory>,
    onApply: (DlsiteLibraryFilters) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(currentFilters) { mutableStateOf(currentFilters) }
    var category by remember { mutableStateOf<DlsiteFilterCategory?>(null) }
    val workInfos = candidates.mapNotNull { it.workInfo }
    val options = remember(workInfos, category) {
        when (category) {
            DlsiteFilterCategory.CIRCLE -> workInfos.map { it.circleName }.filter(String::isNotBlank)
            DlsiteFilterCategory.VOICE_ACTOR -> workInfos.flatMap { it.voiceActors }
            DlsiteFilterCategory.GENRE -> workInfos.flatMap { it.genres }
            DlsiteFilterCategory.RELEASE_YEAR -> workInfos.mapNotNull {
                Regex("\\d{4}").find(it.releaseDate)?.value
            }
            null -> emptyList()
        }.distinct().sorted()
    }

    fun selectedOptions(): Set<String> = when (category) {
        DlsiteFilterCategory.CIRCLE -> draft.circles
        DlsiteFilterCategory.VOICE_ACTOR -> draft.voiceActors
        DlsiteFilterCategory.GENRE -> draft.genres
        DlsiteFilterCategory.RELEASE_YEAR -> draft.releaseYears
        null -> emptySet()
    }

    fun toggleOption(option: String) {
        fun Set<String>.toggled(): Set<String> =
            if (option in this) this - option else this + option
        draft = when (category) {
            DlsiteFilterCategory.CIRCLE -> draft.copy(circles = draft.circles.toggled())
            DlsiteFilterCategory.VOICE_ACTOR -> draft.copy(voiceActors = draft.voiceActors.toggled())
            DlsiteFilterCategory.GENRE -> draft.copy(genres = draft.genres.toggled())
            DlsiteFilterCategory.RELEASE_YEAR ->
                draft.copy(releaseYears = draft.releaseYears.toggled())
            null -> draft
        }
    }

    fun selectedCount(item: DlsiteFilterCategory): Int = when (item) {
        DlsiteFilterCategory.CIRCLE -> draft.circles.size
        DlsiteFilterCategory.VOICE_ACTOR -> draft.voiceActors.size
        DlsiteFilterCategory.GENRE -> draft.genres.size
        DlsiteFilterCategory.RELEASE_YEAR -> draft.releaseYears.size
    }

    fun optionWorkCount(option: String): Int = candidates.count { candidate ->
        val info = candidate.workInfo ?: return@count false
        when (category) {
            DlsiteFilterCategory.CIRCLE -> info.circleName == option
            DlsiteFilterCategory.VOICE_ACTOR -> option in info.voiceActors
            DlsiteFilterCategory.GENRE -> option in info.genres
            DlsiteFilterCategory.RELEASE_YEAR ->
                Regex("\\d{4}").find(info.releaseDate)?.value == option
            null -> false
        }
    }

    val resultCount = candidates.count {
        matchesDlsiteLibraryFilter(it.displayName, it.workInfo, draft)
    }
    val dlsiteBlue = Color(0xFF168ACB)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        if (category == null) onDismiss() else category = null
                    }) { Text(if (category == null) "閉じる" else "‹ 戻る") }
                    Text(
                        category?.label ?: "絞り込み",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                HorizontalDivider()

                if (category == null) {
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    ) {
                        item {
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable { draft = draft.copy(rjOnly = !draft.rjOnly) }
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = draft.rjOnly,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = dlsiteBlue)
                                )
                                Text("RJ作品のみ", modifier = Modifier.weight(1f))
                            }
                            HorizontalDivider()
                        }
                        item {
                            Text(
                                "作品情報から選ぶ",
                                modifier = Modifier.padding(18.dp, 16.dp, 18.dp, 8.dp),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(
                            DlsiteFilterCategory.entries.filter { it in availableCategories }
                        ) { item ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable { category = item }
                                    .padding(horizontal = 20.dp, vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.label, modifier = Modifier.weight(1f))
                                if (selectedCount(item) > 0) {
                                    Text(
                                        "${selectedCount(item)}件選択中  ",
                                        color = dlsiteBlue,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text("›", style = MaterialTheme.typography.titleLarge)
                            }
                            HorizontalDivider()
                        }
                    }
                } else {
                    if (options.isEmpty()) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("選択できる情報がありません")
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                            items(options) { option ->
                                val selected = option in selectedOptions()
                                Row(
                                    Modifier.fillMaxWidth().clickable { toggleOption(option) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = selected,
                                        onCheckedChange = null,
                                        colors = CheckboxDefaults.colors(checkedColor = dlsiteBlue)
                                    )
                                    Text(option, modifier = Modifier.weight(1f))
                                    Text(
                                        "${optionWorkCount(option)}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }

                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { draft = DlsiteLibraryFilters() }
                    ) { Text("条件をクリア") }
                    Button(
                        onClick = { onApply(draft) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = dlsiteBlue)
                    ) {
                        Text("${resultCount}件の作品を表示")
                    }
                }
            }
        }
    }
}

@Composable
private fun DlsiteAudioFolderContent(entry: AudioEntry) {
    val context = LocalContext.current
    val productId = remember(entry.name) { findDlsiteProductId(entry.name).orEmpty() }
    var workInfo by remember(entry.path) { mutableStateOf<DlsiteWorkInfo?>(null) }
    var coverBitmap by remember(entry.path) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(entry.path) { mutableStateOf(true) }

    LaunchedEffect(entry.path) {
        workInfo = loadDlsiteWorkInfo(context, entry.name)
        coverBitmap = workInfo?.let { loadDlsiteCoverBitmap(context, it) }
        isLoading = false
    }

    Row(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(88.dp).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap!!.asImageBitmap(),
                    contentDescription = "DLsite作品画像",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (isLoading) {
                CircularProgressIndicator(Modifier.size(28.dp))
            } else {
                Text("RJ", style = MaterialTheme.typography.titleMedium)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                workInfo?.title ?: entry.name,
                style = MaterialTheme.typography.titleMedium
            )
            workInfo?.circleName?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Text(productId, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun VideoBrowserScreen(
    currentPath: String,
    entries: List<VideoEntry>,
    status: String,
    isLoading: Boolean,
    onOpenFolder: (String) -> Unit,
    onOpenVideo: (VideoEntry) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    var query by rememberSaveable(currentPath) { mutableStateOf("") }
    var sort by rememberSaveable(currentPath) { mutableStateOf(LibrarySort.NAME_ASC) }
    val videoProgress = remember {
        context.getSharedPreferences(VIDEO_PROGRESS_PREFERENCES, Context.MODE_PRIVATE)
    }
    val favoritesPreferences = remember {
        context.getSharedPreferences(FAVORITES_PREFERENCES, Context.MODE_PRIVATE)
    }
    var favorites by remember { mutableStateOf(loadFavorites(favoritesPreferences)) }
    var favOnly by rememberSaveable(currentPath) { mutableStateOf(false) }
    var unwatchedOnly by rememberSaveable(currentPath) { mutableStateOf(false) }
    val searchHistoryPreferences = remember {
        context.getSharedPreferences(SEARCH_HISTORY_PREFERENCES, Context.MODE_PRIVATE)
    }
    var searchHistory by remember {
        mutableStateOf(loadSearchHistory(searchHistoryPreferences, "VIDEO"))
    }
    BackHandler(onBack = onBack)
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ビデオ", style = MaterialTheme.typography.titleLarge)
                Row {
                    TextButton(onClick = onRefresh, enabled = !isLoading) { Text("再読込") }
                    TextButton(onClick = onBack) {
                        Text(if (currentPath.isEmpty()) "ホーム" else "戻る")
                    }
                }
            }
            LocationLabel("video", currentPath)
            if (isLoading) CircularProgressIndicator(Modifier.size(24.dp))
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
            MediaSearchField(
                query,
                { query = it },
                "ビデオ・フォルダを検索",
                history = searchHistory,
                onSearchSubmit = {
                    searchHistory = recordSearchHistory(
                        searchHistoryPreferences, "VIDEO", query
                    )
                },
                onClearHistory = {
                    clearSearchHistory(searchHistoryPreferences, "VIDEO")
                    searchHistory = emptyList()
                }
            )
            LibrarySortControl(sort) { sort = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = favOnly,
                    onClick = { favOnly = !favOnly },
                    label = { Text("お気に入りのみ") }
                )
                FilterChip(
                    selected = unwatchedOnly,
                    onClick = { unwatchedOnly = !unwatchedOnly },
                    label = { Text("未視聴のみ") }
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filteredEntries = sortByName(
                    entries.filter { entry ->
                        matchesSearch(query, entry.name) &&
                            (!favOnly || entry.isDirectory ||
                                isFavorite(favorites, RECENT_TYPE_VIDEO, entry.path)) &&
                            (!unwatchedOnly || entry.isDirectory ||
                                videoProgress.getLong(videoProgressKey(entry.path), 0L) <= 0L)
                    },
                    sort,
                    { it.isDirectory },
                    { it.name }
                )
                if (entries.isEmpty() && !isLoading) {
                    item {
                        EmptyLibraryState(
                            status.ifBlank { "空のフォルダです" },
                            onRefresh
                        )
                    }
                }
                if (filteredEntries.isEmpty() && entries.isNotEmpty()) {
                    item { EmptySearchResult(onClear = { query = "" }) }
                }
                items(filteredEntries) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isLoading) {
                                if (entry.isDirectory) onOpenFolder(entry.path)
                                else onOpenVideo(entry)
                            }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.padding(16.dp).weight(1f)) {
                                Text(entry.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (entry.isDirectory) "フォルダ" else "動画",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (!entry.isDirectory) {
                                    val savedPosition = videoProgress.getLong(
                                        videoProgressKey(entry.path),
                                        0L
                                    )
                                    if (savedPosition > 0L) {
                                        Text(
                                            "続きから ${formatPlaybackTime(savedPosition)}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                            if (!entry.isDirectory) {
                                val isFav = isFavorite(favorites, RECENT_TYPE_VIDEO, entry.path)
                                TextButton(
                                    onClick = {
                                        favorites = toggleFavorite(
                                            favoritesPreferences,
                                            RECENT_TYPE_VIDEO,
                                            entry.path,
                                            entry.name
                                        )
                                    }
                                ) {
                                    Text(if (isFav) "★" else "☆")
                                }
                            }
                        }
                    }
                }
            }
            bottomBar()
        }
    }
}

@Composable
private fun VideoPlayerScreen(
    videoPath: String,
    server: String,
    username: String,
    password: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val videoProgress = remember {
        context.getSharedPreferences(VIDEO_PROGRESS_PREFERENCES, Context.MODE_PRIVATE)
    }
    val player = remember(videoPath, server, username, password) {
        val dataSourceFactory = SmbVideoDataSource.Factory(
            server = server,
            username = username,
            password = password,
            shareName = "video",
            remotePath = videoPath
        )
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(
                MediaItem.fromUri(
                    Uri.Builder()
                        .scheme("smb")
                        .authority("video")
                        .encodedPath(videoPath.replace('\\', '/'))
                        .build()
                )
            )
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(mediaSource)
            val savedPosition = videoProgress.getLong(videoProgressKey(videoPath), 0L)
            if (savedPosition > 0L) seekTo(savedPosition)
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(player, videoPath) {
        while (true) {
            delay(VIDEO_PROGRESS_SAVE_INTERVAL_MILLIS)
            saveVideoProgress(videoProgress, videoPath, player)
        }
    }

    BackHandler(onBack = onBack)
    DisposableEffect(activity, player) {
        val controller = activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView)
        }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    videoProgress.edit().remove(videoProgressKey(videoPath)).apply()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            saveVideoProgress(videoProgress, videoPath, player)
            player.removeListener(listener)
            player.release()
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    AndroidView(
        factory = { playerContext ->
            PlayerView(playerContext).apply {
                this.player = player
                useController = true
            }
        },
        update = { it.player = player },
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    )
}

@Composable
private fun PhotoBrowserScreen(
    currentPath: String,
    entries: List<PhotoEntry>,
    status: String,
    isLoading: Boolean,
    server: String,
    username: String,
    password: String,
    cacheDirectory: LocalFile,
    onOpenFolder: (String) -> Unit,
    onOpenPhoto: (PhotoEntry) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {}
) {
    val thumbnailSemaphore = remember { Semaphore(3) }
    var query by rememberSaveable(currentPath) { mutableStateOf("") }
    var sort by rememberSaveable(currentPath) { mutableStateOf(LibrarySort.NAME_ASC) }
    val context = LocalContext.current
    val searchHistoryPreferences = remember {
        context.getSharedPreferences(SEARCH_HISTORY_PREFERENCES, Context.MODE_PRIVATE)
    }
    var searchHistory by remember {
        mutableStateOf(loadSearchHistory(searchHistoryPreferences, "PHOTOS"))
    }
    BackHandler(onBack = onBack)
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("写真", style = MaterialTheme.typography.titleLarge)
                Row {
                    TextButton(onClick = onRefresh, enabled = !isLoading) { Text("再読込") }
                    TextButton(onClick = onBack) {
                        Text(if (currentPath.isEmpty()) "ホーム" else "戻る")
                    }
                }
            }
            LocationLabel("Picture", currentPath)
            if (isLoading) CircularProgressIndicator(Modifier.size(24.dp))
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
            MediaSearchField(
                query,
                { query = it },
                "写真・フォルダを検索",
                history = searchHistory,
                onSearchSubmit = {
                    searchHistory = recordSearchHistory(
                        searchHistoryPreferences, "PHOTOS", query
                    )
                },
                onClearHistory = {
                    clearSearchHistory(searchHistoryPreferences, "PHOTOS")
                    searchHistory = emptyList()
                }
            )
            LibrarySortControl(sort) { sort = it }

            LazyVerticalGrid(
                modifier = Modifier.weight(1f),
                columns = GridCells.Adaptive(140.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val filteredEntries = sortByName(
                    entries.filter { matchesSearch(query, it.name) },
                    sort,
                    { it.isDirectory },
                    { it.name }
                )
                if (entries.isEmpty() && !isLoading) {
                    item {
                        EmptyLibraryState(
                            status.ifBlank { "空のフォルダです" },
                            onRefresh
                        )
                    }
                }
                if (filteredEntries.isEmpty() && entries.isNotEmpty()) {
                    item { EmptySearchResult(onClear = { query = "" }) }
                }
                gridItems(filteredEntries) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isLoading) {
                                if (entry.isDirectory) {
                                    onOpenFolder(entry.path)
                                } else {
                                    onOpenPhoto(entry)
                                }
                            }
                    ) {
                        Column {
                            if (entry.isDirectory) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("フォルダ", style = MaterialTheme.typography.titleLarge)
                                }
                            } else {
                                PhotoThumbnail(
                                    entry = entry,
                                    server = server,
                                    username = username,
                                    password = password,
                                    cacheDirectory = cacheDirectory,
                                    semaphore = thumbnailSemaphore
                                )
                            }
                            Text(
                                entry.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
            bottomBar()
        }
    }
}

@Composable
private fun PhotoThumbnail(
    entry: PhotoEntry,
    server: String,
    username: String,
    password: String,
    cacheDirectory: LocalFile,
    semaphore: Semaphore
) {
    var thumbnail by remember(entry.path) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(entry.path) { mutableStateOf(false) }

    LaunchedEffect(entry.path) {
        runCatching {
            withContext(Dispatchers.IO) {
                semaphore.withPermit {
                    loadPhotoBitmap(
                        server = server,
                        username = username,
                        password = password,
                        photoPath = entry.path,
                        cacheDirectory = cacheDirectory,
                        maxSide = 420
                    )
                }
            }
        }.onSuccess { thumbnail = it }
            .onFailure { failed = true }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            thumbnail != null -> Image(
                bitmap = thumbnail!!.asImageBitmap(),
                contentDescription = entry.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            failed -> Text("表示できません", style = MaterialTheme.typography.bodySmall)
            else -> CircularProgressIndicator()
        }
    }
}

@Composable
private fun PhotoViewerScreen(
    gallery: PhotoGallery,
    server: String,
    username: String,
    password: String,
    cacheDirectory: LocalFile,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activity = LocalContext.current as? Activity
    var currentIndex by remember(gallery) { mutableIntStateOf(gallery.initialIndex) }
    var bitmap by remember(gallery) { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember(gallery) { mutableStateOf<String?>(null) }
    var showTitle by remember(gallery) { mutableStateOf(true) }
    val titleAlpha by animateFloatAsState(
        targetValue = if (showTitle) 1f else 0f,
        animationSpec = tween(TITLE_AND_GUIDE_FADE_MILLIS),
        label = "photoTitleAlpha"
    )

    BackHandler(onBack = onBack)
    DisposableEffect(activity) {
        val controller = activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView)
        }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    LaunchedEffect(gallery) {
        delay(TITLE_AND_GUIDE_VISIBLE_MILLIS)
        showTitle = false
    }
    LaunchedEffect(gallery, currentIndex) {
        bitmap = null
        errorMessage = null
        runCatching {
            withContext(Dispatchers.IO) {
                loadPhotoBitmap(
                    server = server,
                    username = username,
                    password = password,
                    photoPath = gallery.photoPaths[currentIndex],
                    cacheDirectory = cacheDirectory
                )
            }
        }.onSuccess { bitmap = it }
            .onFailure { errorMessage = "写真を読み込めませんでした" }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        bitmap?.let { photo ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentIndex, gallery.photoPaths.size) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                totalDrag += dragAmount
                            },
                            onDragEnd = {
                                when {
                                    totalDrag < -SWIPE_THRESHOLD_PX &&
                                        currentIndex < gallery.photoPaths.lastIndex -> currentIndex++
                                    totalDrag > SWIPE_THRESHOLD_PX &&
                                        currentIndex > 0 -> currentIndex--
                                }
                            }
                        )
                    }
            ) {
                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = photoFileName(gallery.photoPaths[currentIndex]),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = currentIndex > 0,
                                onClick = { currentIndex-- }
                            )
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = currentIndex < gallery.photoPaths.lastIndex,
                                onClick = { currentIndex++ }
                            )
                    )
                }
            }
        } ?: run {
            if (errorMessage != null) {
                Text(
                    errorMessage!!,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }

        Text(
            "${currentIndex + 1} / ${gallery.photoPaths.size}",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
        Text(
            photoFileName(gallery.photoPaths[currentIndex]),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .alpha(titleAlpha)
                .background(Color.Black.copy(alpha = 0.62f))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp)
        )
    }
}

@Composable
private fun MangaReaderScreen(
    book: MangaBook,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activity = LocalContext.current as? Activity
    var currentPage by remember(book) {
        mutableIntStateOf(initialPage.coerceIn(0, book.pageNames.lastIndex))
    }
    var bitmap by remember(book) { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember(book) { mutableStateOf<String?>(null) }
    var showTitleAndGuide by remember(book) { mutableStateOf(true) }
    var showPageJumpDialog by remember(book) { mutableStateOf(false) }
    val pageCache = remember(book) { mutableMapOf<Int, Bitmap>() }
    val titleAndGuideAlpha by animateFloatAsState(
        targetValue = if (showTitleAndGuide) 1f else 0f,
        animationSpec = tween(TITLE_AND_GUIDE_FADE_MILLIS),
        label = "titleAndGuideAlpha"
    )

    BackHandler(onBack = onBack)

    // 音量キーでページ送り（音量＋＝次、音量－＝前）。リーダーを閉じると解除される
    DisposableEffect(book) {
        MangaVolumeKeyHandler.handler = { next ->
            if (next) {
                if (currentPage < book.pageNames.lastIndex) currentPage++
            } else if (currentPage > 0) {
                currentPage--
            }
        }
        onDispose { MangaVolumeKeyHandler.handler = null }
    }

    DisposableEffect(activity) {
        val insetsController = activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView)
        }
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(book) {
        delay(TITLE_AND_GUIDE_VISIBLE_MILLIS)
        showTitleAndGuide = false
    }

    LaunchedEffect(book, currentPage) {
        val requestedPage = currentPage
        onPageChanged(requestedPage)
        errorMessage = null
        bitmap = pageCache[requestedPage]
        runCatching {
            pageCache[requestedPage] ?: withContext(Dispatchers.IO) {
                readPage(book, requestedPage)
            }
        }.onSuccess {
            pageCache[requestedPage] = it
            bitmap = it

            val pagesToKeep = listOf(requestedPage - 1, requestedPage, requestedPage + 1)
                .filter { page -> page in book.pageNames.indices }
                .toSet()
            pageCache.keys
                .filterNot { page -> page in pagesToKeep }
                .toList()
                .forEach { page -> pageCache.remove(page) }

            pagesToKeep
                .filterNot { page -> pageCache.containsKey(page) }
                .forEach { page ->
                    runCatching {
                        withContext(Dispatchers.IO) { readPage(book, page) }
                    }.onSuccess { preloaded ->
                        pageCache[page] = preloaded
                    }
                }
        }.onFailure {
            errorMessage = "このページを読み取れませんでした"
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            bitmap != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentPage, book.pageNames.size) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                totalDrag += dragAmount
                            },
                            onDragEnd = {
                                when {
                                    totalDrag < -SWIPE_THRESHOLD_PX &&
                                        currentPage < book.pageNames.lastIndex -> currentPage++
                                    totalDrag > SWIPE_THRESHOLD_PX &&
                                        currentPage > 0 -> currentPage--
                                }
                            }
                        )
                    }
            ) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "${book.zipName}の${currentPage + 1}ページ目",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = currentPage > 0,
                                onClick = { currentPage-- }
                            )
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = currentPage < book.pageNames.lastIndex,
                                onClick = { currentPage++ }
                            )
                    )
                }
            }
            errorMessage != null -> Text(
                errorMessage!!,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
            else -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .alpha(titleAndGuideAlpha)
                .background(Color.Black.copy(alpha = 0.62f))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                book.zipName,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "左：前　右：次",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Text(
            "${currentPage + 1} / ${book.pageNames.size}",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable {
                    showPageJumpDialog = true
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )

        if (showPageJumpDialog) {
            val pageListState = rememberLazyListState(
                initialFirstVisibleItemIndex = (currentPage - 3).coerceAtLeast(0)
            )
            AlertDialog(
                onDismissRequest = { showPageJumpDialog = false },
                title = { Text("ページ移動 · ${book.pageNames.size}ページ") },
                text = {
                    LazyColumn(state = pageListState) {
                        itemsIndexed(book.pageNames) { index, _ ->
                            val isCurrentPage = index == currentPage
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentPage = index
                                        showPageJumpDialog = false
                                    },
                                color = if (isCurrentPage) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                }
                            ) {
                                Text(
                                    text = if (isCurrentPage) {
                                        "${index + 1}ページ（現在）"
                                    } else {
                                        "${index + 1}ページ"
                                    },
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPageJumpDialog = false }) {
                        Text("閉じる")
                    }
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NasConnectionPreview() {
    NASMediaTheme {
        NasConnectionScreen(LocalFile("."))
    }
}
