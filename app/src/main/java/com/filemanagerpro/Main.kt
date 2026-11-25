@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.filemanagerpro

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File

/* ───────────────────────────── State & logika ───────────────────────────── */

enum class Screen { Home, Browse, Analyzer }
enum class ShState { Off, NoPerm, Ready }
enum class SortBy(val label: String) { Name("Nama"), Size("Ukuran"), Date("Tanggal") }
class Clip(val items: List<FItem>, val cut: Boolean)

sealed interface Viewer {
    class Pic(val i: FItem) : Viewer
    class Play(val i: FItem) : Viewer
    class Edit(val i: FItem) : Viewer
    class ApkV(val i: FItem) : Viewer
}

class VM(private val app: Application) : AndroidViewModel(app) {
    val fs = Fs(app)
    private val prefs = app.getSharedPreferences("fmp", Context.MODE_PRIVATE)

    var screen by mutableStateOf(Screen.Home)
    var stack by mutableStateOf(listOf<FItem>())
    var raw by mutableStateOf(listOf<FItem>())
        private set
    var selected by mutableStateOf(setOf<String>())
    var clip by mutableStateOf<Clip?>(null)
    var sort by mutableStateOf(SortBy.Name)
    var asc by mutableStateOf(true)
    var hidden by mutableStateOf(false)
    var searchOpen by mutableStateOf(false)
    var query by mutableStateOf("")
    var results by mutableStateOf<List<FItem>?>(null)
    var searching by mutableStateOf(false)
    var busy by mutableStateOf<String?>(null)
    var msg by mutableStateOf<String?>(null)
    var viewer by mutableStateOf<Viewer?>(null)
    var granted by mutableStateOf(true)
    var safRoots by mutableStateOf(prefs.getStringSet("saf", emptySet())!!.toList())
    var analysis by mutableStateOf<Analysis?>(null)
    var analyzing by mutableStateOf(false)
    private var searchJob: Job? = null

