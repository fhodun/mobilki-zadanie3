package com.fhodun.zadanie3.ui.main

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fhodun.zadanie3.MainViewModel
import com.fhodun.zadanie3.domain.model.DownloadStatus

@Composable
fun MainScreen(
    state: MainViewModel.UiState,
    onUrlChange: (String) -> Unit,
    onGetInfoClick: () -> Unit,
    onDownloadClick: () -> Unit,
) {
    val progress = state.progress
    val total = progress?.totalBytes?.takeIf { it > 0 } ?: 0
    val downloaded = progress?.downloadedBytes ?: 0

    val progressLabel = "Postęp: $downloaded / $total"
    val progressValue = if (total > 0) {
        (downloaded.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat()
    } else 0f

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
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Adres URL") }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onGetInfoClick,
                    enabled = !state.isLoadingInfo
                ) {
                    Text(if (state.isLoadingInfo) "Sprawdzam…" else "Pobierz informacje")
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onDownloadClick
                ) {
                    Text("Pobierz plik")
                }
            }

            Text(text = state.infoTypeLabel)
            Text(text = state.infoSizeLabel)

            when (progress?.status) {
                DownloadStatus.ERROR -> {
                    Text(text = "Błąd pobierania")
                    progress.errorMessage?.takeIf { it.isNotBlank() }?.let {
                        Text(text = it)
                    }
                }
                DownloadStatus.DONE -> {
                    Text(text = "Pobieranie zakończone")
                    progress.filePath?.let {
                        Text(text = "Zapisano: $it")
                    }
                }
                DownloadStatus.RUNNING -> {
                    Text(text = progressLabel)
                    progress.filePath?.let {
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
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
