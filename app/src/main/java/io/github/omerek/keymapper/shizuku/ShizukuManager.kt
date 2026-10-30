package io.github.omerek.keymapper.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import io.github.omerek.keymapper.BuildConfig
import io.github.omerek.keymapper.IInputService
import io.github.omerek.keymapper.service.InputService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import rikka.shizuku.Shizuku

enum class PermissionState { UNKNOWN, GRANTED, NOT_REQUESTED, DENIED_PERMANENTLY }

data class ShizukuStatus(
    val installed: Boolean = false,
    val running: Boolean = false,
    val version: Int = -1,
    val tooOld: Boolean = false,
    val permission: PermissionState = PermissionState.UNKNOWN,
    val serviceConnected: Boolean = false,
    val serviceUid: Int = -1,
    val error: String? = null,
) {
    val ready get() = running && !tooOld && permission == PermissionState.GRANTED
}

/** Shizuku durumunu izler ve InputService (UserService) bağlantısını yönetir. */
object ShizukuManager {

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val TAG = "ShizukuManager"
    private const val PERMISSION_REQUEST_CODE = 1001

    private val _status = MutableStateFlow(ShizukuStatus())
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    @Volatile
    var service: IInputService? = null
        private set

    private lateinit var appContext: Context
    private var initialized = false

    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(appContext.packageName, InputService::class.java.name))
            .daemon(false)
            .processNameSuffix("input")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null || !binder.pingBinder()) return
            val s = IInputService.Stub.asInterface(binder)
            service = s
            val uid = runCatching { s.uid }.getOrDefault(-1)
            _status.update { it.copy(serviceConnected = true, serviceUid = uid, error = null) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            _status.update { it.copy(serviceConnected = false, serviceUid = -1) }
        }
    }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener {
            service = null
            refresh()
        }
        Shizuku.addRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == PERMISSION_REQUEST_CODE) {
                refresh()
                if (_status.value.ready) bindService()
            }
        }
        refresh()
    }

    fun refresh() {
        val installed = isShizukuInstalled()
        val running = Shizuku.pingBinder()
        var version = -1
        var tooOld = false
        var permission = PermissionState.UNKNOWN
        if (running) {
            version = runCatching { Shizuku.getVersion() }.getOrDefault(-1)
            tooOld = Shizuku.isPreV11() || version < 11
            if (!tooOld) {
                permission = when {
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> PermissionState.GRANTED
                    Shizuku.shouldShowRequestPermissionRationale() -> PermissionState.DENIED_PERMANENTLY
                    else -> PermissionState.NOT_REQUESTED
                }
            }
        }
        _status.update {
            it.copy(
                installed = installed || running,
                running = running,
                version = version,
                tooOld = tooOld,
                permission = permission,
                serviceConnected = if (running) it.serviceConnected else false,
            )
        }
    }

    fun requestPermission() {
        if (Shizuku.pingBinder()) Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    }

    fun bindService() {
        if (!_status.value.ready) return
        try {
            Shizuku.bindUserService(serviceArgs, connection)
        } catch (e: Exception) {
            Log.e(TAG, "bindUserService başarısız", e)
            _status.update { it.copy(error = e.message ?: e.toString()) }
        }
    }

    fun unbindService() {
        runCatching { Shizuku.unbindUserService(serviceArgs, connection, true) }
        service = null
        _status.update { it.copy(serviceConnected = false, serviceUid = -1) }
    }

    private fun isShizukuInstalled(): Boolean = try {
        appContext.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
