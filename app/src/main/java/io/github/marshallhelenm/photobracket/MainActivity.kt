package io.github.marshallhelenm.photobracket

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.ExistingWorkPolicy
import io.github.marshallhelenm.photobracket.ui.AboutScreen
import io.github.marshallhelenm.photobracket.ui.BracketScreen
import io.github.marshallhelenm.photobracket.ui.SettingsScreen
import io.github.marshallhelenm.photobracket.ui.TrashScreen
import io.github.marshallhelenm.photobracket.ui.theme.PhotoBracketTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DailyReminder.schedule(this, ExistingWorkPolicy.KEEP)
        setContent {
            PhotoBracketTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PhotoBracketApp()
                }
            }
        }
    }
}

private enum class Screen { Bracket, Trash, Settings, About }

private val mediaPermissions: Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasMediaPermission(context: Context): Boolean {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    return when {
        // On Android 14+ the user may grant access to only some photos; that's enough to play.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            granted(Manifest.permission.READ_MEDIA_IMAGES) ||
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        else -> mediaPermissions.all(::granted)
    }
}

@Composable
fun PhotoBracketApp(viewModel: BracketViewModel = viewModel()) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(hasMediaPermission(context)) }
    var screen by rememberSaveable { mutableStateOf(Screen.Bracket) }
    // Photos waiting on the system delete dialog.
    var awaitingSystemDelete by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { hasPermission = hasMediaPermission(context) }

    var notificationsAllowed by remember { mutableStateOf(DailyReminder.canNotify(context)) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { notificationsAllowed = DailyReminder.canNotify(context) }

    fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !DailyReminder.canNotify(context)) {
            SettingsStore(context).askedForNotifications = true
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onDeleted(awaitingSystemDelete)
        awaitingSystemDelete = emptyList()
    }

    fun requestDelete(uris: List<Uri>) {
        if (uris.isEmpty()) return
        // Trashed items stay recoverable from the gallery app for ~30 days.
        val request = MediaStore.createTrashRequest(context.contentResolver, uris, true)
        awaitingSystemDelete = uris
        deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            permissionLauncher.launch(mediaPermissions)
            return@LaunchedEffect
        }
        viewModel.load()
        // Reminders default to on, so ask once up front; after that, only when the user turns them on.
        val store = SettingsStore(context)
        if (store.load().reminderEnabled && !store.askedForNotifications) requestNotificationsIfNeeded()
    }
    LaunchedEffect(viewModel) {
        viewModel.deleteRequests.collect { requestDelete(it) }
    }
    BackHandler(enabled = screen != Screen.Bracket) { screen = Screen.Bracket }

    val state = viewModel.uiState
    val goHome = { screen = Screen.Bracket }

    when {
        // Settings and About don't need photo access, so they stay reachable.
        screen == Screen.Settings -> SettingsScreen(
            settings = state.settings,
            keptCount = state.keptCount,
            notificationsAllowed = notificationsAllowed,
            onIncludeKeptChange = viewModel::setIncludeKept,
            onForgetKept = viewModel::forgetKept,
            onDeleteImmediatelyChange = viewModel::setDeleteImmediately,
            onReminderEnabledChange = { enabled ->
                viewModel.setReminderEnabled(enabled)
                if (enabled) requestNotificationsIfNeeded()
            },
            onReminderRandom = { viewModel.setReminderRandom(true) },
            onReminderTimeChange = viewModel::setReminderTime,
            onBack = goHome,
        )
        screen == Screen.About -> AboutScreen(onBack = goHome)
        !hasPermission -> PermissionPrompt(onRequest = { permissionLauncher.launch(mediaPermissions) })
        screen == Screen.Trash -> TrashScreen(
            trash = state.trash,
            onRestore = viewModel::restore,
            onRestoreAll = viewModel::restoreAll,
            onEmptyTrash = { requestDelete(state.trash) },
            onBack = goHome,
        )
        else -> BracketScreen(
            state = state,
            onKeep = viewModel::keep,
            onKeepBoth = viewModel::keepBoth,
            onKeepNeither = viewModel::keepNeither,
            onOpenTrash = { screen = Screen.Trash },
            onOpenSettings = { screen = Screen.Settings },
            onOpenAbout = { screen = Screen.About },
        )
    }

}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.permission_needed), textAlign = TextAlign.Center)
        Button(onClick = onRequest) { Text(stringResource(R.string.grant_access)) }
    }
}
