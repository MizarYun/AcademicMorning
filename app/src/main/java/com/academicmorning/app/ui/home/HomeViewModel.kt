package com.academicmorning.app.ui.home

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.academicmorning.app.AcademicMorningApp
import com.academicmorning.app.data.local.entity.Journal
import com.academicmorning.app.data.local.entity.Paper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ReadFilter { ALL, UNREAD, READ }

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AcademicMorningApp
    private val paperRepo = app.container.paperRepository
    private val journalRepo = app.container.journalRepository

    /** 关注的期刊（供首页分类筛选 chips）。 */
    val followedJournals: StateFlow<List<Journal>> = journalRepo.followedJournals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 期刊筛选：null = 全部。 */
    private val _journalFilter = MutableStateFlow<String?>(null)
    val journalFilter: StateFlow<String?> = _journalFilter

    fun setJournalFilter(issn: String?) { _journalFilter.value = issn }

    /** 已读状态筛选。 */
    private val _readFilter = MutableStateFlow(ReadFilter.ALL)
    val readFilter: StateFlow<ReadFilter> = _readFilter

    fun setReadFilter(f: ReadFilter) { _readFilter.value = f }

    private val allPapers = paperRepo.allPapers()

    /** 全部论文（应用期刊筛选 + 已读筛选后）。 */
    val papers: StateFlow<List<Paper>> =
        combine(allPapers, _journalFilter, _readFilter) { list, issn, rf ->
            list.filter { p -> issn == null || p.journalIssn == issn }
                .filter { p ->
                    when (rf) {
                        ReadFilter.ALL -> true
                        ReadFilter.UNREAD -> !p.isRead
                        ReadFilter.READ -> p.isRead
                    }
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 今日新增篇数。 */
    val todayCount: StateFlow<Int> = combine(allPapers, MutableStateFlow(Unit)) { list, _ ->
        val today = paperRepo.todayString()
        list.count { it.fetchedDate == today }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _refreshMessage = MutableStateFlow<String?>(null)
    val refreshMessage: StateFlow<String?> = _refreshMessage

    /** 启动自动抓取完成后的顶部提示横幅。 */
    private val _startupBanner = MutableStateFlow<String?>(null)
    val startupBanner: StateFlow<String?> = _startupBanner

    fun dismissStartupBanner() { _startupBanner.value = null }

    /** 收藏分类列表。 */
    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories

    fun loadCategories() = viewModelScope.launch {
        _categories.value = paperRepo.favoriteCategories()
    }

    companion object {
        @Volatile private var startupFetched = false
    }

    init {
        // 每次启动 APP 时自动检索关注期刊昨日新发布（默认 1 天范围）
        if (!startupFetched) {
            startupFetched = true
            viewModelScope.launch {
                try {
                    paperRepo.refreshToday()
                    _startupBanner.value = "昨日发表文献已完成抓取，可在首页右上角选取抓取文献的范围"
                } catch (_: Exception) {
                    _startupBanner.value = "昨日文献抓取失败，可点击右上角手动选择范围重试"
                }
            }
        }
    }

    /** 指定日期范围抓取（跨度≤7天，自动去重）。 */
    fun reviewRange(issn: String?, fromDate: String, toDate: String) {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                val n = paperRepo.refreshRange(fromDate, toDate, issn)
                val scope = if (issn == null) "全部关注期刊" else "目标期刊"
                _refreshMessage.value = if (n > 0) "$fromDate 至 $toDate ${scope}新增 $n 篇论文（已自动去重）"
                    else "$fromDate 至 $toDate ${scope}暂无新增论文"
            } catch (e: Exception) {
                _refreshMessage.value = "抓取失败：${e.message}"
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** 检索指定期刊上一期（回溯最近 31 天）。 */
    fun fetchLastIssue(issn: String, journalName: String) {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                val n = paperRepo.fetchLastIssue(issn)
                _refreshMessage.value = if (n > 0) "已检索到《$journalName》上一期 $n 篇论文"
                    else "近 31 天未检索到《$journalName》的新发表论文"
            } catch (e: Exception) {
                _refreshMessage.value = "抓取失败：${e.message}"
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun clearMessage() { _refreshMessage.value = null }

    fun unfavorite(paper: Paper) = viewModelScope.launch {
        paperRepo.setFavorite(paper.id, false)
    }

    /** 收藏并归入所选分类。 */
    fun favoriteWithTags(paper: Paper, tags: List<String>) = viewModelScope.launch {
        paperRepo.setFavoriteWithTags(paper.id, true, tags.joinToString(","))
        _categories.value = paperRepo.favoriteCategories()
    }

    fun share(paper: Paper) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${paper.title}\n${paper.url}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<AcademicMorningApp>().startActivity(
            Intent.createChooser(intent, "分享论文").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
