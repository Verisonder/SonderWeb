package com.verisonder.sonderweb.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.verisonder.sonderweb.browser.Bar
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Refresh
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.verisonder.sonderweb.browser.Browser
import com.verisonder.sonderweb.browser.ContextMenu
import com.verisonder.sonderweb.browser.Delegates
import com.verisonder.sonderweb.browser.Prompts
import com.verisonder.sonderweb.browser.Ublock

enum class Panel { None, Tabs, History, Bookmarks, Downloads, Settings, Extensions }

/** Screen state that isn't the browser's own. */
class UiState {
    var panel by mutableStateOf(Panel.None)
    var menu by mutableStateOf(false)

    /** "Shred site data" asked for this site, waiting for you to confirm. */
    var shred by mutableStateOf<String?>(null)

    /** The page's picture going through the shredder, and its site. */
    var shredding by mutableStateOf<Pair<android.graphics.Bitmap, String>?>(null)
    var editing by mutableStateOf(false)
    var query by mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(""))
    var finding by mutableStateOf(false)
    var findQuery by mutableStateOf("")
    var findResult by mutableStateOf("")
    var themeVersion by mutableStateOf(0)

    /** The tab switcher is showing the Private list. */
    var tabsPrivate by mutableStateOf(false)
}

@Composable
fun BrowserScreen(activity: MainActivity) {
    val ui = remember { UiState() }
    // Leaving the app closes the ⋮ menu, so it isn't still open (and in the recents picture) on return.
    val owner = activity
    androidx.compose.runtime.DisposableEffect(owner) {
        val watch = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) ui.menu = false
        }
        owner.lifecycle.addObserver(watch)
        onDispose { owner.lifecycle.removeObserver(watch) }
    }
    // A download notification was tapped: its page.
    LaunchedEffect(com.verisonder.sonderweb.browser.Downloads.showPage) {
        if (com.verisonder.sonderweb.browser.Downloads.showPage) {
            com.verisonder.sonderweb.browser.Downloads.showPage = false
            ui.panel = Panel.Downloads
        }
    }
    val tab = Browser.current
    // Reading themeVersion makes a theme change in Settings recompose this screen.
    val theme = ui.themeVersion.let { Browser.prefs.theme }
    val dark = isDark(theme)
    val base = if (dark) DarkPalette else LightPalette
    val palette = if (tab?.isPrivate == true) PrivatePalette else base

    // Status/navigation bar icons follow the bar colour.
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !palette.dark
            isAppearanceLightNavigationBars = !palette.dark
        }
    }

    // Private tabs never end up in screenshots, screen recordings or the recent-apps preview.
    val secure = !Browser.privateScreenshots &&
        (tab?.isPrivate == true || (ui.panel == Panel.Tabs && ui.tabsPrivate && Browser.privateTabsOpen()))
    LaunchedEffect(secure) { activity.setSecure(secure) }

    // Show the current tab in the engine view.
    LaunchedEffect(tab, tab?.ready) {
        val t = tab ?: return@LaunchedEffect
        val gv = activity.geckoView
        if (t.ready && gv.session !== t.session) {
            gv.session?.let { old -> Browser.tabs.firstOrNull { it.session === old }?.let { Delegates.capture(it) } }
            gv.releaseSession()
            gv.setSession(t.session)
        }
    }

    // ---- The bar: always there, sliding away, or shrunk into a small button ----
    val barTop = remember(ui.themeVersion) { Browser.prefs.barOnTop }
    val hideMode = remember(ui.themeVersion) { Browser.prefs.barHide }
    LaunchedEffect(hideMode) { Bar.setMode(hideMode) }
    // On the start page the bar starts as the small button (button mode).
    LaunchedEffect(tab, tab?.isBlank) { if (tab?.isBlank == true) Bar.collapse() }
    var barPx by remember { mutableIntStateOf(0) }
    // The start page shows no bar: only the tabs button and the menu (the bar comes back to type).
    val startPage = tab?.isBlank == true && !ui.editing && !ui.finding
    val pinned = ui.editing || ui.menu || ui.finding || tab == null || activity.inFullscreen ||
        hideMode == Bar.ALWAYS || (hideMode == Bar.SLIDE && tab.isBlank)
    val collapsed = Bar.hidden && !pinned
    // Bar swiped down: the phone's gesture bar hides with it (not over the start page or a panel).
    val hideNavigation = Bar.down && collapsed && !startPage && ui.panel == Panel.None
    SideEffect { activity.hideNavigation = hideNavigation }
    // The animated values are read only while laying out and drawing (never while composing),
    // so each frame of the bar's movement just moves pixels instead of rebuilding the screen.
    val barOffsetState = animateFloatAsState(
        targetValue = if (collapsed) barPx.toFloat() else 0f,
        // Button mode: same pace as the morph so the page and the bar move as one, no bounce on the page.
        animationSpec = if (hideMode == Bar.BUTTON) spring(dampingRatio = 1f, stiffness = 380f) else spring(dampingRatio = 0.9f, stiffness = 500f),
        label = "bar",
    )
    // 0 = full bar, 1 = small button (button mode only).
    val morphState = animateFloatAsState(
        targetValue = if (collapsed && hideMode == Bar.BUTTON) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
        label = "morph",
    )

    // Top: the page keeps clear of the camera hole; that thin strip takes the colour of the page's
    // top edge. Bottom: the page runs on under the gesture bar, one continuous surface, while the
    // engine is told that strip is covered, so buttons pinned to the bottom of a page sit above it.
    val showStatus = remember(ui.themeVersion) { Browser.prefs.showStatusBar }
    val density = LocalDensity.current
    val safeTop = if (activity.inFullscreen || showStatus) 0 else WindowInsets.displayCutout.getTop(density)
    val safeBottom = if (activity.inFullscreen) 0 else WindowInsets.navigationBars.getBottom(density)
    // The bar already covers the strip on its side: only the rest of it lies over the page.
    val overPage = (barPx - if (barTop) safeTop else safeBottom).coerceAtLeast(0)
    val overOffset = { barOffsetState.value.coerceIn(0f, overPage.toFloat()) }
    // The strip by the camera hole shows only at the top of a page: once you scroll, the page
    // glides up and fills the screen to the very top.
    val stripState = animateFloatAsState(
        targetValue = if (tab?.atTop != false) safeTop.toFloat() else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 500f),
        label = "strip",
    )
    // How far below the screen's top the page starts (not counting a top bar). A top bar that is
    // showing still needs the strip under it, so it only goes as the bar slides away.
    val pageTop = {
        if (barTop && overPage > 0) {
            maxOf(stripState.value, safeTop * (1f - overOffset() / overPage))
        } else {
            stripState.value
        }
    }
    LaunchedEffect(overPage, safeBottom, safeTop, activity.inFullscreen) {
        // The page is as tall as the screen; whatever it is pushed down by runs off the bottom,
        // so the engine counts that as covered too (fixed bottom elements stay in sight).
        activity.geckoView.setDynamicToolbarMaxHeight(if (activity.inFullscreen) 0 else overPage + safeBottom + safeTop)
        com.verisonder.sonderweb.browser.Delegates.coveredBottom = if (activity.inFullscreen) 0 else overPage + safeBottom + safeTop
        // Follows the bar and the strip frame by frame without recomposing anything.
        androidx.compose.runtime.snapshotFlow {
            if (activity.inFullscreen) 0 else -(overOffset() + safeTop - pageTop()).roundToInt()
        }.collect { activity.geckoView.setVerticalClipping(it) }
    }
    // While typing the bar is always fully shown, so suggestions sit right under (or over) it.
    val barDp = with(density) { barPx.toDp() }
    val edgeTop by animateColorAsState(tab?.edgeTop?.let { Color(it) } ?: palette.bg, tween(220), label = "edgeTop")

    BackHandler(enabled = true) {
        when {
            Browser.privateLocked && tab?.isPrivate == true -> leavePrivate(ui)
            activity.inFullscreen -> tab?.session?.exitFullScreen()
            Ublock.popup != null -> Ublock.closePopup()
            ContextMenu.request != null -> ContextMenu.dismiss()
            ui.menu -> ui.menu = false
            ui.panel != Panel.None -> ui.panel = Panel.None
            ui.editing -> ui.editing = false
            ui.finding -> {
                tab?.session?.finder?.clear()
                ui.finding = false
            }
            // On the start page, back leaves the app.
            tab == null || tab.isBlank -> activity.moveTaskToBack(true)
            tab.canGoBack -> tab.session.goBack()
            // Nothing left to go back to on this site: the start page (back again leaves the app).
            else -> Browser.home(tab)
        }
    }

    // Top padding: the status bar when it's shown, else only the camera cut-out.
    val screenInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top)

    SonderTheme(palette) {
        Box(
            Modifier
                .fillMaxSize()
                .background(palette.bar),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    // Pages run edge to edge: under the camera hole and the gesture bar. Only the
                    // status bar, when you choose to show it, takes space at the top.
                    .then(if (activity.inFullscreen || !showStatus) Modifier else Modifier.statusBarsPadding())
                    .imePadding()
                    .clipToBounds(),
            ) {
                // The page fills everything; the bar floats over its edge and slides away while
                // you scroll down. The engine is told how much of the page the bar covers.
                // Top bar: the page sits just under the visible part of the bar (its bottom
                // runs off-screen by the same amount, which the engine accounts for).
                val barSpace = if (barTop) PaddingValues(top = barDp) else PaddingValues(bottom = barDp)
                // Behind the page: its own background colour, so the space around the floating bar
                // (where the engine leaves room for it) looks like part of the page.
                val pageBg by animateColorAsState(
                    tab?.edgeBottom?.takeIf { tab.isBlank.not() }?.let { Color(it) } ?: palette.bg,
                    tween(220),
                    label = "pageBg",
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(pageBg),
                ) {
                    Box(Modifier.fillMaxWidth().height(with(density) { safeTop.toDp() }).background(edgeTop).align(Alignment.TopCenter))
                    AndroidView(
                        factory = { activity.geckoView },
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (activity.inFullscreen) {
                                    Modifier
                                } else {
                                    Modifier.offset {
                                        IntOffset(0, (pageTop() + if (barTop) overPage - overOffset() else 0f).roundToInt())
                                    }
                                },
                            ),
                    )
                    if (tab != null && !tab.isBlank && !activity.inFullscreen) {
                        PullIndicator(tab) { (pageTop() + if (barTop) overPage - overOffset() else 0f).roundToInt() }
                    }
                    // Opening the start page covers the engine view at once, so the previous page
                    // never shows through while the start page fades in.
                    if (tab != null && tab.isBlank) Box(Modifier.fillMaxSize().background(palette.bg))
                    // Until the page has drawn: its own colours and a small loading mark (no white flash).
                    LoadingCover(
                        tab = tab,
                        visible = tab != null && tab.covered && !tab.isBlank && !activity.inFullscreen,
                        safeTop = safeTop,
                    )
                    AnimatedVisibility(
                        visible = tab != null && tab.isBlank,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(140)),
                    ) {
                        Box(Modifier.fillMaxSize()) { StartPage(ui) }
                    }
                    AnimatedVisibility(
                        visible = ui.editing && tab != null,
                        enter = fadeIn(tween(160)),
                        exit = fadeOut(tween(120)),
                    ) {
                        // Typing: an opaque sheet over the whole page (also behind and around the bar), so no
                        // part of the page peeks through above the keyboard.
                        tab?.let {
                            Box(Modifier.fillMaxSize().background(palette.bg)) {
                                Box(Modifier.fillMaxSize().padding(barSpace)) { Suggestions(ui, it, barTop) }
                            }
                        }
                    }
                }
                // The (+) in the middle: search or type an address.
                AnimatedVisibility(
                    visible = startPage && !activity.inFullscreen,
                    enter = fadeIn(tween(180)) + scaleIn(spring(dampingRatio = 0.75f, stiffness = 500f), initialScale = 0.85f),
                    exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.9f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(2.dp),
                ) {
                    tab?.let { Scaled(remember(ui.themeVersion) { Browser.prefs.barSize / 100f }) { StartSearchButton(ui, it, Modifier.padding(12.dp)) } }
                }
                AnimatedVisibility(
                    visible = startPage && !activity.inFullscreen,
                    enter = fadeIn(tween(180)) + scaleIn(spring(dampingRatio = 0.75f, stiffness = 500f), initialScale = 0.85f),
                    exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.9f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(2.dp),
                ) {
                    // The margin sits inside the animated layer so the shadow is never cut off.
                    tab?.let { Scaled(remember(ui.themeVersion) { Browser.prefs.barSize / 100f }) { StartControls(ui, it, Modifier.padding(12.dp)) } }
                }
                AnimatedVisibility(
                    visible = !startPage && !activity.inFullscreen,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(120)),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(Modifier.fillMaxSize()) { BarArea(ui, tab, barTop, hideMode, { morphState.value }, { barOffsetState.value }) { barPx = it } }
                }
            }

            SnackbarHost(
                activity.snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = if (barTop) 16.dp else 72.dp),
            ) { data ->
                Snackbar(
                    data,
                    containerColor = palette.field,
                    contentColor = palette.text,
                    actionColor = palette.accent,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                )
            }

            // Settings scrolls on under the gesture bar (nothing looks cut off at the bottom);
            // remembered so it doesn't jump while the panel closes.
            val lastPanel = remember { arrayOf(Panel.None) }
            if (ui.panel != Panel.None) lastPanel[0] = ui.panel
            val panelUnderGestureBar = lastPanel[0] == Panel.Settings
            AnimatedVisibility(
                visible = ui.panel != Panel.None,
                enter = fadeIn(tween(200)) + slideInVertically(spring(dampingRatio = 0.9f, stiffness = 600f)) { it / 14 },
                exit = fadeOut(tween(160)) + slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { it / 14 },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(palette.bg)
                        .windowInsetsPadding(screenInsets)
                        .then(if (panelUnderGestureBar) Modifier else Modifier.navigationBarsPadding()),
                ) {
                    when (ui.panel) {
                        Panel.Tabs -> TabsScreen(ui)
                        Panel.History -> HistoryScreen(ui)
                        Panel.Bookmarks -> BookmarksScreen(ui)
                        Panel.Downloads -> DownloadsScreen(ui)
                        Panel.Settings -> SettingsScreen(ui)
                        Panel.Extensions -> ExtensionsScreen(ui)
                        Panel.None -> Unit
                    }
                }
            }

            ContextMenu.request?.let { ContextSheet(it) }
            Ublock.popup?.let { UblockSheet(it) }
            Prompts.queue.firstOrNull { it.tab === tab }?.let { PromptDialog(it) }
            com.verisonder.sonderweb.browser.Extensions.pending?.let { InstallDialog(it) }
            com.verisonder.sonderweb.browser.AppLinks.ask?.let { OpenAppDialog(it) }
            ui.shred?.let { site ->
                ConfirmDialog(
                    "Shred $site?",
                    "Its tabs close and its cookies and site data are deleted. History and bookmarks stay.",
                    "Shred",
                    { ui.shred = null },
                ) {
                    ui.shred = null
                    // A picture of the page goes through the shredder first (if it's the one on screen).
                    val t = Browser.current
                    val done = {
                        Browser.shred(site)
                        activity.toast("$site shredded")
                    }
                    if (t != null && !t.isPrivate && Browser.siteOf(t.url) == site) {
                        runCatching {
                            activity.geckoView.capturePixels().accept({ bmp ->
                                Browser.main.post { if (bmp != null) ui.shredding = bmp to site else done() }
                            }, { _ -> Browser.main.post { done() } })
                        }.onFailure { done() }
                    } else {
                        done()
                    }
                }
            }
            ui.shredding?.let { (bmp, site) ->
                ShredAnimation(bmp) {
                    ui.shredding = null
                    Browser.shred(site)
                    activity.toast("$site shredded")
                }
            }

            // Back from another app: the private tab stays hidden until you unlock it.
            AnimatedVisibility(
                visible = Browser.privateLocked && tab?.isPrivate == true,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(220)),
            ) {
                PrivateLock(onLeave = { leavePrivate(ui) })
            }
        }
    }
}

