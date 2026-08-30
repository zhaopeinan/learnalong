package com.example.asr.data.sync

import com.example.asr.data.remote.NetworkClient
import com.example.asr.data.remote.toUserMessage
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/** WebDAV 请求失败，message 已是用户可读的中文提示 */
class WebDavException(message: String, val code: Int = -1) : Exception(message)

/**
 * 极简 WebDAV 客户端（坚果云等），基于现有 OkHttp 手写，不引入第三方 WebDAV 库。
 * 所有方法都是阻塞式 HTTP 调用，调用方需切到 IO 线程/协程。
 */
class WebDavClient(baseUrl: String, user: String, password: String) {

    private val root = joinUrl(baseUrl, "")
    private val auth = Credentials.basic(user, password)

    /**
     * WebDAV 专用连接：强制 HTTP/1.1。
     * 坚果云等服务端对 HTTP/2 长连接/大文件上传支持不佳，
     * 会出现 "stream was reset: PROTOCOL_ERROR"，降级到 HTTP/1.1 可规避。
     */
    private val http = NetworkClient.okHttp.newBuilder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    /** PROPFIND 根地址（Depth: 0），2xx/207 即视为连接成功 */
    fun testConnection() {
        execute(Request.Builder().url(root).method("PROPFIND", null).header("Depth", "0").build())
    }

    /** 逐级创建目录（MKCOL 405/409 视为已存在） */
    fun ensureDir(path: String) {
        val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
        var current = ""
        for (seg in segments) {
            current = if (current.isEmpty()) seg else "$current/$seg"
            val req = Request.Builder().url(joinUrl(root, current) + "/").method("MKCOL", null).build()
            try {
                execute(req)
            } catch (e: WebDavException) {
                // 405 = 已存在；409 = 父目录缺失时继续尝试下一级也无意义，但当部分服务端对
                // 已存在目录返回 409，因此也按已存在忽略
                if (e.code != 405 && e.code != 409) throw e
            }
        }
    }

    /** PROPFIND 判断远端资源是否存在 */
    fun exists(path: String): Boolean {
        val req = Request.Builder().url(joinUrl(root, path)).method("PROPFIND", null).header("Depth", "0").build()
        return try {
            execute(req)
            true
        } catch (e: WebDavException) {
            if (e.code == 404) false else throw e
        }
    }

    /** 远端文件大小（getcontentlength），不存在返回 null */
    fun remoteSize(path: String): Long? {
        val req = Request.Builder().url(joinUrl(root, path)).method("PROPFIND", null).header("Depth", "0").build()
        return try {
            val body = execute(req)
            Regex("<[^>]*getcontentlength[^>]*>(\\d+)<", RegexOption.IGNORE_CASE)
                .find(body)?.groupValues?.get(1)?.toLongOrNull()
        } catch (e: WebDavException) {
            if (e.code == 404) null else throw e
        }
    }

    /** 列出远端目录下的文件（PROPFIND Depth: 1），返回 文件名 → 字节数；目录不存在返回空表 */
    fun listFiles(path: String): Map<String, Long> {
        val req = Request.Builder()
            .url(joinUrl(root, path) + "/")
            .method("PROPFIND", null)
            .header("Depth", "1")
            .build()
        val body = try {
            execute(req)
        } catch (e: WebDavException) {
            if (e.code == 404) return emptyMap() else throw e
        }
        // 逐个解析 <response> 块：<href> 末段为文件名（URL 编码），getcontentlength 为大小
        val result = LinkedHashMap<String, Long>()
        for (block in Regex("<[^>]*:response[^>]*>(.*?)</[^>]*:response>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(body)) {
            val blockText = block.groupValues[1]
            val href = Regex("<[^>]*:href[^>]*>([^<]+)<", RegexOption.IGNORE_CASE)
                .find(blockText)?.groupValues?.get(1) ?: continue
            if (href.endsWith("/")) continue // 跳过目录自身与子目录
            val name = try {
                java.net.URLDecoder.decode(href.trimEnd('/').substringAfterLast('/'), "UTF-8")
            } catch (_: Exception) {
                href.substringAfterLast('/')
            }
            val size = Regex("<[^>]*getcontentlength[^>]*>(\\d+)<", RegexOption.IGNORE_CASE)
                .find(blockText)?.groupValues?.get(1)?.toLongOrNull() ?: -1L
            result[name] = size
        }
        return result
    }

    /** PUT 上传本地文件（覆盖写） */
    fun upload(path: String, file: File) {
        val req = Request.Builder()
            .url(joinUrl(root, path))
            .put(file.asRequestBody("application/octet-stream".toMediaType()))
            .header("Expect", "100-continue")
            .build()
        execute(req)
    }

    /** PUT 上传字节内容（覆盖写） */
    fun uploadBytes(path: String, bytes: ByteArray, mime: String = "application/octet-stream") {
        val req = Request.Builder()
            .url(joinUrl(root, path))
            .put(bytes.toRequestBody(mime.toMediaType()))
            .header("Expect", "100-continue")
            .build()
        execute(req)
    }

    /** GET 下载远端文件内容 */
    fun download(path: String): ByteArray {
        val req = Request.Builder().url(joinUrl(root, path)).get().build()
        return executeBytes(req)
    }

    private fun execute(request: Request): String = String(executeBytes(request), Charsets.UTF_8)

    /** 所有 WebDAV 操作（PROPFIND/MKCOL/PUT/GET）都是幂等的，网络层抖动时安全重试一次 */
    private fun executeBytes(request: Request): ByteArray {
        val req = request.newBuilder().header("Authorization", auth).build()
        try {
            return executeOnce(req)
        } catch (e: WebDavException) {
            throw e
        } catch (e: java.io.IOException) {
            // 连接被重置/流错误等瞬时故障，重试一次
            return try {
                executeOnce(req)
            } catch (e2: Exception) {
                throw WebDavException(e2.toUserMessage())
            }
        } catch (e: Exception) {
            throw WebDavException(e.toUserMessage())
        }
    }

    private fun executeOnce(req: Request): ByteArray {
        http.newCall(req).execute().use { resp ->
            val code = resp.code
            // WebDAV 多状态响应 207 也算成功
            if (code in 200..299 || code == 207) {
                return resp.body?.bytes() ?: ByteArray(0)
            }
            throw WebDavException(friendlyMessage(code, resp.body?.string()), code)
        }
    }

    private fun friendlyMessage(code: Int, body: String?): String = when (code) {
        401 -> "账号或应用密码错误（坚果云需使用应用授权码，不是登录密码）"
        403 -> "没有权限访问该路径"
        404 -> "路径不存在"
        507 -> "云端存储空间不足"
        else -> "HTTP $code" + if (body.isNullOrBlank()) "" else "：${body.take(200)}"
    }

    companion object {
        /**
         * 拼接 base 与相对路径：base 统一补末尾斜杠，path 去掉首尾斜杠，
         * 各段交给 OkHttp HttpUrl 做 percent 编码（纯 JVM 实现，便于单元测试）。
         * 例如 joinUrl("https://dav.jianguoyun.com/dav", "ASRTutor/backup.json")
         *   -> "https://dav.jianguoyun.com/dav/ASRTutor/backup.json"
         */
        fun joinUrl(base: String, path: String): String {
            val b = if (base.endsWith("/")) base else "$base/"
            val p = path.trim('/')
            if (p.isEmpty()) return b
            return b.toHttpUrl().newBuilder()
                .addPathSegments(p)
                .build()
                .toString()
        }
    }
}
