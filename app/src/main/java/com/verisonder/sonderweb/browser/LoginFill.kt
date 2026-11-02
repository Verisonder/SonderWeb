package com.verisonder.sonderweb.browser

import android.graphics.Rect
import android.view.autofill.AutofillManager
import org.mozilla.geckoview.Autofill
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * Sits in front of the engine view's own autofill hookup: everything is passed through, and when
 * a sign-in field (email, username or password) gets focus, the phone's password manager
 * (Proton Pass, Bitwarden…) is asked to show its suggestions for it.
 */
class LoginFill(private val view: GeckoView, val inner: Autofill.Delegate) : Autofill.Delegate {

    override fun onSessionStart(session: GeckoSession) = inner.onSessionStart(session)

    override fun onSessionCommit(session: GeckoSession, node: Autofill.Node, data: Autofill.NodeData) =
        inner.onSessionCommit(session, node, data)

    override fun onSessionCancel(session: GeckoSession) = inner.onSessionCancel(session)

    override fun onNodeAdd(session: GeckoSession, node: Autofill.Node, data: Autofill.NodeData) =
        inner.onNodeAdd(session, node, data)

    override fun onNodeRemove(session: GeckoSession, node: Autofill.Node, data: Autofill.NodeData) =
        inner.onNodeRemove(session, node, data)

    override fun onNodeUpdate(session: GeckoSession, node: Autofill.Node, data: Autofill.NodeData) =
        inner.onNodeUpdate(session, node, data)

    override fun onNodeBlur(session: GeckoSession, node: Autofill.Node, data: Autofill.NodeData) =
        inner.onNodeBlur(session, node, data)

    override fun onNodeFocus(session: GeckoSession, node: Autofill.Node, data: Autofill.NodeData) {
        inner.onNodeFocus(session, node, data)
        if (!isSignIn(node)) return
        val id = data.id
        view.post {
            // Still the same field and still on screen.
            if (view.session !== session || session.autofillSession.focusedData?.id != id) return@post
            ask(node, id)
        }
    }

    private fun isSignIn(node: Autofill.Node): Boolean {
        if (node.hint == Autofill.Hint.PASSWORD || node.hint == Autofill.Hint.USERNAME ||
            node.hint == Autofill.Hint.EMAIL_ADDRESS
        ) {
            return true
        }
        val type = node.getAttribute("type")?.lowercase()
        if (type == "password" || type == "email") return true
        val auto = node.getAttribute("autocomplete")?.lowercase().orEmpty()
        return "username" in auto || "password" in auto || "email" in auto
    }

    private fun ask(node: Autofill.Node, id: Int) {
        val manager = view.context.getSystemService(AutofillManager::class.java) ?: return
        runCatching {
            if (!manager.isEnabled) return
            manager.requestAutofill(view, id, screenRect(node))
        }
    }

    /** Where the field is on screen; if the engine hasn't said, the whole page view. */
    private fun screenRect(node: Autofill.Node): Rect {
        if (!node.screenRect.isEmpty) return node.screenRect
        val at = IntArray(2)
        view.getLocationOnScreen(at)
        return Rect(at[0], at[1], at[0] + view.width, at[1] + view.height)
    }
}
