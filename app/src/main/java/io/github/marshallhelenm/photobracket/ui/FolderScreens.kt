package io.github.marshallhelenm.photobracket.ui

import android.icu.text.BreakIterator
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.marshallhelenm.photobracket.Folder
import io.github.marshallhelenm.photobracket.FolderNameProblem
import io.github.marshallhelenm.photobracket.MAX_STARRED_FOLDERS
import io.github.marshallhelenm.photobracket.R
import io.github.marshallhelenm.photobracket.folderNameProblem
import io.github.marshallhelenm.photobracket.ui.theme.textButtonColors

/** What the photo views need to tag photos with folders. Null wherever sorting is turned off. */
class FolderTagging(
    val folders: List<Folder>,
    private val tags: Map<Uri, String>,
    private val onTag: (Uri, String?) -> Unit,
) {
    val starred: List<Folder> get() = folders.filter { it.starred }

    fun folderFor(uri: Uri): Folder? = tags[uri]?.let { id -> folders.find { it.id == id } }

    /** Tags [uri] with [folder], or clears the tag if it's already in that folder. */
    fun toggle(uri: Uri, folder: Folder) = onTag(uri, if (tags[uri] == folder.id) null else folder.id)

    fun clear(uri: Uri) = onTag(uri, null)
}

/** The starred folders' emojis, layered over the right edge of a photo. */
@Composable
fun FolderColumn(uri: Uri, tagging: FolderTagging, modifier: Modifier = Modifier) {
    val selected = tagging.folderFor(uri)
    var showingAll by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tagging.starred.forEach { folder ->
            FolderChip(
                selected = folder.id == selected?.id,
                label = folder.name,
                onClick = { tagging.toggle(uri, folder) },
            ) {
                Text(folder.emoji, fontSize = 20.sp)
            }
        }
        // "More" opens every folder. If the photo's folder isn't starred, show it here so the tag is visible.
        val unstarredSelection = selected?.takeUnless { it.starred }
        Box {
            FolderChip(
                selected = unstarredSelection != null,
                label = unstarredSelection?.name ?: stringResource(R.string.sort_into),
                onClick = { showingAll = true },
            ) {
                if (unstarredSelection != null) {
                    Text(unstarredSelection.emoji, fontSize = 20.sp)
                } else {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.rotate(90f),
                    )
                }
            }
            FolderMenu(uri = uri, tagging = tagging, expanded = showingAll, onDismiss = { showingAll = false })
        }
    }
}

@Composable
private fun FolderChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f))
            .then(if (selected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        content()
    }
}

/** Dropdown of every folder, used from the main screen's "more" chip and the viewer's "Sort into…". */
@Composable
fun FolderMenu(uri: Uri, tagging: FolderTagging, expanded: Boolean, onDismiss: () -> Unit) {
    val current = tagging.folderFor(uri)
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        tagging.folders.forEach { folder ->
            DropdownMenuItem(
                leadingIcon = { Text(folder.emoji, fontSize = 20.sp) },
                text = { Text(folder.name) },
                trailingIcon = {
                    if (folder.id == current?.id) Icon(Icons.Filled.Check, contentDescription = null)
                },
                onClick = {
                    onDismiss()
                    tagging.toggle(uri, folder)
                },
            )
        }
        if (current != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.dont_sort)) },
                onClick = {
                    onDismiss()
                    tagging.clear(uri)
                },
            )
        }
    }
}

