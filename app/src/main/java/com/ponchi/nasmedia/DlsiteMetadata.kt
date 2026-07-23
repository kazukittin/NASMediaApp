package com.ponchi.nasmedia

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal data class DlsiteWorkInfo(
    val productId: String,
    val title: String,
    val circleName: String,
    val description: String,
    val coverUrl: String,
    val productUrl: String,
    val releaseDate: String,
    val voiceActors: List<String>,
    val genres: List<String>
)

private val DLSITE_PRODUCT_ID = Regex("(?i)(?<![A-Z0-9])RJ\\d{6,8}(?!\\d)")
private const val DLSITE_CACHE_PREFERENCES = "dlsite_work_metadata"
private const val DLSITE_CACHE_MAX_AGE_MILLIS = 30L * 24L * 60L * 60L * 1_000L

internal fun findDlsiteProductId(text: String): String? =
    DLSITE_PRODUCT_ID.find(text)?.value?.uppercase()

internal suspend fun loadDlsiteWorkInfo(
    context: Context,
    folderName: String
): DlsiteWorkInfo? = withContext(Dispatchers.IO) {
    val productId = findDlsiteProductId(folderName) ?: return@withContext null
    val preferences = context.getSharedPreferences(
        DLSITE_CACHE_PREFERENCES,
        Context.MODE_PRIVATE
    )
    val cachedJson = preferences.getString(productId, null)
    val cached = cachedJson?.let(::decodeCachedWorkInfo)
    if (cached != null && System.currentTimeMillis() - cached.second < DLSITE_CACHE_MAX_AGE_MILLIS) {
        return@withContext cached.first
    }

    runCatching { fetchDlsiteWorkInfo(productId) }
        .onSuccess { workInfo ->
            preferences.edit()
                .putString(productId, encodeCachedWorkInfo(workInfo, System.currentTimeMillis()))
                .apply()
        }
        .getOrNull() ?: cached?.first
}

internal suspend fun loadDlsiteCoverBitmap(
    context: Context,
    workInfo: DlsiteWorkInfo
): Bitmap? = withContext(Dispatchers.IO) {
    if (workInfo.coverUrl.isBlank()) return@withContext null
    val directory = File(context.cacheDir, "dlsite-cover-cache").apply { mkdirs() }
    val cached = File(directory, "${workInfo.productId}.jpg")
    if (!cached.isFile || cached.length() == 0L) {
        val partial = File(directory, "${workInfo.productId}.part")
        partial.delete()
        runCatching {
            openConnection(workInfo.coverUrl).run {
                inputStream.use { input ->
                    partial.outputStream().use { output -> input.copyTo(output) }
                }
                disconnect()
            }
            if (partial.length() > 0L) {
                cached.delete()
                check(partial.renameTo(cached))
            }
        }.onFailure { partial.delete() }.getOrNull()
    }
    BitmapFactory.decodeFile(cached.absolutePath)
}

internal suspend fun loadPreferredAudioCoverBitmap(
    context: Context,
    server: String,
    username: String,
    password: String,
    folderPath: String
): Bitmap? {
    val folderName = folderPath.substringAfterLast('\\')
    val dlsiteCover = if (findDlsiteProductId(folderName) != null) {
        loadDlsiteWorkInfo(context, folderName)?.let {
            loadDlsiteCoverBitmap(context, it)
        }
    } else {
        null
    }
    return dlsiteCover ?: withContext(Dispatchers.IO) {
        loadAudioCoverBitmap(
            server,
            username,
            password,
            folderPath,
            context.cacheDir
        )
    }
}

