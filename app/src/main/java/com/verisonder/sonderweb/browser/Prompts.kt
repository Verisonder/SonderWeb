package com.verisonder.sonderweb.browser

import androidx.compose.runtime.mutableStateListOf
import com.verisonder.sonderweb.core.UrlText
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.BasePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.ChoicePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse

/** One dialog waiting to be shown. [finish] is called exactly once, by the dialog. */
sealed class PromptUi(val tab: Tab) {
    internal var base: BasePrompt? = null

    class Ask(
        tab: Tab,
        val title: String,
        val message: String?,
        val positive: String,
        val negative: String?,
        val onResult: (Boolean) -> Unit,
    ) : PromptUi(tab)

    class TextInput(
        tab: Tab,
        val title: String,
        val message: String?,
        val initial: String,
        val onResult: (String?) -> Unit,
    ) : PromptUi(tab)

    class Login(
        tab: Tab,
        val title: String,
        val message: String?,
        val passwordOnly: Boolean,
        val initialUser: String,
        val onResult: (Pair<String, String>?) -> Unit,
    ) : PromptUi(tab)

    /** A <select>. Groups are flattened; [Row.header] rows are labels, not choices. */
    class Choose(
        tab: Tab,
        val title: String?,
        val rows: List<Row>,
        val multiple: Boolean,
        val onResult: (List<ChoicePrompt.Choice>?) -> Unit,
    ) : PromptUi(tab) {
        class Row(val choice: ChoicePrompt.Choice?, val label: String, val header: Boolean, val indent: Boolean)
    }

    class PickColor(
        tab: Tab,
        val initial: String,
        val presets: List<String>,
        val onResult: (String?) -> Unit,
    ) : PromptUi(tab)
}

object Prompts {
    val queue = mutableStateListOf<PromptUi>()

    fun remove(p: PromptUi) {
        queue.remove(p)
    }

    /** A yes/no question (also used for site permissions). */
    fun ask(
        tab: Tab,
        title: String,
        message: String? = null,
        positive: String = "OK",
        negative: String? = "Cancel",
        onResult: (Boolean) -> Unit,
    ) {
        queue += PromptUi.Ask(tab, title, message, positive, negative, onResult)
    }

    private fun <P : BasePrompt> track(ui: PromptUi, prompt: P) {
        ui.base = prompt
        prompt.setDelegate(object : PromptDelegate.PromptInstanceDelegate {
            override fun onPromptDismiss(prompt: BasePrompt) {
                queue.remove(ui)
            }
        })
        queue += ui
    }

    private fun site(tab: Tab) = UrlText.host(tab.url).ifEmpty { "This page" }

