@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ponchi.nasmedia

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.media3.common.Player
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import java.io.File as LocalFile
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.EnumSet
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException

internal fun loadAudioEntries(
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

internal fun resolveAudioDirectory(
    server: String,
    username: String,
    password: String,
    initialPath: String
): ResolvedAudioDirectory {
    var path = initialPath
    repeat(MAX_AUDIO_AUTO_ADVANCE_DEPTH) {
        val entries = loadAudioEntries(server, username, password, path)
        val onlyEntry = entries.singleOrNull()
        if (onlyEntry == null || !onlyEntry.isDirectory) {
            return ResolvedAudioDirectory(path, entries)
        }
        path = onlyEntry.path
    }
    return ResolvedAudioDirectory(
        path = path,
        entries = loadAudioEntries(server, username, password, path)
    )
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

internal fun loadVideoEntries(
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

internal fun loadPhotoEntries(
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

internal fun loadPhotoBitmap(
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

    // サムネイル表示では縮小済み画像をディスクに残し、次回は元画像を開かずに済ませる
    val thumbFile = if (maxSide <= 500) {
        val thumbDir = LocalFile(cacheDirectory, "photo-thumb-cache").apply { mkdirs() }
        LocalFile(thumbDir, "thumb-${photoPath.hashCode()}-$maxSide.jpg")
    } else {
        null
    }
    if (thumbFile != null && thumbFile.isFile && thumbFile.length() > 0L) {
        BitmapFactory.decodeFile(thumbFile.absolutePath)
            ?.let { return@withDiskShare it }
    }

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
    }
    val decoded = BitmapFactory.decodeFile(cachedPhoto.absolutePath, options)
        ?: error("写真を読み取れません")
    if (thumbFile != null) {
        runCatching {
            thumbFile.outputStream().use { output ->
                decoded.compress(Bitmap.CompressFormat.JPEG, 82, output)
            }
        }
    }
    decoded
}

internal fun joinSmbPath(parent: String, child: String): String {
    return if (parent.isEmpty()) child else "$parent\\$child"
}

internal fun parentSmbPath(path: String): String = path.substringBeforeLast('\\', "")

internal fun photoFileName(path: String): String = path.substringAfterLast('\\')

internal fun loadMangaZipNames(
    server: String,
    username: String,
    password: String
): List<String> = withMangaShare(server, username, password) { share ->
    share.list("")
        .map { it.fileName }
        .filter { it.endsWith(".zip", ignoreCase = true) }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
}

internal fun downloadAndOpenManga(
    server: String,
    username: String,
    password: String,
    zipName: String,
    cacheDirectory: LocalFile,
    cacheMetadata: SharedPreferences,
    onProgress: ((Long, Long) -> Unit)? = null,
    downloadControl: DownloadControl? = null
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
            downloadZip(share, zipName, cachedZip, remoteSize, onProgress, downloadControl)
            saveCacheMetadata(cacheMetadata, zipName, remoteSize, remoteModified)
            downloadedNow = true
        }

        runCatching {
            readMangaBook(cachedZip, zipName)
        }.recoverCatching {
            if (downloadedNow) throw it
            cachedZip.delete()
            downloadZip(share, zipName, cachedZip, remoteSize, onProgress, downloadControl)
            saveCacheMetadata(cacheMetadata, zipName, remoteSize, remoteModified)
            readMangaBook(cachedZip, zipName)
        }.getOrThrow()
    }
}

internal fun downloadZip(
    share: DiskShare,
    zipName: String,
    destination: LocalFile,
    expectedSize: Long,
    onProgress: ((Long, Long) -> Unit)? = null,
    downloadControl: DownloadControl? = null
) {
    downloadRemoteFile(share, zipName, destination, expectedSize, onProgress, downloadControl)
}

internal fun downloadRemoteFile(
    share: DiskShare,
    remotePath: String,
    destination: LocalFile,
    expectedSize: Long,
    onProgress: ((Long, Long) -> Unit)? = null,
    downloadControl: DownloadControl? = null
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
                partialFile.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var copied = 0L
                    while (true) {
                        if (downloadControl?.cancelled == true) {
                            throw CancellationException("ダウンロードをキャンセルしました")
                        }
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read.toLong()
                        onProgress?.invoke(copied, expectedSize)
                    }
                }
            }
        }
        require(partialFile.length() == expectedSize) { "ファイルの取得が完了していません" }
        destination.delete()
        require(partialFile.renameTo(destination)) { "キャッシュを保存できません" }
    } finally {
        partialFile.delete()
    }
}

