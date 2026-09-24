package com.example.asr.data.repository

import com.example.asr.data.local.dao.ChildDao
import com.example.asr.data.local.dao.MasteryHistoryDao
import com.example.asr.data.local.dao.MasteryHistoryRow
import com.example.asr.data.local.dao.RecordingDao
import com.example.asr.data.local.dao.RecordingPhotoDao
import com.example.asr.data.local.dao.ReviewTaskDao
import com.example.asr.data.local.dao.SubjectCount
import com.example.asr.data.local.dao.TranscriptDao
import com.example.asr.data.local.dao.WeakPointDao
import com.example.asr.data.local.entity.MasteryHistoryEntity
import com.example.asr.data.local.entity.RecordingPhotoEntity
import com.example.asr.data.local.entity.RecordingStatus
import com.example.asr.data.local.entity.ReviewTaskEntity
import com.example.asr.data.local.entity.ReviewTaskWithWeakPoint
import com.example.asr.data.local.entity.WeakPointEntity
import com.example.asr.data.remote.AnalysisResultParser
import com.example.asr.data.remote.DebugLog
import com.example.asr.data.remote.NetworkClient
import com.example.asr.data.remote.TaskContentParser
import com.example.asr.data.remote.dto.ChatMessage
import com.example.asr.data.remote.dto.ChatRequest
import com.example.asr.data.remote.dto.DedupEntry
import com.example.asr.data.remote.dto.TaskContent
import com.example.asr.data.remote.dto.VisionChatMessage
import com.example.asr.data.remote.dto.VisionChatRequest
import com.example.asr.data.remote.dto.VisionContentPart
import com.example.asr.data.remote.dto.WeakPointResult
import com.example.asr.data.remote.toUserMessage
import com.example.asr.data.settings.AppSettings
import com.example.asr.data.settings.SettingsStore
import com.example.asr.domain.AnalysisPrompt
import com.example.asr.domain.DedupPrompt
import com.example.asr.domain.EbbinghausScheduler
import com.example.asr.domain.PhotoAnalysisPrompt
import com.example.asr.domain.PolishPrompt
import com.example.asr.domain.TaskContentPrompt
import com.example.asr.domain.TranscriptText
import com.example.asr.media.PhotoImporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val taskContentJson = Json { ignoreUnknownKeys = true }

