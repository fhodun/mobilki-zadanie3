package com.fhodun.zadanie3

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.fhodun.zadanie3.DownloadProgressRepository
import com.fhodun.zadanie3.FileInfoFetcher
import com.fhodun.zadanie3.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MainScreen()
        }
    }
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val progress by DownloadProgressRepository.progress.collectAsState()

    var url by rememberSaveable { mutableStateOf("https://i.postimg.cc/MX2rz6Qg/danger-alert.gif") }
    var infoTypeLabel by rememberSaveable { mutableStateOf("Typ: -") }
    var infoSizeLabel by rememberSaveable { mutableStateOf("Rozmiar: -") }
    var isLoadingInfo by rememberSaveable { mutableStateOf(false) }

    var pendingDownloadUrl by rememberSaveable { mutableStateOf<String?>(null) }

    val requestPostNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        Log.d("MainActivity", "POST_NOTIFICATIONS granted=$granted")
        pendingDownloadUrl?.let { startDownloadService(context, it) }
        pendingDownloadUrl = null
    }

    val state = remember(url, infoTypeLabel, infoSizeLabel, progress, isLoadingInfo) {
        MainUiState(
            url = url,
            infoTypeLabel = infoTypeLabel,
            infoSizeLabel = infoSizeLabel,
            progressStatus = progress?.status,
            progressDownloadedBytes = progress?.downloadedBytes ?: 0,
            progressTotalBytes = progress?.totalBytes ?: 0,
            progressFraction = progress?.fraction() ?: 0f,
            progressErrorMessage = progress?.errorMessage,
            progressFilePath = progress?.filePath,
            isLoadingInfo = isLoadingInfo
        )
    }

    val actions = remember(context, scope, url) {
        MainActions(
            onUrlChange = { url = it },
            onGetInfoClick = {
                onGetInfoClick(
                    url = url,
                    setLoading = { isLoadingInfo = it },
                    setInfoTypeLabel = { infoTypeLabel = it },
                    setInfoSizeLabel = { infoSizeLabel = it },
                    scope = scope,
                    toast = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                    logError = { msg, t -> Log.e("MainActivity", msg, t) }
                )
            },
            onDownloadClick = {
                onDownloadClick(
                    url = url,
                    context = context,
                    setPendingUrl = { pendingDownloadUrl = it },
                    requestPostNotifications = { requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    toast = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                    log = { msg -> Log.d("MainActivity", msg) }
                )
            }
        )
    }

    MainScreenContent(
        state = state,
        actions = actions
    )
}

@Composable
private fun MainScreenContent(
    state: MainUiState,
    actions: MainActions,
) {
    val total = state.progressTotalBytes.takeIf { it > 0 } ?: 0
    val downloaded = state.progressDownloadedBytes

    val progressLabel = "Postęp: $downloaded / $total"
    val progressValue = state.progressFraction

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
                value = state.url,
                onValueChange = actions.onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Adres URL") }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = actions.onGetInfoClick,
                    enabled = !state.isLoadingInfo
                ) {
                    Text(if (state.isLoadingInfo) "Sprawdzam…" else "Pobierz informacje")
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = actions.onDownloadClick
                ) {
                    Text("Pobierz plik")
                }
            }

            Text(text = state.infoTypeLabel)
            Text(text = state.infoSizeLabel)

            when (state.progressStatus) {
                DownloadStatus.ERROR -> {
                    Text(text = "Błąd pobierania")
                    state.progressErrorMessage?.takeIf { it.isNotBlank() }?.let {
                        Text(text = it)
                    }
                }

                DownloadStatus.DONE -> {
                    Text(text = "Pobieranie zakończone")
                    state.progressFilePath?.let {
                        Text(text = "Zapisano: $it")
                    }
                }

                DownloadStatus.RUNNING -> {
                    Text(text = progressLabel)
                    state.progressFilePath?.let {
                        Text(text = "Docelowo: $it", style = MaterialTheme.typography.bodySmall)
                    }
                }

                else -> {
                    Text(text = progressLabel)
                }
            }

            LinearProgressIndicator(
                progress = { progressValue },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally)
            )
        }
    }
}

private data class MainUiState(
    val url: String,
    val infoTypeLabel: String,
    val infoSizeLabel: String,
    val progressStatus: DownloadStatus?,
    val progressDownloadedBytes: Long,
    val progressTotalBytes: Long,
    val progressFraction: Float,
    val progressErrorMessage: String?,
    val progressFilePath: String?,
    val isLoadingInfo: Boolean
)

private data class MainActions(
    val onUrlChange: (String) -> Unit,
    val onGetInfoClick: () -> Unit,
    val onDownloadClick: () -> Unit,
)

private fun onGetInfoClick(
    url: String,
    setLoading: (Boolean) -> Unit,
    setInfoTypeLabel: (String) -> Unit,
    setInfoSizeLabel: (String) -> Unit,
    scope: CoroutineScope,
    toast: (String) -> Unit,
    logError: (String, Throwable?) -> Unit,
) {
    if (url.isBlank()) {
        toast("Podaj URL")
        return
    }

    setLoading(true)
    scope.launch {
        val result = FileInfoFetcher.fetch(url)
        if (result.isFailure) {
            logError("Błąd pobierania informacji o pliku", result.exceptionOrNull())
        }

        val info = result.getOrNull()
        setInfoTypeLabel(if (info != null) "Typ: ${info.contentType ?: "-"}" else "Typ: błąd")
        setInfoSizeLabel(
            if (info != null) {
                val len = info.contentLengthBytes
                "Rozmiar: ${len?.let { "$it B" } ?: "-"}"
            } else {
                "Rozmiar: błąd"
            }
        )

        setLoading(false)
    }
}

private fun onDownloadClick(
    url: String,
    context: android.content.Context,
    setPendingUrl: (String?) -> Unit,
    requestPostNotifications: () -> Unit,
    toast: (String) -> Unit,
    log: (String) -> Unit,
) {
    if (url.isBlank()) {
        toast("Podaj URL")
        return
    }

    setPendingUrl(url)
    log("Klik: Pobierz plik, url=$url")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            toast("Prośba o zgodę na notyfikacje…")
            requestPostNotifications()
            return
        }
    }

    startDownloadService(context, url)
}

private fun startDownloadService(context: android.content.Context, url: String) {
    val intent = Intent(context, DownloadService::class.java)
        .putExtra(DownloadService.EXTRA_URL, url)

    try {
        Log.d("MainActivity", "Startuję DownloadService")
        Toast.makeText(context, "Start pobierania…", Toast.LENGTH_SHORT).show()
        context.startForegroundService(intent)
    } catch (t: Throwable) {
        Log.e("MainActivity", "Nie udało się uruchomić DownloadService", t)
        Toast.makeText(
            context,
            "Nie udało się uruchomić pobierania: ${t::class.java.simpleName}",
            Toast.LENGTH_LONG
        ).show()
    }
}
