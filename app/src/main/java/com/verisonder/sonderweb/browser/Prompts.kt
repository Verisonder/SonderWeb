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

    /** Which saved sign-in to fill in: (site or username, detail) per row; null: none. */
    class PickLogin(
        tab: Tab,
        val title: String,
        val rows: List<Pair<String, String>>,
        val onResult: (Int?) -> Unit,
    ) : PromptUi(tab)

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

        // ---- Saved passwords (the Firefox Sync option) ----

        override fun onLoginSave(
            session: GeckoSession,
            request: PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSaveOption>,
        ): GeckoResult<PromptResponse>? {
            val option = request.options.firstOrNull() ?: return GeckoResult.fromValue(request.dismiss())
            val login = option.value
            val update = !login.guid.isNullOrEmpty()
            val r = GeckoResult<PromptResponse>()
            track(
                PromptUi.Ask(
                    tab,
                    if (update) "Update saved password?" else "Save password?",
                    listOf(login.username.ifBlank { null }, UrlText.host(login.origin).ifEmpty { site(tab) })
                        .filterNotNull().joinToString(" · ") + "\nSaved in SonderWeb and synced with Firefox Sync.",
                    if (update) "Update" else "Save",
                    "Not now",
                ) { ok -> r.complete(if (ok) request.confirm(option) else request.dismiss()) },
                request,
            )
            return r
        }

        override fun onLoginSelect(
            session: GeckoSession,
            request: PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSelectOption>,
        ): GeckoResult<PromptResponse>? {
            val options = request.options
            if (options.isEmpty()) return GeckoResult.fromValue(request.dismiss())
            val rows = options.map { o ->
                val v = o.value
                v.username.ifBlank { "(no username)" } to UrlText.host(v.origin).ifEmpty { v.origin }
            }
            val r = GeckoResult<PromptResponse>()
            track(
                PromptUi.PickLogin(tab, "Sign in as", rows) { i ->
                    r.complete(if (i != null && i in options.indices) request.confirm(options[i]) else request.dismiss())
                },
                request,
            )
            return r
        }

        // ---- Saved addresses and payment cards (the Firefox Sync option) ----

        override fun onAddressSave(
            session: GeckoSession,
            request: PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.AddressSaveOption>,
        ): GeckoResult<PromptResponse>? {
            if (!com.verisonder.sonderweb.sync.FxSync.fillAddresses) return GeckoResult.fromValue(request.dismiss())
            val option = request.options.firstOrNull() ?: return GeckoResult.fromValue(request.dismiss())
            val a = option.value
            val update = !a.guid.isNullOrEmpty()
            val name = a.name.ifBlank { listOf(a.givenName, a.familyName).filter { it.isNotBlank() }.joinToString(" ") }
            val line = listOf(name, a.streetAddress.lineSequence().firstOrNull().orEmpty(), a.addressLevel2)
                .filter { it.isNotBlank() }.joinToString(", ")
            val r = GeckoResult<PromptResponse>()
            track(
                PromptUi.Ask(
                    tab,
                    if (update) "Update saved address?" else "Save address?",
                    line.ifBlank { site(tab) } + "\nSaved in SonderWeb and synced with Firefox Sync.",
                    if (update) "Update" else "Save",
                    "Not now",
                ) { ok -> r.complete(if (ok) request.confirm(option) else request.dismiss()) },
                request,
            )
            return r
        }

        override fun onAddressSelect(
            session: GeckoSession,
            request: PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.AddressSelectOption>,
        ): GeckoResult<PromptResponse>? {
            val options = request.options
            if (options.isEmpty()) return GeckoResult.fromValue(request.dismiss())
            val rows = options.map { o ->
                val v = o.value
                val name = v.name.ifBlank { listOf(v.givenName, v.familyName).filter { it.isNotBlank() }.joinToString(" ") }
                name.ifBlank { v.email.ifBlank { "Address" } } to
                    listOf(v.streetAddress.lineSequence().firstOrNull().orEmpty(), v.addressLevel2, v.postalCode)
                        .filter { it.isNotBlank() }.joinToString(", ")
            }
            val r = GeckoResult<PromptResponse>()
            track(
                PromptUi.PickLogin(tab, "Fill in an address", rows) { i ->
                    r.complete(if (i != null && i in options.indices) request.confirm(options[i]) else request.dismiss())
                },
                request,
            )
            return r
        }

        override fun onCreditCardSave(
            session: GeckoSession,
            request: PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.CreditCardSaveOption>,
        ): GeckoResult<PromptResponse>? {
            if (!com.verisonder.sonderweb.sync.FxSync.fillCards) return GeckoResult.fromValue(request.dismiss())
            val option = request.options.firstOrNull() ?: return GeckoResult.fromValue(request.dismiss())
            val c = option.value
            val update = !c.guid.isNullOrEmpty()
            val r = GeckoResult<PromptResponse>()
            track(
                PromptUi.Ask(
                    tab,
                    if (update) "Update saved card?" else "Save card?",
                    listOf("•••• " + c.number.filter { it.isDigit() }.takeLast(4), c.name)
                        .filter { it.isNotBlank() }.joinToString(" · ") +
                        "\nSaved in SonderWeb, encrypted, and synced with Firefox Sync.",
                    if (update) "Update" else "Save",
                    "Not now",
                ) { ok -> r.complete(if (ok) request.confirm(option) else request.dismiss()) },
                request,
            )
            return r
        }

        override fun onCreditCardSelect(
            session: GeckoSession,
            request: PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.CreditCardSelectOption>,
        ): GeckoResult<PromptResponse>? {
            val options = request.options
            if (options.isEmpty()) return GeckoResult.fromValue(request.dismiss())
            val rows = options.map { o ->
                val v = o.value
                ("•••• " + v.number.filter { it.isDigit() }.takeLast(4)) to
                    listOf(v.name, if (v.expirationMonth.isNotBlank()) v.expirationMonth + "/" + v.expirationYear.takeLast(2) else "")
                        .filter { it.isNotBlank() }.joinToString(" · ")
            }
            val r = GeckoResult<PromptResponse>()
            track(
                PromptUi.PickLogin(tab, "Fill in a card", rows) { i ->
                    r.complete(if (i != null && i in options.indices) request.confirm(options[i]) else request.dismiss())
                },
                request,
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
