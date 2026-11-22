package net.thirteenpoints.tapertv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.thirteenpoints.tapertv.data.Recording

/** Colors pulled from the 13pts retro logo: ink, cream, and the bolt's gold-to-flame run. */
object Palette {
    val Bg = Color(0xFF0B0B09)
    val Panel = Color(0xFF1B1A16)
    val PanelHi = Color(0xFF3A3220)
    val Text = Color(0xFFF7F1E3)
    val Dim = Color(0xFFA9A293)
    val Gold = Color(0xFFF6B021)
    val Flame = Color(0xFFE8783C)
    val Ink = Color(0xFF0B0B09)
}

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Err(val message: String) : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
}

/** Runs a blocking archive.org call off the main thread and hands back loading / error / result. */
@Composable
fun <T> rememberLoad(vararg keys: Any?, block: () -> T): Load<T> {
    var state by remember(*keys) { mutableStateOf<Load<T>>(Load.Loading) }
    LaunchedEffect(*keys) {
        state = Load.Loading
        state = try {
            Load.Ok(withContext(Dispatchers.IO) { block() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Err(e.message ?: "Couldn't reach archive.org")
        }
    }
    return state
}

@Composable
fun <T> LoadBox(load: Load<T>, content: @Composable (T) -> Unit) {
    when (load) {
        is Load.Loading -> Label("Loading…", color = Palette.Dim)
        is Load.Err -> Label(load.message, color = Palette.Flame)
        is Load.Ok -> content(load.value)
    }
}

/** Waits for typing to pause before handing the text on, so we don't search on every keypress. */
@Composable
fun debounced(text: String, ms: Long = 700): String {
    var out by remember { mutableStateOf(text) }
    LaunchedEffect(text) {
        if (text != out) {
            delay(ms)
            out = text
        }
    }
    return out
}

@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 15,
    color: Color = Palette.Text,
    bold: Boolean = false,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        lineHeight = (size * 1.3f).sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun ScreenTitle(title: String, subtitle: String = "") {
    Column(Modifier.padding(bottom = 14.dp)) {
        Label(title, size = 28, bold = true)
        if (subtitle.isNotBlank()) Label(subtitle, size = 14, color = Palette.Dim)
    }
}

/** The one focusable surface in the app: turns gold when the remote lands on it. */
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    requestFocus: Boolean = false,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    if (requestFocus) {
        LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    }
    Box(
        modifier
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    focused -> Palette.Gold
                    selected -> Palette.PanelHi
                    else -> Palette.Panel
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        content(focused)
    }
}

@Composable
fun RowItem(
    title: String,
    onClick: () -> Unit,
    subtitle: String = "",
    trailing: String = "",
    selected: Boolean = false,
    requestFocus: Boolean = false,
) {
    FocusCard(onClick, Modifier.fillMaxWidth(), selected, requestFocus) { focused ->
        val main = if (focused) Palette.Ink else Palette.Text
        val dim = if (focused) Palette.Ink else Palette.Dim
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label(title, size = 16, color = main, bold = selected)
                if (subtitle.isNotBlank()) Label(subtitle, size = 13, color = dim)
            }
            if (trailing.isNotBlank()) {
                Spacer(Modifier.width(16.dp))
                Label(trailing, size = 13, color = dim)
            }
        }
    }
}

@Composable
fun Chip(label: String, onClick: () -> Unit, selected: Boolean = false, requestFocus: Boolean = false) {
    FocusCard(onClick, selected = selected, requestFocus = requestFocus) { focused ->
        Label(label, size = 14, color = if (focused) Palette.Ink else Palette.Text, bold = selected)
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(hint) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.moveFocus(FocusDirection.Down) }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Palette.Text,
            unfocusedTextColor = Palette.Text,
            focusedBorderColor = Palette.Gold,
            unfocusedBorderColor = Palette.Dim,
            focusedLabelColor = Palette.Gold,
            unfocusedLabelColor = Palette.Dim,
            cursorColor = Palette.Gold,
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    )
}

@Composable
fun RecordingRow(rec: Recording, onClick: () -> Unit, requestFocus: Boolean = false) {
    RowItem(
        title = listOf(rec.artist, rec.date).filter { it.isNotBlank() }.joinToString(" · ")
            .ifBlank { rec.title },
        subtitle = listOf(rec.place, taperLine(rec)).filter { it.isNotBlank() }.joinToString("  —  "),
        trailing = stats(rec),
        onClick = onClick,
        requestFocus = requestFocus,
    )
}

/** Every tape of one night by one artist, with the one we'd play by default. */
data class ShowGroup(val best: Recording, val versions: List<Recording>)

/** Highest rated wins; unrated tapes fall back to most played. */
fun bestOf(versions: List<Recording>): Recording =
    versions.sortedWith(compareByDescending<Recording> { it.rating }.thenByDescending { it.downloads }).first()

/** Collapses a flat list of recordings into one entry per show, keeping the list's order. */
fun groupByShow(recs: List<Recording>): List<ShowGroup> =
    recs.groupBy { if (it.date.isBlank()) it.id else it.artist.lowercase() + "|" + it.date }
        .values.map { ShowGroup(bestOf(it), it) }

@Composable
fun ShowGroupRow(group: ShowGroup, nav: Nav, requestFocus: Boolean = false) {
    val rec = group.best
    val n = group.versions.size
    val tapers = group.versions.map { it.taper }.filter { it.isNotBlank() }.distinct()
    val taperText = when {
        tapers.isEmpty() -> ""
        tapers.size == 1 -> "Taper: ${tapers[0]}"
        else -> "${tapers.size} tapers"
    }
    RowItem(
        title = listOf(rec.artist, rec.date).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { rec.title },
        subtitle = listOf(group.versions.firstOrNull { it.place.isNotBlank() }?.place.orEmpty(), taperText)
            .filter { it.isNotBlank() }.joinToString("  —  "),
        trailing = listOf(if (n > 1) "$n versions" else "", stats(rec)).filter { it.isNotBlank() }.joinToString(" · "),
        onClick = { nav.go(Screen.RecordingPage(rec, group.versions)) },
        requestFocus = requestFocus,
    )
}

fun taperLine(rec: Recording): String = if (rec.taper.isBlank()) "" else "Taper: ${rec.taper}"

fun stats(rec: Recording): String {
    val parts = mutableListOf<String>()
    if (rec.isSoundboard) parts += "SBD"
    if (rec.rating > 0) parts += "★ " + "%.1f".format(rec.rating)
    if (rec.downloads > 0) parts += compact(rec.downloads) + " plays"
    return parts.joinToString(" · ")
}

fun compact(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}

fun clock(totalSec: Long): String {
    val s = totalSec.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