/** The address bar in its current form: full, sliding away, or shrunk into the small button. */
@Composable
private fun BoxScope.BarArea(
    ui: UiState,
    tab: com.verisonder.sonderweb.browser.Tab?,
    barTop: Boolean,
    hideMode: Int,
    morph: () -> Float,
    barOffset: () -> Float,
    onBarSize: (Int) -> Unit,
) {
    val barScale = remember(ui.themeVersion) { Browser.prefs.barSize / 100f }
    if (hideMode == Bar.BUTTON) {
        val saved = remember(ui.themeVersion) { Browser.prefs.buttonCorner }
        var corner by remember(saved, barTop) { mutableStateOf(if (saved in 0..3) saved else if (barTop) 1 else 3) }
        val free = remember(ui.themeVersion) { Browser.prefs.buttonFree }
        var spot by remember(ui.themeVersion) { mutableStateOf(androidx.compose.ui.geometry.Offset(Browser.prefs.buttonX, Browser.prefs.buttonY)) }
        val right = if (free) spot.x > 0.5f else corner == 1 || corner == 3
        // The bar shrinks towards the button's corner while the button grows out of it.
        // Only these two yes/no answers recompose; the in-between frames are drawn directly.
        val barShown by remember { androidx.compose.runtime.derivedStateOf { morph() < 1f } }
        val buttonShown by remember { androidx.compose.runtime.derivedStateOf { morph() > 0f } }
        if (barShown) {
            Box(
                Modifier
                    .align(if (barTop) Alignment.TopCenter else Alignment.BottomCenter)
                    .onSizeChanged { onBarSize(it.height) }
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(if (right) 0.94f else 0.06f, if (barTop) 0f else 1f)
                        val m = morph()
                        // Fade each shape directly (no offscreen layer) so shadows aren't clipped mid-animation.
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                        scaleX = 1f - 0.82f * m
                        scaleY = 1f - 0.3f * m
                        alpha = (1f - m * 1.6f).coerceIn(0f, 1f)
                    },
            ) {
                SwipeToCompact(!ui.editing && !ui.finding && hideMode != Bar.ALWAYS) { Scaled(barScale) { AddressBar(ui, tab, barTop) } }
            }
        }
        if (buttonShown) {
            DraggableBarButton(
                tab,
                remember(ui.themeVersion) { Browser.prefs.buttonSize },
                if (free) FREE else corner,
                spot,
                morph,
                onCorner = { corner = it; Browser.prefs.buttonCorner = it },
                onSpot = {
                    spot = it
                    Browser.prefs.buttonX = it.x
                    Browser.prefs.buttonY = it.y
                },
            ) { Bar.expand() }
        }
    } else {
        // Bottom bar, slide mode: swipe it down and it stays down until you tap its handle.
        val canPutDown = !barTop && hideMode == Bar.SLIDE && !ui.editing && !ui.finding
        val pull = remember { androidx.compose.animation.core.Animatable(0f) }
        Box(
            Modifier
                .align(if (barTop) Alignment.TopCenter else Alignment.BottomCenter)
                .onSizeChanged { onBarSize(it.height) }
                .graphicsLayer { translationY = if (barTop) -barOffset() else barOffset() + pull.value },
        ) {
            SwipeDownToHide(canPutDown, pull) {
                SwipeToCompact(!ui.editing && !ui.finding && hideMode != Bar.ALWAYS) { Scaled(barScale) { AddressBar(ui, tab, barTop) } }
            }
        }
        DownHandle(barTop, tab)
    }
}

