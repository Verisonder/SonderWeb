package com.verisonder.sonderweb.browser

import android.app.ActivityManager
import android.content.Context
import android.os.Process

/**
 * A page that starts loading and then never moves: its process was frozen by the phone while you
 * were away (screen off, another app) and doesn't answer any more, so a refresh goes nowhere.
 * Watched just after coming back to the app and after any refresh. Still stuck after a while:
 * the page gets a fresh start; stuck again: the frozen page processes are ended, the engine
 * starts new ones and the page loads in them.
 */
object Unstick {
    /** After coming back to the app, loads are watched this long. */
    private const val WINDOW = 60_000L

    /** How long a load may sit at its very start before it counts as stuck. */
    private const val WAIT = 10_000L

    /** Below this, a load hasn't really got going. */
    private const val STARTED = 15

    @Volatile
    private var backAt = 0L

    /** Back in the app. */
    fun back() {
        backAt = System.currentTimeMillis()
    }

    /**
     * You refreshed: the load that follows is watched, even if a frozen page never even reports
     * starting (it shows as loading from now on).
     */
    fun refreshed(tab: Tab) {
        tab.watchRefresh = true
        tab.watchStage = 0
        loadingNow(tab)
    }

    /** Shows the tab as loading and checks on it after a while. */
    private fun loadingNow(tab: Tab) {
        tab.loading = true
        tab.progress = 5
        schedule(tab)
    }

    private fun schedule(tab: Tab) {
        tab.watchMoved = false
        val token = ++tab.watchToken
        Browser.main.postDelayed({ check(tab, token) }, WAIT)
    }

    /** A page started loading. */
    fun started(tab: Tab) {
        val watched = tab.watchStage > 0 || tab.watchRefresh || System.currentTimeMillis() - backAt < WINDOW
        if (!watched || tab.watchStage >= 2) return
        schedule(tab)
    }

    /** The load got going: it isn't frozen. */
    fun progressed(tab: Tab, progress: Int) {
        if (progress >= STARTED) tab.watchMoved = true
    }

    /**
     * A load finished. Only a page that loaded counts as all well: a load that was stopped (a
     * refresh stops the one before it) says nothing about the next.
     */
    fun stopped(tab: Tab, success: Boolean) {
        if (!success) return
        tab.watchToken++
        tab.watchStage = 0
        tab.watchRefresh = false
    }

    private fun check(tab: Tab, token: Int) {
        if (token != tab.watchToken || tab.watchMoved) return
        if (Browser.current !== tab || tab !in Browser.tabs) return
        when (tab.watchStage) {
            0 -> {
                // A fresh start for the page (that loads it again, and is watched too).
                tab.watchStage = 1
                Delegates.recover(tab)
                loadingNow(tab)
            }
            1 -> {
                // Still nothing: its process is frozen. Ending it makes the engine start new ones;
                // the page (and any other tab that was in them) loads again by itself.
                tab.watchStage = 2
                endPageProcesses()
            }
        }
    }

    /** Ends the engine's page and drawing processes (never the app itself). */
    private fun endPageProcesses() {
        val am = Browser.app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val me = Process.myPid()
        val uid = Process.myUid()
        runCatching { am.runningAppProcesses }.getOrNull().orEmpty()
            .filter { p ->
                // The engine names them ":sandboxed_tab_…<n>" and ":gpu_…".
                val part = p.processName.substringAfter(':', "")
                p.pid != me && p.uid == uid && (part.contains("tab") || part.startsWith("gpu"))
            }
            .forEach { runCatching { Process.killProcess(it.pid) } }
    }
}
