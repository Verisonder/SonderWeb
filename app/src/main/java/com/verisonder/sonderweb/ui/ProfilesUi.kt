package com.verisonder.sonderweb.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sonderweb.browser.Browser
import com.verisonder.sonderweb.browser.Tab
import com.verisonder.sonderweb.data.Profile
import com.verisonder.sonderweb.data.Profiles

/** A profile's picture, or its first letter on its colour. */
@Composable
fun ProfileAvatar(profile: Profile, size: Dp, modifier: Modifier = Modifier) {
    val version = Profiles.pictures
    val pic = remember(profile.id, version) { Profiles.picture(profile.id)?.asImageBitmap() }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(profile.color)),
        contentAlignment = Alignment.Center,
    ) {
        if (pic != null) {
            Image(pic, profile.name, Modifier.size(size), contentScale = ContentScale.Crop)
        } else {
            Text(
                profile.name.trim().take(1).uppercase().ifEmpty { "?" },
                color = Color.White,
                fontSize = (size.value * 0.46f).sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** In private tabs, in place of the profile's picture: a hat and glasses on the private purple. */
@Composable
fun PrivateAvatar(size: Dp, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val ink = Color(0xFF1B1530)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(p.privateAccent),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(size * 0.66f)) {
            val u = this.size.width / 24f
            // The hat: a crown narrowing to the top, and a brim.
            val crown = androidx.compose.ui.graphics.Path().apply {
                moveTo(7.6f * u, 3.6f * u)
                lineTo(16.4f * u, 3.6f * u)
                lineTo(18.2f * u, 9.8f * u)
                lineTo(5.8f * u, 9.8f * u)
                close()
            }
            drawPath(crown, ink)
            drawRoundRect(
                ink,
                topLeft = androidx.compose.ui.geometry.Offset(2.2f * u, 9.6f * u),
                size = androidx.compose.ui.geometry.Size(19.6f * u, 2.2f * u),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.1f * u),
            )
            // The glasses: two round lenses and a bridge.
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.9f * u)
            drawCircle(ink, radius = 3.1f * u, center = androidx.compose.ui.geometry.Offset(7.4f * u, 16.8f * u), style = stroke)
            drawCircle(ink, radius = 3.1f * u, center = androidx.compose.ui.geometry.Offset(16.6f * u, 16.8f * u), style = stroke)
            drawLine(
                ink,
                androidx.compose.ui.geometry.Offset(10.4f * u, 16.2f * u),
                androidx.compose.ui.geometry.Offset(13.6f * u, 16.2f * u),
                strokeWidth = 1.9f * u,
            )
        }
    }
}