/**
 * Swipe the bar down (or up) to put it away at the bottom edge: it follows your finger and springs
 * back if you don't go far. Once down it stays there until you tap its handle.
 */
@Composable
private fun SwipeDownToHide(
    enabled: Boolean,
    pull: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    content: @Composable () -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 40.dp.toPx() }
    val back = spring<Float>(dampingRatio = 0.8f, stiffness = 420f)
    Box(
        Modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectVerticalDragGestures(
                onDragEnd = {
                    if (pull.value > threshold || pull.value < -threshold) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        Bar.putDown()
                    }
                    // The bar's own slide takes over from where your finger left it.
                    scope.launch { pull.animateTo(0f, back) }
                },
                onDragCancel = { scope.launch { pull.animateTo(0f, back) } },
            ) { change, amount ->
                change.consume()
                // Down follows the finger; up a little more stiffly, then it drops away the same.
                val next = pull.value + if (pull.value + amount > 0f) amount else amount * 0.6f
                scope.launch { pull.snapTo(next.coerceAtLeast(-threshold * 2.5f)) }
            }
        },
    ) { content() }
}

/** The slim handle at the bar's edge while the bar is put away. Tap it, or slide it any way
 *  (sideways, or out from the edge), to bring the bar back. */
@Composable
private fun BoxScope.DownHandle(barTop: Boolean, tab: com.verisonder.sonderweb.browser.Tab?) {
    val shown by animateFloatAsState(if (Bar.down) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 420f), label = "downHandle")
    if (shown < 0.01f) return
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val slide = with(LocalDensity.current) { 20.dp.toPx() }
    Box(
        Modifier
            .align(if (barTop) Alignment.TopCenter else Alignment.BottomCenter)
            .then(
                if (barTop) {
                    Modifier.windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top))
                } else {
                    Modifier.navigationBarsPadding()
                },
            )
            .size(width = 140.dp, height = 30.dp)
            .graphicsLayer {
                alpha = shown
                scaleX = 0.4f + 0.6f * shown
            }
            .pointerInput(Unit) {
                var moved = androidx.compose.ui.geometry.Offset.Zero
                var done = false
                detectDragGestures(
                    onDragStart = {
                        moved = androidx.compose.ui.geometry.Offset.Zero
                        done = false
                    },
                ) { change, amount ->
                    change.consume()
                    moved += amount
                    if (!done && Bar.down && moved.getDistance() > slide) {
                        done = true
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        Bar.expand()
                    }
                }
            }
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                enabled = Bar.down,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                Bar.expand()
            },
        contentAlignment = Alignment.Center,
    ) {
        // Like the phone's own gesture line: dark on light pages, light on dark ones (it follows
        // the colour of the page's edge it sits on), with a thin opposite edge for busy pages.
        val p = LocalPalette.current
        val edge = (if (barTop) tab?.edgeTop else tab?.edgeBottom)?.let { Color(it) } ?: p.bg
        val onLight = edge.luminance() > 0.5f
        val ink by animateColorAsState(if (onLight) Color(0xE6202124) else Color(0xF2FFFFFF), tween(220), label = "handleInk")
        val rim by animateColorAsState(if (onLight) Color(0x66FFFFFF) else Color(0x59000000), tween(220), label = "handleRim")
        Box(
            Modifier
                .size(width = 48.dp, height = 6.dp)
                .shadow(3.dp, RoundedCornerShape(3.dp))
                .clip(RoundedCornerShape(3.dp))
                .background(ink)
                .border(0.5.dp, rim, RoundedCornerShape(3.dp)),
        )
    }
}

