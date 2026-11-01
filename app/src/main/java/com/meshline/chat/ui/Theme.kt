package com.meshline.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal val Ink = Color(0xFF14213D)
internal val Muted = Color(0xFF6B7A90)
internal val Page = Color(0xFFEFF4FB)
internal val Accent = Color(0xFF1F6FEB)
internal val AccentSoft = Color(0xFFDCE8FB)
internal val AccentFill = Color(0xFFCFE1FB)
internal val Green = Color(0xFF22A06B)
internal val Border = Color(0xFFE1E8F2)
internal val Danger = Color(0xFFD64545)
internal val Glass = Color.White.copy(alpha = 0.88f)
internal val PageBrush = Brush.verticalGradient(listOf(Color(0xFFF4F8FE), Color(0xFFDFEAF8)))

@Composable
fun MeshlineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            secondary = Green,
            background = Page,
            surface = Color.White,
            onSurface = Ink,
            onBackground = Ink
        ),
        content = content
    )
}

private val avatarColors = listOf(0xFF5B8DEF, 0xFF7B6CF6, 0xFF2BB3A3, 0xFFE8884D, 0xFFD46A9F, 0xFF4FA3D9).map { Color(it) }

@Composable
internal fun Avatar(name: String, size: Dp = 52.dp, online: Boolean = false) {
    val color = avatarColors[(name.hashCode() and 0x7fffffff) % avatarColors.size]
    Box(Modifier.size(size)) {
        Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "M",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp
            )
        }
        if (online) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(size * 0.27f).clip(CircleShape)
                    .background(Green).border(2.dp, Color.White, CircleShape)
            )
        }
    }
}

internal fun formatClock(millis: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))

private fun sameDay(a: Long, b: Long): Boolean {
    val x = Calendar.getInstance().apply { timeInMillis = a }
    val y = Calendar.getInstance().apply { timeInMillis = b }
    return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
}

/** Clock time for today, short date otherwise (chat list). */
internal fun formatStamp(millis: Long): String =
    if (sameDay(millis, System.currentTimeMillis())) formatClock(millis)
    else SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(millis))

/** "Today", "Yesterday" or a date (chat day separators). */
internal fun dayLabel(millis: Long): String {
    val now = System.currentTimeMillis()
    return when {
        sameDay(millis, now) -> "Today"
        sameDay(millis, now - 24L * 60 * 60 * 1000) -> "Yesterday"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(millis))
    }
}

/** First 16 hex chars of a device ID, grouped for easy reading aloud ("A1B2 C3D4 E5F6 0718"). */
internal fun shortId(id: String): String = id.take(16).uppercase().chunked(4).joinToString(" ")
