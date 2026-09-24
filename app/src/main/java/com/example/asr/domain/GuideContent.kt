package com.example.asr.domain

/**
 * 使用指南内容（我的 → 使用指南），与小程序 utils/guide.ts 逐字对齐。
 * 图文混排：每篇文章由若干小节组成，小节可带示意图（ill 为 emoji 占位）。
 */
object GuideContent {

    data class GuideSection(
        val h: String,
        val p: List<String>,
        val ill: String? = null,
        val illCap: String? = null,
        val tip: String? = null,
    )

    data class GuideArticle(
        val id: String,
        val cat: String,
        val emoji: String,
        /** 封面/插图色调（对应小程序 tint-* 色系） */
        val tint: String,
        val title: String,
        val sub: String,
        val sections: List<GuideSection>,
    )

    data class GuideCat(val key: String, val label: String)

    val CATS = listOf(
        GuideCat("all", "精选"),
        GuideCat("start", "入门"),
        GuideCat("record", "录音"),
        GuideCat("analyze", "分析"),
        GuideCat("review", "复习"),
        GuideCat("more", "更多"),
    )

    val GUIDES: List<GuideArticle> = listOf(
        GuideArticle(
            id = "first",
            cat = "start",
            emoji = "👋",
            tint = "green",
            title = "快速上手",
            sub = "5 分钟完成第一次辅导记录",
            sections = listOf(
                GuideSection(
                    h = "第一步：添加孩子档案",
                    p = listOf(
                        "打开「我的 → 孩子管理」，点击右上角添加，填写孩子的名字（或昵称）、学段和年级。",
                        "家里不止一个孩子也没关系，每个孩子有独立的记录和薄弱点库。",
                    ),
                    ill = "🧒",
                    illCap = "我的 → 孩子管理",
                ),
                GuideSection(
                    h = "第二步：配置 API Key",
                    p = listOf(
                        "本应用通过硅基流动（SiliconFlow）提供的 AI 能力完成转写和分析。打开「我的 → 设置」，粘贴你在 siliconflow.cn 申请的 API Key。",
                        "语音识别、文稿润色、薄弱点分析各用一个模型，不熟悉的话保持默认即可。",
                    ),
                    ill = "⚙️",
                    illCap = "我的 → 设置 → API Key",
                    tip = "API Key 只保存在你自己的手机里，请妥善保管。",
                ),
                GuideSection(
                    h = "第三步：开始第一次录音",
                    p = listOf(
                        "回到首页，点击底部中央的绿色大圆钮，选择「开始录音」。选好孩子和科目后点大圆钮开始。",
                        "录音中可以随时暂停；结束后自动保存为一条记录。",
                    ),
                    ill = "🎙️",
                    illCap = "底部中央按钮 → 开始录音",
                ),
                GuideSection(
                    h = "第四步：转写与分析",
                    p = listOf(
                        "进入刚保存的记录，点「开始转写」。AI 会自动把录音转成文字，并区分家长和孩子的声音。",
                        "转写完成后点「分析薄弱点」，确认后薄弱点会自动加入复习计划。",
                    ),
                    ill = "📝",
                    illCap = "记录详情 → 开始转写 → 分析薄弱点",
                ),
            ),
        ),
        GuideArticle(
            id = "recording",
            cat = "record",
            emoji = "🎙️",
            tint = "blue",
            title = "录音与说话人分离",
            sub = "录下辅导过程，自动区分家长和孩子",
            sections = listOf(
                GuideSection(
                    h = "三种方式记录辅导",
                    p = listOf(
                        "点底部中央的绿色按钮，可以：开始录音、拍错题、相册选图、导入音频。",
                        "「导入音频」可以选择聊天里的录音文件（比如先把手机录音发到文件传输助手）。",
                    ),
                    ill = "➕",
                    illCap = "底部中央按钮的四个入口",
                ),
                GuideSection(
                    h = "说话人分离与角色标注",
                    p = listOf(
                        "转写后每段话会标注「说话人 1 / 说话人 2」。点每段左上角的「角色」，告诉 AI 哪一段是家长、哪一段是孩子。",
                        "标注越准确，薄弱点分析越靠谱——建议至少把孩子的段落标出来。",
                    ),
                    ill = "🏷️",
                    illCap = "点「角色」指派家长 / 孩子",
                    tip = "通常在辅导中提问、讲解的是家长，回答的是孩子。",
                ),
                GuideSection(
                    h = "文稿润色",
                    p = listOf(
                        "口语转写难免啰嗦、有错字。点「润色文稿」，AI 会在保留原意的前提下整理成通顺的文本，原文仍然保留，可随时对照。",
                    ),
                    ill = "✨",
                    illCap = "润色后可与原文对照",
                ),
            ),
        ),
        GuideArticle(
            id = "photo",
            cat = "analyze",
            emoji = "📷",
            tint = "amber",
            title = "拍错题分析",
            sub = "拍下错题，多模态模型帮你找薄弱点",
            sections = listOf(
                GuideSection(
                    h = "拍错题 / 相册选图",
                    p = listOf(
                        "点底部中央按钮 →「拍错题」，拍完一张可以继续拍下一页；也可以从相册一次选多张。",
                        "照片会归属到当前这条记录上，形成「讲解 + 错题」的完整档案。",
                    ),
                    ill = "📸",
                    illCap = "拍照连拍，一次记录整页错题",
                ),
                GuideSection(
                    h = "分析照片薄弱点",
                    p = listOf(
                        "在记录详情页点「分析照片薄弱点」，多模态模型会识别照片中的题目，分析孩子可能的知识漏洞。",
                        "分析结果确认后才会加入薄弱点库，不准确的可以取消。",
                    ),
                    ill = "🔍",
                    illCap = "记录详情 → 分析照片薄弱点",
                    tip = "照片尽量拍清楚题目和孩子的作答痕迹，识别效果更好。",
                ),
            ),
        ),
        GuideArticle(
            id = "review",
            cat = "review",
            emoji = "🎯",
            tint = "sage",
            title = "薄弱点与艾宾浩斯复习",
            sub = "按遗忘曲线安排每天的复习任务",
            sections = listOf(
                GuideSection(
                    h = "薄弱点库",
                    p = listOf(
                        "每次分析出的薄弱点都会进入「薄弱点」页，按孩子、科目归组，掌握度一目了然。",
                        "重复的薄弱点会自动合并，不会越积越多。",
                    ),
                    ill = "📚",
                    illCap = "薄弱点页：按掌握度排序",
                ),
                GuideSection(
                    h = "今日任务（艾宾浩斯曲线）",
                    p = listOf(
                        "系统按照艾宾浩斯遗忘曲线，在第 1、2、4、7、15、30 天自动安排复习。",
                        "「今日」页会列出今天到期的任务，完成一项就勾选一项。",
                    ),
                    ill = "📅",
                    illCap = "今日页：今天该复习什么一目了然",
                    tip = "坚持「少量多次」，比考前突击有效得多。",
                ),
                GuideSection(
                    h = "练习题",
                    p = listOf(
                        "点开今日任务，可以让 AI 针对这个薄弱点出几道练习题，当场检验孩子是否真正掌握。",
                    ),
                    ill = "✏️",
                    illCap = "任务展开 → 生成练习题",
                ),
            ),
        ),
        GuideArticle(
            id = "backup",
            cat = "more",
            emoji = "☁️",
            tint = "grey",
            title = "云备份与恢复",
            sub = "坚果云 WebDAV，换手机也不丢数据",
            sections = listOf(
                GuideSection(
                    h = "配置坚果云",
                    p = listOf(
                        "在坚果云网页版「账户信息 → 安全选项」中生成第三方应用密码。",
                        "打开「我的 → 云备份」，填入坚果云账号和应用密码，点「测试连接」确认可用。",
                    ),
                    ill = "🔑",
                    illCap = "我的 → 云备份 → 测试连接",
                    tip = "应用密码不是你的坚果云登录密码，需要在网页端单独生成。",
                ),
                GuideSection(
                    h = "备份与恢复",
                    p = listOf(
                        "点「立即备份」把全部数据上传到坚果云；换手机或重装后，在新设备上配置同一账号，点「恢复」即可找回。",
                        "开启「自动备份」后，每天会在后台默默备份一次。",
                    ),
                    ill = "🔄",
                    illCap = "备份 / 恢复 / 自动备份",
                ),
            ),
        ),
        GuideArticle(
            id = "faq",
            cat = "more",
            emoji = "💡",
            tint = "purple",
            title = "常见问题",
            sub = "网络失败、权限、模型选择",
            sections = listOf(
                GuideSection(
                    h = "提示「网络请求失败」？",
                    p = listOf(
                        "应用需要访问 siliconflow.cn（AI 服务）和 dav.jianguoyun.com（云备份），请检查网络连接。",
                        "在公司或校园网环境下，若这两个域名被网关拦截，可切换网络后重试。",
                    ),
                ),
                GuideSection(
                    h = "录音没有声音 / 无法录音？",
                    p = listOf(
                        "首次录音会弹出麦克风授权；如果不小心点了拒绝，可以在系统「设置 → 应用 → 权限」中重新开启。",
                    ),
                ),
                GuideSection(
                    h = "分析结果不理想？",
                    p = listOf(
                        "先在记录详情里把说话人角色标对，再试一次「分析薄弱点」。",
                        "还不理想的话，在「设置」里把分析模型换成更强的模型（如 Qwen3 系列）；照片分析同理可换多模态模型。",
                    ),
                ),
            ),
        ),
    )

    fun articleById(id: String): GuideArticle? = GUIDES.firstOrNull { it.id == id }
}
