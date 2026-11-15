package com.lahn.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

private val Ink = Color(0xFF090A0C)
private val Panel = Color(0xFF17191D)
private val Muted = Color(0xFF969BA4)
private val Silver = Color(0xFFE7E9ED)

data class Track(val title: String, val artist: String, val preview: String, val page: String)
private data class DownloadItem(val title: String, val status: String, val location: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 10, 12)
        window.navigationBarColor = android.graphics.Color.rgb(9, 10, 12)
        setContent { LahnApp() }
    }
}

@Composable
private fun LahnApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { ExoPlayer.Builder(context).build() }
    var query by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    var library by remember { mutableStateOf(loadLibrary(context)) }
    var downloads by remember { mutableStateOf(emptyList<DownloadItem>()) }
    var selected by remember { mutableStateOf<Track?>(null) }
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("ابحث عن أغنيتك المفضلة") }
    var tab by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(tab) {
        if (tab == 2) downloads = loadDownloads(context)
        if (tab == 3) library = loadLibrary(context)
    }

    fun play(track: Track) {
        selected = track
        player.setMediaItem(MediaItem.fromUri(track.preview))
        player.prepare()
        player.play()
        playing = true
    }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Ink, surface = Panel, primary = Silver,
        onPrimary = Ink, onBackground = Silver, onSurface = Silver
    )) {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
            Scaffold(
                containerColor = Ink,
                bottomBar = {
                    NavigationBar(containerColor = Panel, contentColor = Silver) {
                        listOf(Icons.Default.Home to "الرئيسية", Icons.Default.Search to "البحث", Icons.Default.Download to "التنزيلات", Icons.Default.LibraryMusic to "مكتبتي").forEachIndexed { index, item ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Icon(item.first, contentDescription = item.second) },
                                label = { Text(item.second, fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(selectedIconColor = Ink, indicatorColor = Silver, unselectedIconColor = Muted, selectedTextColor = Silver, unselectedTextColor = Muted)
                            )
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("لَحْن", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Silver)
                            Text("موسيقاك، كما تحب", color = Muted, fontSize = 12.sp)
                        }
                        Box(Modifier.size(44.dp).background(Panel, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Silver, modifier = Modifier.size(25.dp))
                        }
                    }
                    Spacer(Modifier.height(26.dp))
                    Text(when(tab) { 0 -> "اكتشف الموسيقى"; 1 -> "ابحث واستمع"; 2 -> "التنزيلات"; else -> "مكتبتك" }, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))

                    when (tab) {
                        0, 1 -> {
                            OutlinedTextField(
                                value = query, onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("اسم الأغنية أو الفنان", color = Muted) },
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        if (query.isNotBlank()) scope.launch {
                                            loading = true
                                            message = "جارٍ البحث..."
                                            tracks = searchTracks(query)
                                            loading = false
                                            message = if (tracks.isEmpty()) "لم نعثر على نتائج. حاول باسم آخر." else "نتائج البحث"
                                        }
                                    }) { Icon(Icons.Default.ArrowForward, "بحث", tint = Silver) }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Silver, unfocusedBorderColor = Color(0xFF343840),
                                    focusedTextColor = Silver, unfocusedTextColor = Silver, cursorColor = Silver
                                )
                            )
                            Spacer(Modifier.height(14.dp))
                            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Silver, trackColor = Panel)
                            Text(message, color = Muted, fontSize = 12.sp)
                            if (tracks.isEmpty()) EmptyState("ابحث عن أغنية أو فنان")
                            else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(tracks) { track ->
                                    TrackRow(track, isSaved = library.any { it.preview == track.preview },
                                        onPlay = { play(track) },
                                        onSave = {
                                            if (library.none { it.preview == track.preview }) {
                                                library = (listOf(track) + library).take(300)
                                                saveLibrary(context, library)
                                            }
                                        },
                                        onDownload = {
                                            downloadTrack(context, track)
                                            scope.launch { kotlinx.coroutines.delay(700); downloads = loadDownloads(context) }
                                        })
                                }
                            }
                        }
                        2 -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("الملفات التي نزّلتها عبر لَحْن", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = { downloads = loadDownloads(context) }) { Icon(Icons.Default.Refresh, "تحديث", tint = Silver) }
                            }
                            if (downloads.isEmpty()) EmptyState("لا توجد تنزيلات حتى الآن")
                            else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(downloads) { item ->
                                    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.DownloadDone, null, tint = Silver)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(item.title, color = Silver, maxLines = 1, fontWeight = FontWeight.SemiBold)
                                            Text(item.status, color = Muted, fontSize = 12.sp)
                                            if (item.location.isNotBlank()) Text(item.location, color = Muted, fontSize = 10.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            Text("احفظ المقاطع هنا لتصل إليها بسرعة", color = Muted, fontSize = 12.sp)
                            if (library.isEmpty()) EmptyState("مكتبتك فارغة. احفظ أي مقطع من نتائج البحث.")
                            else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(library) { track ->
                                    TrackRow(track, isSaved = true, onPlay = { play(track) },
                                        onSave = {
                                            library = library.filterNot { it.preview == track.preview }
                                            saveLibrary(context, library)
                                        },
                                        onDownload = { downloadTrack(context, track) })
                                }
                            }
                        }
                    }

                    selected?.let { track ->
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GraphicEq, null, tint = Silver)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, color = Silver, maxLines = 1, fontSize = 13.sp)
                                Text(track.artist, color = Muted, fontSize = 11.sp)
                            }
                            IconButton(onClick = {
                                if (player.isPlaying) { player.pause(); playing = false }
                                else { player.play(); playing = true }
                            }) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "تشغيل أو إيقاف", tint = Silver) }
                            IconButton(onClick = { player.seekTo(0); player.play(); playing = true }) { Icon(Icons.Default.Replay, "إعادة", tint = Muted) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, isSaved: Boolean, onPlay: () -> Unit, onSave: () -> Unit, onDownload: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).background(Color(0xFF292D33), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, null, tint = Silver)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = Silver, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1)
        }
        IconButton(onClick = onPlay) { Icon(Icons.Default.PlayCircle, "تشغيل المعاينة", tint = Silver, modifier = Modifier.size(27.dp)) }
        IconButton(onClick = onSave) { Icon(if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkAdd, "حفظ في المكتبة أو إزالة", tint = if (isSaved) Silver else Muted) }
        IconButton(onClick = onDownload) { Icon(Icons.Default.Download, "تنزيل المعاينة", tint = Muted) }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().heightIn(min = 260.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Headphones, null, tint = Color(0xFF555B64), modifier = Modifier.size(60.dp))
            Spacer(Modifier.height(12.dp))
            Text(text, color = Muted, textAlign = TextAlign.Center)
        }
    }
}