/**
 * The small bar button. Drag it anywhere: let go and it glides to the nearest corner
 * (top or bottom, left or right) and stays there.
 */
@Composable
private fun BoxScope.DraggableBarButton(
    tab: com.verisonder.sonderweb.browser.Tab?,
    buttonDp: Int,
    corner: Int,
    spot: androidx.compose.ui.geometry.Offset,
    morph: () -> Float,
    onCorner: (Int) -> Unit,
    onSpot: (androidx.compose.ui.geometry.Offset) -> Unit,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var area by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val topInset = WindowInsets.displayCutout.getTop(density)
    val bottomInset = WindowInsets.navigationBars.getBottom(density)
    val margin = with(density) { 14.dp.roundToPx() }
    val size = with(density) { buttonDp.dp.roundToPx() }
    // The free area the button can sit in (inside the margins, clear of the hole and gesture bar).
    val left = margin.toFloat()
    val top = (topInset + margin).toFloat()
    fun spanX() = (area.width - 2 * margin - size).coerceAtLeast(1).toFloat()
    fun spanY() = (area.height - topInset - bottomInset - 2 * margin - size).coerceAtLeast(1).toFloat()
    var free by remember { mutableStateOf(spot) }
    LaunchedEffect(spot) { free = spot }
    fun base(c: Int) = if (c == FREE) {
        androidx.compose.ui.geometry.Offset(left + free.x * spanX(), top + free.y * spanY())
    } else {
        androidx.compose.ui.geometry.Offset(
            if (c == 1 || c == 3) left + spanX() else left,
            if (c >= 2) top + spanY() else top,
        )
    }
    val drag = remember { androidx.compose.animation.core.Animatable(androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset.VectorConverter) }
    var held by remember { mutableStateOf(false) }
    // Long-press tucks the button away at the bottom edge until you tap its handle.
    var tucked by Bar.tuckedState
    val tuck by animateFloatAsState(if (tucked) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 420f), label = "tuck")
    val lift by animateFloatAsState(if (held) 1.1f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "lift")
    // Read while laying out, together with the drag, so a change of corner never shows a jump.
    var at by remember { mutableStateOf(corner) }
    LaunchedEffect(corner) { at = corner }

    Box(Modifier.matchParentSize().onSizeChanged { area = it })
    if (area.width == 0) return
    BarButton(
        tab,
        buttonDp,
        Modifier
            .offset {
                val b = base(at)
                IntOffset((b.x + drag.value.x).roundToInt(), (b.y + drag.value.y).roundToInt())
            }
            .pointerInput(size, margin, topInset, bottomInset) {
                detectDragGestures(
                    onDragStart = {
                        held = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = {
                        held = false
                        val c = at
                        val pos = base(c) + drag.value
                        if (c == FREE) {
                            // Custom position (set in Settings): dragging doesn't move it, it glides back.
                            scope.launch { drag.animateTo(androidx.compose.ui.geometry.Offset.Zero, spring(dampingRatio = 0.72f, stiffness = 420f)) }
                        } else {
                            val right = pos.x + size / 2f > area.width / 2f
                            val bottom = pos.y + size / 2f > area.height / 2f
                            val next = (if (bottom) 2 else 0) + (if (right) 1 else 0)
                            scope.launch {
                                // Keep it where your finger left it, then glide into the corner.
                                drag.snapTo(pos - base(next))
                                at = next
                                if (next != c) onCorner(next)
                                drag.animateTo(androidx.compose.ui.geometry.Offset.Zero, spring(dampingRatio = 0.72f, stiffness = 420f))
                            }
                        }
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onDragCancel = {
                        held = false
                        scope.launch { drag.animateTo(androidx.compose.ui.geometry.Offset.Zero, spring(dampingRatio = 0.72f, stiffness = 420f)) }
                    },
                ) { change, amount ->
                    change.consume()
                    scope.launch { drag.snapTo(drag.value + amount) }
                }
            }
            .graphicsLayer {
                val m = morph()
                val t = tuck
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                val k = (0.6f + 0.4f * m) * lift * (1f - 0.4f * t)
                scaleX = k
                scaleY = k
                // Tucked away: it drops down past the bottom edge.
                translationY = t * (area.height - base(at).y + size * 0.4f)
                alpha = ((m - 0.3f) / 0.7f).coerceIn(0f, 1f) * (1f - t)
            },
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            tucked = true
        },
        onClick = onClick,
    )

    // While tucked: a slim handle at the bottom edge, under where the button was. Tap it to bring the button back.
    if (tuck > 0.01f) {
        val handleW = with(density) { 44.dp.roundToPx() }
        val touchH = with(density) { 30.dp.roundToPx() }
        Box(
            Modifier
                .offset {
                    val b = base(at)
                    IntOffset(
                        (b.x + size / 2f - handleW / 2f).roundToInt(),
                        area.height - bottomInset - touchH,
                    )
                }
                .size(width = 44.dp, height = 30.dp)
                .graphicsLayer {
                    alpha = tuck
                    scaleX = 0.4f + 0.6f * tuck
                }
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    enabled = tucked,
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    tucked = false
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 40.dp, height = 5.dp)
                    .shadow(3.dp, RoundedCornerShape(3.dp))
                    .clip(RoundedCornerShape(3.dp))
                    .background(LocalPalette.current.text.copy(alpha = 0.7f)),
            )
        }
    }
}

