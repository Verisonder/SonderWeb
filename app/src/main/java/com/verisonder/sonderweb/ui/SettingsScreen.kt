package com.verisonder.sonderweb.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material3.AlertDialog
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Home
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.shadow
import kotlin.math.roundToInt
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sonderweb.BuildConfig
import com.verisonder.sonderweb.browser.Browser
import com.verisonder.sonderweb.browser.Ublock
import com.verisonder.sonderweb.core.SearchEngines
import com.verisonder.sonderweb.data.Place
import com.verisonder.sonderweb.data.Weather
import kotlinx.coroutines.delay
import org.mozilla.geckoview.StorageController

/** Settings pages, Brave-style: a grouped list on the first page, details one tap away. */
private enum class Page(val title: String) {
    Privacy("Privacy & security"),
    Shields("Extensions"),
    Start("Start page"),
    Search("Search engine"),
    Look("Appearance"),
    Tabs("Tabs"),
    About("About SonderWeb"),
}

/** Every setting by name, so the search box can find it. */
private val index: List<Pair<String, Page>> = listOf(
    "Strict tracking protection" to Page.Privacy,
    "HTTPS only" to Page.Privacy,
    "Fingerprinting" to Page.Privacy,
    "DuckDuckGo look" to Page.Search,
    "Secure DNS" to Page.Privacy,
    "DNS over HTTPS" to Page.Privacy,
    "Keep history" to Page.Privacy,
    "Private tabs" to Page.Privacy,
    "Lock private tabs" to Page.Privacy,
    "Screenshots" to Page.Privacy,
    "Clear browsing data" to Page.Privacy,
    "Cookies" to Page.Privacy,
    "uBlock Origin filters" to Page.Shields,
    "Ad blocker" to Page.Shields,
    "Extensions" to Page.Shields,
    "Add-ons" to Page.Shields,
    "Your name" to Page.Start,
    "Weather" to Page.Start,
    "City" to Page.Start,
    "Clock" to Page.Start,
    "Greeting" to Page.Start,
    "Shortcuts" to Page.Start,
    "Shortcut names" to Page.Start,
    "Search engine" to Page.Search,
    "!bangs" to Page.Search,
    "Theme" to Page.Look,
    "Dark mode" to Page.Look,
    "Address bar position" to Page.Look,
    "Floating address bar" to Page.Look,
    "Hide address bar" to Page.Look,
    "Address bar size" to Page.Look,
    "Button size" to Page.Look,
    "Button position" to Page.Look,
    "Update" to Page.About,
    "Version" to Page.About,
    "Status bar" to Page.Look,
    "Reopen tabs" to Page.Tabs,
    "Clock position" to Page.Start,
    "Start page layout" to Page.Start,
    "Start page background" to Page.Start,
    "Wallpaper" to Page.Start,
    "Open last site" to Page.Tabs,
    "Start page when opening the app" to Page.Tabs,
    "Version" to Page.About,
    "Licences" to Page.About,
)

@Composable
fun SettingsScreen(ui: UiState) {
    val p = LocalPalette.current
    var page by remember { mutableStateOf<Page?>(null) }
    var query by remember { mutableStateOf("") }
    var clearing by remember { mutableStateOf(false) }
    val close = { ui.panel = Panel.None }

    BackHandler(enabled = page != null) { page = null }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButtonSmall(Icons.AutoMirrored.Outlined.ArrowBack, "Back", size = 48) {
                if (page != null) page = null else close()
            }
            Spacer(Modifier.width(8.dp))
            Text(
                page?.title ?: "Settings",
                color = p.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButtonSmall(Icons.Outlined.Close, "Close", size = 48, onClick = close)
        }

        AnimatedContent(
            targetState = page,
            transitionSpec = {
                // Deeper: new page comes in from the right; back: the list returns from the left.
                val forward = targetState != null
                val dir = if (forward) 1 else -1
                (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 500f)) { it * dir / 4 } + fadeIn(tween(180)))
                    .togetherWith(slideOutHorizontally(tween(200)) { -it * dir / 6 } + fadeOut(tween(120)))
            },
            label = "settings",
        ) { shown ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // The list runs on under the gesture bar and ends just above it.
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
        ) {
            when (shown) {
                null -> MainPage(query, { query = it }, onOpen = { page = it }, onClear = { clearing = true })
                Page.Privacy -> PrivacyPage { clearing = true }
                Page.Shields -> ShieldsPage(ui)
                Page.Start -> StartSettings(ui)
                Page.Search -> SearchPage()
                Page.Look -> LookPage(ui)
                Page.Tabs -> TabsPage()
                Page.About -> AboutPage(ui)
            }
        }
        }
    }

    if (clearing) ClearDataDialog { clearing = false }
}

