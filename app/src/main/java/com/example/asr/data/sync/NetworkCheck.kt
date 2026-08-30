package com.example.asr.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** 当前是否连接 WiFi */
fun isOnWifi(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}
