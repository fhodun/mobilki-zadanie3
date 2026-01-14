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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.fhodun.zadanie3.ui.theme.Zadanie3Theme

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
            Zadanie3Theme {
                val url by viewModel.url.collectAsState()
                val typeLabel by viewModel.typeLabel.collectAsState()
                val sizeLabel by viewModel.sizeLabel.collectAsState()
                val progressInfo by viewModel.progressInfo.collectAsState()

                MainScreen(
                    url = url,
                    typeLabel = typeLabel,
                    sizeLabel = sizeLabel,
                    progressInfo = progressInfo,
                    onUrlChange = { viewModel.setUrl(it) },
                    onGetInfoClick = {
                        if (url.isBlank()) {
                            Toast.makeText(this, "Podaj URL", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.fetchFileInfo(url)
                        }
                    },
                    onDownloadClick = {
                        if (url.isBlank()) {
                            Toast.makeText(this, "Podaj URL", Toast.LENGTH_SHORT).show()
                        } else {
                            checkNotifPermissionAndStart(url)
                        }
                    }
                )
            }
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
            Log.d(TAG, "Startuję DownloadService (foreground=${
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            })")
            Toast.makeText(this, "Start pobierania…", Toast.LENGTH_SHORT).show()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Nie udało się uruchomić DownloadService", t)
            Toast.makeText(this, "Nie udało się uruchomić pobierania: ${t::class.java.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    @Composable
    fun MainScreen(
        url: String,
        typeLabel: String,
        sizeLabel: String,
        progressInfo: PostepInfo?,
        onUrlChange: (String) -> Unit,
        onGetInfoClick: () -> Unit,
        onDownloadClick: () -> Unit
    ) {
        var urlText by remember(url) {
            mutableStateOf(TextFieldValue(url))
        }

        val size = progressInfo?.mRozmiar?.takeIf { it > 0 } ?: 0
        val downloaded = progressInfo?.mPobranychBajtow ?: 0
        val progressLabel = "Postęp: $downloaded / $size"
        val progressValue = if (size > 0) {
            ((downloaded.toDouble() / size.toDouble()) * 100).toInt().coerceIn(0, 100)
        } else 0

        Scaffold { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "Zadanie 3 – Pobieranie pliku (Compose)")

                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        onUrlChange(it.text)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Adres URL") }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { onGetInfoClick() }
                    ) {
                        Text("Pobierz informacje")
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val current = urlText.text
                            onUrlChange(current)
                            if (current.isBlank()) {
                                Toast.makeText(this@MainActivity, "Podaj URL", Toast.LENGTH_SHORT).show()
                            } else {
                                checkNotifPermissionAndStart(current)
                            }
                        }
                    ) {
                        Text("Pobierz plik")
                    }
                }

                Text(text = typeLabel)
                Text(text = sizeLabel)

                when (progressInfo?.mStatus) {
                    PostepInfo.STATUS_ERROR -> {
                        Text(text = "Błąd pobierania")
                        progressInfo.mBlad?.takeIf { it.isNotBlank() }?.let {
                            Text(text = it)
                        }
                    }
                    PostepInfo.STATUS_DONE -> {
                        Text(text = "Pobieranie zakończone")
                        progressInfo.mPlikSciezka?.let {
                            Text(text = "Zapisano: $it")
                        }
                    }
                    else -> {
                        Text(text = progressLabel)
                        progressInfo?.mPlikSciezka?.let {
                            Text(text = "Docelowo: $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                LinearProgressIndicator(
                    progress = (progressValue / 100f).coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "AsyncTaska świadomie zakopaliśmy. I tak nikt normalny go już nie używa.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}