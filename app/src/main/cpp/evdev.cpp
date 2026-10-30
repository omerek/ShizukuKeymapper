// /dev/input/event* için küçük JNI köprüsü: aç, yetenekleri sorgula, EVIOCGRAB, olay oku.
// Yalnızca Shizuku UserService (shell, uid 2000) sürecinde kullanılır.

#include <jni.h>
#include <linux/input.h>
#include <fcntl.h>
#include <poll.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include <cerrno>
#include <cstring>

namespace {

constexpr size_t kBitsPerLong = sizeof(unsigned long) * 8;
constexpr size_t nlongs(size_t bits) { return (bits + kBitsPerLong - 1) / kBitsPerLong; }

bool testBit(const unsigned long *arr, int bit) {
    return (arr[bit / kBitsPerLong] >> (bit % kBitsPerLong)) & 1UL;
}

constexpr int kMaxEvents = 64;

}  // namespace

extern "C" {

JNIEXPORT jint JNICALL
Java_io_github_omerek_keymapper_input_EvdevNative_open(JNIEnv *env, jclass, jstring jpath) {
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    int fd = ::open(path, O_RDONLY | O_CLOEXEC | O_NONBLOCK);
    int err = errno;
    env->ReleaseStringUTFChars(jpath, path);
    return fd >= 0 ? fd : -err;
}

JNIEXPORT void JNICALL
Java_io_github_omerek_keymapper_input_EvdevNative_close(JNIEnv *, jclass, jint fd) {
    if (fd >= 0) ::close(fd);
}

JNIEXPORT jstring JNICALL
Java_io_github_omerek_keymapper_input_EvdevNative_name(JNIEnv *env, jclass, jint fd) {
    char name[256] = {0};
    if (ioctl(fd, EVIOCGNAME(sizeof(name) - 1), name) < 0) return nullptr;
    return env->NewStringUTF(name);
}

JNIEXPORT jboolean JNICALL
Java_io_github_omerek_keymapper_input_EvdevNative_hasEventType(JNIEnv *, jclass, jint fd, jint type) {
    unsigned long bits[nlongs(EV_MAX + 1)] = {0};
    if (ioctl(fd, EVIOCGBIT(0, sizeof(bits)), bits) < 0) return JNI_FALSE;
    return testBit(bits, type) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_io_github_omerek_keymapper_input_EvdevNative_hasCode(JNIEnv *, jclass, jint fd, jint type, jint code) {
    unsigned long bits[nlongs(KEY_MAX + 1)] = {0};
    if (code < 0 || code > KEY_MAX) return JNI_FALSE;
    if (ioctl(fd, EVIOCGBIT(type, sizeof(bits)), bits) < 0) return JNI_FALSE;
    return testBit(bits, code) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_io_github_omerek_keymapper_input_EvdevNative_grab(JNIEnv *, jclass, jint fd, jboolean on) {
    return ioctl(fd, EVIOCGRAB, on ? 1 : 0) == 0 ? JNI_TRUE : JNI_FALSE;
}

// out: [type, code, value] üçlüleri. Dönüş: olay sayısı, 0 = zaman aşımı, <0 = -errno (cihaz çıkarıldı vb.)
JNIEXPORT jint JNICALL
Java_io_github_omerek_keymapper_input_EvdevNative_read(JNIEnv *env, jclass, jint fd, jintArray out, jint timeoutMs) {
    pollfd pfd{fd, POLLIN, 0};
    int pr = poll(&pfd, 1, timeoutMs);
    if (pr == 0) return 0;
    if (pr < 0) return errno == EINTR ? 0 : -errno;
    if (pfd.revents & (POLLERR | POLLHUP | POLLNVAL)) return -ENODEV;

    input_event events[kMaxEvents];
    jsize capacity = env->GetArrayLength(out) / 3;
    size_t maxRead = static_cast<size_t>(capacity < kMaxEvents ? capacity : kMaxEvents);
    ssize_t n = ::read(fd, events, maxRead * sizeof(input_event));
    if (n < 0) return (errno == EAGAIN || errno == EINTR) ? 0 : -errno;

    int count = static_cast<int>(n / sizeof(input_event));
    jint buf[kMaxEvents * 3];
    for (int i = 0; i < count; i++) {
        buf[i * 3] = events[i].type;
        buf[i * 3 + 1] = events[i].code;
        buf[i * 3 + 2] = events[i].value;
    }
    env->SetIntArrayRegion(out, 0, count * 3, buf);
    return count;
}

}  // extern "C"
