package io.github.omerek.keymapper.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.omerek.keymapper.MainActivity
import io.github.omerek.keymapper.model.MappingRepository
import io.github.omerek.keymapper.shizuku.ShizukuManager
import io.github.omerek.keymapper.ui.KeymapperTheme
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Oyunun üstünde duran katmanlar: küçük durum simgesi (açık/kapalı) ve tam ekran eşleme editörü.
 * Ön plan servisi olduğu için uygulama arka plandayken süreç (ve Shizuku servisi) yaşamaya devam eder.
 */
class OverlayService : LifecycleService(), SavedStateRegistryOwner {

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var editor: ComposeView? = null
    private var wasEnabledBeforeEdit = false

    override fun onCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        MappingRepository.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startInForeground()
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay izni yok", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_EDIT -> {
                showBubble()
                openEditor()
            }
            else -> showBubble()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        closeEditor(save = false)
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Keymapper overlay", NotificationManager.IMPORTANCE_LOW))
        }
        val piFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), piFlags)
        val edit = PendingIntent.getService(this, 1, intent(this, ACTION_EDIT), piFlags)
        val stop = PendingIntent.getService(this, 2, intent(this, ACTION_STOP), piFlags)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("Shizuku Keymapper")
            .setContentText("F12: eşlemeyi aç/kapa • simgeye dokun: editör")
            .setContentIntent(openApp)
            .addAction(0, "Editör", edit)
            .addAction(0, "Kapat", stop)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    // ---- Durum simgesi ----

    private fun showBubble() {
        if (bubble != null) return
        val size = dp(44)
        val view = TextView(this).apply {
            text = "KM"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
            alpha = 0.85f
        }
        val params = overlayParams(size, size, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE).apply {
            x = dp(8)
            y = dp(120)
        }
        view.setOnTouchListener(BubbleTouchListener(params))
        wm.addView(view, params)
        bubble = view
        bubbleParams = params

        lifecycleScope.launch {
            ShizukuManager.mappingEnabled.collect { on ->
                (view.background as GradientDrawable).setColor(if (on) 0xFF2E7D32.toInt() else 0xFF616161.toInt())
                view.text = if (on) "AÇIK" else "KM"
            }
        }
    }

    /** Sürükleyince simgeyi taşır, dokununca editörü açar. */
    private inner class BubbleTouchListener(private val params: WindowManager.LayoutParams) : View.OnTouchListener {
        private val slop = ViewConfiguration.get(this@OverlayService).scaledTouchSlop
        private var startX = 0f
        private var startY = 0f
        private var origX = 0
        private var origY = 0
        private var dragging = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.rawX; startY = e.rawY
                    origX = params.x; origY = params.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - startX
                    val dy = e.rawY - startY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        params.x = origX + dx.toInt()
                        params.y = origY + dy.toInt()
                        wm.updateViewLayout(v, params)
                    }
                }
                MotionEvent.ACTION_UP -> if (!dragging) openEditor()
            }
            return true
        }
    }

    // ---- Editör ----

    private fun openEditor() {
        if (editor != null) return
        if (ShizukuManager.service == null) {
            Toast.makeText(this, "Shizuku servisi bağlı değil", Toast.LENGTH_LONG).show()
            return
        }
        // Editör açıkken tuşlar dokunuş üretmesin; klavye yakalanıp yalnızca "tuşa bas" için kullanılsın
        wasEnabledBeforeEdit = ShizukuManager.mappingEnabled.value
        ShizukuManager.setMappingEnabled(false)
        ShizukuManager.setCaptureMode(true)

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                KeymapperTheme {
                    EditorOverlay(
                        initial = MappingRepository.mappings.value,
                        onSave = { list ->
                            MappingRepository.replaceAll(list)
                            closeEditor(save = true)
                        },
                        onCancel = { closeEditor(save = false) },
                    )
                }
            }
        }
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        val params = overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, flags)
        wm.addView(view, params)
        editor = view
        bubble?.visibility = View.GONE
    }

    private fun closeEditor(save: Boolean) {
        val view = editor ?: return
        runCatching { wm.removeView(view) }
        editor = null
        bubble?.visibility = View.VISIBLE
        ShizukuManager.setCaptureMode(false)
        if (wasEnabledBeforeEdit) ShizukuManager.setMappingEnabled(true)
        if (save) Toast.makeText(this, "Eşlemeler kaydedildi", Toast.LENGTH_SHORT).show()
    }

    private fun overlayParams(w: Int, h: Int, flags: Int) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_SHOW = "io.github.omerek.keymapper.overlay.SHOW"
        const val ACTION_EDIT = "io.github.omerek.keymapper.overlay.EDIT"
        const val ACTION_STOP = "io.github.omerek.keymapper.overlay.STOP"
        private const val CHANNEL_ID = "overlay"
        private const val NOTIFICATION_ID = 1

        fun intent(context: Context, action: String) = Intent(context, OverlayService::class.java).setAction(action)
    }
}
