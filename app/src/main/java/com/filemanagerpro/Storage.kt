package com.filemanagerpro

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.webkit.MimeTypeMap
import androidx.core.content.pm.PackageInfoCompat
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.PriorityQueue
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class Kind(val label: String) {
    Folder("Folder"), Image("Gambar"), Video("Video"), Audio("Audio"),
    Doc("Dokumen"), Apk("APK"), Archive("Arsip"), Other("Lainnya")
}

private val IMG = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
private val VID = setOf("mp4", "mkv", "webm", "3gp", "avi", "mov", "m4v")
private val AUD = setOf("mp3", "m4a", "wav", "flac", "ogg", "opus", "aac", "amr")
private val DOC = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "rtf", "odt", "json", "xml", "html")
private val ARC = setOf("zip", "rar", "7z", "tar", "gz")
val TEXT_EXT = setOf(
    "txt", "md", "json", "xml", "log", "ini", "cfg", "conf", "properties", "csv", "html",
    "js", "css", "kt", "java", "py", "sh", "yml", "yaml", "toml", "smali", "lua", "bat"
)
const val TEXT_MAX = 500_000L

fun kindOf(e: String): Kind = when {
    e in IMG -> Kind.Image
    e in VID -> Kind.Video
    e in AUD -> Kind.Audio
    e == "apk" -> Kind.Apk
    e in ARC -> Kind.Archive
    e in DOC -> Kind.Doc
    else -> Kind.Other
}

fun mimeOf(name: String): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
        ?: "application/octet-stream"

fun fmtSize(b: Long): String {
    if (b < 1024) return "$b B"
    val u = arrayOf("KB", "MB", "GB", "TB")
    var v = b / 1024.0
    var i = 0
    while (v >= 1024 && i < u.size - 1) { v /= 1024; i++ }
    return String.format(Locale.US, "%.1f %s", v, u[i])
}

private val df = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
fun fmtDate(t: Long): String = if (t <= 0) "-" else df.format(Date(t))

/** Satu item file/folder, bisa berasal dari java.io.File (penyimpanan biasa) atau DocumentFile (SAF). */
data class FItem(
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val file: File? = null,
    val doc: DocumentFile? = null,
    val rpath: String? = null, // path via Shizuku
) {
    val isRemote: Boolean get() = rpath != null
    val id: String get() = file?.absolutePath ?: rpath?.let { "sh:$it" } ?: doc!!.uri.toString()
    val ext: String get() = name.substringAfterLast('.', "").lowercase()
    val kind: Kind get() = if (isDir) Kind.Folder else kindOf(ext)
    val uri get() = doc?.uri ?: android.net.Uri.fromFile(file!!)
}

fun fileItem(f: File) = FItem(f.name.ifEmpty { f.path }, f.isDirectory, if (f.isDirectory) 0 else f.length(), f.lastModified(), file = f)
fun docItem(d: DocumentFile) = FItem(d.name ?: "?", d.isDirectory, if (d.isDirectory) 0 else d.length(), d.lastModified(), doc = d)

data class Analysis(
    val total: Long,
    val free: Long,
    val bytes: Map<Kind, Long>,
    val counts: Map<Kind, Int>,
    val big: List<FItem>,
    val dupes: List<List<FItem>>,
)

class Fs(private val ctx: Context) {
    private val cr get() = ctx.contentResolver

    /** Terisi saat Shizuku terhubung. */
    var service: IFileService? = null
    private fun svc(): IFileService = service ?: error("Shizuku belum terhubung")
    fun remoteRoot(path: String) = FItem(path.substringAfterLast('/'), true, 0, 0, rpath = path)

    private fun parseR(parent: String, line: String): FItem? {
        val p = line.split("|", limit = 4)
        if (p.size < 4) return null
        val dir = p[0] == "d"
        return FItem(p[3], dir, if (dir) 0 else p[1].toLongOrNull() ?: 0, p[2].toLongOrNull() ?: 0, rpath = "$parent/${p[3]}")
    }

    fun list(dir: FItem): List<FItem> = when {
        dir.rpath != null -> svc().list(dir.rpath).mapNotNull { parseR(dir.rpath, it) }
        dir.file != null -> dir.file.listFiles()?.map(::fileItem) ?: emptyList()
        else -> dir.doc!!.listFiles().map(::docItem)
    }

    fun open(i: FItem): InputStream = when {
        i.rpath != null -> ParcelFileDescriptor.AutoCloseInputStream(svc().open(i.rpath, ParcelFileDescriptor.MODE_READ_ONLY))
        i.file != null -> i.file.inputStream()
        else -> cr.openInputStream(i.doc!!.uri)!!
    }

