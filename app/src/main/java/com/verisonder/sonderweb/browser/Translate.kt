package com.verisonder.sonderweb.browser

import java.util.Locale
import org.mozilla.geckoview.GeckoSession
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.TranslationsController
import org.mozilla.geckoview.TranslationsController.SessionTranslation
import org.mozilla.geckoview.TranslationsController.TranslationsException

/**
 * Translates pages with the engine's own on-device models, like Firefox does:
 * the text never goes to a translation service. A language pack is downloaded once, the first time it is needed.
 */
object Translate {

    fun attach(tab: Tab) {
        tab.session.setTranslationsSessionDelegate(
            object : SessionTranslation.Delegate {
                override fun onTranslationStateChange(session: GeckoSession, state: SessionTranslation.TranslationState?) {
                    Browser.main.post { update(tab, state) }
                }
            },
        )
    }

    private fun update(tab: Tab, state: SessionTranslation.TranslationState?) {
        val langs = state?.detectedLanguages
        tab.pageLang = langs?.docLangTag
        tab.userLang = langs?.userLangTag
        // A new page comes back untranslated, so the menu offers "Translate page" again.
        tab.translatedTo = state?.requestedTranslationPair?.toLanguage
        if (state?.error != null && tab.translating) {
            tab.translating = false
            tab.translatedTo = null
            Browser.host?.toast("Couldn't translate this page")
        }
    }

    /** "Translate page" from the menu, or "Show original" once the page is translated. */
    fun toggle(tab: Tab) {
        val st = tab.session.sessionTranslation
        if (st == null) {
            Browser.host?.toast("Translation isn't available here")
            return
        }
        if (tab.translatedTo != null) {
            st.restoreOriginalPage().accept(
                { _ -> Browser.main.post { tab.translatedTo = null } },
                { _ -> Browser.main.post { Browser.host?.toast("Couldn't show the original") } },
            )
            return
        }
        if (tab.translating) return
        // The phone's language, or the one you picked last time.
        val to = Browser.prefs.translateTo.ifEmpty { short(tab.userLang ?: Locale.getDefault().toLanguageTag()) }
        // What the page says it's in. Not trusted when it says your language: x.com always says
        // English, even with Portuguese posts, so then you pick.
        val from = tab.pageLang?.let(::short)?.takeIf { it != to }
        TranslationsController.RuntimeTranslation.listSupportedLanguages().accept(
            { support ->
                Browser.main.post {
                    val sort = { l: List<TranslationsController.Language> -> l.sortedBy { label(it).lowercase() } }
                    ask = Ask(tab, sort(support?.fromLanguages.orEmpty()), sort(support?.toLanguages.orEmpty()), from, to)
                }
            },
            { e -> Browser.main.post { Browser.host?.toast(message(e)) } },
        )
    }

    /** The From / To dialog: which languages, with the page's own and your usual one picked. */
    class Ask(
        val tab: Tab,
        val froms: List<TranslationsController.Language>,
        val tos: List<TranslationsController.Language>,
        val from: String?,
        val to: String,
    )

    /** Shown while not null. */
    var ask by mutableStateOf<Ask?>(null)
        private set

    /** Translate was tapped in the dialog (null: closed). The "To" is kept for next time. */
    fun answer(from: String?, to: String?) {
        val a = ask
        ask = null
        if (a == null || from == null || to == null) return
        Browser.prefs.translateTo = to
        start(a.tab, from, to)
    }

    fun label(l: TranslationsController.Language) = l.localizedDisplayName ?: name(l.code)

    fun label(code: String) = name(code)

    private fun start(tab: Tab, from: String?, to: String) {
        val st = tab.session.sessionTranslation ?: return
        tab.translating = true
        Browser.host?.toast("Translating to ${name(to)}…")
        val options = SessionTranslation.TranslationOptions.Builder().downloadModel(true).build()
        st.translate(SessionTranslation.TranslationPair(from, to), options).accept(
            { _ ->
                Browser.main.post {
                    tab.translating = false
                    tab.translatedTo = to
                }
            },
            { e ->
                Browser.main.post {
                    tab.translating = false
                    Browser.host?.toast(message(e))
                }
            },
        )
    }

    private fun short(tag: String) = tag.substringBefore('-').lowercase()

    private fun name(code: String) =
        Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).ifBlank { code }

    private fun message(e: Throwable?): String = when ((e as? TranslationsException)?.code) {
        TranslationsException.ERROR_MODEL_COULD_NOT_DOWNLOAD,
        TranslationsException.ERROR_MODEL_DOWNLOAD_REQUIRED,
        -> "Couldn't download the language pack. Check your connection"
        TranslationsException.ERROR_LANGUAGE_NOT_SUPPORTED,
        TranslationsException.ERROR_MODEL_LANGUAGE_REQUIRED,
        -> "This page's language can't be translated yet"
        TranslationsException.ERROR_ENGINE_NOT_SUPPORTED,
        TranslationsException.ERROR_ENGINE_DEACTIVATED,
        -> "Translation isn't supported on this phone"
        else -> "Couldn't translate this page"
    }
}
