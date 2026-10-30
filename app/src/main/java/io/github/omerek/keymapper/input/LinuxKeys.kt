package io.github.omerek.keymapper.input

/** Linux input-event-codes.h sabitleri ve tuş adları (eşlemeler bu kodlarla saklanır). */
object LinuxKeys {
    const val EV_SYN = 0
    const val EV_KEY = 1
    const val EV_REL = 2
    const val EV_ABS = 3

    const val REL_X = 0
    const val REL_Y = 1
    const val REL_WHEEL = 8

    const val KEY_A = 30
    const val KEY_Z = 44
    const val KEY_SPACE = 57
    const val KEY_F12 = 88

    const val BTN_LEFT = 0x110
    const val BTN_RIGHT = 0x111
    const val BTN_MIDDLE = 0x112

    private val names: Map<Int, String> = buildMap {
        put(1, "Esc")
        "1234567890".forEachIndexed { i, c -> put(2 + i, c.toString()) }
        put(12, "-"); put(13, "="); put(14, "Backspace"); put(15, "Tab")
        "QWERTYUIOP".forEachIndexed { i, c -> put(16 + i, c.toString()) }
        put(26, "["); put(27, "]"); put(28, "Enter"); put(29, "Sol Ctrl")
        "ASDFGHJKL".forEachIndexed { i, c -> put(30 + i, c.toString()) }
        put(39, ";"); put(40, "'"); put(41, "`"); put(42, "Sol Shift"); put(43, "\\")
        "ZXCVBNM".forEachIndexed { i, c -> put(44 + i, c.toString()) }
        put(51, ","); put(52, "."); put(53, "/"); put(54, "Sağ Shift"); put(55, "Num *")
        put(56, "Sol Alt"); put(57, "Boşluk"); put(58, "Caps Lock")
        for (i in 0 until 10) put(59 + i, "F${i + 1}")
        put(87, "F11"); put(88, "F12")
        put(97, "Sağ Ctrl"); put(100, "Sağ Alt"); put(102, "Home"); put(103, "Yukarı")
        put(104, "Page Up"); put(105, "Sol"); put(106, "Sağ"); put(107, "End"); put(108, "Aşağı")
        put(109, "Page Down"); put(110, "Insert"); put(111, "Delete")
        put(BTN_LEFT, "Mouse Sol"); put(BTN_RIGHT, "Mouse Sağ"); put(BTN_MIDDLE, "Mouse Orta")
        put(0x113, "Mouse Yan"); put(0x114, "Mouse İleri")
    }

    fun name(code: Int): String = names[code] ?: "Kod $code"
}