    /* Shizuku */
    var shizuku by mutableStateOf(ShState.Off)
    private val binderL = Shizuku.OnBinderReceivedListener { updateShizuku() }
    private val deadL = Shizuku.OnBinderDeadListener { fs.service = null; shizuku = ShState.Off }
    private val permL = Shizuku.OnRequestPermissionResultListener { _, _ -> updateShizuku() }
    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
            if (b != null && b.pingBinder()) { fs.service = IFileService.Stub.asInterface(b); shizuku = ShState.Ready }
        }
        override fun onServiceDisconnected(n: ComponentName?) { fs.service = null; updateShizuku() }
    }

    init {
        Shizuku.addBinderReceivedListenerSticky(binderL)
        Shizuku.addBinderDeadListener(deadL)
        Shizuku.addRequestPermissionResultListener(permL)
        Thread { app.cacheDir.listFiles()?.filter { it.name.startsWith("tmp_") }?.forEach { it.deleteRecursively() } }.start()
    }

    override fun onCleared() {
        Shizuku.removeBinderReceivedListener(binderL)
        Shizuku.removeBinderDeadListener(deadL)
        Shizuku.removeRequestPermissionResultListener(permL)
    }

    fun updateShizuku() {
        try {
            when {
                !Shizuku.pingBinder() -> { fs.service = null; shizuku = ShState.Off }
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> shizuku = ShState.NoPerm
                fs.service != null -> shizuku = ShState.Ready
                else -> Shizuku.bindUserService(
                    Shizuku.UserServiceArgs(ComponentName(app.packageName, FileService::class.java.name))
                        .daemon(false).processNameSuffix("files").version(1),
                    conn,
                )
            }
        } catch (e: Throwable) { shizuku = ShState.Off }
    }

    fun askShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                val i = app.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                if (i != null) app.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else msg = "Shizuku belum terpasang. Pasang aplikasi Shizuku lalu aktifkan lewat Wireless debugging."
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                msg = "Izin ditolak. Aktifkan manual di aplikasi Shizuku (daftar aplikasi berizin)."
            } else Shizuku.requestPermission(1)
        } catch (e: Throwable) { msg = "Shizuku error: ${e.message}" }
    }

    val cur: FItem? get() = stack.lastOrNull()

    fun shown(): List<FItem> {
        val f = (results ?: raw).filter { hidden || !it.name.startsWith(".") }
        val cmp: Comparator<FItem> = when (sort) {
            SortBy.Name -> compareBy<FItem> { it.name.lowercase() }
            SortBy.Size -> compareBy<FItem> { it.size }
            SortBy.Date -> compareBy<FItem> { it.modified }
        }
        return f.sortedWith(if (asc) cmp else cmp.reversed()).sortedByDescending { it.isDir }
    }

    fun refreshPerm() {
        granted = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else app.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    /* navigasi */
    fun load() {
        val d = cur ?: return
        viewModelScope.launch { raw = withContext(Dispatchers.IO) { runCatching { fs.list(d) }.getOrDefault(emptyList()) } }
    }

    fun go(d: FItem, reset: Boolean = false) {
        closeSearch()
        stack = if (reset) listOf(d) else stack + d
        screen = Screen.Browse; selected = emptySet(); raw = emptyList(); load()
    }

    fun jump(index: Int) { closeSearch(); stack = stack.take(index + 1); selected = emptySet(); raw = emptyList(); load() }

    fun back() {
        when {
            selected.isNotEmpty() -> selected = emptySet()
            searchOpen -> closeSearch()
            screen == Screen.Browse && stack.size > 1 -> jump(stack.size - 2)
            else -> { screen = Screen.Home; stack = emptyList(); raw = emptyList() }
        }
    }

    fun toggle(i: FItem) { selected = if (i.id in selected) selected - i.id else selected + i.id }

    /* pencarian */
    fun search() {
        val d = cur ?: return
        val q = query.trim()
        searchJob?.cancel()
        if (q.isEmpty()) { results = null; searching = false; return }
        searching = true
        searchJob = viewModelScope.launch {
            results = withContext(Dispatchers.IO) { fs.search(d, q) { !isActive } }
            searching = false
        }
    }

    fun closeSearch() { searchJob?.cancel(); searching = false; searchOpen = false; query = ""; results = null }

    /* operasi file */
    private fun task(label: String, after: () -> Unit = {}, block: suspend () -> String?) {
        viewModelScope.launch {
            busy = label
            val r = withContext(Dispatchers.IO) {
                try { block() } catch (e: Exception) { "Gagal: ${e.message ?: e.javaClass.simpleName}" }
            }
            busy = null
            if (r != null) msg = r
            selected = emptySet()
            if (results != null) closeSearch()
            load(); after()
        }
    }

    fun copyCut(items: List<FItem>, cut: Boolean) {
        clip = Clip(items, cut); selected = emptySet(); msg = if (cut) "Dipotong" else "Disalin"
    }

    fun paste() {
        val c = clip ?: return
        val d = cur ?: return
        task(if (c.cut) "Memindahkan…" else "Menyalin…", after = { if (c.cut) clip = null }) {
            var fail = 0
            for (i in c.items) {
                if (fs.isInside(d, i)) { fail++; continue }
                val ok = try { if (c.cut) fs.move(i, d) else fs.copy(i, d) } catch (e: Exception) { false }
                if (!ok) fail++
            }
            if (fail > 0) "$fail item gagal diproses" else "Selesai"
        }
    }

    fun delete(items: List<FItem>) = task("Menghapus…") {
        val f = items.count { !fs.delete(it) }
        if (f > 0) "$f item gagal dihapus" else "Dihapus: ${items.size} item"
    }

    fun rename(i: FItem, n: String) = task("Mengganti nama…") { if (fs.rename(i, n)) null else "Gagal mengganti nama" }

    fun mkdir(n: String) {
        val d = cur ?: return
        task("Membuat folder…") { if (fs.mkdir(d, n) != null) null else "Gagal membuat folder" }
    }

    fun zip(items: List<FItem>) {
        val d = cur ?: return
        val base = if (items.size == 1) items[0].name.substringBeforeLast('.') else "Arsip"
        task("Mengompres…") { fs.zip(items, d, "$base.zip"); "ZIP dibuat" }
    }

    fun unzip(i: FItem) {
        val d = cur ?: return
        task("Mengekstrak…") { if (i.ext == "rar") fs.unrar(i, d) else fs.unzip(i, d); "Selesai diekstrak" }
    }

    fun saveText(i: FItem, t: String) = task("Menyimpan…") { fs.overwrite(i).use { it.write(t.toByteArray()) }; "Tersimpan" }

    fun openItem(i: FItem, ctx: Context) {
        if (i.isRemote && !i.isDir && !(i.ext in TEXT_EXT && i.size <= TEXT_MAX)) {
            var local: FItem? = null
            task("Menyiapkan file…", after = { local?.let { openItem(it, ctx) } }) { local = fileItem(fs.toLocal(i)); null }
            return
        }
        when {
            i.isDir -> go(i)
            i.kind == Kind.Image -> viewer = Viewer.Pic(i)
            i.kind == Kind.Video || i.kind == Kind.Audio -> viewer = Viewer.Play(i)
            i.kind == Kind.Apk -> viewer = Viewer.ApkV(i)
            i.ext in TEXT_EXT && i.size <= TEXT_MAX -> viewer = Viewer.Edit(i)
            else -> openExternal(ctx, i)
        }
    }

    /* SAF */
    fun safItem(s: String): FItem? = DocumentFile.fromTreeUri(app, Uri.parse(s))?.let(::docItem)

    fun addSaf(uri: Uri) {
        try {
            app.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {}
        val s = uri.toString()
        if (s !in safRoots) { safRoots = safRoots + s; prefs.edit().putStringSet("saf", safRoots.toSet()).apply() }
        safItem(s)?.let { go(it, true) }
    }

    /** Android/data & Android/obb: coba akses langsung, lalu SAF yang sudah tersimpan, lalu minta izin lewat pemilih folder. */
    fun androidDir(sub: String, pick: (Uri) -> Unit) {
        val f = File(Environment.getExternalStorageDirectory(), "Android/$sub")
        if (f.listFiles() != null) { go(fileItem(f), true); return }
        if (shizuku == ShState.Ready && fs.service != null) { go(fs.remoteRoot(f.path), true); return }
        val id = "primary:Android/$sub"
        val have = app.contentResolver.persistedUriPermissions.firstOrNull {
            it.isWritePermission && runCatching { DocumentsContract.getTreeDocumentId(it.uri) }.getOrNull() == id
        }
        if (have != null) {
            val item = safItem(have.uri.toString())
            if (item != null) { go(item, true); return }
        }
        msg = "Pilih folder $sub lalu tekan “Gunakan folder ini”. Jika tombolnya nonaktif, Android di perangkat ini memblokir folder tersebut."
        pick(DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", id))
    }

    /* analisis */
    fun analyze() {
        if (analyzing) return
        analyzing = true
        viewModelScope.launch {
            analysis = withContext(Dispatchers.IO) {
                runCatching { fs.analyze(Environment.getExternalStorageDirectory()) { !isActive } }.getOrNull()
            }
            analyzing = false
        }
    }

    fun deleteFromAnalysis(i: FItem) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { fs.delete(i) }.getOrDefault(false) }
            if (ok) analysis = analysis?.let { a ->
                a.copy(
                    big = a.big.filter { it.id != i.id },
                    dupes = a.dupes.map { g -> g.filter { it.id != i.id } }.filter { it.size > 1 },
                )
            }
            msg = if (ok) "Dihapus" else "Gagal menghapus"
        }
    }
}

