package com.verisonder.sonderweb.core

/**
 * !bangs: "!w paris" searches Wikipedia directly, without going through a search engine.
 * Common bangs are resolved on the phone. Any other bang is handed to DuckDuckGo,
 * which knows thousands more.
 */
data class Bang(val trigger: String, val name: String, val template: String, val home: String)

object Bangs {
    private fun b(trigger: String, name: String, template: String, home: String) =
        Bang(trigger, name, template, home)

    val all: List<Bang> = listOf(
        b("g", "Google", "https://www.google.com/search?q={q}", "https://www.google.com"),
        b("ddg", "DuckDuckGo", "https://duckduckgo.com/?q={q}", "https://duckduckgo.com"),
        b("b", "Bing", "https://www.bing.com/search?q={q}", "https://www.bing.com"),
        b("brave", "Brave Search", "https://search.brave.com/search?q={q}", "https://search.brave.com"),
        b("sp", "Startpage", "https://www.startpage.com/do/search?q={q}", "https://www.startpage.com"),
        b("kagi", "Kagi", "https://kagi.com/search?q={q}", "https://kagi.com"),
        b("gi", "Google Images", "https://www.google.com/search?tbm=isch&q={q}", "https://images.google.com"),
        b("i", "DuckDuckGo Images", "https://duckduckgo.com/?ia=images&iax=images&q={q}", "https://duckduckgo.com"),
        b("n", "Google News", "https://news.google.com/search?q={q}", "https://news.google.com"),
        b("m", "Google Maps", "https://www.google.com/maps/search/{q}", "https://www.google.com/maps"),
        b("osm", "OpenStreetMap", "https://www.openstreetmap.org/search?query={q}", "https://www.openstreetmap.org"),
        b("w", "Wikipedia", "https://en.wikipedia.org/wiki/Special:Search?search={q}", "https://en.wikipedia.org"),
        b("wfr", "Wikipédia (FR)", "https://fr.wikipedia.org/wiki/Special:Search?search={q}", "https://fr.wikipedia.org"),
        b("war", "Wikipedia (AR)", "https://ar.wikipedia.org/wiki/Special:Search?search={q}", "https://ar.wikipedia.org"),
        b("wt", "Wiktionary", "https://en.wiktionary.org/wiki/Special:Search?search={q}", "https://en.wiktionary.org"),
        b("yt", "YouTube", "https://www.youtube.com/results?search_query={q}", "https://www.youtube.com"),
        b("gh", "GitHub", "https://github.com/search?q={q}", "https://github.com"),
        b("so", "Stack Overflow", "https://stackoverflow.com/search?q={q}", "https://stackoverflow.com"),
        b("mdn", "MDN", "https://developer.mozilla.org/search?q={q}", "https://developer.mozilla.org"),
        b("npm", "npm", "https://www.npmjs.com/search?q={q}", "https://www.npmjs.com"),
        b("pypi", "PyPI", "https://pypi.org/search/?q={q}", "https://pypi.org"),
        b("crates", "crates.io", "https://crates.io/search?q={q}", "https://crates.io"),
        b("mvn", "Maven Central", "https://central.sonatype.com/search?q={q}", "https://central.sonatype.com"),
        b("android", "Android Developers", "https://developer.android.com/s/results?q={q}", "https://developer.android.com"),
        b("r", "Reddit", "https://www.reddit.com/search/?q={q}", "https://www.reddit.com"),
        b("x", "X", "https://x.com/search?q={q}", "https://x.com"),
        b("tw", "X", "https://x.com/search?q={q}", "https://x.com"),
        b("ig", "Instagram", "https://www.instagram.com/explore/tags/{q}/", "https://www.instagram.com"),
        b("li", "LinkedIn", "https://www.linkedin.com/search/results/all/?keywords={q}", "https://www.linkedin.com"),
        b("fb", "Facebook", "https://www.facebook.com/search/top/?q={q}", "https://www.facebook.com"),
        b("hn", "Hacker News", "https://hn.algolia.com/?q={q}", "https://news.ycombinator.com"),
        b("a", "Amazon", "https://www.amazon.com/s?k={q}", "https://www.amazon.com"),
        b("ali", "AliExpress", "https://www.aliexpress.com/wholesale?SearchText={q}", "https://www.aliexpress.com"),
        b("ebay", "eBay", "https://www.ebay.com/sch/i.html?_nkw={q}", "https://www.ebay.com"),
        b("imdb", "IMDb", "https://www.imdb.com/find/?q={q}", "https://www.imdb.com"),
        b("lb", "Letterboxd", "https://letterboxd.com/search/{q}/", "https://letterboxd.com"),
        b("spotify", "Spotify", "https://open.spotify.com/search/{q}", "https://open.spotify.com"),
        b("genius", "Genius", "https://genius.com/search?q={q}", "https://genius.com"),
        b("t", "Google Translate", "https://translate.google.com/?sl=auto&tl=en&text={q}", "https://translate.google.com"),
        b("deepl", "DeepL", "https://www.deepl.com/translator#auto/en/{q}", "https://www.deepl.com/translator"),
        b("wa", "Wolfram|Alpha", "https://www.wolframalpha.com/input?i={q}", "https://www.wolframalpha.com"),
        b("arxiv", "arXiv", "https://arxiv.org/search/?query={q}&searchtype=all", "https://arxiv.org"),
        b("scholar", "Google Scholar", "https://scholar.google.com/scholar?q={q}", "https://scholar.google.com"),
        b("aw", "ArchWiki", "https://wiki.archlinux.org/index.php?search={q}", "https://wiki.archlinux.org"),
        b("fdroid", "F-Droid", "https://search.f-droid.org/?q={q}", "https://f-droid.org"),
        b("play", "Google Play", "https://play.google.com/store/search?q={q}", "https://play.google.com/store"),
        b("archive", "Wayback Machine", "https://web.archive.org/web/*/{q}", "https://web.archive.org"),
        b("chess", "Chess.com", "https://www.chess.com/search?query={q}", "https://www.chess.com"),
        b("gpt", "ChatGPT", "https://chatgpt.com/?q={q}", "https://chatgpt.com"),
        b("claude", "Claude", "https://claude.ai/new?q={q}", "https://claude.ai"),
        b("perplexity", "Perplexity", "https://www.perplexity.ai/search?q={q}", "https://www.perplexity.ai"),
    )

    private val byTrigger: Map<String, Bang> = all.associateBy { it.trigger }

    fun find(trigger: String): Bang? = byTrigger[trigger.lowercase()]

    /** A bang token is "!" followed by letters/digits, e.g. "!w" or "!yt". */
    fun isBangToken(token: String): Boolean =
        token.length >= 2 && token[0] == '!' && token.drop(1).all { it.isLetterOrDigit() || it == '_' || it == '-' }
}
