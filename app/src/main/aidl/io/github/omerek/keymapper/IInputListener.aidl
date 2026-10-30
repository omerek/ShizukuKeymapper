package io.github.omerek.keymapper;

// Servisten uygulamaya geri bildirimler (servisin okuma thread'lerini bekletmemek için oneway)
oneway interface IInputListener {
    // code: Linux key code (KEY_*, BTN_*)
    void onKey(int code, boolean down);

    void onMappingEnabledChanged(boolean enabled);

    // EvdevDevice listesi JSON olarak
    void onDevicesChanged(String devicesJson);
}
