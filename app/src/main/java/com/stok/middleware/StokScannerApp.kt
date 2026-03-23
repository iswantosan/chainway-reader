package com.stok.middleware

import android.app.Application
import com.stok.middleware.data.local.AppPreferences

class StokScannerApp : Application() {
    val appPreferences: AppPreferences by lazy { AppPreferences(this) }
}
