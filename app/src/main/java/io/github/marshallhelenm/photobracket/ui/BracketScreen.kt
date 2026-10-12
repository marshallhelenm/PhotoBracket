package io.github.marshallhelenm.photobracket.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.marshallhelenm.photobracket.ui.theme.textButtonColors
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.marshallhelenm.photobracket.BracketUiState
import io.github.marshallhelenm.photobracket.Folder
import io.github.marshallhelenm.photobracket.SUGGESTED_FOLDERS
import io.github.marshallhelenm.photobracket.R
import io.github.marshallhelenm.photobracket.ui.theme.PhotoBracketTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BracketScreen(
    state: BracketUiState,
    onKeep: (Uri) -> Unit,
    onKeepBoth: () -> Unit,
    onKeepNeither: () -> Unit,
    onTag: (Uri, String?) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenSortQueue: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val tagging = if (state.settings.sortingEnabled) FolderTagging(state.folders, state.pairFolders, onTag) else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Logo(size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.app_name))
                    }
                },
                actions = {
                    if (state.settings.sortingEnabled) {
                        CountedIconButton(count = state.sortQueue.size, onClick = onOpenSortQueue) {
                            Icon(painterResource(R.drawable.ic_folder), contentDescription = stringResource(R.string.to_sort))
                        }
                    }
                    CountedIconButton(count = state.trash.size, onClick = onOpenTrash) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.trash))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                    }
                    IconButton(onClick = onOpenAbout) {
                        Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.about))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val pair = state.pair
            when {
                state.isLoading -> CenteredBox { CircularProgressIndicator() }
                pair == null -> CenteredBox {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.not_enough_photos), textAlign = TextAlign.Center)
                        if (!state.settings.includeKept) {
                            Text(
                                stringResource(R.string.kept_hidden_hint),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                else -> {
                    PhotoPair(
                        pair = pair,
                        tagging = tagging,
                        onKeep = onKeep,
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        TextButton(onClick = onKeepBoth, colors = textButtonColors()) { Text(stringResource(R.string.keep_both)) }
                        TextButton(onClick = onKeepNeither, colors = textButtonColors()) { Text(stringResource(R.string.keep_neither)) }
                    }
                }
            }
        }
    }
}

/** An icon button with a count badge. */
@Composable
private fun CountedIconButton(count: Int, onClick: () -> Unit, icon: @Composable () -> Unit) {
    // Badge wraps the button rather than the icon: IconButton clips its content,
    // which cut off the count. The offset pulls the badge back onto the icon.
    BadgedBox(badge = {
        if (count > 0) {
            Badge(modifier = Modifier.offset(x = (-10).dp, y = 10.dp)) {
                Text(count.toString())
            }
        }
    }) {
        IconButton(onClick = onClick, content = icon)
    }
}

@Composable
private fun PhotoPair(
    pair: Pair<Uri, Uri>,
    tagging: FolderTagging?,
    onKeep: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Index of the photo open in the full-size viewer; reset whenever a new pair comes up.
    var viewing by remember(pair) { mutableStateOf<Int?>(null) }
    val photos = pair.toList()

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val options = @Composable { optionModifier: Modifier ->
            photos.forEachIndexed { index, uri ->
                PhotoOption(
                    uri = uri,
                    position = index + 1,
                    tagging = tagging,
                    onView = { viewing = index },
                    onKeep = onKeep,
                    modifier = optionModifier,
                )
            }
        }
        if (maxWidth > maxHeight) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                options(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                options(Modifier.weight(1f))
            }
        }
    }

    viewing?.let { index ->
        PhotoViewer(
            photos = photos,
            initialPage = index,
            tagging = tagging,
            onKeep = onKeep,
            onDismiss = { viewing = null },
        )
    }
}

@Composable
private fun PhotoOption(
    uri: Uri,
    position: Int,
    tagging: FolderTagging?,
    onView: () -> Unit,
    onKeep: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = uri,
                contentDescription = stringResource(R.string.photo_description, position),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clickable(onClick = onView),
            )
            if (tagging != null && tagging.starred.isNotEmpty()) {
                FolderColumn(
                    uri = uri,
                    tagging = tagging,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(6.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onKeep(uri) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.keep_this))
        }
    }
}

@Composable
private fun ColumnScope.CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        content()
    }
}

@Preview(showBackground = true)
@Composable
private fun BracketScreenPreview() {
    PhotoBracketTheme {
        BracketScreen(
            state = BracketUiState(
                isLoading = false,
                pair = Uri.parse("content://media/1") to Uri.parse("content://media/2"),
                settings = io.github.marshallhelenm.photobracket.Settings(sortingEnabled = true),
                folders = SUGGESTED_FOLDERS.mapIndexed { i, (name, emoji) ->
                    Folder(i.toString(), name, emoji, starred = true)
                },
                pairFolders = mapOf(Uri.parse("content://media/1") to "0"),
                trash = listOf(Uri.parse("content://media/3")),
            ),
            onKeep = {},
            onKeepBoth = {},
            onKeepNeither = {},
            onTag = { _, _ -> },
            onOpenTrash = {},
            onOpenSortQueue = {},
            onOpenSettings = {},
            onOpenAbout = {},
        )
    }
}
