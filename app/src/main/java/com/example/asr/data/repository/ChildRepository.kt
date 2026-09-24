package com.example.asr.data.repository

import com.example.asr.data.local.dao.ChildDao
import com.example.asr.data.local.entity.ChildEntity
import kotlinx.coroutines.flow.Flow

class ChildRepository(private val childDao: ChildDao) {

    val children: Flow<List<ChildEntity>> = childDao.observeAll()

    suspend fun add(name: String, grade: String?, voiceId: String? = null): Long =
        childDao.insert(
            ChildEntity(
                name = name.trim(),
                grade = grade?.trim()?.ifEmpty { null },
                voiceId = voiceId?.takeIf { it.isNotBlank() },
            )
        )

    suspend fun update(child: ChildEntity) = childDao.update(child)

    suspend fun delete(child: ChildEntity) = childDao.delete(child)
}
