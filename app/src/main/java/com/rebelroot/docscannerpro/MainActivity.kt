package com.rebelroot.docscannerpro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.rebelroot.docscannerpro.notifications.QuickAccessNotification
import com.rebelroot.docscannerpro.ui.navigation.AppNavigation
import org.opencv.android.OpenCVLoader
import com.rebelroot.docscannerpro.ui.theme.DocScannerTheme
import com.rebelroot.docscannerpro.ui.viewmodel.ScanMode

class MainActivity : ComponentActivity() {

    private var quickMode by mutableStateOf<ScanMode?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) QuickAccessNotification.show(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        check(OpenCVLoader.initLocal()) { "OpenCV failed to initialize" }
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)
        consumeQuickMode(intent)
        maybeShowQuickAccessNotification()
        setContent {
            DocScannerTheme(dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        quickMode = quickMode,
                        onQuickModeConsumed = { quickMode = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeQuickMode(intent)
    }

    private fun consumeQuickMode(intent: Intent?) {
        val mode = intent?.getStringExtra(QuickAccessNotification.EXTRA_QUICK_MODE) ?: return
        quickMode = try {
            ScanMode.valueOf(mode)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun maybeShowQuickAccessNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                QuickAccessNotification.show(this)
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            QuickAccessNotification.show(this)
        }
    }
}
