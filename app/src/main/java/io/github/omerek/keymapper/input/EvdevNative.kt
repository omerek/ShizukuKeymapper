package io.github.omerek.keymapper.input

/** evdev.cpp JNI köprüsü. Yalnızca shell yetkili servis sürecinde çağrılır. */
object EvdevNative {
    const val LIB_NAME = "keymapper"

    @JvmStatic external fun open(path: String): Int
    @JvmStatic external fun close(fd: Int)
    @JvmStatic external fun name(fd: Int): String?
    @JvmStatic external fun hasEventType(fd: Int, type: Int): Boolean
    @JvmStatic external fun hasCode(fd: Int, type: Int, code: Int): Boolean
    @JvmStatic external fun grab(fd: Int, on: Boolean): Boolean
    @JvmStatic external fun read(fd: Int, out: IntArray, timeoutMs: Int): Int
}
