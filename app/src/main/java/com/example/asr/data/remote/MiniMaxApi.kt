package com.example.asr.data.remote

import com.example.asr.data.remote.dto.MiniMaxUploadResponse
import com.example.asr.data.remote.dto.MiniMaxTtsRequest
import com.example.asr.data.remote.dto.MiniMaxTtsResponse
import com.example.asr.data.remote.dto.MiniMaxVoiceCloneRequest
import com.example.asr.data.remote.dto.MiniMaxVoiceCloneResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/** MiniMax 业务错误（base_resp.status_code 非 0）或返回解析失败 */
class MiniMaxApiException(message: String) : Exception(message)

/**
 * MiniMax 语音服务：家长声音复刻（voice_clone）+ 语音合成（t2a_v2）。
 * 流程：录音文件 → files/upload 得 file_id → voice_clone 得 voice_id → t2a_v2 用该 voice_id 合成。
 * 与小程序 minimax.ts 对应；响应统一走 MiniMaxResponseParser 容错解析。
 */
interface MiniMaxApi {

    /** 语音合成：output_format=url，返回 24h 有效的音频链接 */
    @POST("t2a_v2")
    suspend fun synthesize(
        @Header("Authorization") authorization: String,
        @Body request: MiniMaxTtsRequest,
    ): ResponseBody

    /** 上传待复刻的家长录音（10s-5min，mp3/m4a/wav），purpose=voice_clone */
    @Multipart
    @POST("files/upload")
    suspend fun uploadVoiceFile(
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
        @Part("purpose") purpose: RequestBody,
    ): ResponseBody

    /** 复刻音色：返回试听音频 URL（无试听文本时无 demo_audio） */
    @POST("voice_clone")
    suspend fun cloneVoice(
        @Header("Authorization") authorization: String,
        @Body request: MiniMaxVoiceCloneRequest,
    ): ResponseBody
}

/** MiniMax 响应容错解析：先校验 base_resp，再取业务字段 */
object MiniMaxResponseParser {

    private val json = Json { ignoreUnknownKeys = true }

    /** t2a_v2：返回音频链接；合成成功但未返回链接视为错误 */
    fun parseTtsAudioUrl(body: String): String {
        val resp = decode<MiniMaxTtsResponse>(body)
        checkBase(resp.baseResp.statusCode, resp.baseResp.statusMsg)
        return resp.data?.audio?.takeIf { it.isNotBlank() }
            ?: throw MiniMaxApiException("合成成功但未返回音频链接")
    }

    /** files/upload：返回 file_id */
    fun parseUploadFileId(body: String): Long {
        val resp = decode<MiniMaxUploadResponse>(body)
        checkBase(resp.baseResp.statusCode, resp.baseResp.statusMsg)
        return resp.file?.fileId
            ?: throw MiniMaxApiException("上传成功但未返回 file_id")
    }

    /** voice_clone：返回试听音频 URL（无试听文本时为 null） */
    fun parseCloneDemoAudio(body: String): String? {
        val resp = decode<MiniMaxVoiceCloneResponse>(body)
        checkBase(resp.baseResp.statusCode, resp.baseResp.statusMsg)
        return resp.demoAudio?.takeIf { it.isNotBlank() }
    }

    private inline fun <reified T> decode(body: String): T = try {
        json.decodeFromString<T>(body)
    } catch (e: MiniMaxApiException) {
        throw e
    } catch (_: Exception) {
        throw MiniMaxApiException("MiniMax 返回解析失败")
    }

    private fun checkBase(statusCode: Int, statusMsg: String) {
        if (statusCode != 0) {
            throw MiniMaxApiException("MiniMax 错误 $statusCode：${statusMsg.ifBlank { "未知错误" }}")
        }
    }
}