    fun child(dir: FItem, name: String): FItem? = when {
        dir.rpath != null -> svc().stat("${dir.rpath}/$name")?.let { parseR(dir.rpath, it) }
        dir.file != null -> File(dir.file, name).takeIf { it.exists() }?.let(::fileItem)
        else -> dir.doc!!.findFile(name)?.let(::docItem)
    }

    fun uniqueName(dir: FItem, name: String): String {
        if (child(dir, name) == null) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (true) {
            val c = "$base ($n)$ext"
            if (child(dir, c) == null) return c
            n++
        }
    }

    private val wmode = ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE

    fun mkdir(dir: FItem, name: String): FItem? = when {
        dir.rpath != null -> "${dir.rpath}/$name".let { p -> if (svc().mkdirs(p)) remoteRoot(p) else null }
        dir.file != null -> File(dir.file, name).let { if (it.mkdirs() || it.isDirectory) fileItem(it) else null }
        else -> dir.doc!!.createDirectory(name)?.let(::docItem)
    }

    fun createOut(dir: FItem, name: String): OutputStream = when {
        dir.rpath != null -> ParcelFileDescriptor.AutoCloseOutputStream(svc().open("${dir.rpath}/$name", wmode))
        dir.file != null -> File(dir.file, name).outputStream()
        else -> cr.openOutputStream(dir.doc!!.createFile(mimeOf(name), name)!!.uri)!!
    }

    fun overwrite(i: FItem): OutputStream = when {
        i.rpath != null -> ParcelFileDescriptor.AutoCloseOutputStream(svc().open(i.rpath, wmode))
        i.file != null -> i.file.outputStream()
        else -> cr.openOutputStream(i.doc!!.uri, "wt")!!
    }

    fun delete(i: FItem): Boolean = when {
        i.rpath != null -> svc().delete(i.rpath)
        i.file != null -> i.file.deleteRecursively()
        else -> i.doc!!.delete()
    }

    fun rename(i: FItem, name: String): Boolean = when {
        i.rpath != null -> svc().rename(i.rpath, i.rpath.substringBeforeLast('/') + "/" + name)
        i.file != null -> i.file.renameTo(File(i.file.parentFile, name))
        else -> i.doc!!.renameTo(name)
    }

    fun isInside(dest: FItem, src: FItem): Boolean {
        if (src.rpath != null && dest.rpath != null)
            return src.isDir && (dest.rpath == src.rpath || dest.rpath.startsWith(src.rpath + "/"))
        val s = src.file ?: return false
        val d = dest.file ?: return false
        if (!src.isDir) return false
        val sp = s.canonicalPath
        val dp = d.canonicalPath
        return dp == sp || dp.startsWith(sp + File.separator)
    }

    fun copy(src: FItem, dest: FItem): Boolean {
        val name = uniqueName(dest, src.name)
        if (src.isDir) {
            val d = mkdir(dest, name) ?: return false
            var ok = true
            for (c in list(src)) ok = copy(c, d) && ok
            return ok
        }
        open(src).use { i -> createOut(dest, name).use { o -> i.copyTo(o) } }
        return true
    }

    fun move(src: FItem, dest: FItem): Boolean {
        if (src.rpath != null && dest.rpath != null) {
            if (svc().rename(src.rpath, dest.rpath + "/" + uniqueName(dest, src.name))) return true
        }
        if (src.file != null && dest.file != null) {
            if (src.file.renameTo(File(dest.file, uniqueName(dest, src.name)))) return true
        }
        return copy(src, dest) && delete(src)
    }

