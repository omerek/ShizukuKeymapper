# Kurulum ve derleme (Windows 10)

## 1. Bilgisayar

1. **Android Studio** (güncel sürüm) kurulu olmalı. İlk açılışta "Standard" kurulum SDK'yı indirir
   (varsayılan yer: `C:\Users\<kullanıcı>\AppData\Local\Android\Sdk`).
2. Android Studio > *Settings > Languages & Frameworks > Android SDK*:
   - **SDK Platforms:** Android 16 (API 36)
   - **SDK Tools:** Android SDK Build-Tools, Android SDK Platform-Tools (adb). (Adım 2'den itibaren: **NDK (Side by side)** ve **CMake**)
3. Komut satırından derlemek için ortam değişkenleri (PowerShell, bir kez):
   ```powershell
   setx JAVA_HOME "C:\Program Files\Android\Android Studio\jbr"
   setx ANDROID_HOME "$env:LOCALAPPDATA\Android\Sdk"
   setx PATH "$env:PATH;$env:LOCALAPPDATA\Android\Sdk\platform-tools"
   ```
   Yeni bir PowerShell penceresi açıp `java -version` ve `adb version` ile kontrol edin.

## 2. Projeyi alma ve derleme

```powershell
cd E:\Yazılımlar
git clone https://github.com/omerek/ShizukuKeymapper.git
cd ShizukuKeymapper
git checkout adim-1-shizuku-dokunus   # PR birleşene kadar
.\gradlew.bat assembleDebug
```

APK: `app\build\outputs\apk\debug\app-debug.apk`

> Not: Proje yolu Türkçe karakter içerdiği için (`Yazılımlar`) Gradle/NDK sorun çıkarırsa
> projeyi `E:\dev\ShizukuKeymapper` gibi ASCII bir yola taşıyın.

Alternatif: Her PR'da GitHub Actions APK üretir. PR sayfası > *Checks* > *Derleme* > *Artifacts* > `app-debug`.

## 3. Tablete kurma

- USB kablo ile: Tablette *Ayarlar > Telefon hakkında > Yazılım bilgileri > Yapım numarası*'na 7 kez dokunun,
  *Geliştirici seçenekleri > USB hata ayıklama*'yı açın, sonra `adb install -r app\build\outputs\apk\debug\app-debug.apk`.
- Ya da APK'yı tablete kopyalayıp dosya yöneticisinden açın.

## 4. Shizuku'yu Kablosuz hata ayıklama ile başlatma (Android 11+)

1. Shizuku'yu Play Store'dan kurun.
2. Tableti bir Wi-Fi ağına bağlayın (internet şart değil, ağ şart).
3. *Geliştirici seçenekleri > Kablosuz hata ayıklama*'yı açın.
4. Shizuku'yu açın > *Kablosuz hata ayıklama ile başlat* > *Eşleştirme*. Shizuku bir bildirim gösterir.
5. *Kablosuz hata ayıklama > Eşleştirme kodu ile cihaz eşleştir*'e girin, çıkan 6 haneli kodu
   Shizuku bildirimindeki alana yazın.
6. Shizuku'ya dönüp *Başlat*'a basın. "Shizuku çalışıyor" görünmeli.
7. Tablet yeniden başlatılınca 6. adımı tekrarlamak gerekir (eşleştirme kalıcıdır).

Samsung notu: *Ayarlar > Pil > Arka plan kullanım sınırları*'nda Shizuku ve bu uygulamayı "Hiç uyumayan
uygulamalar"a ekleyin, yoksa One UI servisi kapatabilir.

## 5. Adım 1 testi

1. Shizuku Keymapper'ı açın. Sırayla: kurulu ✅, çalışıyor ✅ → *Shizuku izni iste* → *İzin ver*.
2. "Servis bağlı ✅ uid 2000 (shell)" görünmeli.
3. *Hedefe dokun*: mavi alandaki sayaç artmalı (dokunuşu siz değil servis yaptı).
4. *5 sn sonra (X,Y)'ye dokun*: basın, 5 saniye içinde başka bir uygulamaya geçin; o noktaya dokunulmalı.

## 6. Adım 2 testi (klavye)

1. Klavyeyi OTG ile takın. "Algılanan cihazlar" altında klavyeniz "(klavye)" olarak görünmeli
   (en geç 2 saniye). Mouse da "(mouse)" olarak listelenir ama henüz okunmaz.
2. Klavyede bir tuşa basın: "Son basılan tuş" değişmeli.
3. X/Y'yi "Hedef alan"ın ortasına yakın bir değere ayarlayın (test kartındaki "Hedefe dokun" logunda
   koordinat yazar), *Tuşa basarak ekle* > Q'ya basın.
4. *Eşleme aktif*'i açın (veya F12). Q'ya her basışta hedef alan sayacı artmalı; Q basılı tutulursa
   dokunuş da basılı kalır. Eşleme açıkken klavye başka hiçbir yere yazmaz.
5. F12 ile kapatınca klavye normale döner.

## 7. Adım 3 testi (overlay editörü)

1. Ana ekranda "3. Overlay editörü" > *İzni Shizuku ile ver* (olmazsa *Ayarlardan ver*).
2. *Simgeyi göster*: ekranın solunda gri "KM" simgesi çıkar. Sürükleyerek taşıyabilirsiniz.
3. Oyunu (PUBG) açın, simgeye dokunun: ekran kararır ve editör açılır.
   - Boş bir yere dokunun → turuncu nokta çıkar → klavyede bir tuşa basın → nokta mavi olur ve tuş adı yazar.
   - Noktayı sürükleyerek taşıyın. Noktaya dokunup *Tuş değiştir* / *Sil*.
   - Tuş beklerken Esc iptal eder.
   - *Kaydet*.
4. F12 ile eşlemeyi açın: simge yeşil "AÇIK" olur. Atadığınız tuşlar oyunda o noktalara dokunur.
5. Editör açıkken eşleme otomatik durur, kapatınca eski hâline döner.

Not: Simgenin bulunduğu yere enjekte edilen dokunuşlar simgeye gider; simgeyi oyun düğmelerinin
üstünden kenara çekin.
