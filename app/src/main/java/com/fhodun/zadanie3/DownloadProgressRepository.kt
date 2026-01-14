package com.fhodun.zadanie3

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DownloadProgressRepository {

    private val _progress = MutableStateFlow<DownloadProgress?>(null)
    val progress: StateFlow<DownloadProgress?> = _progress.asStateFlow()

    fun emitProgress(value: DownloadProgress) {
        _progress.value = value
    }

    fun reset() {
        _progress.value = null
    }
}