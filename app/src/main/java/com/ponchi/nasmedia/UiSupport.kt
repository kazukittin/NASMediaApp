package com.ponchi.nasmedia

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.io.File as LocalFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun LibrarySortControl(
    sort: LibrarySort,
    onSortChange: (LibrarySort) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LibrarySort.entries.forEach { option ->
            FilterChip(
                selected = sort == option,
                onClick = { onSortChange(option) },
                label = { Text(option.label) }
            )
        }
    }
}

internal fun <T> sortByName(
    entries: List<T>,
    sort: LibrarySort,
    isDirectory: (T) -> Boolean,
    name: (T) -> String
): List<T> = entries.sortedWith { left, right ->
    val leftDir = isDirectory(left)
    val rightDir = isDirectory(right)
    when {
        leftDir && !rightDir -> -1
        !leftDir && rightDir -> 1
        sort == LibrarySort.NAME_ASC -> compareNaturally(name(left), name(right))
        else -> compareNaturally(name(right), name(left))
    }
}

@Composable
internal fun EmptyLibraryState(message: String, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(message)
        Button(onClick = onRefresh) { Text("再読み込み") }
    }
}

@Composable
internal fun MediaSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    label: String,
    history: List<String> = emptyList(),
    onSearchSubmit: () -> Unit = {},
    onClearHistory: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    TextButton(onClick = { onQueryChange("") }) { Text("消去") }
            }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                onSearchSubmit()
                focusManager.clearFocus()
            }),
            singleLine = true
        )
        if (history.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(history) { historyQuery ->
                    FilterChip(
                        selected = false,
                        onClick = { onQueryChange(historyQuery) },
                        label = { Text(historyQuery) }
                    )
                }
                item {
                    TextButton(onClick = onClearHistory) { Text("履歴を削除") }
                }
            }
        }
    }
}

@Composable
internal fun EmptySearchResult(onClear: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("一致する項目がありません")
        TextButton(onClick = onClear) { Text("検索をクリア") }
    }
}

@Composable
internal fun LocationLabel(root: String, currentPath: String) {
    val location = if (currentPath.isEmpty()) {
        root
    } else {
        "$root › ${currentPath.replace("\\", " › ")}"
    }
    Text(
        text = location,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

internal fun matchesSearch(
    query: String,
    name: String,
    workInfo: DlsiteWorkInfo? = null
): Boolean {
    val terms = query.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    if (terms.isEmpty()) return true
    val searchableText = buildString {
        append(name)
        workInfo?.let {
            append(' ')
            append(it.productId)
            append(' ')
            append(it.title)
            append(' ')
            append(it.circleName)
            append(' ')
            append(it.voiceActors.joinToString(" "))
            append(' ')
            append(it.genres.joinToString(" "))
        }
    }
    return terms.all { searchableText.contains(it, ignoreCase = true) }
}

@Composable
internal fun MangaLibraryCardContent(
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