private fun fetchDlsiteWorkInfo(productId: String): DlsiteWorkInfo {
    val requestedUrl =
        "https://www.dlsite.com/home/work/=/product_id/$productId.html/?locale=ja_JP"
    val html = openConnection(requestedUrl).run {
        inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.also { disconnect() }
    }
    val titleWithCircle = metaContent(html, "og:title")
        ?.substringBefore(" | DLsite")
        ?.trim()
        .orEmpty()
    require(titleWithCircle.isNotBlank()) { "DLsite作品情報を取得できません" }
    val titleAndCircle = Regex("^(.*) \\[([^]]+)]$").matchEntire(titleWithCircle)
    val title = titleAndCircle?.groupValues?.get(1)?.trim().orEmpty()
        .ifBlank { titleWithCircle }
    val circle = titleAndCircle?.groupValues?.get(2)?.trim().orEmpty()
    return DlsiteWorkInfo(
        productId = productId,
        title = title,
        circleName = circle,
        description = metaContent(html, "og:description").orEmpty(),
        coverUrl = metaContent(html, "og:image")
            ?: metaContent(html, "twitter:image:src").orEmpty(),
        productUrl = metaContent(html, "og:url") ?: requestedUrl,
        releaseDate = outlineValue(html, "販売日").orEmpty(),
        voiceActors = splitOutlineValues(outlineValue(html, "声優")),
        genres = splitOutlineValues(outlineValue(html, "ジャンル"))
    )
}

private fun openConnection(url: String): HttpURLConnection =
    (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 15_000
        readTimeout = 20_000
        setRequestProperty("User-Agent", "NASMediaApp/1.0 Android")
        setRequestProperty("Accept-Language", "ja-JP,ja;q=0.9")
        setRequestProperty("Cookie", "adultchecked=1")
        connect()
        check(responseCode in 200..299) { "DLsiteへの接続に失敗しました: $responseCode" }
    }

private fun metaContent(html: String, key: String): String? {
    val tag = Regex(
        "<meta[^>]+(?:property|name)=[\\\"']${Regex.escape(key)}[\\\"'][^>]*>",
        RegexOption.IGNORE_CASE
    ).find(html)?.value ?: return null
    val value = Regex("content=[\\\"'](.*?)[\\\"']", RegexOption.IGNORE_CASE)
        .find(tag)?.groupValues?.get(1) ?: return null
    return Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
}

private fun outlineValue(html: String, label: String): String? {
    val rawValue = Regex(
        "<th[^>]*>\\s*${Regex.escape(label)}\\s*</th>[\\s\\S]*?<td[^>]*>([\\s\\S]*?)</td>",
        RegexOption.IGNORE_CASE
    ).find(html)?.groupValues?.get(1) ?: return null
    return Html.fromHtml(rawValue, Html.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun splitOutlineValues(value: String?): List<String> = value
    ?.split(Regex("\\s*(?:/|／)\\s*|\\s+"))
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    .orEmpty()

private fun encodeCachedWorkInfo(info: DlsiteWorkInfo, fetchedAt: Long): String =
    JSONObject()
        .put("productId", info.productId)
        .put("title", info.title)
        .put("circleName", info.circleName)
        .put("description", info.description)
        .put("coverUrl", info.coverUrl)
        .put("productUrl", info.productUrl)
        .put("releaseDate", info.releaseDate)
        .put("voiceActors", info.voiceActors.joinToString("\n"))
        .put("genres", info.genres.joinToString("\n"))
        .put("schemaVersion", 2)
        .put("fetchedAt", fetchedAt)
        .toString()

private fun decodeCachedWorkInfo(json: String): Pair<DlsiteWorkInfo, Long>? = runCatching {
    val value = JSONObject(json)
    DlsiteWorkInfo(
        productId = value.getString("productId"),
        title = value.getString("title"),
        circleName = value.optString("circleName"),
        description = value.optString("description"),
        coverUrl = value.optString("coverUrl"),
        productUrl = value.optString("productUrl"),
        releaseDate = value.optString("releaseDate"),
        voiceActors = value.optString("voiceActors").lineSequence().filter(String::isNotBlank).toList(),
        genres = value.optString("genres").lineSequence().filter(String::isNotBlank).toList()
    ) to if (value.optInt("schemaVersion") >= 2) value.optLong("fetchedAt") else 0L
}.getOrNull()
