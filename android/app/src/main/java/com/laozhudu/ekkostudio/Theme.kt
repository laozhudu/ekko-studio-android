package com.laozhudu.ekkostudio

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 官方**安卓 App 1.0.3** 的视觉规范移植。
 *
 * 令牌来源：`docs/reference/apk-1.0.3/theme-tokens-{light,dark}.json`（官方 APK 导出，
 * 50 项，`--ink-*` 命名）。`OfficialInkTokenTest` 会把这些值与这里逐项比对。
 *
 * 注意区分两套官方设计系统：
 *   - 安卓 App 1.0.3 -> `--ink-*`（本文件采用，手机端界面）
 *   - 网页 0.7.38    -> `--bg-*` / `--text-*` / `--msg-*`（浏览器界面）
 * 网页令牌只在「网页独有组件」上参考，例如任务卡步骤的 success 绿。
 * 详见 docs/web-style-alignment-research.md 的更正章节。
 *
 * 深色（默认）：--UI-BG #000 / --UI-BG-0 #191919 / --UI-BG-1 #1f1f1f /
 *               --UI-BG-2 #232323 / --UI-BG-3 #2f2f2f / --UI-BORDER-COLOR-1 #373737
 *               文字 #fff 分级 80% / 60% / 50% / 30%
 *               品牌色 #4ca66a（绿，带 .12 透明度光晕）、#007aff（蓝）、#c28a30（琥珀）、#e64340（红）
 * 浅色：--ink-bg-primary #fafafa / --ink-bg-card #fff / --ink-text-primary #1a1a1a /
 *       --ink-text-secondary #666 / --ink-border #e0e0e0 / --ink-accent #333
 * 圆角：6 / 8 / 10 / 12 / 16 dp（HStudio 主体是 8–12px，胶囊用 999px）
 * 排版：紧凑档（HStudio 用 9–18px，移动端等比放大到可读下限 11–20sp）
 */
private val StudioDarkColors = darkColorScheme(
    // 品牌绿 #4ca66a
    primary = Color(0xFF4CA66A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1F2A23),   // rgba(76,166,106,.12) over #191919
    onPrimaryContainer = Color(0xFFB7E3C4),
    // 系统蓝 #007aff
    secondary = Color(0xFF007AFF),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF162535),
    onSecondaryContainer = Color(0xFFA8CDFF),
    // 琥珀 #c28a30
    tertiary = Color(0xFFC28A30),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF2D261C),
    onTertiaryContainer = Color(0xFFF0D9A8),
    // 底色：纯黑 + 四层灰阶
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1F1F1F),
    onSurfaceVariant = Color(0xFF999999),    // hsla(0,0%,100%,.6)
    surfaceContainerLowest = Color(0xFF0A0A0A),
    surfaceContainerLow = Color(0xFF191919),
    surfaceContainer = Color(0xFF1F1F1F),
    surfaceContainerHigh = Color(0xFF1F1F1F),
    surfaceContainerHighest = Color(0xFF2C2C2C),
    outline = Color(0xFF373737),             // --UI-BORDER-COLOR-1
    outlineVariant = Color(0xFF2A2A2A),
    error = Color(0xFFE64340),
    onError = Color.White,
    errorContainer = Color(0xFF311E1D),
    onErrorContainer = Color(0xFFFFB4B0),
    surfaceTint = Color(0xFF4CA66A),
)

private val StudioLightColors = lightColorScheme(
    primary = Color(0xFF333333),             // --ink-accent
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0F0F0),    // --ink-bg-secondary
    onPrimaryContainer = Color(0xFF1A1A1A),
    secondary = Color(0xFF666666),           // --ink-text-secondary
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0F0F0),
    onSecondaryContainer = Color(0xFF1A1A1A),
    tertiary = Color(0xFF666666),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF0F0F0),
    onTertiaryContainer = Color(0xFF1A1A1A),
    background = Color(0xFFFAFAFA),          // --ink-bg-primary
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF),             // --ink-bg-card
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF666666),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF0F0F0),
    surfaceContainerHigh = Color(0xFFF1F1F1),
    surfaceContainerHighest = Color(0xFFE5E7EA),
    outline = Color(0xFFE0E0E0),             // --ink-border
    outlineVariant = Color(0xFFEBEBEB),
    error = Color(0xFFC62828),               // --ink-error
    onError = Color.White,
    errorContainer = Color(0xFFF6E9E9),
    onErrorContainer = Color(0xFF5F1612),
)

/** HStudio 主体圆角 8–12px，胶囊 999px。 */
private val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/** HStudio 的紧凑排版档（9–18px），移动端等比上移到 11–20sp。 */
private val StudioTypography = Typography(
    displaySmall = Typography().displaySmall.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    headlineMedium = Typography().headlineMedium.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = Typography().titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = Typography().titleMedium.copy(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = Typography().titleSmall.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
    bodyLarge = Typography().bodyLarge.copy(fontSize = 15.sp, lineHeight = 24.sp),
    bodyMedium = Typography().bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = Typography().bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = Typography().labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = Typography().labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = Typography().labelSmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
)

@Composable
fun HermesTheme(
    appearance: String = "system",
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val dark = when (appearance) {
        "light" -> false
        "dark" -> true
        // HStudio 深色优先（--UI-BG #000），未设置时按系统
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    // Scaling density's fontScale scales every sp in the tree, including the
    // markdown renderer, without touching individual typography styles.
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density,
            fontScale = density.fontScale * fontScale,
        ),
    ) {
        MaterialTheme(
            colorScheme = if (dark) StudioDarkColors else StudioLightColors,
            typography = StudioTypography,
            shapes = StudioShapes,
            content = content,
        )
    }
}

/**
 * HStudio ink tokens (app.css `--ink-*`).
 *
 * These are deliberately **plain functions**, not @Composable: they are pure
 * lookups against a dark/light flag, so calling them inside a recomposition
 * costs nothing and they stay usable from non-composable helpers. Pick the
 * flag with [isInkDark] off the active [ColorScheme].
 */
fun ColorScheme.isInkDark(): Boolean = background.luminance() < 0.5f

/** --ink-bg-message: AI message bubble background. */
fun inkMessageBg(dark: Boolean): Color =
    if (dark) Color(0xFF262828) else Color(0xFFF1F1F1)

/** --ink-accent: user bubble / active send button (#333 light, #eeeeeb dark). */
fun inkAccent(dark: Boolean): Color =
    if (dark) Color(0xFFEEEEEB) else Color(0xFF333333)

/** --ink-on-accent: text drawn on [inkAccent]. */
fun inkOnAccent(dark: Boolean): Color =
    if (dark) Color(0xFF191A1A) else Color.White

// The two-sided Telegram bubble palette that used to live here is gone: the
// official client draws both roles on --ink-bg-message and separates them by
// alignment only. See ChatMetrics for the measurements it does use.
