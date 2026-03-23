# Stok Scanner – Android Middleware (Chainway C72)

Aplikasi Android middleware untuk device handheld (BARCODE + RFID).  
- **App**: project Android di folder `app/` (module `app/`).  
- **API**: backend di folder `web/` (Laravel). Endpoint contoh: `POST /api/rfid/scan` (lihat `web/routes/api.php`).  
  Agar scan muncul di **Monitor → Log Scan** di web, jalankan migration: `cd web && php artisan migrate` (membuat table `scan_logs`).

## Cara menjalankan project

### Lewat Android Studio (disarankan)

1. Buka **Android Studio**.
2. **File** → **Open** → pilih folder **`app`** (bukan folder `stok`).  
   Path lengkap: `d:\Project\stok\app`
3. Tunggu **Gradle sync** selesai (bisa beberapa menit pertama kali).
4. Sambungkan:
   - **Device fisik**: USB debugging aktif, atau
   - **Emulator**: buka Device Manager, buat/jalankan AVD.
5. Klik tombol **Run** (▶) di toolbar, atau tekan **Shift+F10**.  
   Aplikasi akan terinstall dan terbuka di device/emulator.

### Lewat command line (Gradle)

1. Buka terminal/CMD/PowerShell.
2. Masuk ke folder project:  
   `cd d:\Project\stok\app`
3. Install debug ke device yang terhubung:  
   - **Windows**: `gradlew.bat :app:installDebug`  
   - **Linux/Mac**: `./gradlew :app:installDebug`  
   (Jika belum ada `gradlew`, buka dulu project di Android Studio sekali lalu sync Gradle.)
4. Buka aplikasi **Stok Scanner** manual di device.

## Mengubah RFID Intent Action dan Extra Key

- **Intent action RFID**  
  - **Di Settings (runtime)**: Settings → field **"RFID intent action"** → Save.  
  - **Default di kode**: `AppPreferences.DEFAULT_RFID_ACTION` di  
    `app/src/main/java/com/stok/middleware/data/local/AppPreferences.kt`  
  - Bisa juga dirujuk di: `app/src/main/java/com/stok/middleware/utils/Constants.kt` (`RFID_ACTION_DEFAULT`).

- **Extra key untuk data barcode/tag RFID**  
  - **Di Settings (runtime)**: Settings → field **"RFID barcode/tag extra key"** → Save.  
  - **Default di kode**: `AppPreferences.DEFAULT_RFID_EXTRA_KEY` di  
    `app/src/main/java/com/stok/middleware/data/local/AppPreferences.kt`  
  - Juga di: `app/src/main/java/com/stok/middleware/utils/Constants.kt` (`RFID_EXTRA_KEY_DEFAULT`).

Receiver RFID didaftarkan dinamis di `ScannerManager`; action dan extra key diambil dari `AppPreferences` (nilai yang disimpan di Settings).

## Mengganti logo aplikasi

- **Launcher icon** (ikon di home/launcher): ganti file  
  `app/src/main/res/drawable/ic_launcher_foreground.xml` (ikon) dan  
  `app/src/main/res/drawable/ic_launcher_background.xml` (warna latar).  
  Bisa pakai vector XML atau gambar PNG (taruh di `drawable`, lalu ubah referensi di `ic_launcher.xml`).
- **Logo di dalam app** (di halaman utama): ganti  
  `app/src/main/res/drawable/ic_app_logo.xml`.  
  Bisa pakai icon dari [Material Icons](https://fonts.google.com/icons) atau situs icon gratis (export SVG → convert ke Android vector, atau ganti dengan `@drawable/nama_file` jika pakai PNG).

## Flow mode BARCODE

1. Mode default saat buka app: **BARCODE**.
2. Fokus otomatis di EditText barcode.
3. Scanner barcode (keyboard wedge) mengirim teks + Enter ke EditText.
4. Aplikasi mendeteksi selesai scan (Enter/newline), lalu:
   - Menyimpan satu entri log (timestamp, value, mode BARCODE, status LOCAL_ONLY).
   - Mengosongkan EditText untuk scan berikutnya.
5. **Tidak ada** HTTP/API call pada mode BARCODE.

## Flow mode RFID

1. User memilih mode **RFID** (tombol/tab RFID).
2. Aplikasi mendaftar `BroadcastReceiver` untuk action intent yang dikonfigurasi (Settings).
3. Saat device mengirim broadcast hasil scan RFID (dengan extra key yang dikonfigurasi):
   - Nilai RFID ditampilkan di "RFID terakhir".
   - Debounce: tag EPC yang sama dalam 2 detik tidak dikirim dua kali.
   - Payload dibentuk: `epc`, `scanTime` (ISO datetime), `deviceName`, `source`.
   - HTTP POST ke endpoint yang dikonfigurasi di Settings (Base URL + Endpoint path).
   - Status (OK / gagal) ditampilkan di Status; entri log ditambah (SENT atau FAILED).
4. Jika intent tidak pernah datang, aplikasi tidak crash (receiver hanya dipanggil saat ada broadcast).

## Struktur package

- `ui/` – MainActivity, ScanLogAdapter  
- `ui/settings/` – SettingsActivity  
- `scanner/` – ScannerManager, BarcodeInputHandler, RfidScanHandler, RfidBroadcastReceiver  
- `network/` – ApiConfig, RfidApiService  
- `data/model/` – RfidPayload, RfidResponse, ScanLogItem  
- `data/local/` – AppPreferences, ScanLogRepository  
- `utils/` – Constants

## Konfigurasi API (Settings)

- **Base URL**: base server (mis. `https://example.com/api`).  
- **Endpoint path**: path relatif untuk RFID scan (mis. `rfid/scan`).  
- **Static token**: token untuk header (Authorization / X-Auth-Token).  
Setelah Save, instance Retrofit memakai config terbaru (di-recreate lewat `ApiConfig.createRfidApiService(prefs)`).
"# chainway-reader" 
