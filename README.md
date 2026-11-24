# File Manager Pro

Kotlin + Jetpack Compose (Material 3). Kode ringkas: `Storage.kt` (file/zip/analisis) dan `Main.kt` (state + UI).

## Build APK lewat GitHub
1. Buat repo baru, upload seluruh isi folder ini (termasuk `.github`).
2. Tab **Actions** → *Build APK* → tunggu selesai.
3. Unduh artifact `FileManagerPro-debug` → pasang `app-debug.apk`.

## Shizuku (tanpa root, seperti MT Manager)
Pasang & aktifkan Shizuku (Wireless debugging, atau root), lalu di beranda tekan **Beri izin**. Setelah terhubung, `Android/data` dan `Android/obb` terbuka langsung di Android 11–15 dengan operasi penuh (salin, pindah, hapus, rename, edit, ZIP).

## Catatan akses folder
- Android 10 ke bawah: `Android/data` & `Android/obb` bisa dibuka langsung.
- Android 11–12: lewat pemilih folder SAF (kadang diblokir tergantung perangkat).
- Android 13+: diblokir untuk aplikasi biasa; gunakan Shizuku.
