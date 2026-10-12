package io.github.marshallhelenm.photobracket.ui

import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.util.Calendar
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TimePicker
import androidx.compose.material3.RadioButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.selection.selectable
import android.text.format.DateFormat
import io.github.marshallhelenm.photobracket.ui.theme.outlinedButtonColors
import io.github.marshallhelenm.photobracket.ui.theme.textButtonColors
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.marshallhelenm.photobracket.R
import io.github.marshallhelenm.photobracket.Settings

/** The app logo, on a white disc so the line art reads in dark theme too. */
@Composable
fun Logo(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.logo),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White)
            .padding(size / 12),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreen(
    title: String,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = bottomBar,
        content = content,
    )
}

@Composable
fun TrashScreen(
    trash: List<Uri>,
    onRestore: (Uri) -> Unit,
    onRestoreAll: () -> Unit,
    onEmptyTrash: () -> Unit,
    onBack: () -> Unit,
) {
    SubScreen(
        title = stringResource(R.string.trash),
        onBack = onBack,
        bottomBar = {
            if (trash.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedButton(onClick = onRestoreAll, colors = outlinedButtonColors(), modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.restore_all))
                    }
                    Button(onClick = onEmptyTrash, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.empty_trash))
                    }
                }
            }
        },
    ) { innerPadding ->
        if (trash.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.trash_empty))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) {
                items(trash, key = { it.toString() }) { uri ->
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
                        TextButton(onClick = { onRestore(uri) }, colors = textButtonColors()) {
                            Text(stringResource(R.string.restore))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    keptCount: Int,
    notificationsAllowed: Boolean,
    onIncludeKeptChange: (Boolean) -> Unit,
    onForgetKept: () -> Unit,
    onDeleteImmediatelyChange: (Boolean) -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onReminderRandom: () -> Unit,
    onReminderTimeChange: (minuteOfDay: Int) -> Unit,
    onBack: () -> Unit,
) {
    var pickingTime by remember { mutableStateOf(false) }

    SubScreen(title = stringResource(R.string.settings), onBack = onBack) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SwitchSetting(
                title = stringResource(R.string.setting_include_kept),
                summary = stringResource(R.string.setting_include_kept_summary),
                checked = settings.includeKept,
                onCheckedChange = onIncludeKeptChange,
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_forget_kept)) },
                supportingContent = {
                    Text(pluralStringResource(R.plurals.setting_forget_kept_summary, keptCount, keptCount))
                },
                trailingContent = {
                    TextButton(onClick = onForgetKept, enabled = keptCount > 0, colors = textButtonColors()) {
                        Text(stringResource(R.string.reset))
                    }
                },
            )
            HorizontalDivider()
            SwitchSetting(
                title = stringResource(R.string.setting_delete_immediately),
                summary = stringResource(R.string.setting_delete_immediately_summary),
                checked = settings.deleteImmediately,
                onCheckedChange = onDeleteImmediatelyChange,
            )
            HorizontalDivider()
            SwitchSetting(
                title = stringResource(R.string.setting_reminder),
                summary = stringResource(
                    if (settings.reminderEnabled && !notificationsAllowed) {
                        R.string.setting_reminder_blocked
                    } else {
                        R.string.setting_reminder_summary
                    },
                ),
                checked = settings.reminderEnabled,
                onCheckedChange = onReminderEnabledChange,
            )
            if (settings.reminderEnabled) {
                RadioSetting(
                    title = stringResource(R.string.setting_reminder_fixed),
                    summary = formatMinuteOfDay(settings.reminderMinuteOfDay),
                    selected = !settings.reminderRandom,
                    onClick = { pickingTime = true },
                )
                RadioSetting(
                    title = stringResource(R.string.setting_reminder_random),
                    summary = stringResource(R.string.setting_reminder_random_summary),
                    selected = settings.reminderRandom,
                    onClick = onReminderRandom,
                )
            }
        }
    }

    if (pickingTime) {
        val context = LocalContext.current
        val state = rememberTimePickerState(
            initialHour = settings.reminderMinuteOfDay / 60,
            initialMinute = settings.reminderMinuteOfDay % 60,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(colors = textButtonColors(), onClick = {
                    pickingTime = false
                    onReminderTimeChange(state.hour * 60 + state.minute)
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingTime = false }, colors = textButtonColors()) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun formatMinuteOfDay(minuteOfDay: Int): String {
    val context = LocalContext.current
    val time = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        set(Calendar.MINUTE, minuteOfDay % 60)
    }
    return DateFormat.getTimeFormat(context).format(time.time)
}

@Composable
private fun RadioSetting(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier
            .padding(start = 16.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}

@Composable
private fun SwitchSetting(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    SubScreen(title = stringResource(R.string.about), onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Logo(size = 160.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(
                text = stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.about_why))

            Text(stringResource(R.string.about_how_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.about_how))

            Text(stringResource(R.string.about_trash_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.about_trash))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Text(stringResource(R.string.about_trash_system))
            }
        }
    }
}
