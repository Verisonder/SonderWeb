package com.verisonder.sonderweb.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.Box
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
    var editing by mutableStateOf(false)
    var query by mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(""))
    var finding by mutableStateOf(false)
    var findQuery by mutableStateOf("")
    var findResult by mutableStateOf("")
    var themeVersion by mutableStateOf(0)
}

@Composable
fun BrowserScreen(activity: MainActivity) {
    val ui = remember { UiState() }
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
    val pinned = ui.editing || ui.menu || ui.finding || tab == null || activity.inFullscreen ||
        hideMode == Bar.ALWAYS || (hideMode == Bar.SLIDE && tab.isBlank)
    val collapsed = Bar.hidden && !pinned
    val barOffset by animateFloatAsState(
        targetValue = if (collapsed) barPx.toFloat() else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f),
        label = "bar",
    )
    // 0 = full bar, 1 = small button (button mode only).
    val morph by animateFloatAsState(
        targetValue = if (collapsed && hideMode == Bar.BUTTON) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
        label = "morph",
    )
    val visibleBarDp = with(LocalDensity.current) { (barPx - barOffset).coerceAtLeast(0f).toDp() }
    LaunchedEffect(barPx, activity.inFullscreen) {
        activity.geckoView.setDynamicToolbarMaxHeight(if (activity.inFullscreen) 0 else barPx)
    }
    SideEffect {
        activity.geckoView.setVerticalClipping(if (activity.inFullscreen) 0 else -barOffset.roundToInt())
    }

    BackHandler(enabled = true) {
        when {
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
            tab != null && tab.canGoBack -> tab.session.goBack()
            tab != null && tab.openerId != null -> Browser.close(tab)
            else -> activity.moveTaskToBack(true)
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
                    .then(if (activity.inFullscreen) Modifier else Modifier.windowInsetsPadding(screenInsets).navigationBarsPadding())
                    .imePadding()
                    .clipToBounds(),
            ) {
                // The page fills everything; the bar floats over its edge and slides away while
                // you scroll down. The engine is told how much of the page the bar covers.
                // Top bar: the page sits just under the visible part of the bar (its bottom
                // runs off-screen by the same amount, which the engine accounts for).
                val barSpace = if (barTop) PaddingValues(top = visibleBarDp) else PaddingValues(bottom = visibleBarDp)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(palette.bg),
                ) {
                    AndroidView(
                        factory = { activity.geckoView },
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (barTop && !activity.inFullscreen) {
                                    Modifier.offset { IntOffset(0, (barPx - barOffset).roundToInt()) }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                    AnimatedVisibility(
                        visible = tab != null && tab.isBlank,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(140)),
                    ) {
                        Box(Modifier.fillMaxSize().padding(barSpace)) { StartPage(ui) }
                    }
                    AnimatedVisibility(
                        visible = ui.editing && tab != null,
                        enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { if (barTop) -it / 24 else it / 24 },
                        exit = fadeOut(tween(120)),
                    ) {
                        tab?.let { Box(Modifier.fillMaxSize().padding(barSpace)) { Suggestions(ui, it, barTop) } }
                    }
                }
                if (!activity.inFullscreen) {
                    if (hideMode == Bar.BUTTON) {
                        // The bar shrinks towards its left corner while the button grows out of it.
                        if (morph < 1f) {
                            Box(
                                Modifier
                                    .align(if (barTop) Alignment.TopCenter else Alignment.BottomCenter)
                                    .onSizeChanged { barPx = it.height }
                                    .graphicsLayer {
                                        transformOrigin = TransformOrigin(0.06f, if (barTop) 0f else 1f)
                                        scaleX = 1f - 0.82f * morph
                                        scaleY = 1f - 0.3f * morph
                                        alpha = (1f - morph * 1.6f).coerceIn(0f, 1f)
                                    },
                            ) {
                                AddressBar(ui, tab, barTop)
                            }
                        }
                        if (morph > 0f) {
                            BarButton(
                                tab,
                                Modifier
                                    .align(if (barTop) Alignment.TopStart else Alignment.BottomStart)
                                    .padding(14.dp)
                                    .graphicsLayer {
                                        val k = 0.6f + 0.4f * morph
                                        scaleX = k
                                        scaleY = k
                                        alpha = ((morph - 0.3f) / 0.7f).coerceIn(0f, 1f)
                                    },
                            ) { Bar.expand() }
                        }
                    } else {
                        Box(
                            Modifier
                                .align(if (barTop) Alignment.TopCenter else Alignment.BottomCenter)
                                .onSizeChanged { barPx = it.height }
                                .graphicsLayer { translationY = if (barTop) -barOffset else barOffset },
                        ) {
                            AddressBar(ui, tab, barTop)
                        }
                    }
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
                        .navigationBarsPadding(),
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
        }
    }
}

/** The small round button the bar shrinks into. Tap it to bring the bar back. */
@Composable
private fun BarButton(tab: com.verisonder.sonderweb.browser.Tab?, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier
            .size(46.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(p.field)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Mark(26, p.text, if (tab?.isPrivate == true) p.privateAccent else p.accent)
        if (tab != null && tab.loading) {
            CircularProgressIndicator(
                progress = { tab.progress / 100f },
                modifier = Modifier.size(46.dp),
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
