package com.fhodun.zadanie3

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fhodun.zadanie3.data.repository.DownloadProgressRepository
import com.fhodun.zadanie3.domain.model.DownloadProgress
import com.fhodun.zadanie3.domain.model.FileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

class MainViewModel : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    data class UiState(
        val url: String = "https://i.postimg.cc/MX2rz6Qg/danger-alert.gif",
        val infoTypeLabel: String = "Typ: -",
        val infoSizeLabel: String = "Rozmiar: -",
        val progress: DownloadProgress? = null,
        val isLoadingInfo: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val progress: StateFlow<DownloadProgress?> = DownloadProgressRepository.progress
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            progress.collect { p ->
                _uiState.update { it.copy(progress = p) }
            }
        }
    }

    fun setUrl(value: String) {
        _uiState.update { it.copy(url = value) }
    }

    fun fetchFileInfo() {
        val urlString = uiState.value.url

        if (urlString.isBlank()) {
            _uiState.update { it.copy(infoTypeLabel = "Typ: -", infoSizeLabel = "Rozmiar: -") }
            return
        }

        _uiState.update { it.copy(isLoadingInfo = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val url = URL(urlString)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 15_000
                }

                conn.connect()

                val length = conn.contentLengthLong
                val type = conn.contentType

                conn.disconnect()

                FileInfo(
                    contentType = type,
                    contentLengthBytes = if (length >= 0) length else null
                )
            }

            if (result.isFailure) {
                Log.e(TAG, "Błąd fetchFileInfo", result.exceptionOrNull())
            }

            val info = result.getOrNull()

            _uiState.update {
                it.copy(
                    isLoadingInfo = false,
                    infoTypeLabel = if (info != null) "Typ: ${info.contentType ?: "-"}" else "Typ: błąd",
                    infoSizeLabel = if (info != null) {
                        val len = info.contentLengthBytes
                        "Rozmiar: ${len?.let { "$it B" } ?: "-"}"
                    } else {
                        "Rozmiar: błąd"
                    }
                )
            }
        }
    }
}