// ---- First page ----

@Composable
private fun MainPage(
    query: String,
    onQuery: (String) -> Unit,
    onOpen: (Page) -> Unit,
    onClear: () -> Unit,
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(p.field)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = p.mute, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search settings", color = p.faint, fontSize = 16.sp)
            BasicTextField(
                query,
                onQuery,
                singleLine = true,
                textStyle = TextStyle(fontFamily = Inter, color = p.text, fontSize = 16.sp),
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) IconButtonSmall(Icons.Outlined.Close, "Clear", size = 32) { onQuery("") }
    }

    if (query.isNotBlank()) {
        val q = query.trim().lowercase()
        val hits = index.filter { (name, pg) -> name.lowercase().contains(q) || pg.title.lowercase().contains(q) }
        GroupTitle("Results")
        if (hits.isEmpty()) {
            Hint("Nothing matches.")
        } else {
            Group {
                hits.forEachIndexed { i, (name, pg) ->
                    NavRow(iconFor(pg), name, pg.title, first = i == 0, last = i == hits.lastIndex) { onOpen(pg) }
                }
            }
        }
        return
    }

    GroupTitle("Features")
    Group {
        NavRow(Icons.Outlined.Security, "Privacy & security", null, first = true) { onOpen(Page.Privacy) }
        NavRow(
            Icons.Outlined.Extension,
            "Extensions",
            ublockStatus(),
        ) { onOpen(Page.Shields) }
        NavRow(Icons.Outlined.Dashboard, "Start page", null, last = true) { onOpen(Page.Start) }
    }

    GroupTitle("General")
    Group {
        NavRow(Icons.Outlined.Search, "Search engine", Browser.prefs.engine().name, first = true) { onOpen(Page.Search) }
        NavRow(Icons.Outlined.Palette, "Appearance", null) { onOpen(Page.Look) }
        NavRow(Icons.Outlined.Tab, "Tabs", null) { onOpen(Page.Tabs) }
        val isDefault = (ctx as? MainActivity)?.defaultBrowser() == true
        NavRow(
            Icons.AutoMirrored.Outlined.OpenInNew,
            "Default browser",
            if (isDefault) "SonderWeb opens links from other apps" else "Open links from other apps here",
        ) {
            Browser.host?.askDefaultBrowser()
        }
        NavRow(Icons.Outlined.DeleteSweep, "Clear browsing data", null, last = true, onClick = onClear)
    }

    GroupTitle("About")
    Group {
        NavRow(
            Icons.Outlined.Info,
            "About SonderWeb",
            com.verisonder.sonderweb.data.AppUpdate.available?.let { "Version ${it.version} is ready to install" } ?: ("Version " + BuildConfig.VERSION_NAME),
            first = true,
            last = true,
            dot = com.verisonder.sonderweb.data.AppUpdate.available != null,
        ) {
            onOpen(Page.About)
        }
    }
}

private fun ublockStatus(): String = when {
    Ublock.extension != null -> com.verisonder.sonderweb.browser.Extensions.list.size.let { n ->
        if (n > 1) "uBlock Origin and ${n - 1} more" else "uBlock Origin " + BuildConfig.UBLOCK_VERSION
    }
    Ublock.error != null -> "Couldn't start: " + Ublock.error
    else -> "Starting…"
}

private fun iconFor(page: Page): ImageVector = when (page) {
    Page.Privacy -> Icons.Outlined.Security
    Page.Shields -> Icons.Outlined.Extension
    Page.Start -> Icons.Outlined.Dashboard
    Page.Search -> Icons.Outlined.Search
    Page.Look -> Icons.Outlined.Palette
    Page.Tabs -> Icons.Outlined.Tab
    Page.About -> Icons.Outlined.Info
}

// ---- Pages ----

