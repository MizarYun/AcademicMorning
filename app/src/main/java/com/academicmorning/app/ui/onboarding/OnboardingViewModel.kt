package com.academicmorning.app.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.academicmorning.app.AcademicMorningApp
import com.academicmorning.app.data.local.entity.Journal
import com.academicmorning.app.data.repository.AiConfigRepository
import com.academicmorning.app.work.WorkerScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AcademicMorningApp
    private val journalRepo = app.container.journalRepository
    private val aiRepo = app.container.aiConfigRepository
    private val settings = app.container.settings

    // ---- Step 1：学科选择 ----
    private val _selectedDisciplines = MutableStateFlow<Set<String>>(emptySet())
    val selectedDisciplines: StateFlow<Set<String>> = _selectedDisciplines

    fun toggleDiscipline(key: String) {
        val cur = _selectedDisciplines.value
        _selectedDisciplines.value = when {
            cur.contains(key) -> cur - key
            cur.size >= 3 -> cur          // 最多 3 个
            else -> cur + key
        }
    }

    // ---- Step 2：推荐期刊 + 跨学科搜索 ----
    val recommendations: StateFlow<List<Journal>> = _selectedDisciplines
        .flatMapLatest { keys ->
            if (keys.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
            else journalRepo.recommendationsFor(keys.toList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val searchResults: StateFlow<List<Journal>> = _searchQuery
        .flatMapLatest { q ->
            if (q.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
            else journalRepo.search(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    val followedCount: Flow<Int> = journalRepo.followedCount()

    /** 返回 false 表示已达 20 上限。 */
    suspend fun toggleFollow(journal: Journal): Boolean =
        journalRepo.setFollowed(journal.issn, !journal.isFollowed)

    // ---- Step 3：API 配置 ----
    val providers: StateFlow<List<AiConfigRepository.ProviderStatus>> = aiRepo.providers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val testResults = MutableStateFlow<Map<String, String>>(emptyMap())

    fun saveKey(provider: String, key: String) = viewModelScope.launch {
        if (provider == "tencent_tmt") return@launch
        aiRepo.saveKey(provider, key)
    }

    fun saveTencentKeys(id: String, key: String) = viewModelScope.launch {
        aiRepo.saveTencentKeys(id, key)
    }

    fun testConnection(provider: String) = viewModelScope.launch {
        testResults.value = testResults.value + (provider to "测试中…")
        val r = aiRepo.testConnection(provider)
        testResults.value = testResults.value +
            (provider to (r.getOrNull() ?: r.exceptionOrNull()?.message ?: "未知错误"))
    }

    // ---- Step 4：完成 ----
    fun finish(pushEnabled: Boolean, minutes: Int, onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setPushEnabled(pushEnabled)
            settings.setPushTimeMinutes(minutes)
            settings.setFollowedDisciplines(_selectedDisciplines.value.toList())
            settings.setOnboardingDone(true)
            if (pushEnabled) {
                WorkerScheduler.scheduleDaily(getApplication(), minutes)
            }
            onDone()
        }
    }
}
