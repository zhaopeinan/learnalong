package com.example.asr.domain

/** 构造错题照片分析的 prompt（多模态 user 消息中的文本部分，图片另附） */
object PhotoAnalysisPrompt {

    const val SYSTEM = "你是一位耐心的中小学辅导老师，擅长从孩子的错题照片中诊断知识薄弱点，并归纳合并同类错误。"

    /**
     * @param grade 孩子年级（如"三年级"），未知时传 null
     * @param photoCount 本次附带的错题照片数量
     */
    fun build(subject: String, grade: String?, photoCount: Int): String {
        val gradeInfo = if (grade.isNullOrBlank()) "" else "，孩子是${grade}学生"
        val gradeRules = if (grade.isNullOrBlank()) {
            ""
        } else {
            "- 以${grade}的教学要求为基准评估：超出该年级范围的题不必记为薄弱点，属于该年级应掌握水平的要重点记录；\n"
        }
        return """
以下是 $photoCount 张「$subject」科目的题目照片$gradeInfo。这些照片是家长特意拍下的错题/不会做的题——无论照片中是否有作答痕迹，所有拍到的题目都视为孩子做错或没有掌握的题。

请逐张辨认每道题目的内容，然后**按知识点归类合并**：
- 同一知识点的多道错题合并为一条薄弱点，不要把每道题单独列一条；
- description 中概括这类错误的表现，并引用代表性题目（如"3 道分数加法题都把分母直接相加"）；
- mastery 按这类错题的数量和严重程度估计：同类错题越多、错误越基础，掌握度越低。

只返回 JSON 数组，不要输出任何其他文字，不要用 markdown 代码围栏。
数组元素格式：
[{"knowledgePoint":"知识点名称","description":"这类错误的整体表现（引用代表性题目）","mastery":0-100 的整数掌握度,"suggestion":"针对该年级水平的复习建议"}]
提取要求：
$gradeRules- 每个薄弱点对应一个具体的知识点，不要泛泛而谈；
- 照片模糊、无法辨认的题目不要强行分析，忽略它们；
- 只有当所有照片都无法辨认出任何题目内容时，才返回空数组 []。
""".trim()
    }
}