@Composable
private fun PrivacyPage(onClear: () -> Unit) {
    val prefs = Browser.prefs
    var strict by remember { mutableStateOf(prefs.strictTracking) }
    var httpsOnly by remember { mutableStateOf(prefs.httpsOnly) }
    var history by remember { mutableStateOf(prefs.saveHistory) }
    var fingerprinting by remember { mutableStateOf(prefs.blockFingerprinting) }
    var dns by remember { mutableStateOf(prefs.secureDns) }
    var lock by remember { mutableStateOf(prefs.privateLock) }
    var closeOnLeave by remember { mutableStateOf(prefs.privateCloseOnLeave) }
    var screenshots by remember { mutableStateOf(prefs.privateScreenshots) }
    Spacer(Modifier.height(8.dp))
    Group {
        Toggle("Strict tracking protection", "Firefox's own blocking on top of uBlock Origin. Turn off if a site breaks.", strict) {
            strict = it
            prefs.strictTracking = it
            Browser.applySettings()
        }
        Toggle("Block fingerprinting", "Sites get slightly randomised device details, so they can't recognise you without cookies.", fingerprinting) {
            fingerprinting = it
            prefs.blockFingerprinting = it
            Browser.applySettings()
        }
        Toggle("HTTPS only", "Never load a site over an unencrypted connection. You're asked before any exception.", httpsOnly) {
            httpsOnly = it
            prefs.httpsOnly = it
            Browser.applySettings()
        }
        Toggle("Keep history", "Off: pages you visit aren't remembered. Private tabs never are.", history) {
            history = it
            prefs.saveHistory = it
        }
    }
    GroupTitle("Secure DNS")
    Group {
        Browser.dnsProviders.forEachIndexed { i, (name, _) ->
            Choice(
                name,
                dns == i,
                if (i == 0) "Use the phone's DNS (keeps a VPN or DNS ad blocker working). Private tabs still use Quad9." else "Encrypted: your network can't see which sites you look up",
            ) {
                dns = i
                prefs.secureDns = i
                Browser.applySettings()
            }
        }
    }
    GroupTitle("Private tabs")
    Group {
        Toggle("Lock private tabs", "Ask for your fingerprint or screen lock every time you open private tabs or come back to the app.", lock) {
            lock = it
            prefs.privateLock = it
            if (!it) Browser.privateLocked = false
        }
        Toggle("Close private tabs when you leave", "Everything from them is gone the moment you switch apps or lock the phone.", closeOnLeave) {
            closeOnLeave = it
            prefs.privateCloseOnLeave = it
        }
        Toggle("Allow screenshots", "Off: no screenshots, screen recording or recent-apps preview of private tabs.", screenshots) {
            screenshots = it
            prefs.privateScreenshots = it
            Browser.privateScreenshots = it
        }
    }
    Hint("Always on in private tabs: nothing saved to history, no cookies or site data kept once they close, HTTPS only, the keyboard doesn't learn what you type, and no tab previews.")
    Spacer(Modifier.height(12.dp))
    Group { Action("Clear browsing data", "History, cookies, site data, cache", onClick = onClear) }
    Hint("Always on: ads, trackers and malware blocked by uBlock Origin; cookies kept apart per site and trackers' cookies purged; bounce trackers cleared; tracking parameters removed from links; sites can't probe your home network; logins never saved; sites told not to sell your data (Global Privacy Control). Nothing is ever sent to us: no telemetry, no accounts.")
}

@Composable
private fun ShieldsPage(ui: UiState) {
    Spacer(Modifier.height(8.dp))
    Group {
        Action("Manage extensions", ublockStatus()) { ui.panel = Panel.Extensions }
        Action("Get more extensions", "From addons.mozilla.org") {
            ui.panel = Panel.None
            Browser.newTab(com.verisonder.sonderweb.browser.Extensions.GET_MORE, isPrivate = false)
        }
    }
    Hint("Tap the shield in the address bar to see what uBlock Origin blocked on a page, or to turn it off for that site.")
}

@Composable
private fun SearchPage() {
    val p = LocalPalette.current
    val prefs = Browser.prefs
    var engine by remember { mutableStateOf(prefs.searchEngineId) }
    var custom by remember { mutableStateOf(prefs.customSearch) }
    Spacer(Modifier.height(8.dp))
    Group {
        SearchEngines.builtIn.forEach { e ->
            Choice(e.name, engine == e.id) {
                engine = e.id
                prefs.searchEngineId = e.id
            }
        }
        Choice("Custom", engine == SearchEngines.CUSTOM_ID) {
            engine = SearchEngines.CUSTOM_ID
            prefs.searchEngineId = SearchEngines.CUSTOM_ID
        }
    }
    if (engine == SearchEngines.CUSTOM_ID) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            SettingsField(custom, "https://example.com/search?q={q}") {
                custom = it
                prefs.customSearch = it
            }
            Text(
                if (SearchEngines.isValidTemplate(custom)) "Your search goes where {q} is." else "Needs to start with https:// and contain {q}. Until then DuckDuckGo is used.",
                color = if (SearchEngines.isValidTemplate(custom)) p.faint else p.danger,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            )
        }
    }
    Hint("Type !w, !yt, !gh … before or after your words to search a site directly. Unknown bangs go through DuckDuckGo.")
    var clean by remember { mutableStateOf(prefs.cleanDdg) }
    Spacer(Modifier.height(12.dp))
    Group {
        Toggle("Clean DuckDuckGo look", "DuckDuckGo in the browser's own colours and font, without ads, logo, menus or extra buttons.", clean) {
            clean = it
            prefs.cleanDdg = it
            com.verisonder.sonderweb.browser.Look.apply(it)
        }
    }
}

