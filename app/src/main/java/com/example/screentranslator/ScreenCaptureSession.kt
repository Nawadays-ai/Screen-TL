package com.example.screentranslator

import android.content.Intent

object ScreenCaptureSession {

    var resultCode: Int = -1
    var data: Intent? = null

    fun save(resultCode: Int, data: Intent) {
        this.resultCode = resultCode
        this.data = data
    }

    fun clear() {
        resultCode = -1
        data = null
    }
}
