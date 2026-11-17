package com.gamut.spot

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gamut.core.model.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpotSaveSheet(track: Track, vm: SpotViewModel, onDismiss: () -> Unit) {
    val favorites by vm.favorites.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val contents by vm.playlistTracks.collectAsState()
    var name by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color(0xFF222222)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Saved in", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { name = "" }) { Text("New playlist", color = Green, fontWeight = FontWeight.Bold) }
        }
        LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                val liked = favorites.any { it.isrc == track.isrc }
                Row(Modifier.fillMaxWidth().clickable { vm.favorite(track) }.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SLibraryRow("Liked Songs", "Playlist", null, liked = true) { vm.favorite(track) } }
                    Checkbox(liked, { vm.favorite(track) }, colors = CheckboxDefaults.colors(checkedColor = Green, checkmarkColor = Color.Black))
                }
            }
            items(playlists, key = { it.id }) { playlist ->
                val tracks = contents[playlist.id].orEmpty()
                val included = tracks.any { it.isrc == track.isrc }
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SLibraryRow(playlist.name, "${tracks.size} songs", playlist.coverUri ?: tracks.firstOrNull()?.albumArtUrl) { vm.setPlaylistMembership(playlist.id, track, !included) } }
                    Checkbox(included, { vm.setPlaylistMembership(playlist.id, track, it) }, colors = CheckboxDefaults.colors(checkedColor = Green, checkmarkColor = Color.Black))
                }
            }
        }
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) { Text("Done") }
    }
    name?.let { value -> AlertDialog(onDismissRequest = { name = null }, containerColor = Card, title = { Text("New playlist") }, text = { OutlinedTextField(value, { name = it }, label = { Text("Playlist name") }, singleLine = true) }, confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { vm.createPlaylistWithTrack(value, track); name = null }) { Text("Create and add") } }, dismissButton = { TextButton(onClick = { name = null }) { Text("Cancel", color = Color.White) } }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpotArtistSheet(track: Track, vm: SpotViewModel, onDismiss: () -> Unit, onArtist: () -> Unit, onAlbum: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color(0xFF222222)) {
        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
            item { SHeading("Artists on this track") }
            items(vm.artistChoices(track)) { artist ->
                Row(Modifier.fillMaxWidth().clickable { vm.openArtist(artist); onArtist() }.padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Person, null, Modifier.size(36.dp))
                    Column(Modifier.weight(1f).padding(start = 18.dp)) { Text(artist.name, style = MaterialTheme.typography.titleMedium); Text("Artist", color = Muted, style = MaterialTheme.typography.bodySmall) }
                    Icon(Icons.Outlined.ChevronRight, null)
                }
            }
            if (!track.album.isNullOrBlank()) item { SLibraryRow(track.album.orEmpty(), "Album", track.albumArtUrl) { vm.openAlbum(track); onAlbum() } }
        }
    }
}
