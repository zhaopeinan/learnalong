package com.example.asr.domain

import com.example.asr.data.local.entity.SpeakerRole
import com.example.asr.data.local.entity.TranscriptSegmentEntity

/** 把分段转写拼成带说话人标签的完整文稿 */
object TranscriptText {

    fun build(segments: List<TranscriptSegmentEntity>): String =
        segments.sortedWith(compareBy({ it.startSec }, { it.id }))
            .joinToString("\n") { seg -> "【${displaySpeaker(seg)}】${seg.text}" }

    fun displaySpeaker(seg: TranscriptSegmentEntity): String = when (seg.role) {
        SpeakerRole.PARENT -> "家长"
        SpeakerRole.CHILD -> "孩子"
        else -> seg.speakerLabel.ifBlank { "说话人" }
    }
}