/** Swipe the bar left or right to put it away; it follows your finger and springs back if you don't go far. */
@Composable
private fun SwipeToCompact(enabled: Boolean, content: @Composable () -> Unit) {
    val dx = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val back = spring<Float>(dampingRatio = 0.75f, stiffness = 420f)
    Box(
        Modifier
            .graphicsLayer {
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                translationX = dx.value
                alpha = 1f - (kotlin.math.abs(dx.value) / (threshold * 4f)).coerceIn(0f, 0.35f)
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (kotlin.math.abs(dx.value) > threshold) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            Bar.compact()
                        }
                        scope.launch { dx.animateTo(0f, back) }
                    },
                    onDragCancel = { scope.launch { dx.animateTo(0f, back) } },
                ) { change, amount ->
                    change.consume()
                    // A little resistance, so it feels attached to the screen.
                    scope.launch { dx.snapTo(dx.value + amount * 0.7f) }
                }
            },
    ) { content() }
}

/**
 * The pull-to-refresh badge: drops in from the page's top edge as you pull, the arrow turning
 * with it; past the line it fills with the accent colour, and on release it spins until the page
 * has reloaded.
 */
@Composable
private fun BoxScope.PullIndicator(tab: com.verisonder.sonderweb.browser.Tab, pageTop: () -> Int) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    val pull = com.verisonder.sonderweb.browser.PullToRefresh
    val threshold = with(density) { 72.dp.toPx() }
    LaunchedEffect(threshold) { pull.threshold = threshold }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val armed = pull.pull >= threshold
    LaunchedEffect(armed) { if (armed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    // While reloading the badge rests just below the top; otherwise it follows the finger.
    val shown by animateFloatAsState(
        targetValue = if (pull.refreshing) threshold * 0.9f else pull.pull,
        animationSpec = if (pull.pull > 0f) spring(stiffness = 4000f) else spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "pull",
    )
    // The reload is done when the page stops loading (or after a few seconds at most).
    LaunchedEffect(pull.refreshing) {
        if (!pull.refreshing) return@LaunchedEffect
        kotlinx.coroutines.delay(400)
        kotlinx.coroutines.withTimeoutOrNull(8000) {
            androidx.compose.runtime.snapshotFlow { tab.loading }.first { !it }
        }
        pull.refreshing = false
    }
    if (shown < 1f && !pull.refreshing) return
    val size = 40.dp
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .offset { IntOffset(0, pageTop() + (shown - with(density) { size.toPx() }).roundToInt()) }
            .graphicsLayer {
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                alpha = (shown / (threshold * 0.6f)).coerceIn(0f, 1f)
                val k = 0.6f + 0.4f * (shown / threshold).coerceIn(0f, 1f)
                scaleX = k
                scaleY = k
            }
            .size(size)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(if (armed || pull.refreshing) p.accent else p.field),
        contentAlignment = Alignment.Center,
    ) {
        if (pull.refreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = p.onAccent,
                strokeWidth = 2.5.dp,
            )
        } else {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Outlined.Refresh,
                "Reload",
                tint = if (armed) p.onAccent else p.text,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = 300f * (shown / threshold).coerceIn(0f, 1.2f) },
            )
        }
    }
}

