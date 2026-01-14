package com.fhodun.zadanie3

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

class MainViewModel : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    // rotacja nie resetuje pola URL
    private val _url = MutableStateFlow("https://files.catbox.moe/67tta8.gif")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _typeLabel = MutableStateFlow("Typ: -")
    val typeLabel: StateFlow<String> = _typeLabel.asStateFlow()

    private val _sizeLabel = MutableStateFlow("Rozmiar: -")
    val sizeLabel: StateFlow<String> = _sizeLabel.asStateFlow()

    val progressInfo: StateFlow<PostepInfo?> = DownloadRepository.progress
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), null)

    fun setUrl(value: String) {
        _url.value = value
    }

    fun fetchFileInfo(urlString: String) {
        if (urlString.isBlank()) {
            _typeLabel.value = "Typ: -"
            _sizeLabel.value = "Rozmiar: -"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
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

                _typeLabel.value = "Typ: ${type ?: "-"}"
                _sizeLabel.value = "Rozmiar: ${if (length >= 0) "$length B" else "-"}"
            } catch (e: Exception) {
                Log.e(TAG, "Błąd fetchFileInfo", e)
                _typeLabel.value = "Typ: błąd"
                _sizeLabel.value = "Rozmiar: błąd"
            }
        }
    }
}

