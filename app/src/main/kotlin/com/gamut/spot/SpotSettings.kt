package com.gamut.spot

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gamut.BuildConfig
import com.gamut.core.player.StreamQuality

@Composable
internal fun SpotSettings(vm: SpotViewModel, onBack: () -> Unit, onImport: () -> Unit) {
    val settings by vm.playbackSettings.state.collectAsState()
    val context = LocalContext.current
    val updates = remember(context) { updateChannel(context) }
    var quality by remember { mutableStateOf(false) }
    var license by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Row { SIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack); Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) } }
        item { SHeading("Your library") }
        item { MenuRow(Icons.Outlined.Download, "Import from Spotify", onImport) }
        item { SHeading("Audio quality") }
        item { ListItem(headlineContent = { Text("Streaming quality") }, supportingContent = { Text(settings.streamQuality.label, color = Muted) }, modifier = Modifier.clickable { quality = true }) }
        item { SettingSwitch("Preload upcoming songs", "Prepare sources before you tap play", settings.preload) { value -> vm.playbackSettings.update { it.copy(preload = value) } } }
        item { SettingSwitch("Preload on Wi-Fi only", "Limit background source preparation", settings.preloadUnmeteredOnly) { value -> vm.playbackSettings.update { it.copy(preloadUnmeteredOnly = value) } } }
        item { SettingSwitch("Skip silence", "Skip silent sections during playback", settings.skipSilence) { value -> vm.playbackSettings.update { it.copy(skipSilence = value) } } }
        item { SettingSwitch("Mono audio", "Play the same audio in both channels", settings.mono) { value -> vm.playbackSettings.update { it.copy(mono = value) } } }
        item { SHeading("About") }
        item { ListItem(headlineContent = { Text("Spot Music") }, supportingContent = { Text("Version ${BuildConfig.VERSION_NAME}", color = Muted) }) }
        item { ListItem(headlineContent = { Text("Signing identity") }, supportingContent = { Text("$updates · updates must use the same signing key. Keep this app installed if an update is rejected.", color = Muted) }) }
        item { ListItem(headlineContent = { Text("Gamut · AGPL-3.0") }, supportingContent = { Text("Playback foundation and original project", color = Muted) }, modifier = Modifier.clickable { license = "Gamut-AGPL-3.0.txt" }) }
        item { ListItem(headlineContent = { Text("Aran404/SpotAPI · GPL-3.0") }, supportingContent = { Text("Spotify catalog and collection protocol adaptation", color = Muted) }, modifier = Modifier.clickable { license = "SpotAPI-GPL-3.0.txt" }) }
        item { ListItem(headlineContent = { Text("ReadAlong · GPL-3.0") }, supportingContent = { Text("Cover flip and mini-player container animation adaptation", color = Muted) }, modifier = Modifier.clickable { license = "ReadAlong-GPL-3.0.txt" }) }
        item { ListItem(headlineContent = { Text("Offline podcast subtitles · MIT") }, supportingContent = { Text("Whisper Tiny runs on this device. Audio is never sent to a transcription server.", color = Muted) }, modifier = Modifier.clickable { license = "Whisper-MIT.txt" }) }
        item { MenuRow(Icons.Outlined.Code, "Source code") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lukeherc-cpu/SPOT-MUSIC-PLAYER"))) } }
        item { MenuRow(Icons.Outlined.BugReport, "Report a problem") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lukeherc-cpu/SPOT-MUSIC-PLAYER/issues/new"))) } }
    }
    if (quality) AlertDialog(onDismissRequest = { quality = false }, containerColor = Card, title = { Text("Streaming quality") }, text = { Column { StreamQuality.entries.forEach { choice -> Row(Modifier.fillMaxWidth().clickable { vm.playbackSettings.update { it.copy(streamQuality = choice) }; quality = false }.padding(vertical = 10.dp)) { RadioButton(settings.streamQuality == choice, null); Column(Modifier.padding(start = 12.dp)) { Text(choice.label); Text(choice.hint, style = MaterialTheme.typography.bodySmall, color = Muted) } } } } }, confirmButton = { TextButton(onClick = { quality = false }) { Text("Done") } })
    license?.let { file ->
        val text = remember(file) { context.assets.open(file).bufferedReader().use { it.readText() } }
        AlertDialog(onDismissRequest = { license = null }, containerColor = Card, title = { Text(file.substringBefore('-')) }, text = { LazyColumn(Modifier.heightIn(max = 450.dp)) { item { Text(text, style = MaterialTheme.typography.bodySmall) } } }, confirmButton = { TextButton(onClick = { license = null }) { Text("Close") } })
    }
}
@Composable private fun SettingSwitch(title: String, subtitle: String, enabled: Boolean, change: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(subtitle, color = Muted) }, trailingContent = { Switch(enabled, change) }, modifier = Modifier.clickable { change(!enabled) })
}
