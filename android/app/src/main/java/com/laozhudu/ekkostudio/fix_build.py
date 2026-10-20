p='MainActivity.kt'
s=open(p).read()

# Fix AnimatedIndicator
old_animated = """@OptIn(ExperimentalFoundationApi::class)
@Composable
/**
 * Telegram-style pulsing indicator for "working" state.
 */
@Composable
private fun AnimatedIndicator(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "workingIndicator")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animationSpec = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animationSpec = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = alpha
            }
            .background(color, CircleShape),
    )
}"""

new_animated = """@OptIn(ExperimentalFoundationApi::class)
@Composable
/**
 * Telegram-style pulsing indicator for "working" state.
 */
private fun AnimatedIndicator(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "workingIndicator")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = alpha
            }
            .background(color, CircleShape),
    )
}"""

# Fix duplicate @Composable for GlobalRunStatusStrip
old_grs = """@Composable
/**
 * Global run status strip — visible on every screen so the user always knows
 * if a run is in progress, which conversation owns it, and what tool is
 * currently executing. Mirrors Telegram's "typing..." in the header but richer:
 * shows elapsed time, current tool, and latest lifecycle notice.
 */
@Composable
private fun GlobalRunStatusStrip(state: UiState) {"""

new_grs = """


/**
 * Global run status strip — visible on every screen so the user always knows
 * if a run is in progress, which conversation owns it, and what tool is
 * currently executing. Mirrors Telegram's "typing..." in the header but richer:
 * shows elapsed time, current tool, and latest lifecycle notice.
 */
@Composable
private fun GlobalRunStatusStrip(state: UiState) {"""

# Fix duplicate @Composable for ApprovalBanner
old_ab = """@Composable
/**
 * Pinned notice for pending approvals, visible on any screen.
 *
 * Deliberately not a dialog: an approval that steals focus mid-typing is worse
 * than a strip you can ignore until you choose to act.
 */
@Composable
private fun ApprovalBanner(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {"""

new_ab = """


/**
 * Pinned notice for pending approvals, visible on any screen.
 *
 * Deliberately not a dialog: an approval that steals focus mid-typing is worse
 * than a strip you can ignore until you choose to act.
 */
@Composable
private fun ApprovalBanner(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {"""

# Fix duplicate @Composable for SwipeToReply
old_swipe = """@Composable
/**
 * Telegram's swipe-to-reply: drag a message to the right and it quotes itself in
 * the composer. The gesture is deliberately generous (56dp) because the same
 * drag also scrolls sideways in tables.
 */
@Composable
private fun SwipeToReply(enabled: Boolean, onReply: () -> Unit, content: @Composable () -> Unit) {"""

new_swipe = """


/**
 * Telegram's swipe-to-reply: drag a message to the right and it quotes itself in
 * the composer. The gesture is deliberately generous (56dp) because the same
 * drag also scrolls sideways in tables.
 */
@Composable
private fun SwipeToReply(enabled: Boolean, onReply: () -> Unit, content: @Composable () -> Unit) {"""

# Fix duplicate @Composable for DaySeparator
old_day = """@Composable
/**
 * Centred day pill, Telegram's date separator.
 */
@Composable
private fun DaySeparator(raw: String?) {"""

new_day = """


/**
 * Centred day pill, Telegram's date separator.
 */
@Composable
private fun DaySeparator(raw: String?) {"""

# Fix duplicate @Composable for InlineActionKeyboard
old_iak = """@Composable
/**
 * Telegram's inline keyboard: full-width rows separated by hairlines, attached to
 * the message that asked. Used for approvals and for clarifications that offer a
 * fixed set of answers; free-text clarification still opens the dialog.
 */
@Composable
private fun InlineActionKeyboard(action: PendingRunAction, onChoice: (String) -> Unit) {"""

new_iak = """


/**
 * Telegram's inline keyboard: full-width rows separated by hairlines, attached to
 * the message that asked. Used for approvals and for clarifications that offer a
 * fixed set of answers; free-text clarification still opens the dialog.
 */
@Composable
private fun InlineActionKeyboard(action: PendingRunAction, onChoice: (String) -> Unit) {"""

# Fix duplicate @Composable for ApprovalBanner (second occurrence)
old_ab2 = """@Composable
/**
 * Pinned notice for pending approvals, visible on any screen.
 *
 * Deliberately not a dialog: an approval that steals focus mid-typing is worse
 * than a strip you can ignore until you choose to act.
 */
@Composable
private fun ApprovalBanner(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {"""

new_ab2 = """


/**
 * Pinned notice for pending approvals, visible on any screen.
 *
 * Deliberately not a dialog: an approval that steals focus mid-typing is worse
 * than a strip you can ignore until you choose to act.
 */
@Composable
private fun ApprovalBanner(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {"""

# Add missing imports for AnimatedIndicator
imports_to_add = [
    "import androidx.compose.animation.animateFloatAsState",
    "import androidx.compose.animation.core.infiniteRepeatable",
    "import androidx.compose.animation.core.LinearEasing",
    "import androidx.compose.animation.core.RepeatMode",
    "import androidx.compose.animation.core.tween",
    "import androidx.compose.foundation.layout.width",
    "import androidx.compose.foundation.layout.height",
    "import androidx.compose.animation.AnimatedVisibility",
]

s = open('MainActivity.kt').read()

# 1. Fix AnimatedIndicator
s = s.replace(old_animated, new_animated, 1)

# 2. Fix duplicate @Composable for GlobalRunStatusStrip
s = s.replace(old_grs, new_grs, 1)

# 3. Fix duplicate @Composable for ApprovalBanner (first)
s = s.replace(old_ab, new_ab, 1)

# 4. Fix duplicate @Composable for SwipeToReply
s = s.replace(old_swipe, new_swipe, 1)

# 4. Fix duplicate @Composable for DaySeparator
s = s.replace(old_day, new_day, 1)

# 5. Fix duplicate @Composable for InlineActionKeyboard
s = s.replace(old_iak, new_iak, 1)

# Add missing imports
lines = s.split("\n")
last_import = max(i for i, l in enumerate(lines) if l.startswith("import "))
for imp in reversed(imports_to_add):
    if imp not in s:
        lines.insert(last_import + 1, imp)

s = "\n".join(lines)
open(p,'w').write(s)
print("All fixes applied")