package com.verisonder.sonderweb.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.verisonder.sonderweb.data.Prefs

/** The search widget's settings: opens when the widget is added, and when it is reconfigured. */
class WidgetSettingsActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    // Opened from Settings: every search widget on the home screen takes the new look.
    private var allIds = IntArray(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        allIds = intent?.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: intArrayOf(widgetId)
        // Leaving without saving: nothing changes (a widget being added isn't added).
        setResult(Activity.RESULT_CANCELED, result())
        val start = WidgetLook.load(this, widgetId)
        val theme = Prefs(this).theme
        setContent {
            SonderTheme(if (isDark(theme)) DarkPalette else LightPalette) {
                WidgetSettings(start, onCancel = { finish() }, onSave = ::save)
            }
        }
    }

    private fun result() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

    private fun save(look: WidgetLook) {
        (allIds + widgetId).distinct().forEach {
            WidgetLook.save(this, it, look)
            SearchWidget.update(this, it)
        }
        setResult(Activity.RESULT_OK, result())
        finish()
    }
}

/** Which colour the colour dialog is choosing. */
private enum class ColorTarget(val title: String) { Background("Background colour"), Text("Text colour"), Icons("Icon colour") }

@Composable
private fun WidgetSettings(start: WidgetLook, onCancel: () -> Unit, onSave: (WidgetLook) -> Unit) {
    val p = LocalPalette.current
    var look by remember { mutableStateOf(start) }
    var picking by remember { mutableStateOf<ColorTarget?>(null) }
    BackHandler(onBack = onCancel)

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButtonSmall(Icons.AutoMirrored.Outlined.ArrowBack, "Back", size = 48, onClick = onCancel)
            Spacer(Modifier.width(8.dp))
            Text("Search widget", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }

        // The widget itself, drawn exactly as the home screen will draw it.
        Box(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(if (p.dark) Color(0xFF5A5A5A) else Color(0xFFB8BCC2))
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            AndroidView(
                factory = { FrameLayout(it) },
                update = { frame ->
                    frame.removeAllViews()
                    frame.addView(SearchWidget.views(frame.context, look, clickable = false).apply(frame.context, frame))
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
            )
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            GroupTitle("Theme")
            Group {
                Choice("Dark", look.theme == WidgetLook.DARK) { look = look.copy(theme = WidgetLook.DARK) }
                Choice("Light", look.theme == WidgetLook.LIGHT) { look = look.copy(theme = WidgetLook.LIGHT) }
                Choice("Follow phone", look.theme == WidgetLook.FOLLOW, "Light or dark with the phone") {
                    look = look.copy(theme = WidgetLook.FOLLOW)
                }
                if (Build.VERSION.SDK_INT >= 31) {
                    Choice("Material adaptive", look.theme == WidgetLook.ADAPTIVE, "Colours from your wallpaper") {
                        look = look.copy(theme = WidgetLook.ADAPTIVE)
                    }
                }
            }

            GroupTitle("Icons")
            Group {
                Toggle("Monochrome icons", "One colour, matching the theme (off: the blue logo)", look.mono) { look = look.copy(mono = it) }
                Toggle("Logo", "Show the SonderWeb logo", look.logo) { look = look.copy(logo = it) }
                Toggle("Voice search", "Show the microphone", look.voice) { look = look.copy(voice = it) }
                Toggle("Search with a picture", "Show the picture button", look.image) { look = look.copy(image = it) }
            }

            GroupTitle("Display")
            Group {
                Toggle("Search label", "Show \"Search\" in the widget", look.label) { look = look.copy(label = it) }
                Toggle("Drop shadow", null, look.shadow) { look = look.copy(shadow = it) }
            }

            GroupTitle("Colours")
            Group {
                ColorRow("Background colour", look.bg) { picking = ColorTarget.Background }
                ColorRow("Text colour", look.text) { picking = ColorTarget.Text }
                ColorRow("Icon colour", look.tint) { picking = ColorTarget.Icons }
            }
            Hint("A colour you choose here is used whatever the theme. \"Theme\" goes back to the theme's own.")
        }

        // Cancel and Save, kept above the gesture bar.
        Row(
            Modifier
                .fillMaxWidth()
                .background(p.bg)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(CircleShape)
                    .border(1.dp, p.line, CircleShape)
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) { Text("Cancel", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
            Box(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(CircleShape)
                    .background(p.accent)
                    .clickable { onSave(look) },
                contentAlignment = Alignment.Center,
            ) { Text("Save widget", color = p.onAccent, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
        }
    }

    picking?.let { target ->
        val current = when (target) {
            ColorTarget.Background -> look.bg
            ColorTarget.Text -> look.text
            ColorTarget.Icons -> look.tint
        }
        ColorDialog(target.title, current, alpha = target == ColorTarget.Background, onDismiss = { picking = null }) { chosen ->
            look = when (target) {
                ColorTarget.Background -> look.copy(bg = chosen)
                ColorTarget.Text -> look.copy(text = chosen)
                ColorTarget.Icons -> look.copy(tint = chosen)
            }
            picking = null
        }
    }
}

/** A colour setting: its name, and the colour (or "Theme" when it follows the theme). */
@Composable
private fun ColorRow(title: String, color: Int?, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.field)
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 16.sp)
            Text(color?.let { hex(it) } ?: "Theme", color = p.faint, fontSize = 13.sp)
        }
        if (color != null) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(1.dp, p.line, CircleShape)
                    .background(Color(color)),
            )
        }
    }
}

