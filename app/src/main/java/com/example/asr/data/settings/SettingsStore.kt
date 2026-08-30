package com.example.asr.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

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
        val LAST_BACKUP_AT = androidx.datastore.preferences.core.longPreferencesKey("last_backup_at")
        val AUTO_BACKUP_WIFI = androidx.datastore.preferences.core.booleanPreferencesKey("auto_backup_wifi")
        // 科目列表用 \n 分隔存储（简单可靠，科目名不允许含换行）
        val SUBJECTS = stringPreferencesKey("subjects")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

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
}