fun uriFor(c: Context, i: FItem): Uri =
    i.file?.let { FileProvider.getUriForFile(c, "${c.packageName}.fp", it) } ?: i.doc!!.uri

fun openExternal(c: Context, i: FItem) {
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uriFor(c, i), mimeOf(i.name))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try { c.startActivity(Intent.createChooser(intent, i.name)) }
    catch (e: Exception) { Toast.makeText(c, "Tidak ada aplikasi untuk membuka file ini", Toast.LENGTH_SHORT).show() }
}

fun share(c: Context, items: List<FItem>) {
    val files = items.filter { !it.isDir && !it.isRemote }
    if (files.isEmpty()) { Toast.makeText(c, "Pilih file biasa (folder & file Shizuku disalin dulu)", Toast.LENGTH_SHORT).show(); return }
    val uris = ArrayList(files.map { uriFor(c, it) })
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
        .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    c.startActivity(Intent.createChooser(intent, "Bagikan"))
}

/* ───────────────────────────── Activity & tema ───────────────────────────── */

class MainActivity : ComponentActivity() {
    private val vm: VM by viewModels()
    private val treeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addSaf(uri)
    }
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refreshPerm() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Coil.setImageLoader(ImageLoader.Builder(this).components { add(VideoFrameDecoder.Factory()) }.crossfade(true).build())
        setContent { FmTheme { App(vm, pick = { treeLauncher.launch(it) }, askPerm = { askPerm() }) } }
    }

    override fun onResume() { super.onResume(); vm.refreshPerm(); vm.updateShizuku() }

    private fun askPerm() {
        if (Build.VERSION.SDK_INT >= 30) {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
        } else {
            permLauncher.launch(arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }
}

@Composable
fun FmTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(c) else dynamicLightColorScheme(c)
        dark -> darkColorScheme(primary = Color(0xFF8AB4F8), secondaryContainer = Color(0xFF2B3F5C))
        else -> lightColorScheme(primary = Color(0xFF1A73E8), secondaryContainer = Color(0xFFD3E3FD))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/* ───────────────────────────── UI ───────────────────────────── */

@Composable
fun App(vm: VM, pick: (Uri?) -> Unit, askPerm: () -> Unit) {
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.msg) { vm.msg?.let { snack.showSnackbar(it); vm.msg = null } }
    BackHandler(enabled = vm.screen != Screen.Home || vm.selected.isNotEmpty()) { vm.back() }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (vm.screen) {
                Screen.Home -> HomeScreen(vm, askPerm, pick)
                Screen.Browse -> BrowseScreen(vm)
                Screen.Analyzer -> AnalyzerScreen(vm)
            }
        }
    }

    vm.busy?.let {
        AlertDialog(
            onDismissRequest = {}, confirmButton = {},
            title = { Text(it) }, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        )
    }
    when (val v = vm.viewer) {
        is Viewer.Pic -> ImageViewer(vm, v.i)
        is Viewer.Play -> Player(vm, v.i)
        is Viewer.Edit -> Editor(vm, v.i)
        is Viewer.ApkV -> ApkDialog(vm, v.i)
        null -> {}
    }
}

