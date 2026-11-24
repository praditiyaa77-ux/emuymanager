package com.filemanagerpro

import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.File
import kotlin.system.exitProcess

/** Berjalan di proses Shizuku (uid shell) sehingga bisa mengakses Android/data dan Android/obb. */
class FileService() : IFileService.Stub() {
    @Suppress("unused")
    constructor(context: Context) : this()

    private fun line(f: File) = "${if (f.isDirectory) "d" else "f"}|${f.length()}|${f.lastModified()}|${f.name}"

    override fun destroy() { exitProcess(0) }
    override fun list(path: String): MutableList<String> = (File(path).listFiles()?.map { line(it) } ?: emptyList()).toMutableList()
    override fun stat(path: String): String? = File(path).takeIf { it.exists() }?.let { line(it) }
    override fun mkdirs(path: String): Boolean = File(path).let { it.isDirectory || it.mkdirs() }
    override fun delete(path: String): Boolean = File(path).deleteRecursively()
    override fun rename(from: String, to: String): Boolean = File(from).renameTo(File(to))
    override fun open(path: String, mode: Int): ParcelFileDescriptor = ParcelFileDescriptor.open(File(path), mode)
}