private fun loadLibrary(context: Context): List<Track> {
    return try {
        val array = JSONArray(context.getSharedPreferences("lahn_library", Context.MODE_PRIVATE).getString("tracks", "[]"))
        (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            Track(o.getString("title"), o.getString("artist"), o.getString("preview"), o.optString("page", ""))
        }
    } catch (_: Exception) { emptyList() }
}

private fun saveLibrary(context: Context, tracks: List<Track>) {
    val array = JSONArray()
    tracks.forEach { t ->
        array.put(JSONObject().put("title", t.title).put("artist", t.artist).put("preview", t.preview).put("page", t.page))
    }
    context.getSharedPreferences("lahn_library", Context.MODE_PRIVATE).edit().putString("tracks", array.toString()).apply()
}

private fun loadDownloads(context: Context): List<DownloadItem> {
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val cursor = manager.query(DownloadManager.Query())
    val result = mutableListOf<DownloadItem>()
    cursor?.use {
        val titleCol = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
        val statusCol = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
        val uriCol = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
        val descriptionCol = it.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION)
        while (it.moveToNext()) {
            val description = if (descriptionCol >= 0) it.getString(descriptionCol).orEmpty() else ""
            if (!description.contains("لَحْن")) continue
            val status = when (it.getInt(statusCol)) {
                DownloadManager.STATUS_SUCCESSFUL -> "اكتمل التنزيل"
                DownloadManager.STATUS_FAILED -> "فشل التنزيل"
                DownloadManager.STATUS_RUNNING -> "جارٍ التنزيل"
                DownloadManager.STATUS_PAUSED -> "متوقف مؤقتاً"
                else -> "قيد الانتظار"
            }
            val local = if (uriCol >= 0) it.getString(uriCol).orEmpty().substringAfterLast('/') else ""
            result.add(DownloadItem(if (titleCol >= 0) it.getString(titleCol).orEmpty() else "ملف صوتي", status, local))
        }
    }
    return result
}

private suspend fun searchTracks(term: String): List<Track> = withContext(Dispatchers.IO) {
    try {
        val encoded = URLEncoder.encode(term, "UTF-8")
        val response = URL("https://itunes.apple.com/search?term=$encoded&entity=song&limit=30").readText()
        val results = JSONObject(response).getJSONArray("results")
        (0 until results.length()).mapNotNull { index ->
            val item = results.getJSONObject(index)
            val preview = item.optString("previewUrl")
            if (preview.isBlank()) null else Track(
                item.optString("trackName", "مقطع صوتي"),
                item.optString("artistName", "فنان غير معروف"),
                preview,
                item.optString("trackViewUrl", "")
            )
        }
    } catch (_: Exception) { emptyList() }
}

private fun downloadTrack(context: Context, track: Track) {
    try {
        val request = DownloadManager.Request(Uri.parse(track.preview))
            .setTitle(track.title)
            .setDescription("تنزيل معاينة صوتية من لَحْن")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "LAHN/${safeName(track.title)}.m4a")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    } catch (_: Exception) { }
}

private fun safeName(value: String): String = value.map { if (it in charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')) '_' else it }.joinToString("").take(70)
