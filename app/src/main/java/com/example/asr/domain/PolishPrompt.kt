package com.example.asr.domain

/** 文稿润色 prompt：清理口语噪声、按学科语境校正，供后续分析使用 */
object PolishPrompt {

    const val SYSTEM = "你是一位专业的文字整理编辑，擅长把口语对话的语音识别稿整理成通顺、准确的书面文稿，尤其熟悉中小学拼音、英语、数学等学科术语。"

    fun build(subject: String, grade: String?, rawTranscript: String): String {
        val gradeInfo = if (grade.isNullOrBlank()) "" else "，孩子是${grade}学生"
        val gradeRule = if (grade.isNullOrBlank()) {
            "2. 结合「$subject」学科语境校正明显的同音错别字和识别错误（例如拼音的声母韵母、整体认读音节，英语的字母、单词、音标，数学的术语和算式），拿不准的地方保持原样，不要臆造内容；"
        } else {
            "2. 结合「$subject」学科语境和${grade}的知识范围校正明显的同音错别字和识别错误（例如拼音的声母韵母、整体认读音节，英语的字母、单词、音标，数学的术语和算式）；校正时优先考虑该年级正在学的知识点，低年级口语表达不完整是正常现象，保持孩子的原话风格；拿不准的地方保持原样，不要臆造内容；"
        }
        return """
下面是一段「$subject」科目的亲子辅导对话语音识别转写稿$gradeInfo。语音识别会有错别字、同音字错误和口语噪声，请将它润色成通顺的书面文稿。

【原始转写稿】
$rawTranscript

润色要求：
1. 删除语气词和口头禅，如"嗯""啊""那个""就是""然后""呃"等，删除无意义的重复和口吃式断句；
$gradeRule
3. 保留原文的说话人标签格式（每行以【xxx】开头），保持说话顺序和对话结构不变；
4. 不改变任何句子的原意，不添加原文没有的讲解或评价，不总结、不评论；
5. 合理补充标点，使断句通顺；
6. 只输出润色后的文稿正文，不要输出任何解释、标题或 markdown 格式。
""".trim()
    }
}
