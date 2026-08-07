package com.academicmorning.app.data.model

/** 研究领域。defaultIfThreshold 为该学科推荐期刊的 IF 下限默认值。 */
enum class Discipline(val key: String, val label: String, val defaultIfThreshold: Double) {
    BIOLOGY("biology", "生物科学", 5.0),
    ENVIRONMENT("environment", "环境科学", 4.0),
    COMPUTER_SCIENCE("cs", "计算机科学", 3.0),
    MEDICINE("medicine", "医学", 5.0),
    CHEMISTRY("chemistry", "化学", 4.0),
    PHYSICS("physics", "物理学", 3.0),
    MATERIALS("materials", "材料科学", 4.0),
    EARTH("earth", "地球科学", 3.0),
    MATH("math", "数学", 2.0);

    companion object {
        fun fromKey(k: String): Discipline? = entries.firstOrNull { it.key == k }
    }
}
