// The Google account signed in on this page (top right on Google's pages): its picture, email
// and name, sent to SonderWeb for the profile (its picture, and "use another profile?" when a
// different account signs in). Nothing leaves the phone.
(function () {
  if (window !== window.top) return;
  const pictureOf = /^https:\/\/lh\d\.googleusercontent\.com\/a-?\//;
  const emailOf = /[^\s()<>"]+@[^\s()<>"]+\.[a-z]{2,}/i;
  const links = [
    'a[aria-label^="Google Account"]',
    'a[aria-label*="Google Account"]',
    'a[href*="SignOutOptions"]',
  ];
  let sent = "";

  function find() {
    for (const sel of links) {
      for (const a of document.querySelectorAll(sel)) {
        const label = a.getAttribute("aria-label") || "";
        const img = a.querySelector("img");
        const src = img ? (img.currentSrc || img.src || "") : "";
        const email = (label.match(emailOf) || [""])[0];
        if (!email && !pictureOf.test(src)) continue;
        const name = label.replace(/^.*?Google Account:\s*/i, "").replace(/\(.*$/s, "").trim();
        return { picture: pictureOf.test(src) ? src : "", email: email.toLowerCase(), name };
      }
    }
    for (const img of document.querySelectorAll('img[src*="googleusercontent.com/a"]')) {
      const src = img.currentSrc || img.src || "";
      if (pictureOf.test(src)) return { picture: src, email: "", name: "" };
    }
    return null;
  }

  function check() {
    const found = find();
    if (!found) return;
    const key = found.email + "|" + found.picture;
    if (key === sent) return;
    sent = key;
    try {
      browser.runtime.sendNativeMessage("account", found).catch(() => {});
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
