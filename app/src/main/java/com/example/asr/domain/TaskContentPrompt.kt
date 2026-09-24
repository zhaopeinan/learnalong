package com.example.asr.domain

/** 复习任务出题 prompt：按薄弱点 + 年级生成可口头问答的练习（与小程序 prompts.ts 一致） */
object TaskContentPrompt {

    const val SYSTEM = "你是一位中小学教师，擅长针对孩子的薄弱知识点出复习练习题，出题难度严格匹配孩子的年级水平。这个 App 是家长在用的：答案和提示写给家长看，帮助家长口头引导孩子。"

    fun build(
        subject: String,
        grade: String?,
        knowledgePoint: String,
        description: String,
        avoid: List<String> = emptyList(),
    ): String {
        val gradeInfo = if (grade.isNullOrBlank()) "" else "孩子是${grade}学生，题目难度和表述必须符合该年级的教学要求。"
        val avoidSection = if (avoid.isEmpty()) "" else """
【不要重复的题目】（以下是已出过的题，请换全新的考查角度，不要重复，也不要只是换个数字）
${avoid.mapIndexed { i, q -> "${i + 1}. $q" }.joinToString("\n")}
"""
        return """
孩子在「$subject」科目有一个薄弱知识点需要复习巩固。

【薄弱知识点】$knowledgePoint
【具体表现】$description
$gradeInfo$avoidSection
请出 5 道复习练习题，要求：
1. 题目围绕该薄弱点，从易到难排列；
2. 题目要适合家长口头提问、孩子口头回答的场景（不要出需要纸笔演算或看图的大题）；
3. answer 写给家长看：准确的参考答案，可附带关键的判分要点或验算过程，让家长能立刻判断孩子答得对不对；
4. hint 也写给家长：一句话告诉家长怎么引导孩子自己想出来（而不是直接告诉孩子答案），没有好引导方式就留空字符串；
5. 最后给家长一句辅导建议（tips）。

只返回 JSON 对象，不要输出任何其他文字，不要用 markdown 代码围栏。
格式：{"exercises":[{"question":"题干","answer":"参考答案（给家长）","hint":"引导提示（给家长，可为空字符串）"}],"tips":"辅导建议"}
""".trim()
    }

    /** 单题替换（练习页「换一题」）：只出一道新题，难度对齐它在整组题中的位置，避开已有题目 */
    fun buildReplace(
        subject: String,
        grade: String?,
        knowledgePoint: String,
        description: String,
        avoid: List<String>,
        position: Int,
        total: Int,
    ): String {
        val gradeInfo = if (grade.isNullOrBlank()) "" else "孩子是${grade}学生，题目难度和表述必须符合该年级的教学要求。"
        val avoidSection = if (avoid.isEmpty()) "" else """
【已有题目】（请避开，不要重复，也不要只是换个数字）
${avoid.mapIndexed { i, q -> "${i + 1}. $q" }.joinToString("\n")}
"""
        return """
孩子在「$subject」科目有一个薄弱知识点需要复习巩固。

【薄弱知识点】$knowledgePoint
【具体表现】$description
$gradeInfo$avoidSection
请针对该薄弱点重新出 1 道练习题，要求：
1. 一组从易到难的练习共 $total 题，这道题是第 $position 题，难度要与这个位置相称；
2. 题目要适合家长口头提问、孩子口头回答的场景（不要出需要纸笔演算或看图的大题）；
3. answer 写给家长看：准确的参考答案，可附带判分要点；hint 写给家长：一句话引导思路（可为空字符串）。

只返回 JSON 对象，不要输出任何其他文字，不要用 markdown 代码围栏。
格式：{"question":"题干","answer":"参考答案（给家长）","hint":"引导提示（给家长，可为空字符串）"}
""".trim()
    }
}