    fun zip(items: List<FItem>, dir: FItem, name: String) {
        val out = uniqueName(dir, name)
        ZipOutputStream(createOut(dir, out).buffered()).use { z ->
            fun add(i: FItem, prefix: String) {
                if (i.isDir) {
                    z.putNextEntry(ZipEntry("$prefix${i.name}/")); z.closeEntry()
                    list(i).forEach { add(it, "$prefix${i.name}/") }
                } else if (i.name != out) {
                    z.putNextEntry(ZipEntry(prefix + i.name))
                    open(i).use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
            items.forEach { add(it, "") }
        }
    }

    fun unzip(src: FItem, dir: FItem) {
        val target = mkdir(dir, uniqueName(dir, src.name.substringBeforeLast('.'))) ?: error("Gagal membuat folder")
        ZipInputStream(open(src).buffered()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val parts = e.name.split('/').filter { it.isNotEmpty() && it != "." }
                if (parts.isNotEmpty() && parts.none { it == ".." }) { // cegah zip-slip
                    var d = target
                    for (p in if (e.isDirectory) parts else parts.dropLast(1)) {
                        d = child(d, p)?.takeIf { it.isDir } ?: mkdir(d, p) ?: error("Gagal membuat folder $p")
                    }
                    if (!e.isDirectory) createOut(d, parts.last()).use { z.copyTo(it) }
                }
                e = z.nextEntry
            }
        }
    }

    fun unrar(src: FItem, dir: FItem) {
        val rar = toLocal(src)
        val tmp = File(ctx.cacheDir, "rar_${System.nanoTime()}").apply { mkdirs() }
        try {
            com.github.junrar.Junrar.extract(rar, tmp)
            val target = mkdir(dir, uniqueName(dir, src.name.substringBeforeLast('.'))) ?: error("Gagal membuat folder")
            tmp.listFiles()?.forEach { copy(fileItem(it), target) }
        } finally {
            tmp.deleteRecursively()
            if (src.file == null) rar.delete()
        }
    }

    fun toLocal(i: FItem): File = i.file ?: File(ctx.cacheDir, "tmp_${System.nanoTime()}_${i.name}").also { f ->
        open(i).use { a -> f.outputStream().use { a.copyTo(it) } }
    }

    fun readText(i: FItem): String = String(open(i).use { it.readBytes() }, Charsets.UTF_8)

    fun search(root: FItem, q: String, limit: Int = 300, cancel: () -> Boolean): List<FItem> {
        val res = ArrayList<FItem>()
        fun walk(d: FItem) {
            for (c in list(d)) {
                if (res.size >= limit || cancel()) return
                if (c.name.contains(q, ignoreCase = true)) res.add(c)
                if (c.isDir) walk(c)
            }
        }
        walk(root)
        return res
    }

    fun sdRoots(): List<File> = ctx.getExternalFilesDirs(null).drop(1)
        .mapNotNull { it?.path?.substringBefore("/Android/data")?.let(::File) }
        .filter { it.isDirectory }

    fun apkInfo(i: FItem): String {
        val f = toLocal(i)
        try {
            val pm = ctx.packageManager
            val p = pm.getPackageArchiveInfo(f.path, PackageManager.GET_PERMISSIONS) ?: return "Bukan APK yang valid."
            val ai = p.applicationInfo!!
            ai.sourceDir = f.path
            ai.publicSourceDir = f.path
            val installed = runCatching { pm.getPackageInfo(p.packageName, 0) }.getOrNull()
            return buildString {
                appendLine("Nama: ${ai.loadLabel(pm)}")
                appendLine("Package: ${p.packageName}")
                appendLine("Versi: ${p.versionName} (${PackageInfoCompat.getLongVersionCode(p)})")
                appendLine("Min SDK: ${ai.minSdkVersion}   Target SDK: ${ai.targetSdkVersion}")
                appendLine("Ukuran: ${fmtSize(f.length())}")
                appendLine(if (installed != null) "Status: terpasang (${installed.versionName})" else "Status: belum terpasang")
                val perms = p.requestedPermissions?.toList().orEmpty()
                appendLine("\nIzin (${perms.size}):")
                perms.take(25).forEach { appendLine("• ${it.substringAfterLast('.')}") }
                if (perms.size > 25) appendLine("… dan ${perms.size - 25} lainnya")
            }
        } finally {
            if (i.file == null) f.delete()
        }
    }

    private fun md5(f: File): String {
        val md = MessageDigest.getInstance("MD5")
        f.inputStream().use { s ->
            val b = ByteArray(65536)
            while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun analyze(root: File, cancel: () -> Boolean): Analysis {
        val bytes = HashMap<Kind, Long>()
        val counts = HashMap<Kind, Int>()
        val top = PriorityQueue<Pair<Long, File>>(11, compareBy<Pair<Long, File>> { it.first })
        val bySize = HashMap<Long, MutableList<File>>()
        root.walkTopDown().onEnter { !cancel() && !Files.isSymbolicLink(it.toPath()) }.forEach { f ->
            if (f.isFile) {
                val len = f.length()
                val k = kindOf(f.extension.lowercase())
                bytes[k] = (bytes[k] ?: 0L) + len
                counts[k] = (counts[k] ?: 0) + 1
                if (len > 0) { top.add(len to f); if (top.size > 40) top.poll() }
                if (len >= 100_000) bySize.getOrPut(len) { ArrayList() }.add(f)
            }
        }
        val dupes = ArrayList<List<File>>()
        for ((len, g) in bySize) {
            if (g.size < 2 || len > 300_000_000 || cancel()) continue
            g.groupBy { runCatching { md5(it) }.getOrNull() }.forEach { (h, l) -> if (h != null && l.size > 1) dupes.add(l) }
        }
        dupes.sortByDescending { it[0].length() * (it.size - 1) }
        val st = StatFs(root.path)
        return Analysis(
            total = st.totalBytes, free = st.availableBytes, bytes = bytes, counts = counts,
            big = top.sortedByDescending { it.first }.map { fileItem(it.second) },
            dupes = dupes.take(100).map { g -> g.map(::fileItem) },
        )
    }
}
