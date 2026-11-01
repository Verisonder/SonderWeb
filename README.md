# SonderWeb

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

A private, fast and honest web browser for Android. uBlock Origin built in, Firefox's engine underneath, nothing sent home.

## What it does

- **uBlock Origin, built in.** Ads, trackers and malware are blocked from the first page. Tap the shield in the address bar to see what a page blocked, or to switch blocking off for that site.
- **Firefox's engine (GeckoView).** Strict tracking protection, cookies kept apart per site, tracking parameters stripped from links, Global Privacy Control on.
- **!bangs.** Type `!w Tangier`, `!yt lofi` or `!gh sonder` to search a site directly. Common bangs are resolved on your phone; any other bang goes through DuckDuckGo.
- **Your search engine.** DuckDuckGo by default; Brave, Startpage, Qwant, Ecosia, Kagi, Google, Bing, or your own.
- **The page gets the whole screen.** The address bar shrinks into a small button (or slides away, your choice) and the phone status bar is hidden unless you want it.
- **A start page** with the time, a greeting, the weather (Open-Meteo, only the place you pick is sent) and the shortcuts you add.
- **Tabs and private tabs.** Private tabs keep no history, cookies or site data once closed.
- **History, bookmarks, downloads**, all stored only on your phone.
- **No accounts, no telemetry, no tracking.**

## Install

Download the APK from [Releases](https://github.com/Verisonder/SonderWeb/releases/latest). Android 8.0 or newer, 64-bit phones.

## Using SonderWeb

✅ Use it, share it, change it, ship your own build (keep the licence and NOTICE).
🚫 Don't present your build as the official SonderWeb.

## Building

```
gradle assembleDebug
```

The build downloads GeckoView from Mozilla's Maven repository and the uBlock Origin release named in `gradle/libs.versions.toml`.

## License

SonderWeb is released under the [Apache License 2.0](LICENSE). GeckoView is under the Mozilla Public License 2.0 and uBlock Origin under the GPL-3.0; see [NOTICE](NOTICE).
