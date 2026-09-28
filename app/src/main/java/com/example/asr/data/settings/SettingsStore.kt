package com.example.asr.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

/** MiniMax 复刻的家长音色（与小程序 ClonedVoice 一致） */
@Serializable
data class ClonedVoice(
    val voiceId: String,
    val name: String,
)

data class AppSettings(
    val apiKey: String = "",
    val baseUrl: String = DEFAULT_BASE_URL,
    val asrModel: String = DEFAULT_ASR_MODEL,
    val llmModel: String = DEFAULT_LLM_MODEL,
    val vlmModel: String = DEFAULT_VLM_MODEL,
    val reminderHour: Int = DEFAULT_REMINDER_HOUR,
    val reminderMinute: Int = DEFAULT_REMINDER_MINUTE,
    val webdavUrl: String = DEFAULT_WEBDAV_URL,
    val webdavUser: String = "",
    val webdavPassword: String = "",
    val lastBackupAt: Long = 0L,          // 上次成功备份时间，0 = 从未备份
    val autoBackupOnWifi: Boolean = true, // 启动时 WiFi 下自动备份
    val subjects: List<String> = DEFAULT_SUBJECTS, // 可选科目（可在设置页编辑）
    val themeMode: String = THEME_SYSTEM, // 外观：跟随本机 / 白天 / 黑夜
    val agreementAgreedV1: Boolean = false, // 已同意用户协议与隐私政策（协议闸门）
    val appMode: String = MODE_PARENT,    // 家长端 / 孩子端 / 工作端
    val kidChildId: Long? = null,         // 孩子端绑定的孩子 id
    val pointsChildId: Long? = null,      // 积分 tab 上次选中的孩子 id
    val parentPin: String = "",           // 家长密码（4-6 位数字 PIN），空串 = 未设置
    val minimaxApiKey: String = "",       // MiniMax API Key（语音合成 + 家长声音复刻）
    val minimaxModel: String = DEFAULT_MINIMAX_MODEL,
    val clonedVoices: List<ClonedVoice> = emptyList(), // 复刻的家长音色列表
    /** 默认播报音色：空串 = DEFAULT_MINIMAX_VOICE 预置音色 */
    val preferredVoiceId: String = "",
    val chatVoiceEnabled: Boolean = true, // AI 回复语音播报开关
    /** 分析完成后录音文件处理方式 */
    val audioCleanupMode: String = CLEANUP_ASK,
    /** 调试模式：开启后记录失败的模型请求（含完整提示词），便于排查 */
    val debugMode: Boolean = false,
    val tourDoneV1: Boolean = false,      // 新手引导已完成
    val demoSeededV1: Boolean = false,    // 演示数据已写入
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.siliconflow.cn/v1/"
        const val DEFAULT_ASR_MODEL = "XingChenAGI/XingChenASR-Diarize-V3.0"
        const val DEFAULT_LLM_MODEL = "Qwen/Qwen2.5-7B-Instruct"
        const val DEFAULT_VLM_MODEL = "Qwen/Qwen3-VL-32B-Instruct"
        const val DEFAULT_REMINDER_HOUR = 20
        const val DEFAULT_REMINDER_MINUTE = 0
        const val DEFAULT_WEBDAV_URL = "https://dav.jianguoyun.com/dav/"
        val DEFAULT_SUBJECTS = listOf("拼音", "英语", "数学", "语文", "科技")
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val MINIMAX_BASE_URL = "https://api.minimaxi.com/v1/"
        const val DEFAULT_MINIMAX_MODEL = "speech-2.8-hd"
        /** MiniMax 预置音色默认值「温柔学姐」：亲切、吐字清晰，适合辅导场景 */
        const val DEFAULT_MINIMAX_VOICE = "Chinese (Mandarin)_Gentle_Senior"
        const val MODE_PARENT = "parent"
        const val MODE_KID = "kid"
        const val MODE_WORK = "work"
        const val CLEANUP_ASK = "ask"
        const val CLEANUP_KEEP = "keep"
        const val CLEANUP_DELETE = "delete"
        const val CLEANUP_BACKUP_DELETE = "backup_delete"
    }
}

