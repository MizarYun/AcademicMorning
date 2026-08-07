package com.academicmorning.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ===== 配色规范（严格按 UI 参考图） =====
val AmPrimary = Color(0xFFFF8A3D)      // 主色 橙
val AmPrimaryContainer = Color(0xFFFFE3CF)
val AmSecondary = Color(0xFF1F2937)    // 深色文字/辅助
val AmAccent = Color(0xFF2FA772)       // 强调绿
val AmAccentContainer = Color(0xFFE3F4EB)
val AmBackground = Color(0xFFF8FAFC)   // 背景
val AmSurface = Color(0xFFFFFFFF)
val AmDivider = Color(0xFFE5E7EB)      // 分割线
val AmTextPrimary = Color(0xFF1F2937)
val AmTextSecondary = Color(0xFF6B7280)
val AmTextTertiary = Color(0xFF9CA3AF)
val AmPreprint = Color(0xFF8B5CF6)     // 预印本紫
val AmFavorite = Color(0xFFEAB308)     // 收藏黄
val AmQ1 = Color(0xFFFF8A3D)
val AmQ2 = Color(0xFFFBBF24)

private val LightColors = lightColorScheme(
    primary = AmPrimary,
    onPrimary = Color.White,
    primaryContainer = AmPrimaryContainer,
    onPrimaryContainer = AmSecondary,
    secondary = AmSecondary,
    onSecondary = Color.White,
    tertiary = AmAccent,
    onTertiary = Color.White,
    tertiaryContainer = AmAccentContainer,
    onTertiaryContainer = AmAccent,
    background = AmBackground,
    onBackground = AmTextPrimary,
    surface = AmSurface,
    onSurface = AmTextPrimary,
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = AmTextSecondary,
    outline = AmDivider,
    outlineVariant = AmDivider
)

private val AmTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, color = AmTextPrimary),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AmTextPrimary),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AmTextPrimary),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = AmTextPrimary),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, color = AmTextPrimary),
    bodyLarge = TextStyle(fontSize = 15.sp, color = AmTextPrimary),
    bodyMedium = TextStyle(fontSize = 13.sp, color = AmTextSecondary),
    bodySmall = TextStyle(fontSize = 12.sp, color = AmTextTertiary),
    labelSmall = TextStyle(fontSize = 10.sp, color = AmTextSecondary)
)

@Composable
fun AcademicMorningTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AmTypography,
        content = content
    )
}