    fun delegate(tab: Tab) = object : PromptDelegate {

        override fun onAlertPrompt(
            session: GeckoSession,
            prompt: PromptDelegate.AlertPrompt,
        ): GeckoResult<PromptResponse>? {
            val r = GeckoResult<PromptResponse>()
            track(
                PromptUi.Ask(tab, prompt.title.orEmpty().ifBlank { site(tab) + " says" }, prompt.message, "OK", null) {
                    r.complete(prompt.dismiss())
                },
                prompt,
            )
            return r
        }

        override fun onButtonPrompt(
            session: GeckoSession,
            prompt: PromptDelegate.ButtonPrompt,
        ): GeckoResult<PromptResponse>? {
            val r = GeckoResult<PromptResponse>()
            track(
                PromptUi.Ask(tab, prompt.title.orEmpty().ifBlank { site(tab) + " says" }, prompt.message, "OK", "Cancel") { ok ->
                    r.complete(
                        prompt.confirm(
                            if (ok) PromptDelegate.ButtonPrompt.Type.POSITIVE else PromptDelegate.ButtonPrompt.Type.NEGATIVE,
                        ),
                    )
                },
                prompt,
            )
            return r
        }

        override fun onTextPrompt(
            session: GeckoSession,
            prompt: PromptDelegate.TextPrompt,
        ): GeckoResult<PromptResponse>? {
            val r = GeckoResult<PromptResponse>()
            track(
                PromptUi.TextInput(
                    tab,
                    prompt.title.orEmpty().ifBlank { site(tab) + " says" },
                    prompt.message,
                    prompt.defaultValue.orEmpty(),
                ) { text -> r.complete(if (text == null) prompt.dismiss() else prompt.confirm(text)) },
                prompt,
            )
            return r
        }

        override fun onAuthPrompt(
            session: GeckoSession,
            prompt: PromptDelegate.AuthPrompt,
        ): GeckoResult<PromptResponse>? {
            val r = GeckoResult<PromptResponse>()
            val opts = prompt.authOptions
            val passwordOnly = (opts.flags and PromptDelegate.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD) != 0
            track(
                PromptUi.Login(
                    tab,
                    prompt.title.orEmpty().ifBlank { "Sign in to " + UrlText.host(opts.uri).ifEmpty { site(tab) } },
                    prompt.message,
                    passwordOnly,
                    opts.username.orEmpty(),
                ) { creds ->
                    r.complete(
                        when {
                            creds == null -> prompt.dismiss()
                            passwordOnly -> prompt.confirm(creds.second)
                            else -> prompt.confirm(creds.first, creds.second)
                        },
                    )
                },
                prompt,
            )
            return r
        }

        override fun onChoicePrompt(
            session: GeckoSession,
            prompt: ChoicePrompt,
        ): GeckoResult<PromptResponse>? {
            val rows = ArrayList<PromptUi.Choose.Row>()
            fun add(choices: Array<out ChoicePrompt.Choice>, indent: Boolean) {
                for (c in choices) {
                    val items = c.items
                    when {
                        c.separator -> Unit
                        items != null -> {
                            rows += PromptUi.Choose.Row(null, c.label, header = true, indent = false)
                            add(items, indent = true)
                        }
                        else -> rows += PromptUi.Choose.Row(c, c.label, header = false, indent = indent)
                    }
                }
            }
            add(prompt.choices, indent = false)
            val r = GeckoResult<PromptResponse>()
            track(
                PromptUi.Choose(tab, prompt.title, rows, prompt.type == ChoicePrompt.Type.MULTIPLE) { picked ->
                    r.complete(
                        when {
                            picked == null -> prompt.dismiss()
                            prompt.type == ChoicePrompt.Type.MULTIPLE -> prompt.confirm(picked.toTypedArray())
                            picked.isEmpty() -> prompt.dismiss()
                            else -> prompt.confirm(picked.first())
                        },
                    )
                },
                prompt,
            )
            return r
        }

        override fun onColorPrompt(
            session: GeckoSession,
            prompt: PromptDelegate.ColorPrompt,
        ): GeckoResult<PromptResponse>? {
            val r = GeckoResult<PromptResponse>()
            track(
                PromptUi.PickColor(tab, prompt.defaultValue ?: "#000000", prompt.predefinedValues?.toList() ?: emptyList()) { c ->
                    r.complete(if (c == null) prompt.dismiss() else prompt.confirm(c))
                },
                prompt,
            )
            return r
        }

        override fun onDateTimePrompt(
            session: GeckoSession,
            prompt: PromptDelegate.DateTimePrompt,
        ): GeckoResult<PromptResponse>? {
            val host = Browser.host ?: return GeckoResult.fromValue(prompt.dismiss())
            val r = GeckoResult<PromptResponse>()
            host.pickDateTime(prompt.type, prompt.defaultValue, prompt.minValue, prompt.maxValue) { v ->
                r.complete(if (v == null) prompt.dismiss() else prompt.confirm(v))
            }
            return r
        }

        override fun onFilePrompt(
            session: GeckoSession,
            prompt: PromptDelegate.FilePrompt,
        ): GeckoResult<PromptResponse>? {
            val host = Browser.host ?: return GeckoResult.fromValue(prompt.dismiss())
            val r = GeckoResult<PromptResponse>()
            val multiple = prompt.type == PromptDelegate.FilePrompt.Type.MULTIPLE
            val mimes = prompt.mimeTypes?.filter { it.contains('/') }?.toTypedArray() ?: emptyArray()
            host.pickFiles(mimes, multiple) { uris ->
                r.complete(
                    when {
                        uris.isEmpty() -> prompt.dismiss()
                        multiple -> prompt.confirm(Browser.app, uris.toTypedArray())
                        else -> prompt.confirm(Browser.app, uris.first())
                    },
                )
            }
            return r
        }

        override fun onPopupPrompt(
            session: GeckoSession,
            prompt: PromptDelegate.PopupPrompt,
        ): GeckoResult<PromptResponse>? {
            val r = GeckoResult<PromptResponse>()
            val target = prompt.targetUri?.let { UrlText.host(it) }.orEmpty()
            track(
                PromptUi.Ask(
                    tab,
                    site(tab) + " wants to open a pop-up",
                    target.ifEmpty { null },
                    "Allow",
                    "Block",
                ) { ok -> r.complete(prompt.confirm(if (ok) AllowOrDeny.ALLOW else AllowOrDeny.DENY)) },
                prompt,
            )
            return r
        }

        override fun onBeforeUnloadPrompt(
            session: GeckoSession,
            prompt: PromptDelegate.BeforeUnloadPrompt,
        ): GeckoResult<PromptResponse>? {
            val r = GeckoResult<PromptResponse>()
            track(
                PromptUi.Ask(tab, "Leave this page?", "Changes you made may not be saved.", "Leave", "Stay") { ok ->
                    r.complete(prompt.confirm(if (ok) AllowOrDeny.ALLOW else AllowOrDeny.DENY))
                },
                prompt,
            )
            return r
        }

        override fun onRepostConfirmPrompt(
            session: GeckoSession,
            prompt: PromptDelegate.RepostConfirmPrompt,
        ): GeckoResult<PromptResponse>? {
            val r = GeckoResult<PromptResponse>()
            track(
                PromptUi.Ask(
                    tab,
                    "Send the form again?",
                    "Reloading will repeat any action the form did, like a payment.",
                    "Send",
                    "Cancel",
                ) { ok -> r.complete(prompt.confirm(if (ok) AllowOrDeny.ALLOW else AllowOrDeny.DENY)) },
                prompt,
            )
            return r
        }

        override fun onSharePrompt(
            session: GeckoSession,
            prompt: PromptDelegate.SharePrompt,
        ): GeckoResult<PromptResponse>? {
            val host = Browser.host
                ?: return GeckoResult.fromValue(prompt.confirm(PromptDelegate.SharePrompt.Result.FAILURE))
            val text = listOfNotNull(prompt.text, prompt.uri).filter { it.isNotBlank() }.joinToString("\n")
            host.share(text, prompt.title)
            return GeckoResult.fromValue(prompt.confirm(PromptDelegate.SharePrompt.Result.SUCCESS))
        }
    }
}
