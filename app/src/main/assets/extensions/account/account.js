// The picture of the Google account signed in on this page (top right on Google's pages),
// sent to SonderWeb so the profile can use it. Only the picture's address leaves the page.
(function () {
  if (window !== window.top) return;
  const pictureOf = /^https:\/\/lh\d\.googleusercontent\.com\/a-?\//;
  const places = [
    'a[aria-label^="Google Account"] img',
    'a[aria-label*="Google Account"] img',
    'a[href*="SignOutOptions"] img',
    'img[src*="googleusercontent.com/a/"]',
    'img[src*="googleusercontent.com/a-/"]',
  ];
  let sent = "";

  function find() {
    for (const p of places) {
      for (const img of document.querySelectorAll(p)) {
        const src = img.currentSrc || img.src || "";
        if (pictureOf.test(src)) return src;
      }
    }
    return null;
  }

  function check() {
    const src = find();
    if (!src || src === sent) return;
    sent = src;
    try {
      browser.runtime.sendNativeMessage("account", { picture: src }).catch(() => {});
    } catch (e) {}
  }

  // Google draws its header late: look a few times.
  check();
  let n = 0;
  const t = setInterval(() => {
    check();
    if (++n >= 8) clearInterval(t);
  }, 1500);
})();