@Composable
private fun LookPage(ui: UiState) {
    val p = LocalPalette.current
    val prefs = Browser.prefs
    var theme by remember { mutableStateOf(prefs.theme) }
    var barTop by remember { mutableStateOf(prefs.barOnTop) }
    var barHide by remember { mutableStateOf(prefs.barHide) }
    var floating by remember { mutableStateOf(prefs.barFloating) }
    var status by remember { mutableStateOf(prefs.showStatusBar) }
    val changed = {
        Browser.applySettings()
        ui.themeVersion++
    }

    GroupTitle("Theme")
    Group {
        Choice("Follow the system", theme == 0) { theme = 0; prefs.theme = 0; changed() }
        Choice("Light", theme == 1) { theme = 1; prefs.theme = 1; changed() }
        Choice("Dark", theme == 2) { theme = 2; prefs.theme = 2; changed() }
    }

    GroupTitle("Address bar")
    Group {
        Row(
            Modifier.fillMaxWidth().background(p.field).heightIn(min = 56.dp).padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Position", color = p.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Segmented(listOf("Top", "Bottom"), if (barTop) 0 else 1, p.accent) {
                barTop = it == 0
                prefs.barOnTop = barTop
                changed()
            }
        }
        Row(
            Modifier.fillMaxWidth().background(p.field).heightIn(min = 56.dp).padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Style", color = p.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Segmented(listOf("Floating", "Docked"), if (floating) 0 else 1, p.accent) {
                floating = it == 0
                prefs.barFloating = floating
                changed()
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Group {
        Choice("Shrink to a small button", barHide == 2, "Tap it, or scroll to the top of the page, to bring the bar back") {
            barHide = 2; prefs.barHide = 2; changed()
        }
        Choice("Slide away while scrolling", barHide == 1, "Scroll up to bring it back. Swipe it down or sideways to keep it hidden until you tap it") {
            barHide = 1; prefs.barHide = 1; changed()
        }
        Choice("Always shown", barHide == 0) { barHide = 0; prefs.barHide = 0; changed() }
    }

    GroupTitle("Size")
    Group { BarSizes(ui) }

    GroupTitle("Advanced")
    Group { ButtonPlace(ui) }

    GroupTitle("Screen")
    Group {
        Toggle("Phone status bar", "Time and battery at the top. Off: pages use the whole screen; swipe down to peek.", status) {
            status = it
            prefs.showStatusBar = it
            Browser.host?.applySystemBars()
            changed()
        }
    }
}

/** Bar and button size, each with a live preview that resizes as you slide. */
@Composable
private fun BarSizes(ui: UiState) {
    val p = LocalPalette.current
    val prefs = Browser.prefs
    var bar by remember { mutableStateOf(prefs.barSize.toFloat()) }
    var button by remember { mutableStateOf(prefs.buttonSize.toFloat()) }
    val colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.line)

    Column(Modifier.fillMaxWidth().background(p.field).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Address bar", color = p.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("${bar.roundToInt()}%", color = p.faint, fontSize = 14.sp)
        }
        Box(Modifier.fillMaxWidth().height(84.dp), contentAlignment = Alignment.Center) {
            Scaled(bar / 100f) { BarPreview() }
        }
        Slider(
            value = bar,
            onValueChange = { bar = it },
            valueRange = 80f..130f,
            steps = 9,
            onValueChangeFinished = {
                prefs.barSize = bar.roundToInt()
                ui.themeVersion++
            },
            colors = colors,
        )
    }
    Column(Modifier.fillMaxWidth().background(p.field).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Small button", color = p.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("${button.roundToInt()} dp", color = p.faint, fontSize = 14.sp)
        }
        Box(Modifier.fillMaxWidth().height(84.dp), contentAlignment = Alignment.Center) {
            BarButton(null, button.roundToInt(), Modifier) {}
        }
        Slider(
            value = button,
            onValueChange = { button = it },
            valueRange = 36f..64f,
            steps = 13,
            onValueChangeFinished = {
                prefs.buttonSize = button.roundToInt()
                ui.themeVersion++
            },
            colors = colors,
        )
    }
}

