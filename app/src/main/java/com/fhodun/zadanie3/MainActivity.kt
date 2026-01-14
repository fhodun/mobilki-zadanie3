package com.fhodun.zadanie3

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.fhodun.zadanie3.ui.main.MainScreen

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val viewModel: MainViewModel by viewModels()
    private var pendingDownloadUrl: String? = null

    private val requestPostNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        Log.d(TAG, "POST_NOTIFICATIONS granted=$granted")
        pendingDownloadUrl?.let { startDownloadService(it) }
        pendingDownloadUrl = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val state by viewModel.uiState.collectAsState()

            MainScreen(
                state = state,
                onUrlChange = viewModel::setUrl,
                onGetInfoClick = {
                    if (state.url.isBlank()) {
                        Toast.makeText(this, "Podaj URL", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.fetchFileInfo()
                    }
                },
                onDownloadClick = {
                    val url = state.url
                    if (url.isBlank()) {
                        Toast.makeText(this, "Podaj URL", Toast.LENGTH_SHORT).show()
                    } else {
                        checkNotifPermissionAndStart(url)
                    }
                }
            )
        }
    }

    private fun checkNotifPermissionAndStart(url: String) {
        pendingDownloadUrl = url
        Log.d(TAG, "Klik: Pobierz plik, url=$url")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                Toast.makeText(this, "Prośba o zgodę na notyfikacje…", Toast.LENGTH_SHORT).show()
                requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        startDownloadService(url)
    }

    private fun startDownloadService(url: String) {
        val intent = Intent(this, DownloadService::class.java)
            .putExtra(DownloadService.EXTRA_URL, url)

        try {
            Log.d(TAG, "Startuję DownloadService")
            Toast.makeText(this, "Start pobierania…", Toast.LENGTH_SHORT).show()

            startForegroundService(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "Nie udało się uruchomić DownloadService", t)
            Toast.makeText(this, "Nie udało się uruchomić pobierania: ${t::class.java.simpleName}", Toast.LENGTH_LONG).show()
        }
    }
}