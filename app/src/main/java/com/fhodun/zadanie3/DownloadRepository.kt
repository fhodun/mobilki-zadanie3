package com.fhodun.zadanie3

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Prosty singleton na postęp pobierania.
 * Dzięki temu UI może obserwować stan przez ViewModel i nie traci go przy rotacji.
 */
object DownloadRepository {
    private val _progress = MutableStateFlow<PostepInfo?>(null)
    val progress: StateFlow<PostepInfo?> = _progress.asStateFlow()

    fun emitProgress(info: PostepInfo) {
        _progress.value = info
    }

    fun reset() {
        _progress.value = null
    }
}

