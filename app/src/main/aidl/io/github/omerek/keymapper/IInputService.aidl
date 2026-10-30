package io.github.omerek.keymapper;

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
}
