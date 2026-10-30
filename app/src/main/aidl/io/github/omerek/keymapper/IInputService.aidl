package io.github.omerek.keymapper;

import io.github.omerek.keymapper.IInputListener;

// Uygulama <-> Shizuku UserService (shell, uid 2000) arayüzü.
// "slot": dokunuşu başlatan mantıksal kaynak (ör. bir tuş). Her slot ayrı pointer id alır.
interface IInputService {
    // Shizuku'nun servis sonlandırma için ayırdığı işlem kodu
    void destroy() = 16777114;

    void exit() = 1;

    int getUid() = 2;

    boolean touchDown(int slot, float x, float y) = 3;

    boolean touchMove(int slot, float x, float y) = 4;

    boolean touchUp(int slot) = 5;

    void releaseAll() = 6;

    // Kısa dokunuş: DOWN, durationMs bekle, UP (servis tarafında ayrı thread'de)
    void tap(float x, float y, long durationMs) = 7;

    // Basit kaydırma: (x1,y1) -> (x2,y2), durationMs boyunca
    void swipe(float x1, float y1, float x2, float y2, long durationMs) = 8;

    // Uygulamanın nativeLibraryDir'i; servis libkeymapper.so'yu buradan yükler. Hata yoksa null döner.
    String initNative(String nativeLibraryDir) = 9;

    // Algılanan klavye/mouse listesi (JSON)
    String getDevices() = 10;

    // KeyMapping listesi (JSON)
    void setKeyMappings(String mappingsJson) = 11;

    void setMappingEnabled(boolean enabled) = 12;

    boolean isMappingEnabled() = 13;

    void setListener(IInputListener listener) = 14;

    // Eşlemeyi aç/kapa tuşu (varsayılan KEY_F12)
    void setToggleKey(int code) = 15;

    // Editör modu: klavyeyi yakala, tuşları yalnızca bildir (dokunuş üretme)
    void setCaptureMode(boolean capture) = 16;

    // shell yetkisiyle overlay (SYSTEM_ALERT_WINDOW) ve bildirim izinlerini ver; komut çıktılarını döndürür
    String grantAppPermissions(String packageName) = 17;

    // MouseLookConfig (JSON); null = mouse kamerası kapalı
    void setMouseLook(String configJson) = 18;
}
