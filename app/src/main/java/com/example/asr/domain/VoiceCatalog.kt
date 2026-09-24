package com.example.asr.domain

import com.example.asr.data.settings.AppSettings

/**
 * MiniMax 音色与模型目录（与小程序 utils/settings.ts 的 MINIMAX_MODELS / MINIMAX_PRESET_VOICES 逐字对齐）。
 * 设置页音色管理与孩子管理辅导音色共用。
 */
object VoiceCatalog {

    data class VoiceOption(val value: String, val label: String)

    /** MiniMax 合成模型列表（效果最好的 2.8-hd 在最前，为默认） */
    val MINIMAX_MODELS: List<VoiceOption> = listOf(
        VoiceOption("speech-2.8-hd", "speech-2.8-hd（最新高清，推荐）"),
        VoiceOption("speech-2.8-turbo", "speech-2.8-turbo（最新快速）"),
        VoiceOption("speech-2.6-hd", "speech-2.6-hd（高清）"),
        VoiceOption("speech-2.6-turbo", "speech-2.6-turbo（快速）"),
        VoiceOption("speech-02-hd", "speech-02-hd（经典高清）"),
        VoiceOption("speech-02-turbo", "speech-02-turbo（经典快速）"),
        VoiceOption("speech-01-hd", "speech-01-hd（早期高清）"),
        VoiceOption("speech-01-turbo", "speech-01-turbo（早期快速）"),
    )

    /** MiniMax 预置音色（普通话，按「适合给孩子做 AI 老师」筛选） */
    val PRESET_VOICES: List<VoiceOption> = listOf(
        VoiceOption(AppSettings.DEFAULT_MINIMAX_VOICE, "温柔学姐"),
        VoiceOption("Chinese (Mandarin)_Warm_Girl", "温暖少女"),
        VoiceOption("Chinese (Mandarin)_Sweet_Lady", "甜美女声"),
        VoiceOption("female-chengshu", "成熟女性"),
        VoiceOption("Chinese (Mandarin)_Sincere_Adult", "真诚青年"),
        VoiceOption("Chinese (Mandarin)_Gentleman", "温润男声"),
        VoiceOption("Chinese (Mandarin)_Radio_Host", "电台男主播"),
        VoiceOption("Chinese (Mandarin)_Kind-hearted_Antie", "热心大婶"),
        VoiceOption("Chinese (Mandarin)_Kind-hearted_Elder", "花甲奶奶"),
        VoiceOption("lovely_girl", "萌萌女童"),
        VoiceOption("clever_boy", "聪明男童"),
        VoiceOption("cartoon_pig", "卡通猪小琪"),
    )

    /** 音色试听文案（设置页与孩子管理共用，对齐小程序 onVoicePreview） */
    const val PREVIEW_TEXT = "宝贝你好呀，今天想学什么呀？"

    /** 声音复刻试听文案（复刻成功后 MiniMax 现场合成，对齐小程序 onStartClone） */
    const val CLONE_DEMO_TEXT = "宝贝你好呀，我是爸爸/妈妈，今天想学什么呀？"

    /** 孩子辅导音色选项表头项：空 key = 跟随全局默认（对齐小程序 listVoiceOptions） */
    const val FOLLOW_DEFAULT_LABEL = "默认声音（跟随设置）"

    /** 音色显示名：先查预置音色，再查复刻音色；空串返回默认音色名 */
    fun labelOf(voiceId: String, cloned: List<Pair<String, String>>): String =
        PRESET_VOICES.firstOrNull { it.value == voiceId }?.label
            ?: cloned.firstOrNull { it.first == voiceId }?.second
            ?: labelOf(AppSettings.DEFAULT_MINIMAX_VOICE, emptyList())
}