/** [DraggableBarButton]'s corner value for the free (advanced) position. */
private const val FREE = 4

/** From a locked private tab to your normal tabs. */
fun leavePrivate(ui: UiState) {
    ui.panel = Panel.None
    ui.editing = false
    val normal = Browser.tabs.lastOrNull { !it.isPrivate }
    if (normal != null) Browser.select(normal) else Browser.newTab(null, isPrivate = false)
}

/** The small round button the bar shrinks into. Tap it to bring the bar back. */
@Composable
fun BarButton(
    tab: com.verisonder.sonderweb.browser.Tab?,
    sizeDp: Int,
    modifier: Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    Box(
        modifier
            .size(sizeDp.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(p.field)
            .combinedClickable(onLongClick = onLongClick, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        MarkLines((sizeDp * 0.67f).roundToInt(), p.text)
        if (tab != null && tab.loading) {
            CircularProgressIndicator(
                progress = { tab.progress / 100f },
                modifier = Modifier.size(sizeDp.dp),
                color = p.accent,
                strokeWidth = 2.dp,
                trackColor = androidx.compose.ui.graphics.Color.Transparent,
            )
        }
    }
}

/** A thin progress line at the top of the bar while a page loads. */
@Composable
fun LoadLine(progress: Int, color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.fillMaxWidth().height(2.dp)) {
        Box(
            Modifier
                .fillMaxWidth((progress.coerceIn(5, 100)) / 100f)
                .height(2.dp)
                .background(color),
        )
    }
}
