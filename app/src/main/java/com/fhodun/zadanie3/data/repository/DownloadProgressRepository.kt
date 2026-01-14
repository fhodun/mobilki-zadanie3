package com.fhodun.zadanie3.data.repository

import com.fhodun.zadanie3.domain.model.DownloadProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DownloadProgressRepository {
    private val _progress = MutableStateFlow<DownloadProgress?>(null)
    val progress: StateFlow<DownloadProgress?> = _progress.asStateFlow()

    fun emitProgress(info: DownloadProgress) {
        _progress.value = info
    }

    fun reset() {
        _progress.value = null
    }
}

