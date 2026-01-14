package com.fhodun.zadanie3

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PostepInfo(
    var mPobranychBajtow: Long = 0,
    var mRozmiar: Long = 0,
    var mStatus: Int = STATUS_IDLE,
    var mPlikSciezka: String? = null,
    var mBlad: String? = null
) : Parcelable {
    companion object {
        const val STATUS_IDLE = 0
        const val STATUS_RUNNING = 1
        const val STATUS_DONE = 2
        const val STATUS_ERROR = 3
    }
}