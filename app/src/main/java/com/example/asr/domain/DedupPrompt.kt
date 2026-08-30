package com.example.asr.domain

import com.example.asr.data.local.entity.WeakPointEntity
import com.example.asr.data.remote.dto.WeakPointResult

/** 薄弱点查重 prompt：让 LLM 判断新提取的薄弱点是否与已有记录重复 */
object DedupPrompt {

    const val SYSTEM =
        "你是一位中小学教研老师，负责判断新发现的孩子薄弱知识点是否与已有记录重复，避免重复建档。"

    fun build(existing: List<WeakPointEntity>, candidates: List<WeakPointResult>): String {
        val existingText = existing.joinToString("\n") { "[${it.id}] ${it.knowledgePoint}：${it.description}" }
        val candidateText = candidates.mapIndexed { i, c -> "[$i] ${c.knowledgePoint}：${c.description}" }
            .joinToString("\n")
        return """
【已有薄弱点记录】
$existingText

【新提取的薄弱点】
$candidateText

请逐一判断每个新提取项是否与已有记录实质上是同一个知识点。判断标准：
- 同一知识点的不同说法算重复（如"b p 不分"和"声母 b 和 p 混淆"）；
- 已有知识点的子点或具体表现算重复（如已有"乘法口诀不熟练"，新的"7 的乘法口诀记错"算重复）；
- 只是同科目同章节但考查内容不同，不算重复。

只返回 JSON 数组，不要输出任何其他文字，不要用 markdown 代码围栏。
数组元素格式：[{"index":新提取项的序号,"duplicateOf":重复的已有记录id，无重复则为null}]
每个新提取项都要有对应的一项。
""".trim()
    }
}
