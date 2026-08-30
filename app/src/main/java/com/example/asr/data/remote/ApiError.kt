package com.example.asr.data.remote

import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** 把网络/业务异常转成用户可读的中文提示 */
fun Throwable.toUserMessage(): String = when (this) {
    is HttpException -> {
        val body = try {
            response()?.errorBody()?.string()?.take(300)
        } catch (_: Exception) {
            null
        }
        "HTTP ${code()}" + if (body.isNullOrBlank()) "" else "：$body"
    }
    is UnknownHostException -> "网络连接失败，请检查网络或 Base URL"
    is SocketTimeoutException -> "请求超时，请稍后重试"
    else -> message ?: "操作失败"
}
