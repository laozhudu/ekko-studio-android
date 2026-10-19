package xyz.rflg.hstudiodirect

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * HStudio 视觉规范移植（令牌取自 HStudio App 的 app.css）。
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
fun HermesTheme(appearance: String = "system", content: @Composable () -> Unit) {
    val dark = when (appearance) {
        "light" -> false
        "dark" -> true
        // HStudio 深色优先（--UI-BG #000），未设置时按系统
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) StudioDarkColors else StudioLightColors,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content,
    )
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

/** --ink-bg-card-hover: tool-call rows, attach chips, avatar discs. */
fun inkCardHover(dark: Boolean): Color =
    if (dark) Color(0xFF222323) else Color(0xFFFAFAFA)

/** --ink-bg-input: composer field background. */
fun inkInputBg(dark: Boolean): Color =
    if (dark) Color(0xFF1E1F1F) else Color.White

/** --ink-input-border: hairline on the composer field and tool rows. */
fun inkInputBorder(dark: Boolean): Color =
    if (dark) Color(0x29FFFFFF) else Color(0x2E333333)   // .16 white / .18 #333

/** --ink-bg-code: fenced code block background. */
fun inkCodeBg(dark: Boolean): Color =
    if (dark) Color(0xFF131414) else Color(0xFFF4F4F4)

/** --ink-selected-bg: dots, focus rings and tinted chips — NOT row fills. */
fun inkSelectedBg(dark: Boolean): Color =
    // Dark is rgba(255,255,255,.18) = 0x2E, deliberately *not* the .12 that
    // --ink-focus-ring uses; the two are easy to conflate.
    if (dark) Color(0x2EFFFFFF) else Color(0x1A333333)

/** --ink-text-muted: timestamps and secondary chrome. */
fun inkTextMuted(dark: Boolean): Color =
    if (dark) Color(0xFF858987) else Color(0xFF999999)

/** --ink-error-soft: tinted background behind error notes and bubbles. */
fun inkErrorSoft(dark: Boolean): Color =
    if (dark) Color(0x1FEF8C87) else Color(0x14C62828)

/** --ink-error: error foreground (#c62828 light, #ef8c87 dark). */
fun inkError(dark: Boolean): Color =
    if (dark) Color(0xFFEF8C87) else Color(0xFFC62828)

/** --ink-bg-card: sheets, composer area, panels. */
fun inkCardBg(dark: Boolean): Color =
    if (dark) Color(0xFF181919) else Color.White

/** --ink-border / SCSS $border-color: blockquote bar, generic hairlines. */
fun inkBorder(dark: Boolean): Color =
    if (dark) Color(0xFF383A39) else Color(0xFFE0E0E0)

/** --ink-border-light / SCSS $border-light: soft panel hairlines. */
fun inkBorderLight(dark: Boolean): Color =
    if (dark) Color(0xFF2D2F2E) else Color(0xFFEBEBEB)

/** --ink-text-secondary / SCSS $text-secondary: sender labels, quotes. */
fun inkTextSecondary(dark: Boolean): Color =
    if (dark) Color(0xFFB8BAB8) else Color(0xFF666666)

/** --ink-bg-secondary: agent badge fill, active session row (light #f0f0f0, dark #202121). */
fun inkSecondaryBg(dark: Boolean): Color =
    if (dark) Color(0xFF202121) else Color(0xFFF0F0F0)

/** --ink-text-primary: body copy, inline code, attachment names. */
fun inkTextPrimary(dark: Boolean): Color =
    if (dark) Color(0xFFEEEEEB) else Color(0xFF1A1A1A)

/** --ink-pressed: tap feedback and the code-block header strip. */
fun inkPressed(dark: Boolean): Color =
    if (dark) Color(0x0FFFFFFF) else Color(0x0A000000)

/** --ink-code-text: foreground inside fenced code blocks. */
fun inkCodeText(dark: Boolean): Color =
    if (dark) Color(0xFFE5E7EB) else Color(0xFF1F2937)

/** --ink-file-card-bg / --ink-file-card-border: official App dark tokens are real (#202121 / #2d2f2e). */
fun inkFileCardBg(dark: Boolean): Color =
    if (dark) Color(0xFF202121) else Color(0xFFE5E7EA)

fun inkFileCardBorder(dark: Boolean): Color =
    if (dark) Color(0xFF2D2F2E) else Color(0xFFD1D5DA)

/** --ink-file-card-icon-bg: glyph disc on file cards and attachment chips. */
fun inkFileCardIconBg(dark: Boolean): Color =
    if (dark) Color(0xFF181919) else Color(0xFFF8F9FA)

/**
 * --ink-focus-ring: 3px ring behind a focused `.field-shell`
 * (login.css). Light rgba(51,51,51,.1), dark rgba(255,255,255,.12).
 */
fun inkFocusRing(dark: Boolean): Color =
    if (dark) Color(0x1FFFFFFF) else Color(0x1A333333)

/** --ink-shadow-lg ambient for elevated cards (.app-confirm-card). */
fun inkShadowLg(dark: Boolean): Color =
    if (dark) Color(0x4D000000) else Color(0x17000000)   // .3 / .09 black

/** --ink-login-bg: the sheet that holds the auth forms. */
fun inkLoginBg(dark: Boolean): Color =
    if (dark) Color(0xFF181919) else Color.White

/**
 * .login-banner base wash — the App layers two radial glows over a diagonal
 * gradient (light #f4f3ef→#e8eceb→#dde8ed, dark #292d2e→#242a2c→#202b32);
 * these three stops are drawn as a Brush.
 */
fun inkBannerStart(dark: Boolean): Color =
    if (dark) Color(0xFF292D2E) else Color(0xFFF4F3EF)

fun inkBannerMid(dark: Boolean): Color =
    if (dark) Color(0xFF242A2C) else Color(0xFFE8ECEB)

fun inkBannerEnd(dark: Boolean): Color =
    if (dark) Color(0xFF202B32) else Color(0xFFDDE8ED)

/** Banner type colours are fixed art, not ink tokens (#202428 / #f0f1ee). */
fun inkBannerTitle(dark: Boolean): Color =
    if (dark) Color(0xFFF0F1EE) else Color(0xFF202428)

fun inkBannerBody(dark: Boolean): Color =
    if (dark) Color(0xFFADB5B9) else Color(0xFF657078)

fun inkBannerKicker(dark: Boolean): Color =
    if (dark) Color(0xFFA0A8AC) else Color(0xFF6D777D)

/** Studio shows a short clock for today and a date for older rows. */
fun formatStamp(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val millis = raw.toLongOrNull()
    if (millis != null) {
        val normalized = if (millis < 100_000_000_000L) millis * 1000 else millis
        val now = java.util.Calendar.getInstance()
        val then = java.util.Calendar.getInstance().apply { timeInMillis = normalized }
        val sameDay = now.get(java.util.Calendar.ERA) == then.get(java.util.Calendar.ERA) &&
            now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
            now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
        return android.text.format.DateFormat.format(if (sameDay) "h:mm a" else "yyyy-MM-dd", normalized).toString()
    }
    // ISO 8601: keep the clock when the day is today, otherwise the date.
    val date = raw.take(10)
    val time = raw.drop(11).take(5)
    val today = android.text.format.DateFormat.format("yyyy-MM-dd", System.currentTimeMillis()).toString()
    return if (date == today && time.isNotBlank()) time else date
}
