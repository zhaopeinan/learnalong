package com.example.asr.domain

/** 复习任务出题 prompt：按薄弱点 + 年级生成可口头问答的练习 */
object TaskContentPrompt {

    const val SYSTEM = "你是一位中小学教师，擅长针对孩子的薄弱知识点出复习练习题，出题难度严格匹配孩子的年级水平。"

    fun build(subject: String, grade: String?, knowledgePoint: String, description: String): String {
        val gradeInfo = if (grade.isNullOrBlank()) "" else "孩子是${grade}学生，题目难度和表述必须符合该年级的教学要求。"
        return """
孩子在「$subject」科目有一个薄弱知识点需要复习巩固。

【薄弱知识点】$knowledgePoint
【具体表现】$description
$gradeInfo
请出 5 道复习练习题，要求：
1. 题目围绕该薄弱点，从易到难排列；
2. 题目要适合家长口头提问、孩子口头回答的场景（不要出需要纸笔演算或看图的大题）；
3. 每题给出准确答案，必要时给一句提示；
4. 最后给家长一句辅导建议（tips）。

只返回 JSON 对象，不要输出任何其他文字，不要用 markdown 代码围栏。
格式：{"exercises":[{"question":"题干","answer":"答案","hint":"提示，可为空字符串"}],"tips":"辅导建议"}
""".trim()
    }
}
