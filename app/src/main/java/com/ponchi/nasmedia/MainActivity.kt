@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ponchi.nasmedia

import android.app.Activity
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import kotlinx.coroutines.Dispatchers
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

private data class MangaBook(
    val zipName: String,
    val cachedZip: LocalFile,
    val pageNames: List<String>,
    val charset: Charset
)

private data class PhotoEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean
)

private data class PhotoGallery(
    val folderPath: String,
    val photoPaths: List<String>,
    val initialIndex: Int
)

private data class VideoEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean
)

private data class AudioEntry(val name: String, val path: String, val isDirectory: Boolean)
private data class AudioPlaylist(
    val folderPath: String,
    val paths: List<String>,
    val initialIndex: Int
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

@Composable
private fun NasConnectionScreen(
    cacheDirectory: LocalFile,
    modifier: Modifier = Modifier
) {
    var server by remember { mutableStateOf("192.168.40.2") }
    var username by remember { mutableStateOf("media_app") }
    var password by remember { mutableStateOf("") }
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
    var openedAudioDetails by remember { mutableStateOf<AudioEntry?>(null) }
    var openedAudioPlaylist by remember { mutableStateOf<AudioPlaylist?>(null) }
    var status by remember { mutableStateOf("接続情報を入力してください") }
    var isLoading by remember { mutableStateOf(false) }
    var rememberLogin by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var hasSavedCredentials by remember {
        mutableStateOf(SecureNasCredentials.hasSaved(context))
    }
    val readingProgress = remember {
        context.getSharedPreferences(READING_PROGRESS_PREFERENCES, Context.MODE_PRIVATE)
    }
    val mangaCacheMetadata = remember {
        context.getSharedPreferences(MANGA_CACHE_PREFERENCES, Context.MODE_PRIVATE)
    }

    fun connectToNas() {
        if (server.isBlank() || username.isBlank() || password.isBlank()) {
            status = "すべて入力してください"
            return
        }
        isLoading = true
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
                status = if (files.isEmpty()) {
                    "接続成功。Manga直下にZIPはありません"
                } else {
                    "接続成功：${files.size}件。読みたいZIPをタップしてください"
                }
            }.onFailure {
                status = "接続できませんでした。IP・ユーザー名・パスワードを確認してください"
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
            status = "保存情報を読み込めなかったため削除しました"
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

    fun openPhotoDirectory(path: String) {
        photoPath = path
        photoEntries = emptyList()
        photoStatus = "読み込み中…"
        photoIsLoading = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    loadPhotoEntries(server.trim(), username.trim(), password, path)
                }
            }.onSuccess { entries ->
                photoEntries = entries
                photoStatus = if (entries.isEmpty()) "このフォルダは空です" else ""
            }.onFailure {
                photoStatus = "写真フォルダを読み込めませんでした"
            }
            photoIsLoading = false
        }
    }

    fun openVideoDirectory(path: String) {
        videoPath = path
        videoEntries = emptyList()
        videoStatus = "読み込み中…"
        videoIsLoading = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    loadVideoEntries(server.trim(), username.trim(), password, path)
                }
            }.onSuccess { entries ->
                videoEntries = entries
                videoStatus = if (entries.isEmpty()) "このフォルダは空です" else ""
            }.onFailure {
                videoStatus = "ビデオフォルダを読み込めませんでした"
            }
            videoIsLoading = false
        }
    }

    fun openAudioDirectory(path: String) {
        audioPath = path
        audioEntries = emptyList()
        audioStatus = "読み込み中…"
        audioIsLoading = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                loadAudioEntries(server.trim(), username.trim(), password, path)
            } }.onSuccess {
                audioEntries = it
                audioStatus = if (it.isEmpty()) "このフォルダは空です" else ""
            }.onFailure { audioStatus = "音声フォルダを読み込めませんでした" }
            audioIsLoading = false
        }
    }

    openedAudioPlaylist?.let { playlist ->
        AudioPlayerScreen(
            playlist, server, username, password,
            onBack = { openedAudioPlaylist = null }, modifier = modifier
        )
        return
    }

    openedAudioDetails?.let { entry ->
        DlsiteWorkDetailScreen(
            entry = entry,
            onOpenFolder = {
                openedAudioDetails = null
                openAudioDirectory(entry.path)
            },
            onBack = { openedAudioDetails = null },
            modifier = modifier
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
        MediaHomeScreen(
            server = server,
            hasSavedCredentials = hasSavedCredentials,
            onSelect = { section ->
                selectedSection = section
                if (section == MediaSection.PHOTOS) openPhotoDirectory("")
                if (section == MediaSection.VIDEOS) openVideoDirectory("")
                if (section == MediaSection.AUDIO) openAudioDirectory("")
            },
            onForgetCredentials = {
                scope.launch {
                    withContext(Dispatchers.IO) { SecureNasCredentials.clear(context) }
                    hasSavedCredentials = false
                    rememberLogin = false
                    password = ""
                    isConnected = false
                    selectedSection = null
                    status = "保存したログイン情報を削除しました"
                }
            },
            modifier = modifier
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
            onOpenVideo = { openedVideoPath = it.path },
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
            onOpenFolder = { entry ->
                if (findDlsiteProductId(entry.name) != null) {
                    openedAudioDetails = entry
                } else {
                    openAudioDirectory(entry.path)
                }
            },
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
                val tracks = audioEntries.filterNot { it.isDirectory }
                openedAudioPlaylist = AudioPlaylist(
                    folderPath = audioPath,
                    paths = tracks.map { it.path },
                    initialIndex = tracks.indexOfFirst { it.path == selected.path }.coerceAtLeast(0)
                )
            },
            onBack = {
                if (audioPath.isEmpty()) selectedSection = null
                else openAudioDirectory(parentSmbPath(audioPath))
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        if (!isConnected) {
            Text("NAS Media", style = MaterialTheme.typography.headlineMedium)
            Text("自宅のNASへ接続")

        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("NASのIPアドレス") },
            singleLine = true
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("専用ユーザー名") },
            singleLine = true
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("パスワード") },
            visualTransformation = PasswordVisualTransformation(),
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
                onClick = ::connectToNas,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("NASに接続")
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
                Text("マンガ", style = MaterialTheme.typography.headlineMedium)
                Button(onClick = { selectedSection = null }) {
                    Text("ホーム")
                }
            }
        }

        if (isLoading) {
            CircularProgressIndicator()
        }
        Text(status)

            if (isConnected && selectedSection == MediaSection.MANGA) {
                DlsiteLibraryFilterControls(
                    candidates = mangaFiles.map {
                        DlsiteFilterCandidate(it, mangaMetadata[it])
                    },
                    filters = mangaFilters,
                    onFiltersChange = { mangaFilters = it }
                )
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val filteredMangaFiles = mangaFiles.filter { fileName ->
                    matchesDlsiteLibraryFilter(
                        displayName = fileName,
                        workInfo = mangaMetadata[fileName],
                        filters = mangaFilters
                    )
                }
                items(filteredMangaFiles) { fileName ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isLoading) {
                            isLoading = true
                            status = "「$fileName」を読み込み中…"
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        downloadAndOpenManga(
                                            server = server.trim(),
                                            username = username.trim(),
                                            password = password,
                                            zipName = fileName,
                                            cacheDirectory = cacheDirectory,
                                            cacheMetadata = mangaCacheMetadata
                                        )
                                    }
                                }.onSuccess {
                                    readingProgress.edit()
                                        .putInt(pageCountKey(it.zipName), it.pageNames.size)
                                        .apply()
                                    openedBook = it
                                    status = "接続成功"
                                }.onFailure {
                                    status = "画像を開けませんでした。ZIP内にJPG・PNG・WEBPがあるか確認してください"
                                }
                                isLoading = false
                            }
                            }
                    ) {
                        MangaLibraryCardContent(
                            fileName = fileName,
                            cacheDirectory = cacheDirectory,
                            progressLabel = savedProgressLabel(readingProgress, fileName)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaLibraryCardContent(
    fileName: String,
    cacheDirectory: LocalFile,
    progressLabel: String?
) {
    val context = LocalContext.current
    val productId = remember(fileName) { findDlsiteProductId(fileName) }
    var workInfo by remember(fileName) { mutableStateOf<DlsiteWorkInfo?>(null) }
    var thumbnail by remember(fileName) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(fileName) { mutableStateOf(true) }

    LaunchedEffect(fileName) {
        if (productId != null) {
            workInfo = loadDlsiteWorkInfo(context, fileName)
            thumbnail = workInfo?.let { loadDlsiteCoverBitmap(context, it) }
        }
        if (thumbnail == null) {
            thumbnail = withContext(Dispatchers.IO) {
                loadCachedMangaThumbnail(cacheDirectory, fileName)
            }
        }
        isLoading = false
    }

    Row(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.width(92.dp).height(128.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = "マンガ表紙",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (isLoading) {
                CircularProgressIndicator(Modifier.size(28.dp))
            } else {
                Text("表紙なし", style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                workInfo?.title ?: fileName.substringBeforeLast('.'),
                style = MaterialTheme.typography.titleMedium
            )
            workInfo?.circleName?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            productId?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            progressLabel?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MediaHomeScreen(
    server: String,
    hasSavedCredentials: Boolean,
    onSelect: (MediaSection) -> Unit,
    onForgetCredentials: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("NAS Media", style = MaterialTheme.typography.headlineMedium)
            Text("DS223j（$server）に接続済み")
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                gridItems(MediaSection.entries) { section ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clickable { onSelect(section) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(section.title, style = MaterialTheme.typography.titleLarge)
                            Text(
                                section.description,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            if (hasSavedCredentials) {
                TextButton(
                    onClick = onForgetCredentials,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存情報を削除してログアウト")
                }
            }
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
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(section.title, style = MaterialTheme.typography.headlineMedium)
            Text("この機能は次に実装します")
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var filters by remember(currentPath) { mutableStateOf(DlsiteLibraryFilters()) }
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
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("音声", style = MaterialTheme.typography.headlineMedium)
                Button(onClick = onBack) { Text(if (currentPath.isEmpty()) "ホーム" else "上のフォルダ") }
            }
            Text(if (currentPath.isEmpty()) "Voice" else "Voice\\$currentPath")
            if (isLoading) CircularProgressIndicator()
            if (status.isNotEmpty()) Text(status)
            DlsiteLibraryFilterControls(
                candidates = entries.map {
                    DlsiteFilterCandidate(it.name, metadataByPath[it.path])
                },
                filters = filters,
                onFiltersChange = { filters = it }
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val filteredEntries = entries.filter { entry ->
                    matchesDlsiteLibraryFilter(
                        displayName = entry.name,
                        workInfo = metadataByPath[entry.path],
                        filters = filters
                    )
                }
                items(filteredEntries) { entry ->
                    Card(Modifier.fillMaxWidth().clickable(enabled = !isLoading) {
                        if (entry.isDirectory) onOpenFolder(entry) else onOpenAudio(entry)
                    }) {
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
                }
            }
        }
    }
}

@Composable
private fun DlsiteLibraryFilterControls(
    candidates: List<DlsiteFilterCandidate>,
    filters: DlsiteLibraryFilters,
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
                    Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp),
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
                        items(DlsiteFilterCategory.entries) { item ->
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
private fun DlsiteWorkDetailScreen(
    entry: AudioEntry,
    onOpenFolder: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var workInfo by remember(entry.path) { mutableStateOf<DlsiteWorkInfo?>(null) }
    var coverBitmap by remember(entry.path) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(entry.path) { mutableStateOf(true) }

    LaunchedEffect(entry.path) {
        workInfo = loadDlsiteWorkInfo(context, entry.name)
        coverBitmap = workInfo?.let { loadDlsiteCoverBitmap(context, it) }
        isLoading = false
    }
    BackHandler(onBack = onBack)

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("作品情報", style = MaterialTheme.typography.headlineMedium)
                Button(onClick = onBack) { Text("戻る") }
            }

            Box(
                Modifier.fillMaxWidth().aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap!!.asImageBitmap(),
                        contentDescription = "DLsite作品画像",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text("作品画像なし", style = MaterialTheme.typography.titleLarge)
                }
            }

            Text(
                workInfo?.title ?: entry.name,
                style = MaterialTheme.typography.headlineSmall
            )
            workInfo?.circleName?.takeIf(String::isNotBlank)?.let {
                DetailValue("サークル", it)
            }
            DetailValue("作品番号", findDlsiteProductId(entry.name).orEmpty())
            workInfo?.releaseDate?.takeIf(String::isNotBlank)?.let {
                DetailValue("発売日", it)
            }
            workInfo?.voiceActors?.takeIf(List<String>::isNotEmpty)?.let {
                DetailValue("声優", it.joinToString(" / "))
            }
            workInfo?.genres?.takeIf(List<String>::isNotEmpty)?.let {
                DetailValue("ジャンル", it.joinToString("・"))
            }
            workInfo?.description?.takeIf(String::isNotBlank)?.let {
                Text("作品紹介", style = MaterialTheme.typography.titleMedium)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            Button(onClick = onOpenFolder, modifier = Modifier.fillMaxWidth()) {
                Text("トラック一覧へ")
            }
            workInfo?.productUrl?.takeIf(String::isNotBlank)?.let { productUrl ->
                TextButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(productUrl)))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("DLsiteで見る")
                }
            }
        }
    }
}

@Composable
private fun DetailValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
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
private fun AudioPlayerScreen(
    playlist: AudioPlaylist,
    server: String,
    username: String,
    password: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentIndex by remember(playlist) { mutableIntStateOf(playlist.initialIndex) }
    var position by remember(playlist) { mutableStateOf(0L) }
    var duration by remember(playlist) { mutableStateOf(0L) }
    var isPlaying by remember(playlist) { mutableStateOf(false) }
    var volume by remember(playlist) { mutableStateOf(1f) }
    var coverBitmap by remember(playlist) { mutableStateOf<Bitmap?>(null) }
    val controllerFuture = remember(playlist, server, username, password) {
        AudioPlaybackRequestStore.request = AudioPlaybackRequest(
            server,
            username,
            password,
            playlist.folderPath,
            playlist.paths,
            playlist.initialIndex
        )
        ContextCompat.startForegroundService(
            context,
            Intent(context, AudioPlaybackService::class.java)
                .setAction(AudioPlaybackService.ACTION_LOAD_PLAYLIST)
        )
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
        while (true) {
            currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0L } ?: 0L
            isPlaying = player.isPlaying
            volume = player.volume
            delay(250L)
        }
    }
    BackHandler(onBack = onBack)
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                TextButton(onClick = { player.seekTo((player.currentPosition - 5_000L).coerceAtLeast(0L)) }) {
                    Text("↶5")
                }
                Button(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                    Text(if (isPlaying) "一時停止" else "再生")
                }
                TextButton(onClick = { player.seekTo(player.currentPosition + 30_000L) }) {
                    Text("30↷")
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val videoProgress = remember {
        context.getSharedPreferences(VIDEO_PROGRESS_PREFERENCES, Context.MODE_PRIVATE)
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ビデオ", style = MaterialTheme.typography.headlineMedium)
                Button(onClick = onBack) {
                    Text(if (currentPath.isEmpty()) "ホーム" else "上のフォルダ")
                }
            }
            Text(if (currentPath.isEmpty()) "video" else "video\\$currentPath")
            if (isLoading) CircularProgressIndicator()
            if (status.isNotEmpty()) Text(status)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isLoading) {
                                if (entry.isDirectory) onOpenFolder(entry.path)
                                else onOpenVideo(entry)
                            }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
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
                    }
                }
            }
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbnailSemaphore = remember { Semaphore(3) }
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("写真", style = MaterialTheme.typography.headlineMedium)
                Button(onClick = onBack) {
                    Text(if (currentPath.isEmpty()) "ホーム" else "上のフォルダ")
                }
            }
            Text(if (currentPath.isEmpty()) "Picture" else "Picture\\$currentPath")
            if (isLoading) CircularProgressIndicator()
            if (status.isNotEmpty()) Text(status)

            LazyVerticalGrid(
                modifier = Modifier.weight(1f),
                columns = GridCells.Adaptive(140.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                gridItems(entries) { entry ->
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
                "左へ払う／右半分タップ：次　右へ払う／左半分タップ：前",
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
                title = { Text("ページを選択（全${book.pageNames.size}ページ）") },
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

private fun loadAudioEntries(
    server: String, username: String, password: String, path: String
): List<AudioEntry> = withDiskShare(server, username, password, "Voice") { share ->
    share.list(path).asSequence()
        .filterNot { it.fileName == "." || it.fileName == ".." }
        .mapNotNull { info ->
            val directory = info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
            val audio = info.fileName.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
            if (!directory && !audio) return@mapNotNull null
            AudioEntry(info.fileName, joinSmbPath(path, info.fileName), directory)
        }
        .sortedWith { left, right ->
            when {
                left.isDirectory && !right.isDirectory -> -1
                !left.isDirectory && right.isDirectory -> 1
                else -> compareNaturally(left.name, right.name)
            }
        }.toList()
}

internal fun loadAudioCoverBitmap(
    server: String,
    username: String,
    password: String,
    folderPath: String,
    cacheDirectory: LocalFile
): Bitmap? = withDiskShare(server, username, password, "Voice") { share ->
    val candidates = share.list(folderPath)
        .filterNot { it.fileName == "." || it.fileName == ".." }
        .filter { it.fileName.substringAfterLast('.', "").lowercase() in PHOTO_EXTENSIONS }
        .sortedBy { info ->
            val name = info.fileName.substringBeforeLast('.').lowercase()
            when (name) {
                "cover" -> 0
                "folder" -> 1
                "front" -> 2
                else -> 3
            }
        }
    val cover = candidates.firstOrNull() ?: return@withDiskShare null
    val remotePath = joinSmbPath(folderPath, cover.fileName)
    val info = share.getFileInformation(remotePath)
    val size = info.standardInformation.endOfFile
    val cacheFolder = LocalFile(cacheDirectory, "audio-cover-cache").apply { mkdirs() }
    val cached = LocalFile(cacheFolder, "cover-${remotePath.hashCode()}")
    if (!cached.isFile || cached.length() != size) {
        downloadRemoteFile(share, remotePath, cached, size)
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(cached.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withDiskShare null
    BitmapFactory.decodeFile(cached.absolutePath, BitmapFactory.Options().apply {
        inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, 1400)
    })
}

private fun loadVideoEntries(
    server: String,
    username: String,
    password: String,
    path: String
): List<VideoEntry> = withDiskShare(server, username, password, "video") { share ->
    share.list(path)
        .asSequence()
        .filterNot { it.fileName == "." || it.fileName == ".." }
        .mapNotNull { information ->
            val isDirectory = information.fileAttributes and
                FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
            val isVideo = information.fileName.substringAfterLast('.', "")
                .lowercase() in VIDEO_EXTENSIONS
            if (!isDirectory && !isVideo) return@mapNotNull null
            VideoEntry(
                name = information.fileName,
                path = joinSmbPath(path, information.fileName),
                isDirectory = isDirectory
            )
        }
        .sortedWith { left, right ->
            when {
                left.isDirectory && !right.isDirectory -> -1
                !left.isDirectory && right.isDirectory -> 1
                else -> compareNaturally(left.name, right.name)
            }
        }
        .toList()
}

private fun loadPhotoEntries(
    server: String,
    username: String,
    password: String,
    path: String
): List<PhotoEntry> = withDiskShare(server, username, password, "Picture") { share ->
    share.list(path)
        .asSequence()
        .filterNot { it.fileName == "." || it.fileName == ".." }
        .mapNotNull { information ->
            val isDirectory = information.fileAttributes and
                FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
            val isPhoto = information.fileName.substringAfterLast('.', "")
                .lowercase() in PHOTO_EXTENSIONS
            if (!isDirectory && !isPhoto) return@mapNotNull null
            PhotoEntry(
                name = information.fileName,
                path = joinSmbPath(path, information.fileName),
                isDirectory = isDirectory
            )
        }
        .sortedWith { left, right ->
            when {
                left.isDirectory && !right.isDirectory -> -1
                !left.isDirectory && right.isDirectory -> 1
                else -> compareNaturally(left.name, right.name)
            }
        }
        .toList()
}

private fun loadPhotoBitmap(
    server: String,
    username: String,
    password: String,
    photoPath: String,
    cacheDirectory: LocalFile,
    maxSide: Int = 2560
): Bitmap = withDiskShare(server, username, password, "Picture") { share ->
    val information = share.getFileInformation(photoPath)
    val remoteSize = information.standardInformation.endOfFile
    val remoteModified = information.basicInformation.lastWriteTime.toEpochMillis()
    val photoCache = LocalFile(cacheDirectory, "photo-cache").apply { mkdirs() }
    val cachedPhoto = LocalFile(photoCache, "photo-${photoPath.hashCode()}")

    if (!cachedPhoto.isFile ||
        cachedPhoto.length() != remoteSize ||
        cachedPhoto.lastModified() != remoteModified
    ) {
        downloadRemoteFile(share, photoPath, cachedPhoto, remoteSize)
        cachedPhoto.setLastModified(remoteModified)
    }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(cachedPhoto.absolutePath, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "写真を読み取れません" }
    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
    }
    BitmapFactory.decodeFile(cachedPhoto.absolutePath, options)
        ?: error("写真を読み取れません")
}

private fun joinSmbPath(parent: String, child: String): String {
    return if (parent.isEmpty()) child else "$parent\\$child"
}

private fun parentSmbPath(path: String): String = path.substringBeforeLast('\\', "")

private fun photoFileName(path: String): String = path.substringAfterLast('\\')

private fun loadMangaZipNames(
    server: String,
    username: String,
    password: String
): List<String> = withMangaShare(server, username, password) { share ->
    share.list("")
        .map { it.fileName }
        .filter { it.endsWith(".zip", ignoreCase = true) }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
}

private fun downloadAndOpenManga(
    server: String,
    username: String,
    password: String,
    zipName: String,
    cacheDirectory: LocalFile,
    cacheMetadata: SharedPreferences
): MangaBook {
    val previewDirectory = LocalFile(cacheDirectory, "manga-preview").apply { mkdirs() }
    val cachedZip = LocalFile(previewDirectory, "${zipName.hashCode()}.zip")

    return withMangaShare(server, username, password) { share ->
        val information = share.getFileInformation(zipName)
        val remoteSize = information.standardInformation.endOfFile
        val remoteModified = information.basicInformation.lastWriteTime.toEpochMillis()
        val cacheIsCurrent = cachedZip.isFile &&
            cachedZip.length() == remoteSize &&
            cacheMetadata.getLong(cacheSizeKey(zipName), -1L) == remoteSize &&
            cacheMetadata.getLong(cacheModifiedKey(zipName), -1L) == remoteModified

        var downloadedNow = false
        if (!cacheIsCurrent) {
            downloadZip(share, zipName, cachedZip, remoteSize)
            saveCacheMetadata(cacheMetadata, zipName, remoteSize, remoteModified)
            downloadedNow = true
        }

        runCatching {
            readMangaBook(cachedZip, zipName)
        }.recoverCatching {
            if (downloadedNow) throw it
            cachedZip.delete()
            downloadZip(share, zipName, cachedZip, remoteSize)
            saveCacheMetadata(cacheMetadata, zipName, remoteSize, remoteModified)
            readMangaBook(cachedZip, zipName)
        }.getOrThrow()
    }
}

private fun downloadZip(
    share: DiskShare,
    zipName: String,
    destination: LocalFile,
    expectedSize: Long
) {
    downloadRemoteFile(share, zipName, destination, expectedSize)
}

private fun downloadRemoteFile(
    share: DiskShare,
    remotePath: String,
    destination: LocalFile,
    expectedSize: Long
) {
    val partialFile = LocalFile(destination.parentFile, "${destination.name}.part")
    partialFile.delete()
    try {
        share.openFile(
            remotePath,
            EnumSet.of(AccessMask.GENERIC_READ),
            EnumSet.noneOf(FileAttributes::class.java),
            EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OPEN,
            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
        ).use { remoteFile ->
            remoteFile.inputStream.use { input ->
                partialFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        require(partialFile.length() == expectedSize) { "ファイルの取得が完了していません" }
        destination.delete()
        require(partialFile.renameTo(destination)) { "キャッシュを保存できません" }
    } finally {
        partialFile.delete()
    }
}

private fun readMangaBook(zip: LocalFile, zipName: String): MangaBook {
    return runCatching {
        readMangaBook(zip, zipName, StandardCharsets.UTF_8)
    }.recoverCatching {
        readMangaBook(zip, zipName, Charset.forName("MS932"))
    }.getOrThrow()
}

private fun readMangaBook(
    zip: LocalFile,
    zipName: String,
    charset: Charset
): MangaBook {
    ZipFile(zip, charset).use { archive ->
        val pageNames = archive.entries().asSequence()
            .filterNot { it.isDirectory }
            .filter { entry ->
                val extension = entry.name.substringAfterLast('.', "").lowercase()
                extension in setOf("jpg", "jpeg", "png", "webp")
            }
            .map { it.name }
            .sortedWith(::compareNaturally)
            .toList()
        require(pageNames.isNotEmpty()) { "ZIP内に対応画像がありません" }
        return MangaBook(zipName, zip, pageNames, charset)
    }
}

private fun readPage(book: MangaBook, pageIndex: Int, maxSide: Int = 2048): Bitmap {
    ZipFile(book.cachedZip, book.charset).use { archive ->
        val entry = archive.getEntry(book.pageNames[pageIndex])
            ?: error("ページが見つかりません")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        archive.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "画像を読み取れません" }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        }
        val bitmap = archive.getInputStream(entry).use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("画像を読み取れません")
        return bitmap
    }
}

private fun loadCachedMangaThumbnail(
    cacheDirectory: LocalFile,
    zipName: String
): Bitmap? {
    val cachedZip = LocalFile(LocalFile(cacheDirectory, "manga-preview"), "${zipName.hashCode()}.zip")
    if (!cachedZip.isFile) return null
    return runCatching {
        val book = readMangaBook(cachedZip, zipName)
        readPage(book, 0, maxSide = 480)
    }.getOrNull()
}

private fun compareNaturally(left: String, right: String): Int {
    val leftParts = NATURAL_SORT_PARTS.findAll(left.lowercase()).map { it.value }.toList()
    val rightParts = NATURAL_SORT_PARTS.findAll(right.lowercase()).map { it.value }.toList()
    val commonSize = minOf(leftParts.size, rightParts.size)

    for (index in 0 until commonSize) {
        val leftPart = leftParts[index]
        val rightPart = rightParts[index]
        val comparison = if (leftPart.all(Char::isDigit) && rightPart.all(Char::isDigit)) {
            leftPart.trimStart('0').ifEmpty { "0" }.let { normalizedLeft ->
                rightPart.trimStart('0').ifEmpty { "0" }.let { normalizedRight ->
                    normalizedLeft.length.compareTo(normalizedRight.length)
                        .takeIf { it != 0 }
                        ?: normalizedLeft.compareTo(normalizedRight)
                }
            }
        } else {
            leftPart.compareTo(rightPart)
        }
        if (comparison != 0) return comparison
    }
    return leftParts.size.compareTo(rightParts.size)
}

private val NATURAL_SORT_PARTS = Regex("\\d+|\\D+")
private val PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")
private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "mov", "m4v", "avi")
private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")
private const val SWIPE_THRESHOLD_PX = 80f
private const val READING_PROGRESS_PREFERENCES = "reading_progress"
private const val MANGA_CACHE_PREFERENCES = "manga_cache_metadata"
private const val VIDEO_PROGRESS_PREFERENCES = "video_progress"
private const val TITLE_AND_GUIDE_VISIBLE_MILLIS = 3_000L
private const val TITLE_AND_GUIDE_FADE_MILLIS = 700
private const val VIDEO_PROGRESS_SAVE_INTERVAL_MILLIS = 5_000L
private const val VIDEO_COMPLETION_MARGIN_MILLIS = 10_000L

private fun pageKey(zipName: String) = "page:$zipName"

private fun pageCountKey(zipName: String) = "page_count:$zipName"

private fun cacheSizeKey(zipName: String) = "size:$zipName"

private fun cacheModifiedKey(zipName: String) = "modified:$zipName"

private fun videoProgressKey(videoPath: String) = "position:$videoPath"

private fun saveVideoProgress(
    preferences: SharedPreferences,
    videoPath: String,
    player: Player
) {
    val position = player.currentPosition.coerceAtLeast(0L)
    val duration = player.duration
    val isNearEnd = duration > 0L &&
        duration != androidx.media3.common.C.TIME_UNSET &&
        duration - position <= VIDEO_COMPLETION_MARGIN_MILLIS
    if (isNearEnd || player.playbackState == Player.STATE_ENDED || position < 1_000L) {
        preferences.edit().remove(videoProgressKey(videoPath)).apply()
    } else {
        preferences.edit().putLong(videoProgressKey(videoPath), position).apply()
    }
}

private fun formatPlaybackTime(positionMillis: Long): String {
    val totalSeconds = positionMillis / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun saveCacheMetadata(
    preferences: SharedPreferences,
    zipName: String,
    size: Long,
    modified: Long
) {
    preferences.edit()
        .putLong(cacheSizeKey(zipName), size)
        .putLong(cacheModifiedKey(zipName), modified)
        .apply()
}

private fun savedPage(preferences: SharedPreferences, zipName: String): Int {
    return preferences.getInt(pageKey(zipName), 0)
}

private fun savedProgressLabel(
    preferences: SharedPreferences,
    zipName: String
): String? {
    if (!preferences.contains(pageKey(zipName))) return null
    val page = preferences.getInt(pageKey(zipName), 0) + 1
    val pageCount = preferences.getInt(pageCountKey(zipName), 0)
    return if (pageCount > 0) {
        "続きから：$page / $pageCount"
    } else {
        "続きから：${page}ページ"
    }
}

private fun calculateSampleSize(width: Int, height: Int, maxSide: Int): Int {
    var sampleSize = 1
    while (width / sampleSize > maxSide || height / sampleSize > maxSide) {
        sampleSize *= 2
    }
    return sampleSize
}

private inline fun <T> withMangaShare(
    server: String,
    username: String,
    password: String,
    block: (DiskShare) -> T
): T = withDiskShare(server, username, password, "Manga", block)

private inline fun <T> withDiskShare(
    server: String,
    username: String,
    password: String,
    shareName: String,
    block: (DiskShare) -> T
): T {
    SMBClient().use { client ->
        client.connect(server).use { connection ->
            val authentication = AuthenticationContext(
                username,
                password.toCharArray(),
                null
            )
            connection.authenticate(authentication).use { session ->
                (session.connectShare(shareName) as DiskShare).use { share ->
                    return block(share)
                }
            }
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