/** Advanced: put the small button anywhere, with a little phone showing where. */
@Composable
private fun ButtonPlace(ui: UiState) {
    val p = LocalPalette.current
    val prefs = Browser.prefs
    var on by remember { mutableStateOf(prefs.buttonFree) }
    var x by remember { mutableStateOf(prefs.buttonX) }
    var y by remember { mutableStateOf(prefs.buttonY) }
    val colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.line)
    val save: () -> Unit = {
        prefs.buttonX = x
        prefs.buttonY = y
        ui.themeVersion++
    }

    Toggle("Custom button position", "Place the small button anywhere on the screen instead of a corner, with the sliders below.", on) {
        on = it
        prefs.buttonFree = it
        ui.themeVersion++
    }
    AnimatedVisibility(on) {
        Row(
            Modifier.fillMaxWidth().background(p.field).padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Left – right  ${(x * 100).roundToInt()}%", color = p.mute, fontSize = 14.sp)
                Slider(x, { x = it }, onValueChangeFinished = save, colors = colors)
                Spacer(Modifier.height(4.dp))
                Text("Top – bottom  ${(y * 100).roundToInt()}%", color = p.mute, fontSize = 14.sp)
                Slider(y, { y = it }, onValueChangeFinished = save, colors = colors)
            }
            Spacer(Modifier.width(18.dp))
            // The phone, with the button where it will be.
            Box(
                Modifier
                    .size(width = 64.dp, height = 132.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(2.dp, p.line, RoundedCornerShape(14.dp))
                    .background(p.bg)
                    .padding(7.dp),
            ) {
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                    val dot = 14.dp
                    Box(
                        Modifier
                            .offset((maxWidth - dot) * x, (maxHeight - dot) * y)
                            .size(dot)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(p.accent),
                    )
                }
            }
        }
    }
}

/** A stand-in for the floating address bar, drawn with the same sizes. */
@Composable
private fun BarPreview() {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp, shape)
            .clip(shape)
            .background(p.card)
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Home, null, tint = p.mute, modifier = Modifier.size(22.dp))
        }
        Row(
            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(p.field).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Lock, null, tint = p.faint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text("example.com", color = p.text, fontSize = 15.sp, maxLines = 1)
        }
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(20.dp).border(2.dp, p.mute, RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("3", color = p.mute, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.MoreVert, null, tint = p.mute, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun TabsPage() {
    val prefs = Browser.prefs
    var restore by remember { mutableStateOf(prefs.restoreTabs) }
    Spacer(Modifier.height(8.dp))
    Group {
        Toggle("Reopen tabs", "Bring back your normal tabs when the app starts. Private tabs never come back.", restore) {
            restore = it
            prefs.restoreTabs = it
        }
    }
    OpenOnLaunch()
}

/** Moves part of the start page up or down (5 dp steps); saved when you let go. */
@Composable
private fun LiftSlider(title: String, saved: Float, onDone: (Float) -> Unit) {
    val p = LocalPalette.current
    var v by remember(saved) { mutableStateOf(saved) }
    Column(Modifier.fillMaxWidth().background(p.field).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = p.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            val n = v.roundToInt()
            Text(
                when {
                    n > 0 -> "$n dp higher"
                    n < 0 -> "${-n} dp lower"
                    else -> "Usual place"
                },
                color = p.faint,
                fontSize = 14.sp,
            )
        }
        Slider(
            value = v,
            onValueChange = { v = it },
            valueRange = -120f..200f,
            steps = 63,
            onValueChangeFinished = { onDone(v) },
            colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.line),
        )
    }
}

/** Colours to pick from for the start page (dark ones first, then light ones). */
private val startColors = listOf(
    0xFF000000, 0xFF0F172A, 0xFF1E293B, 0xFF14281D, 0xFF0B2545, 0xFF2A1736, 0xFF3B0D1A,
    0xFFF3EBDD, 0xFFE8EEF4, 0xFFE3F2EA, 0xFFF8E5E8, 0xFFFFFFFF,
).map { it.toInt() }