/** In the bar, where Home was: tap for the start page, hold to switch profiles. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileHomeButton(ui: UiState, tab: Tab) {
    val haptics = LocalHapticFeedback.current
    val p = LocalPalette.current
    // A grey ring in the address field's colour, as tall as the field (40 dp), so the two line up.
    Box(
        Modifier
            .size(44.dp)
            .padding(2.dp)
            .clip(CircleShape)
            .background(p.field)
            .combinedClickable(
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    ui.profiles = true
                },
                onClick = { Browser.goHome(tab) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (tab.isPrivate) PrivateAvatar(30.dp) else ProfileAvatar(Profiles.active, 30.dp)
    }
}

/** The start page's profile button, bottom left: tap to switch profiles. */
@Composable
fun StartProfileButton(ui: UiState, modifier: Modifier) {
    val p = LocalPalette.current
    Box(
        modifier
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(p.field)
            .clickable { ui.profiles = true }
            .size(44.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (Browser.current?.isPrivate == true) PrivateAvatar(32.dp) else ProfileAvatar(Profiles.active, 32.dp)
    }
}

/** The profile list that pops out of the profile button: pick one, add one, or manage them. */
@Composable
fun ProfileSwitcher(ui: UiState, top: Boolean) {
    val p = LocalPalette.current
    val state = remember { MutableTransitionState(false) }
    state.targetState = ui.profiles
    var adding by remember { mutableStateOf(false) }
    BackHandler(enabled = ui.profiles) { ui.profiles = false }

    if (state.currentState || state.targetState) {
        val origin = TransformOrigin(0f, if (top) 0f else 1f)
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(state, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable(interactionSource = null, indication = null) { ui.profiles = false },
                )
            }
            AnimatedVisibility(
                state,
                modifier = Modifier
                    .align(if (top) Alignment.TopStart else Alignment.BottomStart)
                    .then(if (top) Modifier.statusBarsPadding() else Modifier.navigationBarsPadding())
                    .padding(start = 0.dp, top = if (top) 56.dp else 0.dp, bottom = if (top) 0.dp else 56.dp),
                enter = fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = 600f), initialScale = 0.85f, transformOrigin = origin),
                exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.9f, transformOrigin = origin),
            ) {
                // The margin sits inside the animated layer so the shadow is never cut off.
                val shape = RoundedCornerShape(22.dp)
                Column(
                    Modifier
                        .padding(16.dp)
                        .width(264.dp)
                        .shadow(16.dp, shape)
                        .clip(shape)
                        .background(p.card)
                        .padding(vertical = 8.dp),
                ) {
                    Profiles.list.forEach { pr ->
                        val mine = pr.id == Profiles.current
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ui.profiles = false
                                    Browser.switchProfile(pr.id)
                                }
                                .heightIn(min = 52.dp)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProfileAvatar(pr, 32.dp)
                            Spacer(Modifier.width(14.dp))
                            Text(
                                pr.name,
                                color = p.text,
                                fontSize = 16.sp,
                                fontWeight = if (mine) FontWeight.Medium else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (mine) Icon(Icons.Outlined.Check, "In use", tint = p.accent, modifier = Modifier.size(20.dp))
                        }
                    }
                    Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(1.dp).background(p.line))
                    SwitcherAction(Icons.Outlined.Add, "Add profile") {
                        ui.profiles = false
                        adding = true
                    }
                    SwitcherAction(Icons.Outlined.ManageAccounts, "Manage profiles") {
                        ui.profiles = false
                        ui.settingsPage = "Profiles"
                        ui.panel = Panel.Settings
                    }
                }
            }
        }
    }
    if (adding) {
        ProfileDialog(null) { made ->
            adding = false
            if (made != null) Browser.switchProfile(made.id)
        }
    }
}

@Composable
private fun SwitcherAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = p.mute, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, color = p.text, fontSize = 15.sp)
    }
}

