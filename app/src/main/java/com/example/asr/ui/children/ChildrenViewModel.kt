package com.example.asr.ui.children

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.asr.data.local.entity.ChildEntity
import com.example.asr.data.repository.ChildRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChildrenViewModel(private val childRepository: ChildRepository) : ViewModel() {

    val children: StateFlow<List<ChildEntity>> = childRepository.children
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(name: String, grade: String) {
        if (name.isBlank()) return
        viewModelScope.launch { childRepository.add(name, grade) }
    }

    fun update(child: ChildEntity) {
        if (child.name.isBlank()) return
        viewModelScope.launch { childRepository.update(child) }
    }

    fun delete(child: ChildEntity) {
        viewModelScope.launch { childRepository.delete(child) }
    }
}
