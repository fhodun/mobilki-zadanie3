package com.fhodun.zadanie3.domain.model

data class DownloadProgress(
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val status: DownloadStatus = DownloadStatus.IDLE,
    val filePath: String? = null,
    val errorMessage: String? = null
)

