# Mimari

## Genel bakış

```
┌──────────────────────────── Uygulama süreci (normal uid) ───────────────────────────┐
│ MainActivity (Compose)  ── Shizuku durumu, izin, test ekranı                        │
│ ShizukuManager          ── Shizuku binder/izin takibi, UserService bağlama          │
│ (Adım 3+) OverlayService ── ön plan servisi + TYPE_APPLICATION_OVERLAY editör/ikon  │
│ (Adım 6)  ProfileStore  ── JSON profiller (uygulama paketine göre)                  │
└───────────────────────────────────────┬─────────────────────────────────────────────┘
                                        │ AIDL (IInputService / ileride IInputCallback)
┌───────────────────────────────────────▼─── Shizuku UserService (shell, uid 2000) ───┐
│ InputService     ── AIDL uygulaması                                                 │
│ TouchInjector    ── çoklu dokunuş durumu → MotionEvent → injectInputEvent           │
│ (Adım 2) EvdevReader + libevdev_bridge.so (JNI) ── /dev/input/event* okuma, EVIOCGRAB│
│ (Adım 2) Mapper  ── tuş/mouse olayı → slot → touchDown/Move/Up                      │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

**Neden eşleme mantığı servis tarafında?** Klavye olayı → dokunuş yolunda Binder gidiş-dönüşü olmasın diye.
Uygulama, aktif profili servise bir kez gönderir; servis /dev/input'tan okuduğu her olayı kendi içinde
dokunuşa çevirir. Uygulama yalnızca editör modunda ("tuşa bas" algılama) olay dinler.

**Pointer id'ler:** Her tuş/kaynak bir "slot". `TouchInjector` her slota DOWN anında boş pointer id (0–9) verir,
UP'a kadar tutar. Böylece W basılıyken Q'ya basmak ve mouse ile kamera çevirmek aynı anda çalışır.

**Koordinatlar:** Profilde 0..1 oransal saklanır, servise gönderilirken o anki ekran boyutu ve dönüşüne
göre piksele çevrilir (Adım 8). Adım 1–2'de doğrudan piksel kullanılıyor.

## Dosya yapısı

```
ShizukuKeymapper/
├─ settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml, gradlew(.bat)
├─ .github/workflows/build.yml          # Her PR'da assembleDebug + APK çıktısı
├─ docs/MIMARI.md, docs/KURULUM.md
└─ app/
   ├─ build.gradle.kts
   └─ src/main/
      ├─ AndroidManifest.xml            # ShizukuProvider, <queries>
      ├─ aidl/.../IInputService.aidl
      ├─ cpp/ (Adım 2)                  # evdev okuma + EVIOCGRAB, CMake
      └─ java/io/github/omerek/keymapper/
         ├─ MainActivity.kt
         ├─ shizuku/ShizukuManager.kt
         ├─ service/InputService.kt     # UserService (shell)
         ├─ service/TouchInjector.kt
         ├─ input/ (Adım 2)             # EvdevReader, cihaz algılama, keycode tabloları
         ├─ overlay/ (Adım 3,7)         # editör katmanı, aç/kapa ikonu
         ├─ model/ (Adım 2+)            # Profile, Mapping (Tap, Joystick, MouseLook...)
         └─ ui/
```

## Yol haritası

| Adım | İçerik | Durum |
|---|---|---|
| 1 | Shizuku bağlantı ekranı + sabit noktaya dokunuş/kaydırma | PR #1 |
| 2 | /dev/input okuma (JNI), klavye algılama, EVIOCGRAB, tuş → nokta (basılı tutma), F12 aç/kapa | PR #2 |
| 3 | Overlay editörü: nokta sürükle, "tuşa bas" ile ata + durum simgesi | PR #3 |
| 4 | Mouse: alan içinde kamera sürüklemesi, hassasiyet, mouse tuşu → nokta | PR #4 |
| 5 | WASD joystick | |
| 6 | Uygulamaya özel profiller (JSON) | |
| 7 | F12 aç/kapa + durum ikonu | PR #2 + #3 |
| 8 | Oransal koordinat, döndürme | |