class TutorRepository(
    private val weakPointDao: WeakPointDao,
    private val reviewTaskDao: ReviewTaskDao,
    private val transcriptDao: TranscriptDao,
    private val recordingDao: RecordingDao,
    private val childDao: ChildDao,
    private val masteryHistoryDao: MasteryHistoryDao,
    private val recordingPhotoDao: RecordingPhotoDao,
    private val settingsStore: SettingsStore,
    private val debugLog: DebugLog? = null,
) {

    fun observeWeakPoints(childId: Long?, subject: String?): Flow<List<WeakPointEntity>> =
        weakPointDao.observe(childId, subject)

    fun observeWeakPointsByRecording(recordingId: Long): Flow<List<WeakPointEntity>> =
        weakPointDao.observeByRecording(recordingId)

    fun observeSubjects(): Flow<List<String>> = weakPointDao.observeSubjects()

    fun observeDueTasks(nowMillis: Long): Flow<List<ReviewTaskWithWeakPoint>> =
        reviewTaskDao.observeDue(EbbinghausScheduler.dayEndOf(nowMillis))

    suspend fun countDueTasks(nowMillis: Long): Int =
        reviewTaskDao.countDue(EbbinghausScheduler.dayEndOf(nowMillis))

    suspend fun deleteWeakPoint(id: Long) = weakPointDao.deleteById(id)

    suspend fun getWeakPoint(id: Long): WeakPointEntity? = weakPointDao.getById(id)

    /** 记录附带的错题照片 */
    fun observePhotos(recordingId: Long): Flow<List<RecordingPhotoEntity>> =
        recordingPhotoDao.observeByRecording(recordingId)

    suspend fun addPhotos(recordingId: Long, files: List<java.io.File>) {
        val now = System.currentTimeMillis()
        recordingPhotoDao.insertAll(
            files.map { RecordingPhotoEntity(recordingId = recordingId, filePath = it.absolutePath, createdAt = now) }
        )
    }

    /** 删除照片记录并删除本地文件 */
    suspend fun deletePhoto(photo: RecordingPhotoEntity) {
        recordingPhotoDao.deleteById(photo.id)
        java.io.File(photo.filePath).delete()
    }

    /** 删除某记录的全部照片文件（表记录由外键级联删除） */
    suspend fun deletePhotosForRecording(recordingId: Long) {
        recordingPhotoDao.getByRecording(recordingId).forEach { java.io.File(it.filePath).delete() }
    }

    /**
     * 薄弱点出题：优先读缓存（退出重进不丢题，对应小程序 getWeakPointContent）；
     * 没有缓存则生成并缓存到薄弱点上。
     */
    suspend fun getWeakPointContent(wp: WeakPointEntity): TaskContent {
        wp.exerciseCache?.let { cached ->
            TaskContentParser.parse(cached)?.let { return it }
        }
        return regenerateWeakPointContent(wp)
    }

    /** 换一批题：强制重新生成并覆盖缓存（避开当前已有的题目，防止只是换数字） */
    suspend fun regenerateWeakPointContent(wp: WeakPointEntity): TaskContent {
        val cached = wp.exerciseCache?.let { TaskContentParser.parse(it) }
        val avoid = cached?.exercises?.map { it.question } ?: emptyList()
        val content = generate(wp.subject, wp.childId, wp.knowledgePoint, wp.description, avoid = avoid)
        // 用最新行覆盖缓存，避免丢生成期间其它字段（掌握度等）的变更
        weakPointDao.getById(wp.id)?.let { fresh ->
            weakPointDao.update(fresh.copy(exerciseCache = taskContentJson.encodeToString(content)))
        }
        return content
    }

    /** 换一题：只重新生成第 index 题（避开其余题目），其余题目保留 */
    suspend fun replaceExerciseInCache(wp: WeakPointEntity, index: Int): TaskContent {
        val cached = wp.exerciseCache?.let { TaskContentParser.parse(it) }
        require(cached != null && index in cached.exercises.indices) { "题目不存在，请换一批题" }
        val settings = settingsStore.settings.first()
        require(settings.apiKey.isNotBlank()) { "请先在设置页填写 SiliconFlow API Key" }
        val grade = childDao.getById(wp.childId)?.grade
        val userPrompt = TaskContentPrompt.buildReplace(
            wp.subject, grade, wp.knowledgePoint, wp.description,
            cached.exercises.map { it.question }, index + 1, cached.exercises.size,
        )
        val raw = try {
            val api = NetworkClient.api(settings.baseUrl)
            api.chatCompletions(
                authorization = "Bearer ${settings.apiKey}",
                request = ChatRequest(
                    model = settings.llmModel,
                    messages = listOf(
                        ChatMessage(role = "system", content = TaskContentPrompt.SYSTEM),
                        ChatMessage(role = "user", content = userPrompt),
                    ),
                ),
            ).text
        } catch (e: Exception) {
            debugLog?.record(
                action = "换一题",
                model = settings.llmModel,
                prompt = "[system]\n${TaskContentPrompt.SYSTEM}\n\n[user]\n$userPrompt",
                error = e.toUserMessage(),
            )
            throw Exception(e.toUserMessage())
        }
        val item = TaskContentParser.parseExerciseItem(raw)
            ?: throw IllegalStateException("换题失败，请重试")
        val next = cached.copy(
            exercises = cached.exercises.mapIndexed { i, e -> if (i == index) item else e },
        )
        weakPointDao.getById(wp.id)?.let { fresh ->
            weakPointDao.update(fresh.copy(exerciseCache = taskContentJson.encodeToString(next)))
        }
        return next
    }

    /** 获取任务的练习内容：有缓存直接解析返回；没有则调 LLM 按薄弱点+年级生成并缓存。 */
    suspend fun getTaskContent(task: ReviewTaskWithWeakPoint): TaskContent {
        task.content?.let { cached ->
            TaskContentParser.parse(cached)?.let { return it }
        }
        return generate(task.subject, task.childId, task.knowledgePoint, task.description, cacheTaskId = task.taskId)
    }

    /** 调 LLM 生成练习内容（带科目+年级，avoid 为要避开的已出题目），成功后将原文缓存到任务 */
    private suspend fun generate(
        subject: String,
        childId: Long,
        knowledgePoint: String,
        description: String,
        cacheTaskId: Long? = null,
        avoid: List<String> = emptyList(),
    ): TaskContent {
        val settings = settingsStore.settings.first()
        require(settings.apiKey.isNotBlank()) { "请先在设置页填写 SiliconFlow API Key" }
        val grade = childDao.getById(childId)?.grade
        val userPrompt = TaskContentPrompt.build(subject, grade, knowledgePoint, description, avoid)

        val raw = try {
            val api = NetworkClient.api(settings.baseUrl)
            api.chatCompletions(
                authorization = "Bearer ${settings.apiKey}",
                request = ChatRequest(
                    model = settings.llmModel,
                    messages = listOf(
                        ChatMessage(role = "system", content = TaskContentPrompt.SYSTEM),
                        ChatMessage(role = "user", content = userPrompt),
                    ),
                ),
            ).text
        } catch (e: Exception) {
            debugLog?.record(
                action = "生成练习题",
                model = settings.llmModel,
                prompt = "[system]\n${TaskContentPrompt.SYSTEM}\n\n[user]\n$userPrompt",
                error = e.toUserMessage(),
            )
            throw Exception(e.toUserMessage())
        }

        val content = TaskContentParser.parse(raw)
            ?: throw IllegalStateException("练习生成失败，请重试")
        if (cacheTaskId != null) reviewTaskDao.updateContent(cacheTaskId, raw)
        return content
    }

    /** 复习反馈：完成当前任务，按艾宾浩斯曲线推进/回退并生成下一次任务，同时记录掌握度快照 */
    suspend fun applyReview(taskId: Long, weakPointId: Long, mastered: Boolean) {
        val wp = weakPointDao.getById(weakPointId) ?: return
        val now = System.currentTimeMillis()
        val outcome = EbbinghausScheduler.applyReview(wp.reviewStage, wp.mastery, mastered, now)
        weakPointDao.update(
            wp.copy(
                reviewStage = outcome.newStage,
                mastery = outcome.newMastery,
                nextReviewAt = outcome.nextReviewAt,
            )
        )
        recordMastery(wp.id, outcome.newMastery, now)
        reviewTaskDao.complete(taskId, now)
        if (!outcome.finished) {
            reviewTaskDao.insert(ReviewTaskEntity(weakPointId = wp.id, dueDate = outcome.nextReviewAt))
        }
    }

    /** 某薄弱点的掌握度历史（成长曲线用） */
    fun observeMasteryHistory(weakPointId: Long): Flow<List<MasteryHistoryEntity>> =
        masteryHistoryDao.observeByWeakPoint(weakPointId)

    /** 周报用：since 之后完成的复习按科目分布 */
    suspend fun countCompletedBySubject(since: Long): List<SubjectCount> =
        reviewTaskDao.countCompletedBySubject(since)

    /** 周报用：since 之后记录的掌握度快照 */
    suspend fun getMasteryHistorySince(since: Long): List<MasteryHistoryRow> =
        masteryHistoryDao.getSince(since)

    private suspend fun recordMastery(weakPointId: Long, mastery: Int, at: Long) {
        masteryHistoryDao.insert(
            MasteryHistoryEntity(weakPointId = weakPointId, mastery = mastery, recordedAt = at)
        )
    }

    /** 调用 LLM 润色转写文稿（去语气词、按学科校正识别错误），保存并返回润色后的文本 */
    suspend fun polishRecording(recordingId: Long): String {
        val recording = recordingDao.getById(recordingId)
            ?: throw IllegalArgumentException("录音不存在")
        val segments = transcriptDao.getByRecording(recordingId)
        require(segments.isNotEmpty()) { "请先完成转写" }

        val settings = settingsStore.settings.first()
        require(settings.apiKey.isNotBlank()) { "请先在设置页填写 SiliconFlow API Key" }

        val grade = childDao.getById(recording.childId)?.grade
        val userPrompt = PolishPrompt.build(
            recording.subject,
            grade,
            TranscriptText.build(segments),
        )

        val polished = try {
            val api = NetworkClient.api(settings.baseUrl)
            val response = api.chatCompletions(
                authorization = "Bearer ${settings.apiKey}",
                request = ChatRequest(
                    model = settings.llmModel,
                    messages = listOf(
                        ChatMessage(role = "system", content = PolishPrompt.SYSTEM),
                        ChatMessage(role = "user", content = userPrompt),
                    ),
                ),
            )
            response.text.trim()
        } catch (e: Exception) {
            debugLog?.record(
                action = "润色文稿",
                model = settings.llmModel,
                prompt = "[system]\n${PolishPrompt.SYSTEM}\n\n[user]\n$userPrompt",
                error = e.toUserMessage(),
            )
            throw Exception(e.toUserMessage())
        }
        require(polished.isNotBlank()) { "模型返回为空，请重试" }
        recordingDao.updatePolishedText(recordingId, polished)
        recordingDao.updatePolishedAt(recordingId, System.currentTimeMillis())
        return polished
    }

    /** 分析出的候选薄弱点；duplicateOfId 非空表示与已有记录重复，确认后将合并而非新增 */
    data class AnalysisCandidate(
        val knowledgePoint: String,
        val description: String,
        val mastery: Int,
        val suggestion: String,
        val duplicateOfId: Long? = null,
        val duplicateOfPoint: String? = null,
    )

    /** applyAnalysis 的结果统计 */
    data class ApplyResult(val added: Int, val merged: Int)

    /**
     * 分析录音（不写库）：LLM 提取薄弱点后，若该孩子该科目已有记录，
     * 再让 LLM 查重，重复的标记合并目标。返回候选列表供用户确认。
     */
    suspend fun analyzeRecording(recordingId: Long): List<AnalysisCandidate> {
        val recording = recordingDao.getById(recordingId)
            ?: throw IllegalArgumentException("录音不存在")
        val segments = transcriptDao.getByRecording(recordingId)
        require(segments.isNotEmpty()) { "请先完成转写" }

        val settings = settingsStore.settings.first()
        require(settings.apiKey.isNotBlank()) { "请先在设置页填写 SiliconFlow API Key" }

        val rawTranscript = TranscriptText.build(segments)
        val grade = childDao.getById(recording.childId)?.grade
        val userPrompt = AnalysisPrompt.build(
            recording.subject,
            grade,
            rawTranscript,
            recording.polishedText,
        )

        val api = NetworkClient.api(settings.baseUrl)
        val auth = "Bearer ${settings.apiKey}"

        val results = try {
            val response = api.chatCompletions(
                authorization = auth,
                request = ChatRequest(
                    model = settings.llmModel,
                    messages = listOf(
                        ChatMessage(role = "system", content = AnalysisPrompt.SYSTEM),
                        ChatMessage(role = "user", content = userPrompt),
                    ),
                ),
            )
            AnalysisResultParser.parse(response.text)
        } catch (e: Exception) {
            debugLog?.record(
                action = "薄弱点分析",
                model = settings.llmModel,
                prompt = "[system]\n${AnalysisPrompt.SYSTEM}\n\n[user]\n$userPrompt",
                error = e.toUserMessage(),
            )
            throw Exception(e.toUserMessage())
        }
        if (results.isEmpty()) return emptyList()

        return dedupAndMap(settings, recording.childId, recording.subject, results)
    }

    /** 照片分析结果：候选薄弱点 + 模型原始返回文本（未提取到时展示给用户排查） */
    data class PhotoAnalysisOutcome(
        val candidates: List<AnalysisCandidate>,
        val rawText: String,
    )

    /**
     * 分析错题照片（不写库）：多模态模型逐张辨认题目与作答痕迹，提取薄弱点后
     * 走与录音分析相同的查重逻辑，返回候选列表供用户确认。
     */
    suspend fun analyzePhotos(recordingId: Long): PhotoAnalysisOutcome {
        val recording = recordingDao.getById(recordingId)
            ?: throw IllegalArgumentException("录音不存在")
        val photos = recordingPhotoDao.getByRecording(recordingId)
        require(photos.isNotEmpty()) { "请先添加错题照片" }

        val settings = settingsStore.settings.first()
        require(settings.apiKey.isNotBlank()) { "请先在设置页填写 SiliconFlow API Key" }

        val grade = childDao.getById(recording.childId)?.grade
        val parts = mutableListOf(
            VisionContentPart.text(PhotoAnalysisPrompt.build(recording.subject, grade, photos.size))
        )
        photos.forEach { p ->
            parts.add(VisionContentPart.image(PhotoImporter.toDataUrl(java.io.File(p.filePath))))
        }

        val rawText = try {
            val api = NetworkClient.api(settings.baseUrl)
            api.visionChatCompletions(
                authorization = "Bearer ${settings.apiKey}",
                request = VisionChatRequest(
                    model = settings.vlmModel,
                    messages = listOf(
                        VisionChatMessage("system", listOf(VisionContentPart.text(PhotoAnalysisPrompt.SYSTEM))),
                        VisionChatMessage("user", parts),
                    ),
                ),
            ).text
        } catch (e: Exception) {
            debugLog?.record(
                action = "照片薄弱点分析",
                model = settings.vlmModel,
                prompt = "[system]\n${PhotoAnalysisPrompt.SYSTEM}\n\n[user]\n" +
                    PhotoAnalysisPrompt.build(recording.subject, grade, photos.size) +
                    "\n" + photos.joinToString("\n") { "[image] ${java.io.File(it.filePath).name}" },
                error = e.toUserMessage(),
            )
            throw Exception(e.toUserMessage())
        }

        val results = AnalysisResultParser.parse(rawText)
        if (results.isEmpty()) return PhotoAnalysisOutcome(emptyList(), rawText)

        return PhotoAnalysisOutcome(
            dedupAndMap(settings, recording.childId, recording.subject, results),
            rawText,
        )
    }

    /** 查重（同孩子同科目已有薄弱点）并把模型结果映射为候选列表 */
    private suspend fun dedupAndMap(
        settings: AppSettings,
        childId: Long,
        subject: String,
        results: List<WeakPointResult>,
    ): List<AnalysisCandidate> {
        val existing = weakPointDao.getByChildAndSubject(childId, subject)
        val dupMap: Map<Int, Long?> = if (existing.isEmpty()) {
            emptyMap()
        } else {
            try {
                val api = NetworkClient.api(settings.baseUrl)
                val resp = api.chatCompletions(
                    authorization = "Bearer ${settings.apiKey}",
                    request = ChatRequest(
                        model = settings.llmModel,
                        messages = listOf(
                            ChatMessage(role = "system", content = DedupPrompt.SYSTEM),
                            ChatMessage(role = "user", content = DedupPrompt.build(existing, results)),
                        ),
                    ),
                )
                parseDedup(resp.text)
            } catch (e: Exception) {
                emptyMap() // 查重失败不阻断，全部按新增处理
            }
        }

        val existingById = existing.associateBy { it.id }
        return results.mapIndexed { i, r ->
            val dupId = dupMap[i]?.takeIf { existingById.containsKey(it) }
            AnalysisCandidate(
                knowledgePoint = r.knowledgePoint,
                description = r.description,
                mastery = r.mastery.coerceIn(0, 100),
                suggestion = r.suggestion,
                duplicateOfId = dupId,
                duplicateOfPoint = dupId?.let { existingById[it]?.knowledgePoint },
            )
        }
    }

    /**
     * 确认入库：新增项建档并排定首次复习；重复项合并进已有记录——
     * 掌握度取低值、复习阶段重置（再次出现说明仍需巩固）、重新排期。
     */
    suspend fun applyAnalysis(recordingId: Long, candidates: List<AnalysisCandidate>): ApplyResult {
        val recording = recordingDao.getById(recordingId)
            ?: throw IllegalArgumentException("录音不存在")
        val now = System.currentTimeMillis()
        val initial = EbbinghausScheduler.initialSchedule(now)
        var added = 0
        var merged = 0

        for (c in candidates) {
            val description = buildString {
                append(c.description)
                if (c.suggestion.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append("建议：").append(c.suggestion)
                }
            }
            val dupId = c.duplicateOfId
            val existing = dupId?.let { weakPointDao.getById(it) }
            if (existing != null) {
                // 合并：追加最新表现，掌握度取低，复习计划重置
                val mergedMastery = minOf(existing.mastery, c.mastery)
                weakPointDao.update(
                    existing.copy(
                        description = existing.description + "\n最新表现：" + description,
                        mastery = mergedMastery,
                        reviewStage = initial.newStage,
                        nextReviewAt = initial.nextReviewAt,
                    )
                )
                recordMastery(existing.id, mergedMastery, now)
                reviewTaskDao.deleteByWeakPoint(existing.id)
                reviewTaskDao.insert(ReviewTaskEntity(weakPointId = existing.id, dueDate = initial.nextReviewAt))
                merged++
            } else {
                val wpId = weakPointDao.insert(
                    WeakPointEntity(
                        childId = recording.childId,
                        subject = recording.subject,
                        knowledgePoint = c.knowledgePoint,
                        description = description,
                        mastery = c.mastery,
                        reviewStage = initial.newStage,
                        nextReviewAt = initial.nextReviewAt,
                        createdAt = now,
                        sourceRecordingId = recordingId,
                    )
                )
                recordMastery(wpId, c.mastery, now)
                reviewTaskDao.insert(ReviewTaskEntity(weakPointId = wpId, dueDate = initial.nextReviewAt))
                added++
            }
        }
        recordingDao.updateStatus(recordingId, RecordingStatus.ANALYZED)
        return ApplyResult(added, merged)
    }

    /** 解析查重输出：[{"index":0,"duplicateOf":123|null}]，解析失败返回空表（全部按新增） */
    private fun parseDedup(raw: String): Map<Int, Long?> {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyMap()
        return try {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString<List<DedupEntry>>(raw.substring(start, end + 1))
                .filter { it.index >= 0 }
                .associate { it.index to it.duplicateOf }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