internal fun readMangaBook(zip: LocalFile, zipName: String): MangaBook {
    return runCatching {
        readMangaBook(zip, zipName, StandardCharsets.UTF_8)
    }.recoverCatching {
        readMangaBook(zip, zipName, Charset.forName("MS932"))
    }.getOrThrow()
}

internal fun readMangaBook(
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

internal fun readPage(book: MangaBook, pageIndex: Int, maxSide: Int = 2048): Bitmap {
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

internal fun loadCachedMangaThumbnail(
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

internal fun compareNaturally(left: String, right: String): Int {
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

internal val NATURAL_SORT_PARTS = Regex("\\d+|\\D+")
internal val PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")
internal val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "mov", "m4v", "avi")
internal val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")
internal const val MAX_AUDIO_AUTO_ADVANCE_DEPTH = 12
internal const val SWIPE_THRESHOLD_PX = 80f
internal const val READING_PROGRESS_PREFERENCES = "reading_progress"
internal const val MANGA_CACHE_PREFERENCES = "manga_cache_metadata"
internal const val VIDEO_PROGRESS_PREFERENCES = "video_progress"
internal const val RECENT_PREFERENCES = "recently_opened"
internal const val RECENT_ITEMS_KEY = "items"
internal const val MAX_RECENT_ITEMS = 20
internal const val RECENT_TYPE_MANGA = "MANGA"
internal const val RECENT_TYPE_VIDEO = "VIDEO"
internal const val RECENT_TYPE_AUDIO = "AUDIO"
internal const val TITLE_AND_GUIDE_VISIBLE_MILLIS = 3_000L
internal const val TITLE_AND_GUIDE_FADE_MILLIS = 700
internal const val VIDEO_PROGRESS_SAVE_INTERVAL_MILLIS = 5_000L
internal const val VIDEO_COMPLETION_MARGIN_MILLIS = 10_000L

internal fun pageKey(zipName: String) = "page:$zipName"

internal fun pageCountKey(zipName: String) = "page_count:$zipName"

internal fun cacheSizeKey(zipName: String) = "size:$zipName"

internal fun cacheModifiedKey(zipName: String) = "modified:$zipName"

internal fun videoProgressKey(videoPath: String) = "position:$videoPath"

internal fun saveVideoProgress(
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

internal fun formatPlaybackTime(positionMillis: Long): String {
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

internal fun saveCacheMetadata(
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

internal fun savedPage(preferences: SharedPreferences, zipName: String): Int {
    return preferences.getInt(pageKey(zipName), 0)
}

internal fun savedProgressLabel(
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

internal fun calculateSampleSize(width: Int, height: Int, maxSide: Int): Int {
    var sampleSize = 1
    while (width / sampleSize > maxSide || height / sampleSize > maxSide) {
        sampleSize *= 2
    }
    return sampleSize
}

internal fun classifyNasConnectionError(error: Throwable): String {
    val details = generateSequence(error) { it.cause }
        .flatMap { throwable ->
            sequenceOf(throwable.javaClass.name, throwable.message.orEmpty())
        }
        .joinToString(" ")
        .lowercase()
    return when {
        details.contains("logon_failure") || details.contains("logon failure") ||
            details.contains("access_denied") || details.contains("access denied") ||
            details.contains("authentication") || details.contains("ntlm") ||
            details.contains("パスワード") ->
            "認証に失敗しました。ユーザー名とパスワードを確認してください"
        details.contains("bad_network_name") || details.contains("bad network name") ||
            details.contains("object_name_not_found") || details.contains("object name not found") ||
            details.contains("object_path_not_found") || details.contains("no such file") ||
            details.contains("share") && details.contains("not found") ->
            "共有フォルダが見つかりません。NASの共有フォルダ（Manga）を確認してください"
        details.contains("unknownhost") || details.contains("timed out") ||
            details.contains("timeout") || details.contains("connectexception") ||
            details.contains("noroutetohost") || details.contains("unreachable") ||
            details.contains("ehostunreach") || details.contains("etimedout") ||
            details.contains("connection refused") || details.contains("failed to connect") ->
            "NASに接続できません。NASの電源とIPアドレス、同じネットワークに接続しているか確認してください"
        else -> "接続できません。NASとネットワークの状態を確認してください"
    }
}

internal fun recordRecentItem(
    preferences: SharedPreferences,
    type: String,
    key: String,
    name: String
) {
    val sanitizedKey = key.replace('\t', ' ').replace('\n', ' ')
    val sanitizedName = name.replace('\t', ' ').replace('\n', ' ')
    val current = loadRecentItems(preferences)
        .filterNot { it.type == type && it.key == sanitizedKey }
    val updated = (listOf(
        RecentItem(type, sanitizedKey, sanitizedName, System.currentTimeMillis())
    ) + current).take(MAX_RECENT_ITEMS)
    preferences.edit()
        .putString(
            RECENT_ITEMS_KEY,
            updated.joinToString("\n") { item ->
                listOf(item.type, item.timestamp.toString(), item.key, item.name)
                    .joinToString("\t")
            }
        )
        .apply()
}

internal fun loadRecentItems(preferences: SharedPreferences): List<RecentItem> {
    val raw = preferences.getString(RECENT_ITEMS_KEY, null) ?: return emptyList()
    return raw.split("\n").mapNotNull { line ->
        val parts = line.split("\t")
        if (parts.size < 4) return@mapNotNull null
        RecentItem(
            type = parts[0],
            key = parts[2],
            name = parts[3],
            timestamp = parts[1].toLongOrNull() ?: 0L
        )
    }
}

internal fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return "%.1f %s".format(value, units[unitIndex])
}

internal fun mangaCacheSizeBytes(cacheDirectory: LocalFile): Long {
    val previewDirectory = LocalFile(cacheDirectory, "manga-preview")
    if (!previewDirectory.isDirectory) return 0L
    return previewDirectory.walkTopDown()
        .filter { it.isFile }
        .sumOf { it.length() }
}

internal fun clearMangaCache(cacheDirectory: LocalFile, cacheMetadata: SharedPreferences) {
    val previewDirectory = LocalFile(cacheDirectory, "manga-preview")
    previewDirectory.listFiles()?.forEach { it.delete() }
    cacheMetadata.edit().clear().apply()
}

internal inline fun <T> withMangaShare(
    server: String,
    username: String,
    password: String,
    block: (DiskShare) -> T
): T = withDiskShare(server, username, password, "Manga", block)

internal inline fun <T> withDiskShare(
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


// ---- Phase 2: お気に入り ----
internal const val FAVORITES_PREFERENCES = "favorites"
internal const val FAVORITES_KEY = "items"

internal data class FavoriteItem(
    val type: String,
    val key: String,
    val name: String,
    val timestamp: Long
)

private fun sanitizeKeyPart(value: String): String =
    value.replace('\t', ' ').replace('\n', ' ')

internal fun loadFavorites(preferences: SharedPreferences): List<FavoriteItem> {
    val raw = preferences.getString(FAVORITES_KEY, null) ?: return emptyList()
    return raw.split("\n").mapNotNull { line ->
        val parts = line.split("\t")
        if (parts.size < 4) return@mapNotNull null
        FavoriteItem(
            type = parts[0],
            key = parts[2],
            name = parts[3],
            timestamp = parts[1].toLongOrNull() ?: 0L
        )
    }
}

internal fun isFavorite(favorites: List<FavoriteItem>, type: String, key: String): Boolean =
    favorites.any { it.type == type && it.key == key }

/** お気に入りを切り替え、切り替え後の全件を返す */
internal fun toggleFavorite(
    preferences: SharedPreferences,
    type: String,
    key: String,
    name: String
): List<FavoriteItem> {
    val current = loadFavorites(preferences)
    val updated = if (isFavorite(current, type, key)) {
        current.filterNot { it.type == type && it.key == key }
    } else {
        listOf(
            FavoriteItem(type, sanitizeKeyPart(key), sanitizeKeyPart(name), System.currentTimeMillis())
        ) + current
    }
    preferences.edit()
        .putString(
            FAVORITES_KEY,
            updated.joinToString("\n") { item ->
                listOf(item.type, item.timestamp.toString(), item.key, item.name)
                    .joinToString("\t")
            }
        )
        .apply()
    return updated
}

// ---- Phase 2: 音声の状態（速度・キュー・しおり） ----
internal const val AUDIO_STATE_PREFERENCES = "audio_state"
internal const val MAX_AUDIO_QUEUE_FOLDERS = 20

internal data class AudioQueueState(
    val trackIndex: Int,
    val positionMs: Long,
    val trackPath: String
)

internal data class AudioBookmark(
    val trackIndex: Int,
    val positionMs: Long,
    val trackPath: String
)

internal fun audioSpeedKey(folderPath: String) = "speed:$folderPath"

internal fun loadAudioSpeed(preferences: SharedPreferences, folderPath: String): Float =
    preferences.getFloat(audioSpeedKey(folderPath), 1f)

internal fun saveAudioSpeed(preferences: SharedPreferences, folderPath: String, speed: Float) {
    preferences.edit().putFloat(audioSpeedKey(folderPath), speed).apply()
}

private fun audioQueueKey(folderPath: String) = "queue:$folderPath"
private const val AUDIO_QUEUE_ORDER_KEY = "queue_order"

internal fun saveAudioQueue(
    preferences: SharedPreferences,
    folderPath: String,
    trackIndex: Int,
    positionMs: Long,
    trackPath: String
) {
    val order = preferences.getString(AUDIO_QUEUE_ORDER_KEY, "")
        ?.split("\n")?.filter { it.isNotEmpty() } ?: emptyList()
    val newOrder = (listOf(folderPath) + order.filterNot { it == folderPath })
    val editor = preferences.edit()
    editor.putString(
        audioQueueKey(folderPath),
        listOf(trackIndex.toString(), positionMs.toString(), sanitizeKeyPart(trackPath))
            .joinToString("\t")
    )
    newOrder.drop(MAX_AUDIO_QUEUE_FOLDERS).forEach { stale ->
        editor.remove(audioQueueKey(stale))
    }
    editor.putString(
        AUDIO_QUEUE_ORDER_KEY,
        newOrder.take(MAX_AUDIO_QUEUE_FOLDERS).joinToString("\n")
    )
    editor.apply()
}

internal fun loadAudioQueue(
    preferences: SharedPreferences,
    folderPath: String
): AudioQueueState? {
    val raw = preferences.getString(audioQueueKey(folderPath), null) ?: return null
    val parts = raw.split("\t")
    if (parts.size < 3) return null
    return AudioQueueState(
        trackIndex = parts[0].toIntOrNull() ?: return null,
        positionMs = parts[1].toLongOrNull() ?: 0L,
        trackPath = parts[2]
    )
}

internal fun clearAudioQueue(preferences: SharedPreferences, folderPath: String) {
    preferences.edit().remove(audioQueueKey(folderPath)).apply()
}

private fun audioBookmarksKey(folderPath: String) = "bookmarks:$folderPath"

internal fun loadAudioBookmarks(
    preferences: SharedPreferences,
    folderPath: String
): List<AudioBookmark> {
    val raw = preferences.getString(audioBookmarksKey(folderPath), null) ?: return emptyList()
    return raw.split("\n").mapNotNull { line ->
        val parts = line.split("\t")
        if (parts.size < 3) return@mapNotNull null
        AudioBookmark(
            trackIndex = parts[0].toIntOrNull() ?: return@mapNotNull null,
            positionMs = parts[1].toLongOrNull() ?: 0L,
            trackPath = parts[2]
        )
    }
}

private fun saveAudioBookmarks(
    preferences: SharedPreferences,
    folderPath: String,
    bookmarks: List<AudioBookmark>
) {
    preferences.edit()
        .putString(
            audioBookmarksKey(folderPath),
            bookmarks.joinToString("\n") { bookmark ->
                listOf(
                    bookmark.trackIndex.toString(),
                    bookmark.positionMs.toString(),
                    sanitizeKeyPart(bookmark.trackPath)
                ).joinToString("\t")
            }
        )
        .apply()
}

internal fun addAudioBookmark(
    preferences: SharedPreferences,
    folderPath: String,
    bookmark: AudioBookmark
): List<AudioBookmark> {
    val updated = (loadAudioBookmarks(preferences, folderPath) + bookmark)
        .distinctBy { it.trackIndex to it.positionMs }
        .sortedWith(compareBy({ it.trackIndex }, { it.positionMs }))
    saveAudioBookmarks(preferences, folderPath, updated)
    return updated
}

internal fun removeAudioBookmark(
    preferences: SharedPreferences,
    folderPath: String,
    bookmark: AudioBookmark
): List<AudioBookmark> {
    val updated = loadAudioBookmarks(preferences, folderPath)
        .filterNot { it.trackIndex == bookmark.trackIndex && it.positionMs == bookmark.positionMs }
    saveAudioBookmarks(preferences, folderPath, updated)
    return updated
}

// ---- Phase 2: 検索履歴 ----
internal const val SEARCH_HISTORY_PREFERENCES = "search_history"
internal const val MAX_SEARCH_HISTORY = 8

internal fun loadSearchHistory(preferences: SharedPreferences, section: String): List<String> {
    val raw = preferences.getString("history:$section", null) ?: return emptyList()
    return raw.split("\n").filter { it.isNotEmpty() }
}

internal fun recordSearchHistory(
    preferences: SharedPreferences,
    section: String,
    query: String
): List<String> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return loadSearchHistory(preferences, section)
    val updated = (listOf(trimmed) + loadSearchHistory(preferences, section)
        .filterNot { it.equals(trimmed, ignoreCase = true) }).take(MAX_SEARCH_HISTORY)
    preferences.edit().putString("history:$section", updated.joinToString("\n")).apply()
    return updated
}

internal fun clearSearchHistory(preferences: SharedPreferences, section: String) {
    preferences.edit().remove("history:$section").apply()
}

// ---- Phase 2: プライバシー設定 ----
internal const val PRIVACY_PREFERENCES = "privacy_settings"
internal const val HIDE_AUDIO_ARTWORK_KEY = "hide_audio_artwork"

// ---- Phase 2: マンガキャッシュ管理 ----
internal data class CachedMangaEntry(
    val zipName: String,
    val sizeBytes: Long
)

internal fun isMangaCached(
    cacheDirectory: LocalFile,
    cacheMetadata: SharedPreferences,
    zipName: String
): Boolean {
    if (!cacheMetadata.contains(cacheSizeKey(zipName))) return false
    val cachedZip = LocalFile(
        LocalFile(cacheDirectory, "manga-preview"), "${zipName.hashCode()}.zip"
    )
    return cachedZip.isFile && cachedZip.length() > 0L
}

internal fun listCachedManga(
    cacheDirectory: LocalFile,
    cacheMetadata: SharedPreferences
): List<CachedMangaEntry> {
    return cacheMetadata.all.keys
        .filter { it.startsWith("size:") }
        .map { key -> key.removePrefix("size:") }
        .filter { zipName -> isMangaCached(cacheDirectory, cacheMetadata, zipName) }
        .map { zipName ->
            CachedMangaEntry(zipName, cacheMetadata.getLong(cacheSizeKey(zipName), 0L))
        }
        .sortedWith { a, b -> compareNaturally(a.zipName, b.zipName) }
}

internal fun deleteCachedManga(
    cacheDirectory: LocalFile,
    cacheMetadata: SharedPreferences,
    zipName: String
) {
    val cachedZip = LocalFile(
        LocalFile(cacheDirectory, "manga-preview"), "${zipName.hashCode()}.zip"
    )
    cachedZip.delete()
    cacheMetadata.edit()
        .remove(cacheSizeKey(zipName))
        .remove(cacheModifiedKey(zipName))
        .apply()
}

// ---- Phase 2: マンガリーダーの音量キーページ送り ----
internal object MangaVolumeKeyHandler {
    /** true = 次のページ, false = 前のページ。リーダー表示中のみ非null */
    @Volatile var handler: ((Boolean) -> Unit)? = null
}
