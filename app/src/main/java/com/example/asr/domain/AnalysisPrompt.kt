package com.example.asr.domain

/** 构造薄弱点分析的 prompt（OpenAI chat 格式的 user 消息内容） */
object AnalysisPrompt {

    const val SYSTEM = "你是一位耐心的中小学辅导老师，擅长从家长辅导孩子的对话中诊断孩子的知识薄弱点。"

    /**
     * @param grade 孩子年级（如"三年级"），未知时传 null
     * @param rawTranscript 带说话人标签的原始转写文稿
     * @param polishedTranscript 润色后的文稿，未润色时传 null
     */
    fun build(subject: String, grade: String?, rawTranscript: String, polishedTranscript: String?): String {
        val gradeInfo = if (grade.isNullOrBlank()) "" else "，孩子是${grade}学生"
        val gradeRules = if (grade.isNullOrBlank()) {
            "- 重点关注孩子回答错误、犹豫、跟读不准确、被家长纠正的地方；"
        } else {
            """- 重点关注孩子回答错误、犹豫、跟读不准确、被家长纠正的地方；
- 以${grade}的教学要求为基准评估：超出该年级范围的错误不必记为薄弱点，低于该年级应掌握水平的要重点记录；
- mastery 掌握度按该年级标准估计，错误多则低；"""
        }
        val polishedSection = if (polishedTranscript.isNullOrBlank()) {
            ""
        } else {
            "\n【润色后的文稿】（已清理口语噪声、校正识别错误，分析时以此为准，原始稿用于对照）\n$polishedTranscript\n"
        }
        return """
下面是一段「$subject」科目的亲子辅导对话文稿$gradeInfo。对话中【家长】是辅导者，【孩子】是学习者；如果说话人只标了"说话人1/2"等标签，请根据内容自行判断谁是孩子——通常被提问、跟读、回答问题、被纠错的一方是孩子。
$polishedSection
【原始转写稿】
$rawTranscript

请从对话中提取孩子的薄弱知识点，只返回 JSON 数组，不要输出任何其他文字，不要用 markdown 代码围栏。
数组元素格式：
[{"knowledgePoint":"知识点名称","description":"孩子错在哪里（引用对话中的具体表现）","mastery":0-100 的整数掌握度,"suggestion":"针对该年级水平的复习建议"}]
提取要求：
$gradeRules
- 每个薄弱点对应一个具体的知识点，不要泛泛而谈；
- 如果对话中确实没有可提取的薄弱点，返回空数组 []。
""".trim()
    }
}
