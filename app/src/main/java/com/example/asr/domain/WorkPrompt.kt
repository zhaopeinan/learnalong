package com.example.asr.domain

import com.example.asr.data.local.entity.WorkScenario

/**
 * 工作端分析 prompt（与小程序 prompts.ts 的 WORK_SCENARIO_META /
 * WORK_ANALYSIS_SYSTEM / buildWorkAnalysisPrompt / buildWorkMergePrompt 逐字一致）。
 */
object WorkPrompt {

    data class ScenarioMeta(val label: String, val focus: String)

    val SCENARIO_META: Map<String, ScenarioMeta> = mapOf(
        WorkScenario.MEETING to ScenarioMeta(
            label = "会议",
            focus = """这是一场工作会议。请重点提取：
- 会议讨论的主要议题与每个议题的结论；
- 明确分配的行动项（谁、做什么、什么时候完成）；
- 悬而未决的问题与风险点。""",
        ),
        WorkScenario.TALK to ScenarioMeta(
            label = "工作谈话",
            focus = """这是一次工作谈话（如一对一沟通、绩效反馈、项目讨论）。请重点提取：
- 谈话的核心话题与双方的主要观点；
- 达成的共识与各自做出的承诺；
- 需要后续跟进的事项。""",
        ),
        WorkScenario.CALL to ScenarioMeta(
            label = "通话录音",
            focus = """这是一段电话/语音通话录音。请重点提取：
- 通话沟通的关键信息（事项、数据、约定）；
- 双方确认的安排与约定；
- 需要回电、回复或后续办理的事项。""",
        ),
    )

    const val SYSTEM = "你是一位专业的职场助理，擅长把会议、工作谈话、通话的录音转写稿整理成结构清晰的纪要，并准确提取待办事项。"

    fun scenarioLabel(scenario: String): String = SCENARIO_META[scenario]?.label ?: "会议"

    /**
     * 工作录音分析：返回 JSON 对象 {"title":"...","summary":"...","todos":[...]}
     * partial=true 表示这只是长文稿的一个片段（结果后续会再做合并）。
     */
    fun buildAnalysisPrompt(scenarioLabel: String, focus: String, transcript: String, partial: Boolean): String {
        val partialNote = if (partial) "\n注意：这段转写稿只是完整录音的一个片段，请只基于本片段内容提取，不要推测片段之外的内容。" else ""
        return """
$focus$partialNote

【转写稿】
$transcript

请输出纪要并提取待办事项，只返回 JSON 对象，不要输出任何其他文字，不要用 markdown 代码围栏。
格式：
{"title":"这段${scenarioLabel}的一句话标题（15 字以内）","summary":"纪要正文（用 markdown 分节列要点，200 字以内）","todos":[{"text":"待办事项描述","assignee":"负责人（不明确则空字符串）","deadline":"截止时间（不明确则空字符串）"}]}
要求：
- summary 用「## 主题」分节，每节 2-4 个要点，要点以「- 」开头；
- todos 只提取明确需要后续行动的事项，没有行动项时返回空数组；
- 转写稿中的说话人标签（说话人1/2 等）可在 summary 中按语境称为"甲方/乙方/某同事"，拿不准就保持"说话人1"。
""".trim()
    }

    /** 长录音分段分析后的合并：把多段纪要合成一份，待办去重 */
    fun buildMergePrompt(scenarioLabel: String, partials: List<WorkPartial>): String {
        val parts = partials.mapIndexed { i, p ->
            "【第 ${i + 1} 段纪要】\n${p.summary}\n待办：${if (p.todos.isNotEmpty()) p.todos.joinToString("；") else "无"}"
        }.joinToString("\n\n")
        return """
这是一段${scenarioLabel}录音分段分析出的 ${partials.size} 份纪要，请合并成一份完整纪要：
- 按主题重新组织，去掉重复内容，保持时间/逻辑顺序；
- 待办事项合并去重（同一件事的重复描述只保留一条）。

$parts

只返回 JSON 对象，不要输出任何其他文字，不要用 markdown 代码围栏。
格式：
{"title":"整段${scenarioLabel}的一句话标题（15 字以内）","summary":"合并后的纪要正文（markdown 分节）","todos":[{"text":"待办事项描述","assignee":"负责人","deadline":"截止时间"}]}
""".trim()
    }

    /** 分段分析的部分结果（合并 prompt 用） */
    data class WorkPartial(val summary: String, val todos: List<String>)
}