class SettingsStore(private val context: Context) {

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }

    private object Keys {
        val API_KEY = stringPreferencesKey("api_key")
        val BASE_URL = stringPreferencesKey("base_url")
        val ASR_MODEL = stringPreferencesKey("asr_model")
        val LLM_MODEL = stringPreferencesKey("llm_model")
        val VLM_MODEL = stringPreferencesKey("vlm_model")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val WEBDAV_URL = stringPreferencesKey("webdav_url")
        val WEBDAV_USER = stringPreferencesKey("webdav_user")
        // WebDAV 应用密码与 API Key 一样明文存 DataStore（App 私有目录，未加密）
        val WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val AUTO_BACKUP_WIFI = booleanPreferencesKey("auto_backup_wifi")
        // 科目列表用 \n 分隔存储（简单可靠，科目名不允许含换行）
        val SUBJECTS = stringPreferencesKey("subjects")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AGREEMENT_AGREED_V1 = booleanPreferencesKey("agreement_agreed_v1")
        val APP_MODE = stringPreferencesKey("app_mode")
        val KID_CHILD_ID = longPreferencesKey("kid_child_id")
        val POINTS_CHILD_ID = longPreferencesKey("points_child_id")
        val PARENT_PIN = stringPreferencesKey("parent_pin")
        val MINIMAX_API_KEY = stringPreferencesKey("minimax_api_key")
        val MINIMAX_MODEL = stringPreferencesKey("minimax_model")
        // 复刻音色列表存 JSON（[ {voiceId, name} ]）
        val CLONED_VOICES = stringPreferencesKey("cloned_voices")
        val PREFERRED_VOICE_ID = stringPreferencesKey("preferred_voice_id")
        val CHAT_VOICE_ENABLED = booleanPreferencesKey("chat_voice_enabled")
        val AUDIO_CLEANUP_MODE = stringPreferencesKey("audio_cleanup_mode")
        val DEBUG_MODE = booleanPreferencesKey("debug_mode")
        val TOUR_DONE_V1 = booleanPreferencesKey("tour_done_v1")
        val DEMO_SEEDED_V1 = booleanPreferencesKey("demo_seeded_v1")
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun parseClonedVoices(raw: String?): List<ClonedVoice> =
        raw?.let {
            try {
                json.decodeFromString<List<ClonedVoice>>(it)
                    .filter { v -> v.voiceId.isNotEmpty() }
            } catch (_: Exception) {
                emptyList()
            }
        } ?: emptyList()

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            // Key/密码不允许含空白字符：粘贴时常带入换行，读取时统一清除（兼容已存的脏数据）
            apiKey = (prefs[Keys.API_KEY] ?: "").replace(WHITESPACE, ""),
            baseUrl = prefs[Keys.BASE_URL] ?: AppSettings.DEFAULT_BASE_URL,
            asrModel = prefs[Keys.ASR_MODEL] ?: AppSettings.DEFAULT_ASR_MODEL,
            llmModel = prefs[Keys.LLM_MODEL] ?: AppSettings.DEFAULT_LLM_MODEL,
            vlmModel = prefs[Keys.VLM_MODEL] ?: AppSettings.DEFAULT_VLM_MODEL,
            reminderHour = prefs[Keys.REMINDER_HOUR] ?: AppSettings.DEFAULT_REMINDER_HOUR,
            reminderMinute = prefs[Keys.REMINDER_MINUTE] ?: AppSettings.DEFAULT_REMINDER_MINUTE,
            webdavUrl = prefs[Keys.WEBDAV_URL] ?: AppSettings.DEFAULT_WEBDAV_URL,
            webdavUser = prefs[Keys.WEBDAV_USER] ?: "",
            webdavPassword = (prefs[Keys.WEBDAV_PASSWORD] ?: "").replace(WHITESPACE, ""),
            lastBackupAt = prefs[Keys.LAST_BACKUP_AT] ?: 0L,
            autoBackupOnWifi = prefs[Keys.AUTO_BACKUP_WIFI] ?: true,
            subjects = prefs[Keys.SUBJECTS]
                ?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() }
                ?: AppSettings.DEFAULT_SUBJECTS,
            themeMode = prefs[Keys.THEME_MODE] ?: AppSettings.THEME_SYSTEM,
            agreementAgreedV1 = prefs[Keys.AGREEMENT_AGREED_V1] ?: false,
            appMode = prefs[Keys.APP_MODE]?.takeIf {
                it in listOf(AppSettings.MODE_PARENT, AppSettings.MODE_KID, AppSettings.MODE_WORK)
            } ?: AppSettings.MODE_PARENT,
            kidChildId = prefs[Keys.KID_CHILD_ID]?.takeIf { it > 0 },
            pointsChildId = prefs[Keys.POINTS_CHILD_ID]?.takeIf { it > 0 },
            parentPin = (prefs[Keys.PARENT_PIN] ?: "").replace(WHITESPACE, ""),
            minimaxApiKey = (prefs[Keys.MINIMAX_API_KEY] ?: "").replace(WHITESPACE, ""),
            minimaxModel = prefs[Keys.MINIMAX_MODEL] ?: AppSettings.DEFAULT_MINIMAX_MODEL,
            clonedVoices = parseClonedVoices(prefs[Keys.CLONED_VOICES]),
            preferredVoiceId = (prefs[Keys.PREFERRED_VOICE_ID] ?: "").replace(WHITESPACE, ""),
            chatVoiceEnabled = prefs[Keys.CHAT_VOICE_ENABLED] ?: true,
            audioCleanupMode = prefs[Keys.AUDIO_CLEANUP_MODE]?.takeIf {
                it in listOf(
                    AppSettings.CLEANUP_ASK, AppSettings.CLEANUP_KEEP,
                    AppSettings.CLEANUP_DELETE, AppSettings.CLEANUP_BACKUP_DELETE,
                )
            } ?: AppSettings.CLEANUP_ASK,
            debugMode = prefs[Keys.DEBUG_MODE] ?: false,
            tourDoneV1 = prefs[Keys.TOUR_DONE_V1] ?: false,
            demoSeededV1 = prefs[Keys.DEMO_SEEDED_V1] ?: false,
        )
    }

    suspend fun setApiKey(value: String) = context.dataStore.edit { it[Keys.API_KEY] = value.trim() }

    suspend fun setBaseUrl(value: String) = context.dataStore.edit {
        var v = value.trim()
        if (v.isEmpty()) v = AppSettings.DEFAULT_BASE_URL
        if (!v.endsWith("/")) v += "/"
        it[Keys.BASE_URL] = v
    }

    suspend fun setAsrModel(value: String) = context.dataStore.edit {
        it[Keys.ASR_MODEL] = value.trim().ifEmpty { AppSettings.DEFAULT_ASR_MODEL }
    }

    suspend fun setLlmModel(value: String) = context.dataStore.edit {
        it[Keys.LLM_MODEL] = value.trim().ifEmpty { AppSettings.DEFAULT_LLM_MODEL }
    }

    suspend fun setVlmModel(value: String) = context.dataStore.edit {
        it[Keys.VLM_MODEL] = value.trim().ifEmpty { AppSettings.DEFAULT_VLM_MODEL }
    }

    suspend fun setReminderTime(hour: Int, minute: Int) = context.dataStore.edit {
        it[Keys.REMINDER_HOUR] = hour.coerceIn(0, 23)
        it[Keys.REMINDER_MINUTE] = minute.coerceIn(0, 59)
    }

    suspend fun setWebdavUrl(value: String) = context.dataStore.edit {
        var v = value.trim()
        if (v.isEmpty()) v = AppSettings.DEFAULT_WEBDAV_URL
        if (!v.endsWith("/")) v += "/"
        it[Keys.WEBDAV_URL] = v
    }

    suspend fun setWebdavUser(value: String) = context.dataStore.edit {
        it[Keys.WEBDAV_USER] = value.trim()
    }

    suspend fun setWebdavPassword(value: String) = context.dataStore.edit {
        it[Keys.WEBDAV_PASSWORD] = value.trim()
    }

    suspend fun setLastBackupAt(value: Long) = context.dataStore.edit {
        it[Keys.LAST_BACKUP_AT] = value
    }

    suspend fun setAutoBackupOnWifi(value: Boolean) = context.dataStore.edit {
        it[Keys.AUTO_BACKUP_WIFI] = value
    }

    suspend fun setSubjects(value: List<String>) = context.dataStore.edit {
        it[Keys.SUBJECTS] = value.joinToString("\n")
    }

    suspend fun setThemeMode(value: String) = context.dataStore.edit {
        it[Keys.THEME_MODE] = value
    }

    suspend fun setAgreementAgreedV1(value: Boolean) = context.dataStore.edit {
        it[Keys.AGREEMENT_AGREED_V1] = value
    }

    suspend fun setAppMode(value: String) = context.dataStore.edit {
        it[Keys.APP_MODE] = value
    }

    suspend fun setKidChildId(value: Long?) = context.dataStore.edit {
        if (value == null) it.remove(Keys.KID_CHILD_ID) else it[Keys.KID_CHILD_ID] = value
    }

    suspend fun setPointsChildId(value: Long?) = context.dataStore.edit {
        if (value == null) it.remove(Keys.POINTS_CHILD_ID) else it[Keys.POINTS_CHILD_ID] = value
    }

    suspend fun setParentPin(value: String) = context.dataStore.edit {
        it[Keys.PARENT_PIN] = value.trim()
    }

    suspend fun setMinimaxApiKey(value: String) = context.dataStore.edit {
        it[Keys.MINIMAX_API_KEY] = value.trim()
    }

    suspend fun setMinimaxModel(value: String) = context.dataStore.edit {
        it[Keys.MINIMAX_MODEL] = value.trim().ifEmpty { AppSettings.DEFAULT_MINIMAX_MODEL }
    }

    suspend fun setClonedVoices(value: List<ClonedVoice>) = context.dataStore.edit {
        it[Keys.CLONED_VOICES] = json.encodeToString(value)
    }

    suspend fun setPreferredVoiceId(value: String) = context.dataStore.edit {
        it[Keys.PREFERRED_VOICE_ID] = value.trim()
    }

    suspend fun setChatVoiceEnabled(value: Boolean) = context.dataStore.edit {
        it[Keys.CHAT_VOICE_ENABLED] = value
    }

    suspend fun setAudioCleanupMode(value: String) = context.dataStore.edit {
        it[Keys.AUDIO_CLEANUP_MODE] = value
    }

    suspend fun setDebugMode(value: Boolean) = context.dataStore.edit {
        it[Keys.DEBUG_MODE] = value
    }

    suspend fun setTourDoneV1(value: Boolean) = context.dataStore.edit {
        it[Keys.TOUR_DONE_V1] = value
    }

    suspend fun setDemoSeededV1(value: Boolean) = context.dataStore.edit {
        it[Keys.DEMO_SEEDED_V1] = value
    }
}