/** Start page › Background: the theme's colour, a colour of your choice, or a wallpaper. */
@Composable
private fun StartBackgroundSettings(ui: UiState) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val prefs = Browser.prefs
    var color by remember { mutableStateOf(prefs.startBgColor) }
    var wallpaper by remember { mutableStateOf(prefs.startWallpaper) }
    var dim by remember { mutableStateOf(prefs.startDim.toFloat()) }
    var busy by remember { mutableStateOf(false) }

    fun pickColor(c: Int) {
        color = c
        prefs.startBgColor = c
        wallpaper = false
        prefs.startWallpaper = false
        ui.themeVersion++
    }

    GroupTitle("Background")
    Group {
        Row(
            Modifier
                .fillMaxWidth()
                .background(p.field)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The theme's own colour first.
            Swatch(p.bg, selected = color == 0 && !wallpaper, label = "Theme") { pickColor(0) }
            startColors.forEach { c ->
                Swatch(Color(c), selected = color == c && !wallpaper, label = null) { pickColor(c) }
            }
        }
        Action(
            if (wallpaper) "Change wallpaper" else "Choose a wallpaper",
            if (busy) "Adding the picture…" else "A picture from your phone",
        ) {
            Browser.host?.pickFiles(arrayOf("image/*"), multiple = false) { uris ->
                val uri = uris.firstOrNull() ?: return@pickFiles
                busy = true
                com.verisonder.sonderweb.data.StartBackground.set(ctx, uri) { ok ->
                    busy = false
                    if (ok) {
                        wallpaper = true
                        prefs.startWallpaper = true
                        ui.themeVersion++
                    } else {
                        Browser.host?.toast("Couldn't use that picture")
                    }
                }
            }
        }
        if (wallpaper) {
            Column(Modifier.fillMaxWidth().background(p.field).padding(horizontal = 18.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Darken", color = p.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text("${dim.roundToInt()}%", color = p.faint, fontSize = 14.sp)
                }
                Slider(
                    value = dim,
                    onValueChange = { dim = it },
                    valueRange = 0f..80f,
                    steps = 15,
                    onValueChangeFinished = {
                        prefs.startDim = dim.roundToInt()
                        ui.themeVersion++
                    },
                    colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.line),
                )
            }
            Action("Remove wallpaper", "Back to a plain colour") {
                com.verisonder.sonderweb.data.StartBackground.clear(ctx)
                wallpaper = false
                prefs.startWallpaper = false
                ui.themeVersion++
            }
        }
    }
}

/** A round colour to tap; the chosen one has a ring. */
@Composable
private fun Swatch(c: Color, selected: Boolean, label: String?, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(if (selected) 2.5.dp else 1.dp, if (selected) p.accent else p.line, CircleShape)
                .padding(if (selected) 4.dp else 0.dp)
                .clip(CircleShape)
                .background(c)
                .clickable(onClick = onClick),
        )
        if (label != null) {
            Spacer(Modifier.height(4.dp))
            Text(label, color = p.faint, fontSize = 11.sp)
        }
    }
}

/** Minutes the timer can be set to, from a minute to a day. */
private val awaySteps = listOf(1, 5, 15, 30, 60, 120, 240, 480, 720, 1440)

private fun awayLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes < 1440 -> "${minutes / 60} h"
    else -> "1 day"
}

/** What opening the app shows: the last site, the start page, or the start page after a while away. */
@Composable
private fun OpenOnLaunch() {
    val p = LocalPalette.current
    val prefs = Browser.prefs
    var mode by remember { mutableStateOf(prefs.openOnLaunch) }
    var step by remember {
        mutableStateOf(awaySteps.indexOfFirst { it >= prefs.startPageAfter }.let { if (it < 0) awaySteps.lastIndex else it }.toFloat())
    }
    val minutes = awaySteps[step.roundToInt().coerceIn(0, awaySteps.lastIndex)]
    fun pick(m: Int) {
        mode = m
        prefs.openOnLaunch = m
    }
    GroupTitle("When you open the app")
    Group {
        Choice("Last site", mode == 0, "Carry on where you left off") { pick(0) }
        Choice("Start page", mode == 1, "When you open the app after closing it") { pick(1) }
        Choice("Start page after a while", mode == 2, "Also when you come back after ${awayLabel(minutes)} away") { pick(2) }
        if (mode == 2) {
            Column(Modifier.fillMaxWidth().background(p.field).padding(horizontal = 18.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Away for", color = p.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text(awayLabel(minutes), color = p.faint, fontSize = 14.sp)
                }
                Slider(
                    value = step,
                    onValueChange = { step = it },
                    valueRange = 0f..awaySteps.lastIndex.toFloat(),
                    steps = awaySteps.size - 2,
                    onValueChangeFinished = { prefs.startPageAfter = minutes },
                    colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.line),
                )
            }
        }
    }
    Hint("Your tabs stay open either way.")
}

@Composable
private fun AboutPage(ui: UiState) {
    Spacer(Modifier.height(8.dp))
    Group { UpdateRow() }
    Spacer(Modifier.height(12.dp))
    Group {
        Action("SonderWeb " + BuildConfig.VERSION_NAME, "by Verisonder · Apache License 2.0") {}
        Action("Source code", "github.com/Verisonder/SonderWeb") {
            ui.panel = Panel.None
            Browser.newTab("https://github.com/Verisonder/SonderWeb", isPrivate = false)
        }
    }
    Hint(
        "Engine: GeckoView by Mozilla (Mozilla Public License 2.0)\n" +
            "uBlock Origin by Raymond Hill (GPL-3.0), included unmodified\n" +
            "Inter typeface (SIL Open Font License 1.1)\n" +
            "No accounts, no telemetry, no tracking.",
    )
}

