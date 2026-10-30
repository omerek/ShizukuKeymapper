package io.github.omerek.keymapper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.omerek.keymapper.shizuku.ShizukuManager
import io.github.omerek.keymapper.ui.MainScreen
import io.github.omerek.keymapper.ui.KeymapperTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ShizukuManager.init(this)
        setContent {
            KeymapperTheme {
                MainScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Kullanıcı Shizuku'yu başlatıp geri dönmüş olabilir
        ShizukuManager.refresh()
    }
}
