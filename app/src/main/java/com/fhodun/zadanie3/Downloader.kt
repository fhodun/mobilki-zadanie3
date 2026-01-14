package com.fhodun.zadanie3

interface Downloader {
    suspend fun download(url: String, onProgress: (DownloadProgress) -> Unit)
}