/** Updates: check, see what's new, install in one tap. */
@Composable
private fun UpdateRow() {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val up = com.verisonder.sonderweb.data.AppUpdate
    val info = up.available
    val progress = up.progress
    var checking by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().background(p.field).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (info != null) "Version ${info.version} is ready" else "Updates",
                    color = p.text,
                    fontSize = 16.sp,
                )
                Text(
                    when {
                        progress != null -> "Downloading… ${(progress * 100).roundToInt()}%"
                        info != null -> "You have " + BuildConfig.VERSION_NAME + (if (info.size > 0) " · ${info.size / 1_000_000} MB" else "")
                        else -> note ?: ("You have " + BuildConfig.VERSION_NAME)
                    },
                    color = p.faint,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            val label = when {
                progress != null -> null
                info != null -> "Update"
                checking -> "Checking…"
                else -> "Check"
            }
            if (label != null) {
                Text(
                    label,
                    color = if (info != null) p.onAccent else p.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (info != null) p.accent else p.line)
                        .clickable(enabled = !checking) {
                            scope.launch {
                                if (info != null) {
                                    runCatching { up.install(ctx, info) }
                                        .onFailure { note = "Couldn't download: " + (it.message ?: "network error") }
                                } else {
                                    checking = true
                                    note = runCatching { up.check(ctx) }.fold(
                                        { if (it == null) "You're on the latest version" else null },
                                        { "Couldn't check: " + (it.message ?: "network error") },
                                    )
                                    checking = false
                                }
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                )
            }
        }
        if (progress != null) {
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                color = p.accent,
                trackColor = p.line,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)),
            )
        }
        if (info != null && info.notes.isNotBlank() && progress == null) {
            Spacer(Modifier.height(10.dp))
            Text(info.notes.trim().take(600), color = p.mute, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

/** Start page settings (also reachable from the start page itself). */
@Composable
private fun StartSettings(ui: UiState) {
    val p = LocalPalette.current
    val prefs = Browser.prefs
    var name by remember { mutableStateOf(prefs.userName) }
    var clock by remember { mutableStateOf(prefs.showClock) }
    var lift by remember { mutableStateOf(prefs.clockLift.toFloat()) }
    var rest by remember { mutableStateOf(prefs.restLift.toFloat()) }
    var greeting by remember { mutableStateOf(prefs.showGreeting) }
    var names by remember { mutableStateOf(prefs.showShortcutNames) }
    var weatherOn by remember { mutableStateOf(prefs.weatherOn) }
    var fahrenheit by remember { mutableStateOf(prefs.fahrenheit) }
    var place by remember { mutableStateOf(prefs.place) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    GroupTitle("Show")
    Group {
        Toggle("Clock and date", null, clock) {
            clock = it
            prefs.showClock = it
            ui.themeVersion++
        }
        Toggle("Greeting", null, greeting) {
            greeting = it
            prefs.showGreeting = it
            ui.themeVersion++
        }
        Toggle("Shortcut names", "The site's name under each shortcut", names) {
            names = it
            prefs.showShortcutNames = it
            ui.themeVersion++
        }
        Toggle("Weather", "From Open-Meteo. Only the city you pick is sent.", weatherOn) {
            weatherOn = it
            prefs.weatherOn = it
            if (!it) Weather.forget()
            ui.themeVersion++
        }
    }

    StartBackgroundSettings(ui)

    GroupTitle("Layout")
    Group {
        if (clock) {
            LiftSlider("Time and date", lift) { v ->
                lift = v
                prefs.clockLift = v.roundToInt()
                ui.themeVersion++
            }
        }
        LiftSlider("Greeting, weather, search and shortcuts", rest) { v ->
            rest = v
            prefs.restLift = v.roundToInt()
            ui.themeVersion++
        }
    }

    if (greeting) {
        GroupTitle("Your name")
        Column(Modifier.padding(horizontal = 16.dp)) {
            SettingsField(name, "Shown in the greeting") {
                name = it
                prefs.userName = it
                ui.themeVersion++
            }
        }
    }

    if (weatherOn) {
        GroupTitle("Weather")
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                place?.name ?: "No city yet",
                color = if (place == null) p.faint else p.text,
                fontSize = 15.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
            SettingsField(query, "Search a city") { query = it }
            LaunchedEffect(query) {
                if (query.trim().length < 2) {
                    results = emptyList()
                    return@LaunchedEffect
                }
                delay(350)
                searching = true
                results = Weather.search(query)
                searching = false
            }
            if (searching) Text("Searching…", color = p.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
            results.forEach { r ->
                Text(
                    r.name,
                    color = p.text,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            place = r
                            prefs.place = r
                            query = ""
                            results = emptyList()
                            Weather.refresh(prefs, force = true)
                            ui.themeVersion++
                        }
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Segmented(listOf("°C", "°F"), if (fahrenheit) 1 else 0, p.accent) {
                fahrenheit = it == 1
                prefs.fahrenheit = fahrenheit
                Weather.refresh(prefs, force = true)
                ui.themeVersion++
            }
        }
    }
    Hint("Add shortcuts with the + on the start page. Long-press one to edit or remove it.")
}

// ---- Building blocks ----

@Composable
private fun GroupTitle(title: String) {
    val p = LocalPalette.current
    Text(
        title,
        color = p.mute,
        fontSize = 15.sp,
        modifier = Modifier.padding(start = 32.dp, end = 20.dp, top = 24.dp, bottom = 10.dp),
    )
}

/** A rounded card holding a few rows, separated by thin gaps like Brave's settings. */
@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    sub: String?,
    first: Boolean = false,
    last: Boolean = false,
    dot: Boolean = false,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (first) 18.dp else 4.dp, if (first) 18.dp else 4.dp, if (last) 18.dp else 4.dp, if (last) 18.dp else 4.dp))
            .background(p.field)
            .clickable(onClick = onClick)
            .heightIn(min = 68.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = p.text, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 17.sp)
            if (!sub.isNullOrBlank()) Text(sub, color = if (dot) p.accent else p.faint, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (dot) UpdateDot()
    }
}

@Composable
private fun Hint(text: String) {
    val p = LocalPalette.current
    Text(text, color = p.faint, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(start = 32.dp, end = 24.dp, top = 12.dp))
}

@Composable
private fun Choice(label: String, on: Boolean, sub: String? = null, onClick: () -> Unit) {
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
            Text(label, color = p.text, fontSize = 16.sp)
            if (sub != null) Text(sub, color = p.faint, fontSize = 13.sp)
        }
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (on) p.accent else p.line),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Box(Modifier.size(9.dp).clip(CircleShape).background(p.onAccent))
        }
    }
}