/**
 * Adds a profile ([existing] null) or edits one: name, colour, picture; a profile other than
 * the first can be deleted. [onDone] gets the new or edited profile, or null.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileDialog(existing: Profile?, onDone: (Profile?) -> Unit) {
    val p = LocalPalette.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var color by remember { mutableStateOf(existing?.color ?: Profiles.colors[Profiles.list.size % Profiles.colors.size]) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val id = existing?.id
    val version = Profiles.pictures
    val hasPicture = remember(id, version) { id != null && Profiles.hasOwnPicture(id) }

    AlertDialog(
        onDismissRequest = { onDone(null) },
        containerColor = p.card,
        title = { Text(if (existing == null) "Add profile" else "Edit profile", color = p.text, fontSize = 18.sp) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(Profile(id ?: "#new", name.ifBlank { "?" }, color), 56.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        TextButton({
                            Browser.host?.pickFiles(arrayOf("image/*"), false) { uris ->
                                val uri = uris.firstOrNull() ?: return@pickFiles
                                if (id != null) Profiles.setPicture(id, uri) {} else picked = uri
                            }
                        }) { Text(if (hasPicture || picked != null) "Change picture" else "Choose picture", color = p.accent) }
                        if (hasPicture) {
                            TextButton({ Profiles.removePicture(id!!) }) { Text("Remove picture", color = p.mute) }
                        } else if (picked != null) {
                            Text("Picture chosen", color = p.faint, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(32) },
                    singleLine = true,
                    placeholder = { Text("Name", color = p.faint) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = p.text,
                        unfocusedTextColor = p.text,
                        focusedBorderColor = p.accent,
                        unfocusedBorderColor = p.line,
                        cursorColor = p.accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Profiles.colors.forEach { c -> Swatch(Color(c), c == color, null) { color = c } }
                }
                if (Browser.prefs.profileGooglePictures && !hasPicture && picked == null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Without a picture of its own, the profile shows the picture of the Google account signed in on it.",
                        color = p.faint,
                        fontSize = 13.sp,
                    )
                }
                if (existing != null && existing.id.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    TextButton({ deleting = true }) { Text("Delete profile", color = p.danger) }
                }
            }
        },
        confirmButton = {
            TextButton({
                if (existing == null) {
                    val made = Profiles.add(name, color)
                    picked?.let { Profiles.setPicture(made.id, it) {} }
                    onDone(made)
                } else {
                    Profiles.edit(existing.id, name, color)
                    onDone(Profiles.get(existing.id))
                }
            }) { Text(if (existing == null) "Add" else "Save", color = p.accent) }
        },
        dismissButton = { TextButton({ onDone(null) }) { Text("Cancel", color = p.text) } },
    )

    if (deleting && existing != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            containerColor = p.card,
            title = { Text("Delete ${existing.name}?", color = p.text, fontSize = 18.sp) },
            text = {
                Text(
                    "Its sign-ins, site data, open tabs, history, bookmarks and shortcuts are deleted from this phone.",
                    color = p.mute,
                )
            },
            confirmButton = {
                TextButton({
                    deleting = false
                    Browser.deleteProfile(existing.id)
                    onDone(null)
                }) { Text("Delete", color = p.danger) }
            },
            dismissButton = { TextButton({ deleting = false }) { Text("Cancel", color = p.text) } },
        )
    }
}

/** Settings › Profiles: each profile (tap to edit) and Add profile. */
@Composable
internal fun ProfilesPage() {
    val p = LocalPalette.current
    var editing by remember { mutableStateOf<Profile?>(null) }
    var adding by remember { mutableStateOf(false) }
    Group {
        Toggle(
            "Use profiles",
            "Separate sign-ins (and tabs, history, bookmarks) for different accounts",
            Profiles.enabled,
        ) { Browser.turnProfiles(it) }
    }
    if (!Profiles.enabled) {
        Hint(
            "Off, SonderWeb works as one browser, as always. On, a profile picture replaces Home in the bar and sits " +
                "bottom left on the start page: switch profiles from there. Turning it off goes back to the first " +
                "profile; the others are kept for next time.",
        )
        return
    }
    GroupTitle("Your profiles")
    Group {
        Profiles.list.forEach { pr ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(p.field)
                    .clickable { editing = pr }
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileAvatar(pr, 40.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(pr.name, color = p.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (pr.id == Profiles.current) Text("In use", color = p.faint, fontSize = 13.sp)
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(p.field)
                .clickable { adding = true }
                .heightIn(min = 56.dp)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Add, null, tint = p.mute, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Text("Add profile", color = p.text, fontSize = 16.sp)
        }
    }
    Hint("Switch by holding the profile picture in the bar, or tapping it on the start page.")

    val prefs = Browser.prefs
    var google by remember { mutableStateOf(prefs.profileGooglePictures) }
    Group {
        Toggle(
            "Google account pictures",
            "A profile without its own picture shows the picture of the Google account signed in on it",
            google,
        ) {
            Profiles.changeGooglePictures(it)
            google = it
        }
    }
    var tabs by remember { mutableStateOf(prefs.splitTabs) }
    var history by remember { mutableStateOf(prefs.splitHistory) }
    var bookmarks by remember { mutableStateOf(prefs.splitBookmarks) }
    var shortcuts by remember { mutableStateOf(prefs.splitShortcuts) }
    GroupTitle("Kept separate in each profile")
    Group {
        Toggle(
            "Open tabs",
            if (tabs) "Switching shows that profile's own tabs" else "One list of tabs; each tab keeps its own profile's sign-ins",
            tabs,
        ) {
            Browser.changeSplitTabs(it)
            tabs = it
            Profiles.changed()
        }
        Toggle("History", if (history) null else "Every profile sees the first profile's history", history) {
            prefs.splitHistory = it
            history = it
            Profiles.changed()
        }
        Toggle("Bookmarks", if (bookmarks) null else "Every profile sees the first profile's bookmarks", bookmarks) {
            prefs.splitBookmarks = it
            bookmarks = it
            Profiles.changed()
        }
        Toggle("Start page shortcuts", if (shortcuts) null else "Every profile has the same shortcuts", shortcuts) {
            prefs.splitShortcuts = it
            shortcuts = it
            Profiles.changed()
        }
    }
    Hint(
        "Sign-ins, cookies and site data are always separate. Turning something back to separate brings back what each " +
            "profile had. Extensions, settings and downloads are shared by all profiles.",
    )
    editing?.let { pr -> ProfileDialog(pr) { editing = null } }
    if (adding) ProfileDialog(null) { adding = false }
}
