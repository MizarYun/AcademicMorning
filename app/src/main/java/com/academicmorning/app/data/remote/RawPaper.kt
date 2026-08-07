package com.academicmorning.app.data.remote

/** 抓取层的统一论文原始数据结构。 */
data class RawPaper(
    val doi: String?,
    val title: String,
    val abstractText: String?,
    val journalName: String,
    val journalIssn: String,
    val authors: String,
    val publishedDate: String,   // "yyyy-MM-dd"
    val url: String,
    val isOpenAccess: Boolean,
    val isPreprint: Boolean
) {
    /** Room 主键：doi 小写；无 doi 时按标题哈希。 */
    fun stableId(): String =
        doi?.lowercase() ?: "src:" + (journalIssn + title).hashCode().toString(16)
}