@Composable
fun FoldersScreen(
    folders: List<Folder>,
    onAdd: (name: String, emoji: String) -> Unit,
    onUpdate: (Folder) -> Unit,
    onStarredChange: (folderId: String, starred: Boolean) -> Unit,
    onDelete: (folderId: String) -> Unit,
    onBack: () -> Unit,
) {
    // The folder whose edit dialog is open, if any.
    var editing by remember { mutableStateOf<Folder?>(null) }
    var adding by remember { mutableStateOf(false) }
    val starredCount = folders.count { it.starred }

    SubScreen(
        title = stringResource(R.string.folders),
        onBack = onBack,
        bottomBar = {
            Button(
                onClick = { adding = true },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            ) {
                Text(stringResource(R.string.add_folder))
            }
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            item {
                Text(
                    text = stringResource(R.string.folders_hint, MAX_STARRED_FOLDERS),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(folders, key = { it.id }) { folder ->
                ListItem(
                    leadingContent = { Text(folder.emoji, fontSize = 28.sp) },
                    headlineContent = { Text(folder.name) },
                    supportingContent = { Text(folder.relativePath) },
                    trailingContent = {
                        val canStar = folder.starred || starredCount < MAX_STARRED_FOLDERS
                        IconButton(
                            onClick = { onStarredChange(folder.id, !folder.starred) },
                            enabled = canStar,
                        ) {
                            Icon(
                                painter = if (folder.starred) {
                                    rememberVectorPainter(Icons.Filled.Star)
                                } else {
                                    painterResource(R.drawable.ic_star_border)
                                },
                                contentDescription = stringResource(
                                    if (folder.starred) R.string.unstar_folder else R.string.star_folder,
                                ),
                                tint = if (folder.starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (canStar) 1f else 0.38f),
                            )
                        }
                    },
                    modifier = Modifier.clickable { editing = folder },
                )
            }
        }
    }

    if (adding) {
        FolderDialog(
            initial = null,
            others = folders,
            onSave = { name, emoji ->
                adding = false
                onAdd(name, emoji)
            },
            onDelete = null,
            onDismiss = { adding = false },
        )
    }
    editing?.let { folder ->
        FolderDialog(
            initial = folder,
            others = folders.filterNot { it.id == folder.id },
            onSave = { name, emoji ->
                editing = null
                onUpdate(folder.copy(name = name.trim(), emoji = emoji))
            },
            onDelete = {
                editing = null
                onDelete(folder.id)
            },
            onDismiss = { editing = null },
        )
    }
}

/** The last whole emoji (or character) in [text], so typing a new emoji replaces the old one. */
private fun lastGrapheme(text: String): String {
    if (text.isEmpty()) return text
    val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
    val end = iterator.last()
    val start = iterator.previous()
    return text.substring(start, end)
}

@Composable
private fun FolderDialog(
    initial: Folder?,
    others: List<Folder>,
    onSave: (name: String, emoji: String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var emoji by remember { mutableStateOf(initial?.emoji.orEmpty()) }
    val problem = folderNameProblem(name, others)
    // Don't nag about a blank name before the user has typed anything.
    val shownProblem = problem.takeUnless { it == FolderNameProblem.Blank }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.add_folder else R.string.edit_folder)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = lastGrapheme(it.trim()) },
                    label = { Text(stringResource(R.string.folder_emoji)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.folder_name)) },
                    singleLine = true,
                    isError = shownProblem != null,
                    supportingText = {
                        Text(
                            when (shownProblem) {
                                FolderNameProblem.Slash -> stringResource(R.string.folder_name_slash)
                                FolderNameProblem.LeadingDot -> stringResource(R.string.folder_name_dot)
                                FolderNameProblem.TooLong -> stringResource(R.string.folder_name_long)
                                FolderNameProblem.Duplicate -> stringResource(R.string.folder_name_duplicate)
                                else -> stringResource(R.string.folder_path_preview, name.trim().ifEmpty { "…" })
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.delete_folder))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, emoji) },
                enabled = problem == null && emoji.isNotBlank(),
                colors = textButtonColors(),
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = textButtonColors()) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
fun SortQueueScreen(
    queue: List<Pair<Uri, String>>,
    folders: List<Folder>,
    onRemove: (Uri) -> Unit,
    onSort: () -> Unit,
    onBack: () -> Unit,
) {
    val byFolder = folders.associateBy { it.id }
    val groups = queue.groupBy({ it.second }, { it.first })
        .mapNotNull { (folderId, uris) -> byFolder[folderId]?.let { it to uris } }
        .sortedBy { (folder, _) -> folders.indexOf(folder) }

    SubScreen(
        title = stringResource(R.string.to_sort),
        onBack = onBack,
        bottomBar = {
            if (queue.isNotEmpty()) {
                Button(
                    onClick = onSort,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                ) {
                    Text(pluralStringResource(R.plurals.sort_photos, queue.size, queue.size))
                }
            }
        },
    ) { innerPadding ->
        if (queue.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.sort_queue_empty), style = MaterialTheme.typography.bodyLarge)
            }
            return@SubScreen
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            groups.forEach { (folder, uris) ->
                item(key = "header-${folder.id}", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    ) {
                        Text(folder.emoji, fontSize = 22.sp)
                        Text(
                            text = "  ${folder.name} · ${uris.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                items(uris, key = { it.toString() }) { uri ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        TextButton(onClick = { onRemove(uri) }, colors = textButtonColors()) {
                            Text(stringResource(R.string.dont_sort))
                        }
                    }
                }
            }
        }
    }
}
