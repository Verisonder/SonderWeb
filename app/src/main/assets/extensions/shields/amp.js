// De-AMP: an AMP page names the real page it's a copy of; go there instead.
(() => {
  const go = () => {
    const html = document.documentElement;
    if (!html || !(html.hasAttribute("amp") || html.hasAttribute("⚡"))) return;
    const link = document.querySelector('head > link[rel="canonical"][href]');
    if (!link) return;
    let target;
    try { target = new URL(link.href, location.href); } catch (e) { return; }
    if (!/^https?:$/.test(target.protocol) || target.href === location.href) return;
    // Only once per page, so a site that points back at its AMP copy can't loop.
    try {
      if (sessionStorage.getItem("sw-deamp") === location.href) return;
      sessionStorage.setItem("sw-deamp", location.href);
    } catch (e) {}
    location.replace(target.href);
  };
  const start = () => {
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", go, { once: true });
    } else {
      go();
    }
  };
  // Off in the browser's settings: leave AMP pages alone.
  try {
    browser.storage.local.get("amp").then((r) => { if (r.amp !== false) start(); }, start);
  } catch (e) {
    start();
  }
})();