private class Sc(val title: String, val icon: ImageVector, val act: () -> Unit)

fun iconOf(k: Kind): ImageVector = when (k) {
    Kind.Folder -> Icons.Rounded.Folder
    Kind.Image -> Icons.Rounded.Image
    Kind.Video -> Icons.Rounded.Movie
    Kind.Audio -> Icons.Rounded.MusicNote
    Kind.Doc -> Icons.Rounded.Description
    Kind.Apk -> Icons.Rounded.Android
    Kind.Archive -> Icons.Rounded.Archive
    Kind.Other -> Icons.Rounded.InsertDriveFile
}

@Composable
fun HomeScreen(vm: VM, askPerm: () -> Unit, pick: (Uri?) -> Unit) {
    val internal = Environment.getExternalStorageDirectory()
    val stat = remember(vm.screen) {
        runCatching { StatFs(internal.path).let { it.totalBytes to it.availableBytes } }.getOrDefault(0L to 0L)
    }
    val cards = buildList<Sc> {
        add(Sc("Penyimpanan internal", Icons.Rounded.Storage) { vm.go(fileItem(internal), true) })
        val std = listOf(
            "Download" to Icons.Rounded.Download, "Documents" to Icons.Rounded.Description, "DCIM" to Icons.Rounded.Image,
            "Movies" to Icons.Rounded.Movie, "Music" to Icons.Rounded.MusicNote, "Pictures" to Icons.Rounded.Image,
        )
        for ((n, ic) in std) File(internal, n).takeIf { it.isDirectory }?.let { f -> add(Sc(n, ic) { vm.go(fileItem(f), true) }) }
        add(Sc("Android/data", Icons.Rounded.Android) { vm.androidDir("data") { pick(it) } })
        add(Sc("Android/obb", Icons.Rounded.Android) { vm.androidDir("obb") { pick(it) } })
        vm.fs.sdRoots().forEach { r -> add(Sc("Kartu SD", Icons.Rounded.SdCard) { vm.go(fileItem(r), true) }) }
        vm.safRoots.forEach { s ->
            val label = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(s)).replace("primary:", "Internal/") }
                .getOrDefault("Folder SAF")
            add(Sc(label, Icons.Rounded.Folder) { vm.safItem(s)?.let { vm.go(it, true) } })
        }
        add(Sc("Tambah folder (SAF)", Icons.Rounded.CreateNewFolder) { pick(null) })
        add(Sc("Analisis penyimpanan", Icons.Rounded.PieChart) { vm.screen = Screen.Analyzer; vm.analyze() })
    }
    val full = { GridItemSpan(2) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { full() }) {
            Column(Modifier.padding(top = 24.dp, bottom = 4.dp)) {
                Text("File Manager Pro", style = MaterialTheme.typography.headlineMedium)
                Text("Kelola file, folder aplikasi, dan penyimpanan", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(span = { full() }) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    val used = stat.first - stat.second
                    Text("Penyimpanan internal", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(8.dp))
                    LinearProgressIndicator(progress = { if (stat.first > 0) used.toFloat() / stat.first else 0f }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.size(6.dp))
                    Text("${fmtSize(used)} terpakai dari ${fmtSize(stat.first)} • ${fmtSize(stat.second)} kosong", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (!vm.granted) item(span = { full() }) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Izin akses semua file diperlukan", style = MaterialTheme.typography.titleMedium)
                    Text("Tanpa izin ini aplikasi tidak bisa menampilkan isi penyimpanan.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = askPerm) { Text("Beri izin") }
                }
            }
        }
        item(span = { full() }) {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Shizuku", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when (vm.shizuku) {
                                ShState.Off -> "Belum aktif. Aktifkan Shizuku (tanpa root via Wireless debugging, atau root) untuk membuka Android/data & obb di Android 11+."
                                ShState.NoPerm -> "Aktif, tetapi izin untuk aplikasi ini belum diberikan."
                                ShState.Ready -> "Terhubung. Android/data & Android/obb bisa diakses langsung."
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    when (vm.shizuku) {
                        ShState.Off -> Button({ vm.askShizuku() }) { Text("Buka") }
                        ShState.NoPerm -> Button({ vm.askShizuku() }) { Text("Beri izin") }
                        ShState.Ready -> Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        items(cards) { s ->
            ElevatedCard(onClick = s.act, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Icon(s.icon, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(8.dp))
                    Text(s.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private sealed interface Dlg {
    class Rename(val i: FItem) : Dlg
    object NewDir : Dlg
    class Del(val l: List<FItem>) : Dlg
    class Extract(val i: FItem) : Dlg
}

@Composable
fun BrowseScreen(vm: VM) {
    val ctx = LocalContext.current
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    val list = remember(vm.raw, vm.results, vm.sort, vm.asc, vm.hidden) { vm.shown() }
    val sel = list.filter { it.id in vm.selected }
    val selecting = vm.selected.isNotEmpty()

    LaunchedEffect(vm.query, vm.searchOpen) {
        if (vm.searchOpen) { delay(450); vm.search() }
    }

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            TopAppBar(
                title = { Text("${vm.selected.size} dipilih") },
                navigationIcon = { IconButton({ vm.selected = emptySet() }) { Icon(Icons.Rounded.Close, "Batal") } },
                actions = {
                    IconButton({ vm.copyCut(sel, false) }) { Icon(Icons.Rounded.ContentCopy, "Salin") }
                    IconButton({ vm.copyCut(sel, true) }) { Icon(Icons.Rounded.ContentCut, "Potong") }
                    IconButton({ dlg = Dlg.Del(sel) }) { Icon(Icons.Rounded.Delete, "Hapus") }
                    Box {
                        IconButton({ moreMenu = true }) { Icon(Icons.Rounded.MoreVert, "Lainnya") }
                        DropdownMenu(moreMenu, { moreMenu = false }) {
                            DropdownMenuItem({ Text("Pilih semua") }, { moreMenu = false; vm.selected = list.map { it.id }.toSet() })
                            DropdownMenuItem({ Text("Bagikan") }, { moreMenu = false; share(ctx, sel) })
                            DropdownMenuItem({ Text("Kompres ke ZIP") }, { moreMenu = false; vm.zip(sel) })
                            if (sel.size == 1) {
                                DropdownMenuItem({ Text("Ganti nama") }, { moreMenu = false; dlg = Dlg.Rename(sel[0]) })
                                if (sel[0].ext == "zip" || sel[0].ext == "rar")
                                    DropdownMenuItem({ Text("Ekstrak di sini") }, { moreMenu = false; vm.unzip(sel[0]) })
                            }
                        }
                    }
                },
            )
        } else {
            TopAppBar(
                title = { Text(vm.cur?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton({ vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") } },
                actions = {
                    IconButton({ if (vm.searchOpen) vm.closeSearch() else vm.searchOpen = true }) { Icon(Icons.Rounded.Search, "Cari") }
                    Box {
                        IconButton({ sortMenu = true }) { Icon(Icons.Rounded.Sort, "Urutkan") }
                        DropdownMenu(sortMenu, { sortMenu = false }) {
                            SortBy.entries.forEach { s ->
                                val mark = if (vm.sort == s) (if (vm.asc) "  ▲" else "  ▼") else ""
                                DropdownMenuItem({ Text(s.label + mark) }, {
                                    if (vm.sort == s) vm.asc = !vm.asc else { vm.sort = s; vm.asc = true }
                                    sortMenu = false
                                })
                            }
                            HorizontalDivider()
                            DropdownMenuItem({ Text((if (vm.hidden) "✓ " else "") + "File tersembunyi") }, { vm.hidden = !vm.hidden; sortMenu = false })
                        }
                    }
                    IconButton({ dlg = Dlg.NewDir }) { Icon(Icons.Rounded.CreateNewFolder, "Folder baru") }
                },
            )
        }

        if (vm.searchOpen) {
            OutlinedTextField(
                value = vm.query, onValueChange = { vm.query = it }, singleLine = true,
                placeholder = { Text("Cari di ${vm.cur?.name ?: ""} dan subfolder…") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (vm.searching) LinearProgressIndicator(Modifier.fillMaxWidth())

        // breadcrumb
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
            vm.stack.forEachIndexed { idx, f ->
                Text(
                    f.name + if (idx < vm.stack.lastIndex) " › " else "",
                    style = MaterialTheme.typography.labelMedium, maxLines = 1,
                    color = if (idx == vm.stack.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(enabled = idx < vm.stack.lastIndex) { vm.jump(idx) },
                )
            }
        }

        if (list.isEmpty() && !vm.searching) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (vm.results != null) "Tidak ditemukan" else "Folder kosong atau tidak dapat dibaca",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(list, key = { it.id }) { f ->
                    val isSel = f.id in vm.selected
                    FileRow(
                        f,
                        if (f.isDir) fmtDate(f.modified) else "${fmtSize(f.size)} • ${fmtDate(f.modified)}",
                        Modifier
                            .combinedClickable(
                                onClick = {
                                    when {
                                        selecting -> vm.toggle(f)
                                        f.ext == "zip" || f.ext == "rar" -> dlg = Dlg.Extract(f)
                                        else -> vm.openItem(f, ctx)
                                    }
                                },
                                onLongClick = { vm.toggle(f) },
                            )
                            .background(if (isSel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
                    ) { if (isSel) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) }
                }
            }
        }

        vm.clip?.let { c ->
            Surface(tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${c.items.size} item ${if (c.cut) "dipotong" else "disalin"}", Modifier.weight(1f))
                    TextButton({ vm.clip = null }) { Text("Batal") }
                    Button({ vm.paste() }) { Icon(Icons.Rounded.ContentPaste, null); Spacer(Modifier.width(6.dp)); Text("Tempel") }
                }
            }
        }
    }

    when (val d = dlg) {
        is Dlg.Rename -> TextDialog("Ganti nama", d.i.name, "Simpan", { dlg = null }) { vm.rename(d.i, it); dlg = null }
        Dlg.NewDir -> TextDialog("Folder baru", "", "Buat", { dlg = null }) { vm.mkdir(it); dlg = null }
        is Dlg.Del -> AlertDialog(
            onDismissRequest = { dlg = null },
            title = { Text("Hapus ${d.l.size} item?") },
            text = { Text("Item yang dihapus tidak dapat dikembalikan.") },
            confirmButton = { TextButton({ vm.delete(d.l); dlg = null }) { Text("Hapus") } },
            dismissButton = { TextButton({ dlg = null }) { Text("Batal") } },
        )
        is Dlg.Extract -> AlertDialog(
            onDismissRequest = { dlg = null },
            title = { Text("Ekstrak ${d.i.name}?") },
            text = { Text("Isi arsip akan diekstrak ke folder baru di lokasi ini.") },
            confirmButton = { TextButton({ vm.unzip(d.i); dlg = null }) { Text("Ekstrak") } },
            dismissButton = { TextButton({ dlg = null }) { Text("Batal") } },
        )
        null -> {}
    }
}

@Composable
fun TextDialog(title: String, init: String, ok: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var t by remember { mutableStateOf(init) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(t, { t = it }, singleLine = true) },
        confirmButton = { TextButton({ if (t.isNotBlank() && !t.contains('/')) onOk(t.trim()) }) { Text(ok) } },
        dismissButton = { TextButton(onDismiss) { Text("Batal") } },
    )
}

@Composable
fun Thumb(f: FItem) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if ((f.kind == Kind.Image || f.kind == Kind.Video) && !f.isRemote) {
            AsyncImage(f.file ?: f.doc!!.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(iconOf(f.kind), null, tint = if (f.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FileRow(f: FItem, sub: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Thumb(f)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/* ───────────── Viewer ───────────── */

@Composable
fun Full(color: Color, onClose: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = color) { Box(Modifier.fillMaxSize(), content = content) }
    }
}

@Composable
fun ImageViewer(vm: VM, i: FItem) {
    var scale by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    Full(Color.Black, { vm.viewer = null }) {
        AsyncImage(
            model = i.file ?: i.doc!!.uri, contentDescription = i.name, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        off = if (scale == 1f) Offset.Zero else off + pan
                    }
                }
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = off.x, translationY = off.y),
        )
        IconButton({ vm.viewer = null }, Modifier.align(Alignment.TopStart).padding(8.dp)) { Icon(Icons.Rounded.Close, "Tutup", tint = Color.White) }
    }
}

@Composable
fun Player(vm: VM, i: FItem) {
    Full(Color.Black, { vm.viewer = null }) {
        AndroidView(
            factory = { c ->
                VideoView(c).apply {
                    val mc = MediaController(c)
                    mc.setAnchorView(this)
                    setMediaController(mc)
                    setVideoURI(i.uri)
                    setOnPreparedListener { start() }
                }
            },
            onRelease = { it.stopPlayback() },
            modifier = Modifier.fillMaxSize().align(Alignment.Center),
        )
        IconButton({ vm.viewer = null }, Modifier.align(Alignment.TopStart).padding(8.dp)) { Icon(Icons.Rounded.Close, "Tutup", tint = Color.White) }
    }
}

@Composable
fun Editor(vm: VM, i: FItem) {
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(i.id) {
        text = withContext(Dispatchers.IO) { runCatching { vm.fs.readText(i) }.getOrElse { "" } }
    }
    Full(MaterialTheme.colorScheme.surface, { vm.viewer = null }) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(i.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton({ vm.viewer = null }) { Icon(Icons.Rounded.Close, "Tutup") } },
                actions = { IconButton({ text?.let { vm.saveText(i, it) }; vm.viewer = null }) { Icon(Icons.Rounded.Save, "Simpan") } },
            )
            val t = text
            if (t == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                BasicTextField(
                    value = t, onValueChange = { text = it },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
                )
            }
        }
    }
}

@Composable
fun ApkDialog(vm: VM, i: FItem) {
    val ctx = LocalContext.current
    var info by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(i.id) {
        info = withContext(Dispatchers.IO) { runCatching { vm.fs.apkInfo(i) }.getOrElse { "Tidak dapat membaca APK: ${it.message}" } }
    }
    AlertDialog(
        onDismissRequest = { vm.viewer = null },
        title = { Text(i.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            val t = info
            if (t == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else Text(t, Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = { TextButton({ vm.viewer = null }) { Text("Tutup") } },
        dismissButton = { TextButton({ openExternal(ctx, i) }) { Text("Pasang") } },
    )
}

/* ───────────── Analisis penyimpanan ───────────── */

@Composable
fun AnalyzerScreen(vm: VM) {
    var tab by remember { mutableIntStateOf(0) }
    var del by remember { mutableStateOf<FItem?>(null) }
    val a = vm.analysis

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Analisis penyimpanan") },
            navigationIcon = { IconButton({ vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") } },
            actions = { IconButton({ vm.analyze() }) { Icon(Icons.Rounded.Refresh, "Pindai ulang") } },
        )
        if (vm.analyzing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (a == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (vm.analyzing) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(); Spacer(Modifier.size(12.dp)); Text("Memindai penyimpanan…")
                } else Text("Analisis gagal. Pastikan izin akses file sudah diberikan.")
            }
        } else {
            TabRow(selectedTabIndex = tab) {
                listOf("Ringkasan", "Terbesar", "Duplikat").forEachIndexed { n, t ->
                    Tab(selected = tab == n, onClick = { tab = n }, text = { Text(t) })
                }
            }
            val open = { f: FItem -> f.file?.parentFile?.let { vm.go(fileItem(it), true) }; Unit }
            LazyColumn(Modifier.fillMaxSize()) {
                when (tab) {
                    0 -> {
                        item {
                            Column(Modifier.padding(16.dp)) {
                                val used = a.total - a.free
                                Text("${fmtSize(used)} terpakai dari ${fmtSize(a.total)}", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.size(8.dp))
                                LinearProgressIndicator(progress = { if (a.total > 0) used.toFloat() / a.total else 0f }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        val max = (a.bytes.values.maxOrNull() ?: 1L).coerceAtLeast(1L)
                        items(Kind.entries.filter { it != Kind.Folder }) { k ->
                            val b = a.bytes[k] ?: 0L
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(iconOf(k), null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${k.label} • ${a.counts[k] ?: 0} file")
                                    LinearProgressIndicator(progress = { b.toFloat() / max }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(fmtSize(b), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    1 -> items(a.big, key = { it.id }) { f ->
                        FileRow(f, "${fmtSize(f.size)} • ${f.file?.parent ?: ""}", Modifier.clickable { open(f) }) {
                            IconButton({ del = f }) { Icon(Icons.Rounded.Delete, "Hapus") }
                        }
                    }
                    else -> {
                        if (a.dupes.isEmpty()) item { Text("Tidak ada file duplikat (≥100 KB).", Modifier.padding(16.dp)) }
                        a.dupes.forEach { g ->
                            item {
                                Text(
                                    "${g.size} salinan • ${fmtSize(g[0].size)} per file • boros ${fmtSize(g[0].size * (g.size - 1))}",
                                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
                                )
                            }
                            items(g, key = { it.id }) { f ->
                                FileRow(f, f.file?.parent ?: "", Modifier.clickable { open(f) }) {
                                    IconButton({ del = f }) { Icon(Icons.Rounded.Delete, "Hapus") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    del?.let { f ->
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text("Hapus ${f.name}?") },
            text = { Text("File tidak dapat dikembalikan.") },
            confirmButton = { TextButton({ vm.deleteFromAnalysis(f); del = null }) { Text("Hapus") } },
            dismissButton = { TextButton({ del = null }) { Text("Batal") } },
        )
    }
}
