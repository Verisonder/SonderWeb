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
        val to = short(tab.userLang ?: Locale.getDefault().toLanguageTag())
        val from = tab.pageLang?.let(::short)
        // The page says it's in your language (x.com always says English, even with Portuguese
        // posts), or doesn't say: you pick the language to translate from.
        if (from == null || from == to) {
            pickFrom(tab, to)
            return
        }
        start(tab, from, to)
    }

    /** The "Translate from" list: shown while not null. */
    var choices by mutableStateOf<List<TranslationsController.Language>?>(null)
        private set
    private var choosingFor: Tab? = null
    private var choosingTo = ""

    private fun pickFrom(tab: Tab, to: String) {
        TranslationsController.RuntimeTranslation.listSupportedLanguages().accept(
            { support ->
                Browser.main.post {
                    val list = support?.fromLanguages.orEmpty()
                        .filter { short(it.code) != to }
                        .sortedBy { (it.localizedDisplayName ?: name(it.code)).lowercase() }
                    if (list.isEmpty()) {
                        Browser.host?.toast("This page is already in ${name(to)}")
                    } else {
                        choosingFor = tab
                        choosingTo = to
                        choices = list
                    }
                }
            },
            { e -> Browser.main.post { Browser.host?.toast(message(e)) } },
        )
    }

    /** Picked from the list (null: closed without picking). */
    fun picked(code: String?) {
        val tab = choosingFor
        choices = null
        choosingFor = null
        if (code != null && tab != null) start(tab, code, choosingTo)
    }

    fun label(l: TranslationsController.Language) = l.localizedDisplayName ?: name(l.code)

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
