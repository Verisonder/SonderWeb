// Each shield can be turned off in the browser's settings (Privacy & security › Shields).
// The browser sends its switches here; until then all are on.
const on = { webrtc: true, amp: true, debounce: true };

// WebRTC: sites only see the address your traffic already uses (your VPN's, if you have one),
// never your local network addresses. Calls still work.
function applyWebrtc() {
  try {
    const policy = browser.privacy.network.webRTCIPHandlingPolicy;
    if (on.webrtc) policy.set({ value: "default_public_interface_only" });
    else policy.clear({});
  } catch (e) {}
}
applyWebrtc();

function settings(m) {
  for (const k of Object.keys(on)) if (typeof m[k] === "boolean") on[k] = m[k];
  applyWebrtc();
  // The AMP page check runs in the page itself: it reads this.
  try { browser.storage.local.set({ amp: on.amp }); } catch (e) {}
}

// Talk to the browser; if it isn't listening yet (still starting), try again shortly.
let tries = 0;
function connect() {
  let port;
  try {
    port = browser.runtime.connectNative("shields");
  } catch (e) {
    if (tries++ < 20) setTimeout(connect, 1500);
    return;
  }
  port.onMessage.addListener(settings);
  port.onDisconnect.addListener(() => { if (tries++ < 20) setTimeout(connect, 1500); });
}
connect();

// Tracking redirects ("debouncing"): links that only pass through a tracker on the way to the
// real page go straight to the real page. host -> [path prefix, parameter holding the address]
const BOUNCES = {
  "www.google.com": [["/url", "q"], ["/url", "url"]],
  "google.com": [["/url", "q"], ["/url", "url"]],
  "l.facebook.com": [["/l.php", "u"]],
  "lm.facebook.com": [["/l.php", "u"]],
  "l.instagram.com": [["/", "u"]],
  "l.messenger.com": [["/l.php", "u"]],
  "www.youtube.com": [["/redirect", "q"]],
  "m.youtube.com": [["/redirect", "q"]],
  "out.reddit.com": [["/", "url"]],
  "away.vk.com": [["/away.php", "to"]],
  "vk.com": [["/away.php", "to"]],
  "steamcommunity.com": [["/linkfilter/", "url"], ["/linkfilter/", "u"]],
  "slack-redir.net": [["/link", "url"]],
  "www.linkedin.com": [["/redir/redirect", "url"], ["/safety/go", "url"]],
  "href.li": [["/", ""]],
  "nl.hideref.org": [["/", ""]],
  "click.linksynergy.com": [["/", "murl"]],
  "www.awin1.com": [["/cread.php", "ued"], ["/awclick.php", "p"]],
  "go.redirectingat.com": [["/", "url"]],
  "deref-gmx.net": [["/mail/client/", "redirectUrl"]],
  "deref-web.de": [["/mail/client/", "redirectUrl"]],
};

function realTarget(url) {
  const u = new URL(url);
  const rules = on.debounce ? BOUNCES[u.hostname] : null;
  if (rules) {
    for (const [path, param] of rules) {
      if (!u.pathname.startsWith(path)) continue;
      // href.li style: the address is the whole query.
      let to = param ? u.searchParams.get(param) : decodeURIComponent(u.search.slice(1));
      if (to && /^https?:\/\//i.test(to)) return to;
    }
  }
  if (!on.amp) return null;
  // Google's AMP copies: /amp/s/example.com/page -> https://example.com/page
  if ((u.hostname === "www.google.com" || u.hostname === "google.com") && u.pathname.startsWith("/amp/")) {
    const rest = u.pathname.replace(/^\/amp\/(s\/)?/, "");
    if (rest.includes(".")) return (u.pathname.startsWith("/amp/s/") ? "https://" : "http://") + rest + u.search;
  }
  // AMP cache: example-com.cdn.ampproject.org/c/s/example.com/page -> https://example.com/page
  if (u.hostname.endsWith(".cdn.ampproject.org")) {
    const m = u.pathname.match(/^\/[a-z]+\/(s\/)?(.+)$/);
    if (m && m[2].includes(".")) return (m[1] ? "https://" : "http://") + m[2] + u.search;
  }
  return null;
}

browser.webRequest.onBeforeRequest.addListener(
  (details) => {
    try {
      const to = realTarget(details.url);
      if (to && to !== details.url) return { redirectUrl: to };
    } catch (e) {}
    return {};
  },
  { urls: ["<all_urls>"], types: ["main_frame"] },
  ["blocking"]
);
