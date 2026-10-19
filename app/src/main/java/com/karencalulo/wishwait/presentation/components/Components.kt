package com.karencalulo.wishwait.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karencalulo.wishwait.R
import com.karencalulo.wishwait.presentation.common.Fmt
import com.karencalulo.wishwait.presentation.common.FriendVoice
import com.karencalulo.wishwait.presentation.common.desireLabelRes
import com.karencalulo.wishwait.presentation.common.res
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.karencalulo.wishwait.presentation.common.LocalMoney
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Round emoji badge. */
@Composable
fun EmojiAvatar(
    emoji: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .clearAndSetSemantics { }, // decorative: screen readers skip it
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size.value * 0.5f).sp)
    }
}

/** Ollie talking. Slides in so it feels like a reply, not a report. */
@Composable
fun FriendBubble(
    headline: String,
    message: String,
    modifier: Modifier = Modifier,
    reaction: String? = null,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 },
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.semantics(mergeDescendants = true) { }) {
            Box(Modifier.clearAndSetSemantics { }) {
                EmojiAvatar(FriendVoice.AVATAR, size = 52.dp, container = MaterialTheme.colorScheme.primaryContainer)
                if (reaction != null) {
                    Text(
                        reaction,
                        fontSize = 18.sp,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Card(
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.friend_says, FriendVoice.name(res())),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(headline, style = MaterialTheme.typography.titleMedium)
                    if (message.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Animated progress ring for cooldowns. */
@Composable
fun CountdownRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    strokeWidth: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: @Composable () -> Unit = {},
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(800), label = "ring")
    Box(
        modifier
            .size(size)
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

/**
 * Where the price lands on a "day → year of salary" scale.
 * Piecewise so that both a coffee and a car are readable on the same bar.
 */
@Composable
fun ImpactMeter(incomeRatio: Double, modifier: Modifier = Modifier) {
    val stops = doubleArrayOf(0.0, 1.0 / 30.0, 0.25, 1.0, 3.0, 12.0)
    val target = run {
        if (incomeRatio >= stops.last()) return@run 1f
        val i = (1 until stops.size).first { incomeRatio < stops[it] }
        val local = (incomeRatio - stops[i - 1]) / (stops[i] - stops[i - 1])
        ((i - 1 + local) / (stops.size - 1)).toFloat()
    }
    var start by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { start = true }
    val fraction by animateFloatAsState(if (start) target else 0f, tween(1200), label = "impact")

    val colors = MaterialTheme.colorScheme
    val description = stringResource(
        R.string.cd_impact,
        Fmt.incomeShare(incomeRatio),
        Fmt.incomeShareLabel(res(), incomeRatio),
    )
    Column(modifier.clearAndSetSemantics { contentDescription = description }) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(14.dp),
        ) {
            val r = size.height / 2
            drawRoundRect(
                color = colors.surfaceContainerHighest,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            )
            if (fraction > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(colors.primary, colors.tertiary, colors.error),
                        startX = 0f,
                        endX = size.width,
                    ),
                    size = Size(size.width * fraction, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf(
                R.string.meter_day,
                R.string.meter_week,
                R.string.meter_month,
                R.string.meter_3months,
                R.string.meter_year,
            ).map { stringResource(it) }.forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            stringResource(R.string.meter_of_pay),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
fun StatTile(
    emoji: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .semantics(mergeDescendants = true) { },
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(emoji, fontSize = 20.sp, modifier = Modifier.clearAndSetSemantics { })
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    suffix: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        prefix = prefix?.let { { Text("$it ") } },
        suffix = suffix?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        singleLine = true,
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    AmountField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        prefix = LocalMoney.current.symbol,
        supportingText = supportingText,
        isError = isError,
        imeAction = imeAction,
        textStyle = textStyle,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        trailing()
    }
}

/** 1–5 "how badly do you want it" picker. */
@Composable
fun DesirePicker(
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            (1..5).forEach { level ->
                val isSelected = selected == level
                val scale by animateFloatAsState(if (isSelected) 1.15f else 1f, label = "desire")
                val label = stringResource(R.string.cd_desire_option, stringResource(desireLabelRes(level)), level)
                Surface(
                    selected = isSelected,
                    onClick = { onSelect(level) },
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .size((52 * scale).dp)
                        .semantics {
                            contentDescription = label
                            role = Role.RadioButton
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            FriendVoice.desireEmoji(level),
                            fontSize = 24.sp,
                            modifier = Modifier.clearAndSetSemantics { },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(selected?.let { desireLabelRes(it) } ?: R.string.desire_tap_hint),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/** Little line chart of desire check-ins over time. */
@Composable
fun DesireChart(levels: List<Int>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val r = res()
    val description = stringResource(
        R.string.cd_desire_chart,
        levels.joinToString(", ") { r.getString(desireLabelRes(it)) },
    )
    Canvas(
        modifier
            .fillMaxWidth()
            .height(96.dp)
            .semantics { contentDescription = description },
    ) {
        val pad = 8.dp.toPx()
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        for (i in 0..4) {
            val y = pad + h * i / 4f
            drawLine(grid, Offset(pad, y), Offset(pad + w, y), strokeWidth = 1f)
        }
        if (levels.isEmpty()) return@Canvas
        val points = levels.mapIndexed { i, level ->
            val x = if (levels.size == 1) pad + w / 2 else pad + w * i / (levels.size - 1)
            val y = pad + h * (5 - level.coerceIn(1, 5)) / 4f
            Offset(x, y)
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        points.forEach { drawCircle(line, radius = 5.dp.toPx(), center = it) }
    }
}

private data class Particle(
    val x: Float,
    val speed: Float,
    val wobble: Float,
    val phase: Float,
    val spin: Float,
    val colorIndex: Int,
    val w: Float,
    val h: Float,
)

/** Celebration! Restarts each time [trigger] changes to a new non-zero value. */
@Composable
fun ConfettiOverlay(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.error,
        Color(0xFFFFC94D),
    )
    val progress = remember(trigger) { Animatable(0f) }
    val particles = remember(trigger) {
        val rnd = Random(trigger)
        List(90) {
            Particle(
                x = rnd.nextFloat(),
                speed = 0.6f + rnd.nextFloat() * 0.8f,
                wobble = 1f + rnd.nextFloat() * 3f,
                phase = rnd.nextFloat(),
                spin = rnd.nextFloat() * 2f - 1f,
                colorIndex = rnd.nextInt(5),
                w = 8f + rnd.nextFloat() * 10f,
                h = 6f + rnd.nextFloat() * 6f,
            )
        }
    }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(2600, easing = LinearEasing))
    }
    Canvas(modifier.fillMaxSize().clearAndSetSemantics { }) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        particles.forEach { p ->
            val x = size.width * p.x + sin(((t * p.wobble + p.phase) * 2 * PI).toFloat()) * 40f
            val y = -40f + (size.height + 120f) * (t * p.speed)
            rotate(degrees = 720f * t * p.spin, pivot = Offset(x, y)) {
                drawRect(
                    color = colors[p.colorIndex].copy(alpha = 1f - t * 0.4f),
                    topLeft = Offset(x - p.w / 2, y - p.h / 2),
                    size = Size(p.w, p.h),
                )
            }
        }
    }
}