@Composable
private fun Toggle(title: String, sub: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.field)
            .clickable { onChange(!on) }
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 16.sp)
            if (sub != null) {
                Spacer(Modifier.height(2.dp))
                Text(sub, color = p.faint, fontSize = 13.sp, lineHeight = 17.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.onAccent))
    }
}

@Composable
private fun Action(title: String, sub: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(p.field)
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(title, color = p.text, fontSize = 16.sp)
        Spacer(Modifier.height(2.dp))
        Text(sub, color = p.faint, fontSize = 13.sp)
    }
}

@Composable
private fun SettingsField(value: String, placeholder: String, onChange: (String) -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(p.field)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, color = p.faint, fontSize = 15.sp)
        BasicTextField(
            value,
            onChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = Inter, color = p.text, fontSize = 15.sp),
            cursorBrush = SolidColor(p.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ClearDataDialog(onDone: () -> Unit) {
    val p = LocalPalette.current
    var hist by remember { mutableStateOf(true) }
    var cookies by remember { mutableStateOf(true) }
    var cache by remember { mutableStateOf(true) }
    var downloads by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = p.card,
        title = { Text("Clear browsing data", color = p.text) },
        text = {
            Column {
                Check("History", hist) { hist = it }
                Check("Cookies and site data (signs you out)", cookies) { cookies = it }
                Check("Cached images and files", cache) { cache = it }
                Check("Downloads list (files stay)", downloads) { downloads = it }
            }
        },
        confirmButton = {
            TextButton({
                if (hist) Browser.db.clearHistory()
                if (downloads) Browser.db.clearDownloads()
                var flags = 0L
                if (cookies) {
                    flags = flags or StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.DOM_STORAGES or
                        StorageController.ClearFlags.AUTH_SESSIONS
                }
                if (cache) flags = flags or StorageController.ClearFlags.ALL_CACHES
                if (flags != 0L) {
                    Browser.clearData(flags) { Browser.host?.toast("Cleared") }
                } else {
                    Browser.host?.toast("Cleared")
                }
                onDone()
            }) { Text("Clear", color = p.danger) }
        },
        dismissButton = { TextButton(onDone) { Text("Cancel", color = p.text) } },
    )
}

@Composable
private fun Check(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(on, onChange, colors = CheckboxDefaults.colors(checkedColor = p.accent))
        Text(label, color = p.text, fontSize = 14.sp)
    }
}
