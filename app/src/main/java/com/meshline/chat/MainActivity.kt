package com.meshline.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.view.WindowCompat
import com.meshline.chat.ui.MeshlineApp
import com.meshline.chat.ui.MeshlineTheme

class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_PEER_ID = "peerId" }

    private val viewModel: ChatViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // The notification permission is optional; only the radio permissions are required.
        if (hasNearbyPermissions()) beginNearby() else viewModel.permissionDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            MeshlineTheme {
                MeshlineApp(
                    viewModel = viewModel,
                    onStartNearby = ::startNearbyWithPermissions
                )
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        viewModel.setAppVisible(true)
    }

    override fun onStop() {
        viewModel.setAppVisible(false)
        super.onStop()
    }

    private fun handleIntent(intent: Intent?) {
        val peerId = intent?.getStringExtra(EXTRA_PEER_ID) ?: return
        intent.removeExtra(EXTRA_PEER_ID)
        viewModel.selectConversation(peerId)
    }

    private fun requiredNearbyPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else if (Build.VERSION.SDK_INT >= 29) {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }.toTypedArray()

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasNearbyPermissions(): Boolean = requiredNearbyPermissions().all(::isGranted)

    private fun startNearbyWithPermissions() {
        // Android 11 and older cannot scan for nearby devices while the Location switch is off.
        if (Build.VERSION.SDK_INT <= 30) {
            val manager = getSystemService(LocationManager::class.java)
            if (manager == null || !LocationManagerCompat.isLocationEnabled(manager)) {
                viewModel.reportStatus("Turn on Location in quick settings · Android needs it to find nearby devices")
                return
            }
        }
        val wanted = buildList {
            addAll(requiredNearbyPermissions())
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = wanted.filterNot(::isGranted)
        if (missing.isEmpty()) beginNearby() else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun beginNearby() {
        try {
            NearbyService.start(this)   // keeps connections alive in the background
        } catch (e: Exception) {
            viewModel.reportStatus("Background service could not start: ${e.message ?: "unknown error"}")
        }
        viewModel.startNearby()
    }
}
