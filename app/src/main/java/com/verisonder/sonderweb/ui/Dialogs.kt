package com.verisonder.sonderweb.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.verisonder.sonderweb.browser.Browser
import com.verisonder.sonderweb.browser.ContextMenu
import com.verisonder.sonderweb.browser.Downloads
import com.verisonder.sonderweb.browser.PromptUi
import com.verisonder.sonderweb.browser.Prompts
import com.verisonder.sonderweb.browser.Ublock
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/** Shows one waiting page dialog. Every way out answers the page exactly once. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromptDialog(prompt: PromptUi) {
    val p = LocalPalette.current
    var answered by remember(prompt) { mutableStateOf(false) }
    fun finish(block: () -> Unit) {
        if (answered) return
        answered = true
        Prompts.remove(prompt)
        block()
    }

    when (prompt) {
        is PromptUi.Ask -> AlertDialog(
            onDismissRequest = { finish { prompt.onResult(false) } },
            containerColor = p.card,
            title = { Text(prompt.title, color = p.text, fontSize = 18.sp) },
            text = prompt.message?.let { { Text(it, color = p.mute) } },
            confirmButton = {
                TextButton({ finish { prompt.onResult(true) } }) {
                    Text(prompt.positive, color = p.accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = prompt.negative?.let { neg ->
                { TextButton({ finish { prompt.onResult(false) } }) { Text(neg, color = p.text) } }
            },
        )

        is PromptUi.TextInput -> {
            var value by remember(prompt) { mutableStateOf(prompt.initial) }
            AlertDialog(
                onDismissRequest = { finish { prompt.onResult(null) } },
                containerColor = p.card,
                title = { Text(prompt.title, color = p.text, fontSize = 18.sp) },
                text = {
                    Column {
                        prompt.message?.let { Text(it, color = p.mute, modifier = Modifier.padding(bottom = 10.dp)) }
                        OutlinedTextField(value, { value = it }, singleLine = true, colors = fieldColors())
                    }
                },
                confirmButton = { TextButton({ finish { prompt.onResult(value) } }) { Text("OK", color = p.accent) } },
                dismissButton = { TextButton({ finish { prompt.onResult(null) } }) { Text("Cancel", color = p.text) } },
            )
        }

        is PromptUi.Login -> {
            var user by remember(prompt) { mutableStateOf(prompt.initialUser) }
            var pass by remember(prompt) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { finish { prompt.onResult(null) } },
                containerColor = p.card,
                title = { Text(prompt.title, color = p.text, fontSize = 18.sp) },
                text = {
                    Column {
                        prompt.message?.let { Text(it, color = p.mute, modifier = Modifier.padding(bottom = 10.dp)) }
                        if (!prompt.passwordOnly) {
                            OutlinedTextField(
                                user,
                                { user = it },
                                label = { Text("Username") },
                                singleLine = true,
                                colors = fieldColors(),
                            )
                            Spacer(Modifier.size(8.dp))
                        }
                        OutlinedTextField(
                            pass,
                            { pass = it },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = fieldColors(),
                        )
                    }
                },
                confirmButton = {
                    TextButton({ finish { prompt.onResult(user to pass) } }) { Text("Sign in", color = p.accent) }
                },
                dismissButton = { TextButton({ finish { prompt.onResult(null) } }) { Text("Cancel", color = p.text) } },
            )
        }

        is PromptUi.Choose -> ChooseDialog(prompt) { picked -> finish { prompt.onResult(picked) } }

        is PromptUi.PickColor -> {
            val colors = remember(prompt) {
                (prompt.presets + listOf(
                    "#000000", "#FFFFFF", "#9CA3AF", "#EF4444", "#F97316", "#EAB308", "#22C55E",
                    "#14B8A6", "#3B82F6", "#6366F1", "#A855F7", "#EC4899", "#78350F", "#1E3A8A",
                )).map { it.uppercase() }.distinct()
            }
            var picked by remember(prompt) { mutableStateOf(prompt.initial.uppercase()) }
            AlertDialog(
                onDismissRequest = { finish { prompt.onResult(null) } },
                containerColor = p.card,
                title = { Text("Pick a colour", color = p.text, fontSize = 18.sp) },
                text = {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        colors.forEach { hex ->
                            val c = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull() ?: return@forEach
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(if (hex == picked) 3.dp else 1.dp, if (hex == picked) p.accent else p.line, CircleShape)
                                    .clickable { picked = hex },
                            )
                        }
                    }
                },
                confirmButton = { TextButton({ finish { prompt.onResult(picked.lowercase()) } }) { Text("OK", color = p.accent) } },
                dismissButton = { TextButton({ finish { prompt.onResult(null) } }) { Text("Cancel", color = p.text) } },
            )
        }
    }
}

@Composable
private fun fieldColors() = LocalPalette.current.let { p ->
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = p.text,
        unfocusedTextColor = p.text,
        focusedBorderColor = p.accent,
        unfocusedBorderColor = p.line,
        cursorColor = p.accent,
        focusedLabelColor = p.accent,
        unfocusedLabelColor = p.mute,
    )
}

@Composable
private fun ChooseDialog(prompt: PromptUi.Choose, done: (List<org.mozilla.geckoview.GeckoSession.PromptDelegate.ChoicePrompt.Choice>?) -> Unit) {
    val p = LocalPalette.current
    val selected = remember(prompt) {
        mutableStateListOf<String>().apply { prompt.rows.forEach { r -> r.choice?.takeIf { it.selected }?.let { add(it.id) } } }
    }
    AlertDialog(
        onDismissRequest = { done(null) },
        containerColor = p.card,
        title = prompt.title?.takeIf { it.isNotBlank() }?.let { { Text(it, color = p.text, fontSize = 18.sp) } },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(prompt.rows) { _, row ->
                    val c = row.choice
                    if (row.header || c == null) {
                        Text(
                            row.label,
                            color = p.faint,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                        )
                    } else {
                        val on = c.id in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = !c.disabled) {
                                    if (prompt.multiple) {
                                        if (on) selected.remove(c.id) else selected.add(c.id)
                                    } else {
                                        done(listOf(c))
                                    }
                                }
                                .padding(start = if (row.indent) 12.dp else 0.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (prompt.multiple) {
                                Checkbox(on, null, enabled = !c.disabled, colors = CheckboxDefaults.colors(checkedColor = p.accent))
                            } else {
                                RadioButton(on, null, enabled = !c.disabled, colors = RadioButtonDefaults.colors(selectedColor = p.accent))
                            }
                            Text(
                                row.label,
                                color = if (c.disabled) p.faint else p.text,
                                fontSize = 15.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (prompt.multiple) {
                TextButton({
                    done(prompt.rows.mapNotNull { it.choice }.filter { it.id in selected })
                }) { Text("OK", color = p.accent) }
            }
        },
        dismissButton = { TextButton({ done(null) }) { Text("Cancel", color = p.text) } },
    )
}

/** Long-press on a link or image. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextSheet(req: ContextMenu.Request) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close = { ContextMenu.dismiss() }
    fun copy(text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("link", text))
    }

    ModalBottomSheet(
        onDismissRequest = close,
        sheetState = state,
        containerColor = p.bar,
        contentColor = p.text,
        dragHandle = null,
    ) {
        Column(Modifier.navigationBarsPadding().padding(vertical = 8.dp)) {
            Text(
                req.link ?: req.src ?: "",
                color = p.faint,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            req.link?.let { link ->
                MenuRow(Icons.Outlined.OpenInNew, "Open in new tab") {
                    Browser.newTab(link, isPrivate = req.tab.isPrivate, select = false, opener = req.tab)
                    Browser.host?.toast("Opened in a new tab", "Switch") {
                        Browser.tabs.lastOrNull { it.url == link }?.let { Browser.select(it) }
                    }
                    close()
                }
                if (!req.tab.isPrivate) {
                    MenuRow(Icons.Outlined.VisibilityOff, "Open in private tab") {
                        Browser.newTab(link, isPrivate = true)
                        close()
                    }
                }
                MenuRow(Icons.Outlined.ContentCopy, "Copy link") {
                    copy(link)
                    close()
                }
                MenuRow(Icons.Outlined.Share, "Share link") {
                    Browser.host?.share(link, req.linkText)
                    close()
                }
                if (!ContextMenu.isMedia(req)) {
                    MenuRow(Icons.Outlined.Download, "Download link") {
                        Downloads.fetch(link, req.tab.isPrivate, req.tab.url)
                        close()
                    }
                }
            }
            req.src?.takeIf { ContextMenu.isMedia(req) }?.let { src ->
                MenuRow(Icons.Outlined.Image, if (req.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE) "Open image in new tab" else "Open media in new tab") {
                    Browser.newTab(src, isPrivate = req.tab.isPrivate, opener = req.tab)
                    close()
                }
                MenuRow(Icons.Outlined.Download, if (req.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE) "Save image" else "Save media") {
                    Downloads.fetch(src, req.tab.isPrivate, req.tab.url)
                    close()
                }
                MenuRow(Icons.Outlined.ContentCopy, "Copy image address") {
                    copy(src)
                    close()
                }
            }
        }
    }
}

/** uBlock Origin's own panel (what's blocked, the power button for this site). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UblockSheet(session: GeckoSession) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val height = LocalConfiguration.current.screenHeightDp * 0.72f
    // Fits the panel when it says how tall it is (uBlock's does), up to most of the screen.
    val said = com.verisonder.sonderweb.browser.Extensions.popupHeight
    val fit by androidx.compose.animation.core.animateDpAsState(
        if (said > 0) said.toFloat().coerceAtMost(height).dp else 420.dp,
        androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 500f),
        label = "panel",
    )
    // The panel's own colour until it has drawn (no white flash while it opens).
    val view = remember(session) { GeckoView(ctx).apply { coverUntilFirstPaint(p.bar.toArgb()) } }
    DisposableEffect(session) {
        view.setSession(session)
        onDispose { view.releaseSession() }
    }
    ModalBottomSheet(
        onDismissRequest = { Ublock.closePopup() },
        sheetState = state,
        containerColor = p.bar,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(p.line),
            )
        },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(fit)
                .navigationBarsPadding(),
        ) {
            AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().fillMaxHeight())
            // A cover in the sheet's colour until the panel has really drawn: it says so by
            // reporting its height (uBlock's does); others get a moment. No white flash.
            var shown by remember(session) { mutableStateOf(false) }
            LaunchedEffect(session, said > 0) {
                kotlinx.coroutines.delay(if (said > 0) 150 else 1200)
                shown = true
            }
            val cover by androidx.compose.animation.core.animateFloatAsState(
                if (shown) 0f else 1f,
                androidx.compose.animation.core.tween(180),
                label = "panelCover",
            )
            if (cover > 0.01f) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = cover }.background(p.bar))
            }
        }
    }
}

/** "Open in <app>?": the app, or stay in the browser. Nothing opens until you choose. */
@Composable
fun OpenAppDialog(ask: com.verisonder.sonderweb.browser.AppLinks.Ask) {
    val p = LocalPalette.current
    val links = com.verisonder.sonderweb.browser.AppLinks
    AlertDialog(
        onDismissRequest = { links.stayInBrowser() },
        containerColor = p.card,
        icon = ask.icon?.let { bmp ->
            {
                androidx.compose.foundation.Image(
                    bmp.asImageBitmap(),
                    null,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                )
            }
        },
        title = { Text("Open in ${ask.app}?", color = p.text, fontSize = 18.sp) },
        text = {
            Text(
                if (ask.stay != null) "This link can open in the app, or stay here in SonderWeb." else "This link wants to open another app.",
                color = p.mute,
            )
        },
        confirmButton = {
            TextButton({ links.openApp() }) { Text("Open app", color = p.accent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton({ links.stayInBrowser() }) {
                Text(if (ask.stay != null) "Stay in browser" else "Cancel", color = p.text)
            }
        },
    )
}
