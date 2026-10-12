package io.github.marshallhelenm.photobracket.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import io.github.marshallhelenm.photobracket.PhotoRepository
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import io.github.marshallhelenm.photobracket.R
import kotlinx.coroutines.launch

private const val MAX_ZOOM = 5f

/**
 * Full-screen view of the current pair. Swipe, or tap the photo, to flip to the other one.
 * Pinch to zoom; while zoomed in, dragging pans the photo instead of flipping.
 */
@Composable
fun PhotoViewer(
    photos: List<Uri>,
    initialPage: Int,
    tagging: FolderTagging?,
    onKeep: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val pagerState = rememberPagerState(initialPage = initialPage) { photos.size }
        val scope = rememberCoroutineScope()
        var zoomed by remember { mutableStateOf(false) }
        // Tapping can flip pages while zoomed in; the new page always starts unzoomed.
        LaunchedEffect(pagerState.settledPage) { zoomed = false }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding(),
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !zoomed,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ZoomableImage(
                    uri = photos[page],
                    contentDescription = stringResource(R.string.photo_description, page + 1),
                    isCurrentPage = pagerState.settledPage == page,
                    onZoomedChange = { if (pagerState.settledPage == page) zoomed = it },
                    onTap = {
                        scope.launch { pagerState.animateScrollToPage((page + 1) % photos.size) }
                    },
                )
            }

            // Dark band so the controls stay readable over bright photos.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))),
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
                }
                Text(
                    text = stringResource(R.string.photo_position, pagerState.currentPage + 1, photos.size) +
                        tagging?.folderFor(photos[pagerState.currentPage])?.let { "  ·  ${it.emoji} ${it.name}" }.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Center).padding(vertical = 12.dp),
                )
                PhotoMenu(
                    uri = photos[pagerState.currentPage],
                    tagging = tagging,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
            Button(
                onClick = {
                    onKeep(photos[pagerState.currentPage])
                    onDismiss()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.keep_this))
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    uri: Uri,
    contentDescription: String,
    isCurrentPage: Boolean,
    onZoomedChange: (Boolean) -> Unit,
    onTap: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Zoom resets once the user has flipped to the other photo.
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    AsyncImage(
        model = uri,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        // One finger at normal size is left alone so the pager can swipe.
                        if (event.changes.size > 1 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                            val maxX = size.width * (scale - 1) / 2
                            val maxY = size.height * (scale - 1) / 2
                            val panned = offset + event.calculatePan()
                            offset = Offset(panned.x.coerceIn(-maxX, maxX), panned.y.coerceIn(-maxY, maxY))
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                            onZoomedChange(scale > 1f)
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .clickable(interactionSource = null, indication = null, onClick = onTap)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}

@Composable
private fun PhotoMenu(uri: Uri, tagging: FolderTagging?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var choosingFolder by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options), tint = Color.White)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.copy)) },
                onClick = {
                    expanded = false
                    PhotoActions.copy(context, uri)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share)) },
                onClick = {
                    expanded = false
                    PhotoActions.share(context, uri)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.show_in_folder)) },
                onClick = {
                    expanded = false
                    scope.launch {
                        val folder = withContext(Dispatchers.IO) { PhotoRepository(context).folderOf(uri) }
                        PhotoActions.showInFolder(context, folder)
                    }
                },
            )
            if (tagging != null && tagging.folders.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_into)) },
                    onClick = {
                        expanded = false
                        choosingFolder = true
                    },
                )
            }
        }
        if (tagging != null) {
            FolderMenu(
                uri = uri,
                tagging = tagging,
                expanded = choosingFolder,
                onDismiss = { choosingFolder = false },
            )
        }
    }
}
