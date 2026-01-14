package com.fhodun.zadanie3

import com.fhodun.zadanie3.data.repository.DownloadProgressRepository
import com.fhodun.zadanie3.domain.model.DownloadProgress
import kotlinx.coroutines.flow.StateFlow

object DownloadRepository {
    val progress: StateFlow<DownloadProgress?> = DownloadProgressRepository.progress

    fun emitProgress(info: DownloadProgress) {
        DownloadProgressRepository.emitProgress(info)
    }

    fun reset() {
        DownloadProgressRepository.reset()
    }
}
