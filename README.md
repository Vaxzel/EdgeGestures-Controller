# Edge Gestures Controller & LSPosed Hook

[![Android Version](https://img.shields.io/badge/Android-8.0%20--%2016%20(HyperOS%203.0)-green.svg)](https://android.com)
[![Framework](https://img.shields.io/badge/Framework-LSPosed%20%7C%20KernelSU%20%7C%20Magisk-blue.svg)](https://github.com/LSPosed/LSPosed)
[![Target App](https://img.shields.io/badge/Target%20App-Edge%20Gestures%20(com.ss.edgegestures)-informational.svg?logo=googleplay)](https://play.google.com/store/apps/details?id=com.ss.edgegestures)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/Vaxzel/EdgeGestures-Controller?color=brightgreen)](https://github.com/Vaxzel/EdgeGestures-Controller/releases/latest)

Modul **LSPosed** dan aplikasi antarmuka kontroler modern untuk aplikasi **Edge Gestures** (`com.ss.edgegestures`). Proyek ini memungkinkan pengaktifan gestur navigasi pada kondisi layar terkunci / kamera aman, serta menyediakan fitur **penonaktifan gestur per-sisi (Kiri, Kanan, atau Bawah) secara selektif dan *real-time*** saat game atau aplikasi tertentu sedang berjalan.

---

### 🎯 Target Application (Aplikasi Target yang Di-Hook)

<table>
  <tr>
    <td align="center" width="120" valign="middle">
      <a href="https://play.google.com/store/apps/details?id=com.ss.edgegestures">
        <img src="docs/assets/edgegestures_app_icon.png" width="96" height="96" alt="Edge Gestures Icon" style="border-radius: 20%;" />
      </a>
    </td>
    <td valign="middle">
      <h3 style="margin: 0;"><a href="https://play.google.com/store/apps/details?id=com.ss.edgegestures">Edge Gestures</a></h3>
      <b>Developer:</b> ChYK the dev<br/>
      <b>Package Name:</b> <code>com.ss.edgegestures</code><br/>
      <b>Google Play Store:</b> <a href="https://play.google.com/store/apps/details?id=com.ss.edgegestures">https://play.google.com/store/apps/details?id=com.ss.edgegestures</a><br/>
      <i>Modul LSPosed ini dibuat khusus untuk memodifikasi dan mengontrol aplikasi Edge Gestures di atas agar dapat tetap aktif di lockscreen / kamera serta mendukung penonaktifan gestur per-sisi secara dinamis ketika membuka aplikasi atau game tertentu.</i>
    </td>
  </tr>
</table>

---

## 🚀 Fitur Utama

### 1. 🔓 Lockscreen & Secure Camera Gesture Bypass
- Secara default pada Android modern (khususnya Xiaomi / Poco HyperOS / Android 14–16), Edge Gestures tidak dapat digunakan ketika layar terkunci atau saat aplikasi Kamera dibuka dari lockscreen.
- Modul ini membypass pemeriksaan keamanan internal `KeyguardManager` (`isKeyguardLocked`, `inKeyguardRestrictedInputMode`, `isDeviceLocked`).
- Menginjeksi flag jendela `WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED` secara otomatis ke setiap layer sentuh Edge Gestures, sehingga gestur Back / Home / App Switching tetap dapat digunakan kapan pun layar menyala.

### 2. 🎮 Selective Per-Side Edge Gesture Disabling (Per-Game Rule)
- Mengatasi masalah gestur tidak sengaja tersentuh saat bermain game (contoh: *Roblox*, *The Spike*, *Mobile Legends*, *PUBG Mobile*).
- Anda dapat menonaktifkan sisi tertentu secara independen:
  - **Sisi Bawah (Bottom)**: Sangat berguna untuk game yang membutuhkan kontrol swipe di bagian bawah layar agar tidak terpicu gestur navigasi.
  - **Sisi Kiri (Left)**: Menghindari benturan dengan joystick / D-pad virtual.
  - **Sisi Kanan (Right)**: Menghindari benturan dengan tombol tembak / aksi / kamera game.
- **Sisi lainnya tetap aktif normal!** (Contoh: Menolak sentuhan sisi bawah & kanan saat bermain Roblox, tetapi gestur sisi kiri tetap berfungsi untuk tombol Back).

### 3. ⚡ Real-Time Dynamic Reapplication (Live Hot-Reload)
- Tidak perlu me-restart game atau keluar dari aplikasi!
- Setiap perubahan pada antarmuka Controller langsung dikirim via IPC Broadcast berkecepatan tinggi (`ACTION_RELOAD_RULES`) dengan prioritas latar depan (`FLAG_RECEIVER_FOREGROUND`) dan kompatibel penuh dengan pembatasan keamanan `RECEIVER_EXPORTED` pada Android 14+.
- Hook secara otomatis memperbarui tata letak jendela overlay (`FLAG_NOT_TOUCHABLE`) dan menyinkronkan state native `EdgeService` seketika.

### 4. 🛡️ Anti-Kill & Anti-Crash Guard
- Menahan dan mencekal panggilan *process suicide* (`Process.killProcess`, `System.exit`) dari Edge Gestures.
- Memasang pengaman *uncaught exception handler* untuk menjaga servis tetap aktif terus-menerus tanpa henti.
- Meningkatkan prioritas thread ke `THREAD_PRIORITY_URGENT_DISPLAY` untuk respon sentuhan instan tanpa lag.

### 5. 📱 Aplikasi Antarmuka (UI Controller) yang Elegan
- **Collapsible Cards (Akordeon)**: Konfigurasi per aplikasi disajikan dalam kartu yang dapat dibuka/tutup secara rapi di dalam daftar utama (bukan pop-up/dialog mengambang).
- **Auto-Save Instan**: Aturan langsung tersimpan dan diaplikasikan otomatis begitu kotak centang (checkbox) diklik.
- **Urutan Prioritas Cerdas**: Aplikasi atau game yang memiliki aturan aktif otomatis ditaruh di urutan paling atas daftar agar mudah dipantau.
- **Badge Visual Status**: Terdapat chip/badge ringkas (contoh: `[Bawah]`, `[Kanan]`) langsung di baris aplikasi tanpa perlu membuka kartu terlebih dahulu.
- **Pencarian Cepat & Filter Ganda**: Pencarian instan berdasarkan Nama Aplikasi atau Package Name, serta toggle filter untuk menampilkan User Apps saja atau menyertakan System Apps.
- **Dukungan Dark Mode Otomatis**: Mendukung tema gelap dan terang mengikuti pengaturan sistem.
- **Ikon Peluncur Adaptif**: Dilengkapi aset ikon peluncur resolusi tinggi (`anydpi-v26` vector serta raster `mdpi` hingga `xxxhdpi`).

---

## 🛠️ Cara Kerja (Arsitektur Teknis)

```
┌───────────────────────────────────────┐
│   Edge Gestures Controller (App UI)   │
│   - Simpan aturan ke JSON             │
│   - Kirim broadcast ACTION_RELOAD     │
└──────────────────┬────────────────────┘
                   │ IPC Broadcast (RECEIVER_EXPORTED)
                   ▼
┌───────────────────────────────────────┐
│       LSPosed Module (MainHook)       │
│       Target: com.ss.edgegestures     │
├───────────────────────────────────────┤
│ 1. KeyguardManager Bypass             │
│    - isKeyguardLocked -> false        │
│ 2. WindowManager Injection            │
│    - FLAG_SHOW_WHEN_LOCKED            │
│ 3. Foreground Package Detection       │
│    - onAccessibilityEvent             │
│    - EdgeService.n ComponentName      │
│ 4. Per-Side View Control              │
│    - WindowManager FLAG_NOT_TOUCHABLE │
│    - Native tp1.b / EdgeService.m     │
│    - Touch Interception (fe0/ge0)     │
└───────────────────────────────────────┘
```

> **PENTING**: Modul ini di-hook langsung ke proses `com.ss.edgegestures`. Di LSPosed Manager, **hanya centang `Edge Gestures`** dalam daftar scope modul. Game atau aplikasi target **TIDAK PERLU** dicentang di LSPosed karena modul mendeteksi aplikasi aktif di layar secara otomatis.

---

## 📋 Persyaratan Sistem

- **Perangkat Android**: Android 8.0 (API 26) hingga Android 16 (API 36+ / HyperOS 3.0).
- **Akses Root**: KernelSU, Magisk (v24+), atau APatch.
- **Framework Xposed**: LSPosed (Zygisk Release direkomendasikan).
- **Aplikasi Target**: [Edge Gestures (`com.ss.edgegestures`)](https://play.google.com/store/apps/details?id=com.ss.edgegestures) terpasang di sistem.

---

## 📥 Panduan Instalasi & Penggunaan

1. **Unduh & Pasang APK**:
   - Pasang file `edgegestures_controller.apk` ke perangkat Android Anda.
2. **Aktifkan Modul di LSPosed**:
   - Buka aplikasi **LSPosed**.
   - Masuk ke tab **Modules** dan cari **Edge Gestures Controller**.
   - Aktifkan modul.
   - Pastikan pada bagian **Scope**, aplikasi yang dicentang hanyalah **Edge Gestures** (`com.ss.edgegestures`).
3. **Pengaturan Hak Akses Root (KernelSU / APatch / Magisk)**:
   - Jika menggunakan KernelSU, pastikan profil `com.custom.edgegestureslock` diberikan izin root (`allow_su: true`) agar sinkronisasi file cadangan berjalan lancar.
4. **Mulai Konfigurasi**:
   - Buka aplikasi **Edge Gestures Controller** dari app drawer / home screen.
   - Cari game yang ingin Anda atur (misal: `Roblox` atau `The Spike`).
   - Ketuk item game untuk membuka kartu akordeon.
   - Centang sisi gestur yang ingin dinonaktifkan (misal: `Nonaktifkan Sisi Bawah`).
   - Pengaturan langsung tersimpan dan aktif secara *real-time*!

---

## 🔨 Kompilasi dari Source Code

Proyek ini menggunakan struktur standar Android Gradle dan juga menyediakan script kompilasi mandiri.

### Cara 1: Menggunakan Gradle / Android Studio

```bash
# Clone repositori
git clone https://github.com/Vaxzel/EdgeGestures-Controller.git
cd EdgeGestures-Controller

# Build APK Debug / Release
./gradlew assembleRelease
```
File APK hasil build akan berada di `app/build/outputs/apk/release/app-release-unsigned.apk`.

### Cara 2: Menggunakan Build Script Mandiri (Windows)

Jalankan script:
```cmd
build.bat
```

---

## 📂 Struktur Proyek

```
EdgeGestures-Controller/
├── .github/
│   └── workflows/
│       └── build.yml               # Otomasi GitHub Actions CI build APK
├── app/
│   ├── build.gradle                # Konfigurasi Gradle modul app
│   ├── proguard-rules.pro          # Aturan ProGuard / R8
│   └── src/main/
│       ├── AndroidManifest.xml     # Manifest aplikasi & deklarasi LSPosed
│       ├── assets/
│       │   └── xposed_init         # Entrypoint class hook (com.custom.edgegestureslock.MainHook)
│       ├── java/com/custom/edgegestureslock/
│       │   ├── MainHook.java       # Seluruh logika hooking Xposed & Keyguard bypass
│       │   ├── data/
│       │   │   └── RuleManager.java# Pengelola database JSON & IPC Broadcast
│       │   ├── model/
│       │   │   ├── AppItem.java    # Model data item list & status UI
│       │   │   └── RuleModel.java  # Model data aturan per-sisi (left, right, bottom)
│       │   ├── provider/
│       │   │   └── RuleProvider.java# ContentProvider IPC data rules
│       │   └── ui/
│       │       ├── AppListAdapter.java# Adapter daftar akordeon interaktif
│       │       └── MainActivity.java  # Layar pengaturan & pencarian
│       └── res/                    # Ikon peluncur adaptif, warna, dan string
├── stubs/                          # Compile-time Xposed API stubs (tidak di-pack ke DEX)
├── build.bat                       # Script build cepat untuk Windows
├── settings.gradle
├── .gitignore
├── LICENSE                         # Lisensi MIT
└── README.md                       # Dokumentasi lengkap proyek
```

---

## ⚠️ Catatan Penting Mengenai Xposed API Dexing

Saat melakukan kompilasi modul Xposed / LSPosed:
> Class stub compile-time `de.robv.android.xposed.*` **TIDAK BOLEH** ikut ter-dex ke dalam `classes.dex` APK!
> Jika ikut terkompilasi, runtime LSPosed akan menolak memuat modul dengan galat:
> *"The Xposed API classes are compiled into the module's APK"*.
> 
> Konfigurasi `app/build.gradle` pada repositori ini sudah menggunakan `compileOnly` untuk memastikan stub Xposed hanya digunakan saat kompilasi kode dan dilepas dari file biner APK akhir.

---

## 📜 Lisensi

Proyek ini dirilis di bawah lisensi [MIT](LICENSE). Bebas digunakan, dimodifikasi, dan didistribusikan kembali.

---

**Dikembangkan oleh [Vaxzel](https://github.com/Vaxzel)**
