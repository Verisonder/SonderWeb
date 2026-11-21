// The Google account signed in on this page (top right on Google's pages): its picture, email
// and name, sent to SonderWeb for the profile (its picture, and "use another profile?" when a
// different account signs in). Nothing leaves the phone.
(function () {
  if (window !== window.top) return;
  // Account pictures: lh3.googleusercontent.com/a/…, /a-/… and, in Google's own header, /ogw/….
  const pictureOf = /^https:\/\/lh\d\.googleusercontent\.com\//;
  const emailOf = /[^\s()<>"]+@[^\s()<>"]+\.[a-z]{2,}/i;
  // The account button: its link (any language) or its English label.
  const links = [
    'a[href*="SignOutOptions"]',
    'a[aria-label*="Google Account"]',
  ];

  function srcOf(img) {
    if (!img) return "";
    const s = img.currentSrc || img.getAttribute("src") || img.getAttribute("data-src") || "";
    if (pictureOf.test(s)) return s;
    const set = (img.getAttribute("srcset") || "").split(",").map((x) => x.trim().split(/\s+/)[0]);
    return set.find((x) => pictureOf.test(x)) || "";
  }
  let sent = "";

  function find() {
    for (const sel of links) {
      for (const a of document.querySelectorAll(sel)) {
        const label = a.getAttribute("aria-label") || "";
        const src = srcOf(a.querySelector("img"));
        const email = (label.match(emailOf) || [""])[0];
        if (!email && !pictureOf.test(src)) continue;
        // "Google Account: Name (email)", in any language: the part between the colon and the email.
        const name = label.replace(/\(.*$/s, "").replace(/^.*[:：]\s*/s, "").trim();
        return { picture: src, email: email.toLowerCase(), name };
      }
    }
    for (const img of document.querySelectorAll('img[src*="googleusercontent.com/a"], img[src*="googleusercontent.com/ogw/"]')) {
      const src = srcOf(img);
      if (src) return { picture: src, email: "", name: "" };
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
