package com.example.asr.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object NetworkClient {

    private val json = Json { ignoreUnknownKeys = true }

    /** 供 WebDAV 等 Retrofit 之外的网络调用复用（同样的超时与日志配置） */
    val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)   // 长音频转写耗时较长
            .writeTimeout(300, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    /** baseUrl 可配置，因此每次按当前设置构建 */
    fun api(baseUrl: String): SiliconFlowApi =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SiliconFlowApi::class.java)

    /** MiniMax 语音服务（base 固定 https://api.minimaxi.com/v1/） */
    fun minimaxApi(baseUrl: String): MiniMaxApi =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MiniMaxApi::class.java)
}