private fun hex(c: Int): String =
    if (c ushr 24 == 0xFF) "#%06X".format(c and 0xFFFFFF) else "#%08X".format(c)

private fun parseHex(text: String): Int? {
    val t = text.trim().removePrefix("#")
    if (!t.matches(Regex("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}"))) return null
    val v = t.toLong(16).toInt()
    return if (t.length == 6) v or 0xFF000000.toInt() else v
}

private val SWATCHES = listOf(
    0xFF1C1C1C, 0xFF000000, 0xFF303134, 0xFF5F6368, 0xFFDEDEDE, 0xFFF1F3F4, 0xFFFFFFFF,
    0xFFCB1A21, 0xFFE8710A, 0xFFF9AB00, 0xFF1E8E3E, 0xFF12B5CB, 0xFF1A73E8, 0xFF8AB4F8,
    0xFF9334E6, 0xFFD01884,
).map { it.toInt() }

/** Swatches and a hex field. [alpha]: see-through colours too (#AARRGGBB, and a half-clear swatch). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorDialog(title: String, current: Int?, alpha: Boolean, onDismiss: () -> Unit, onChosen: (Int?) -> Unit) {
    val p = LocalPalette.current
    var text by remember { mutableStateOf(current?.let { hex(it) } ?: "") }
    val parsed = parseHex(text)
    val swatches = if (alpha) SWATCHES + listOf(0x991C1C1C.toInt(), 0x99FFFFFF.toInt(), 0x00000000) else SWATCHES
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text(title, color = p.text) },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    swatches.forEach { c -> Swatch(Color(c), parsed == c, null) { text = hex(c) } }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(9) },
                    singleLine = true,
                    placeholder = { Text(if (alpha) "#RRGGBB or #AARRGGBB" else "#RRGGBB", color = p.faint) },
                    isError = text.isNotBlank() && parsed == null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = p.text,
                        unfocusedTextColor = p.text,
                        focusedBorderColor = p.accent,
                        unfocusedBorderColor = p.line,
                        cursorColor = p.accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = {
                onChosen(parsed?.let { if (alpha) it else it or 0xFF000000.toInt() })
            }) { Text("Done", color = if (parsed != null) p.accent else p.faint) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onChosen(null) }) { Text("Theme", color = p.accent) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = p.mute) }
            }
        },
    )
}
