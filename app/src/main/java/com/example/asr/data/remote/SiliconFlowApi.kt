package com.example.asr.data.remote

import com.example.asr.data.remote.dto.ChatRequest
import com.example.asr.data.remote.dto.ChatResponse
import com.example.asr.data.remote.dto.VisionChatRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface SiliconFlowApi {

    @Multipart
    @POST("audio/transcriptions")
    suspend fun transcribe(
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("response_format") responseFormat: RequestBody,
    ): ResponseBody

    @POST("chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") authorization: String,
        @Body request: ChatRequest,
    ): ChatResponse

    /** 多模态对话：消息体带图片 content part，走同一 endpoint */
    @POST("chat/completions")
    suspend fun visionChatCompletions(
        @Header("Authorization") authorization: String,
        @Body request: VisionChatRequest,
    ): ChatResponse
}
