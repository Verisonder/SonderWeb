// The Google account signed in on this page: its picture, email and name, sent to SonderWeb for
// the profile (its picture, and "use another profile?" when a different account signs in).
// Nothing leaves the phone.
(function () {
  if (window !== window.top) return;
  // Account pictures: lh3.googleusercontent.com/a/…, /a-/…, /ogw/… (Google's header), /-…/photo.jpg.
  const pictureOf = /^https:\/\/lh\d\.googleusercontent\.com\//;
  const emailOf = /[^\s()<>"\[\],]+@[^\s()<>"\[\],]+\.[a-z]{2,}/i;
  let sent = "";
  let listed = false;

  function srcOf(img) {
    if (!img) return "";
    const s = img.currentSrc || img.getAttribute("src") || img.getAttribute("data-src") || "";
    if (pictureOf.test(s)) return s;
    const set = (img.getAttribute("srcset") || "").split(",").map((x) => x.trim().split(/\s+/)[0]);
    return set.find((x) => pictureOf.test(x)) || "";
  }

  function nameFrom(label) {
    // "Google Account: Name (email)", in any language: between the colon and the email.
    return label.replace(/\(.*$/s, "").replace(/^.*[:：]\s*/s, "").trim();
  }

  // 1. The account button at the top of Google's pages.
  function fromPage() {
    let email = "", name = "", picture = "";
    for (const el of document.querySelectorAll("[aria-label]")) {
      const label = el.getAttribute("aria-label") || "";
      const m = label.match(emailOf);
      if (!m) continue;
      email = m[0].toLowerCase();
      name = nameFrom(label);
      picture = srcOf(el.querySelector("img"));
      break;
    }
    if (!picture) {
      for (const img of document.querySelectorAll("img")) {
        const s = srcOf(img);
        if (s && /googleusercontent\.com\/(a-?|ogw)\//.test(s)) { picture = s; break; }
      }
    }
    return email || picture ? { picture, email, name } : null;
  }

  // 2. Google's list of the accounts signed in (what its account switcher reads).
  async function fromList() {
    if (listed) return null;
    listed = true;
    try {
      const url = "https://accounts.google.com/ListAccounts?gpsia=1&source=ogb&mo=1&origin=" + encodeURIComponent(location.origin);
      const text = await (await fetch(url, { credentials: "include" })).text();
      const email = (text.match(emailOf) || [""])[0].toLowerCase();
      if (!email) return null;
      // The first account's entry: name before its email, picture after it.
      const at = text.indexOf(email);
      const before = text.slice(Math.max(0, at - 200), at);
      const after = text.slice(at, at + 400);
      const names = before.match(/"([^"]+)"\s*,\s*"[^"]*$/);
      const pic = (after.match(/https:\\?\/\\?\/lh\d\.googleusercontent\.com[^"]+/) || [""])[0].replace(/\\\//g, "/").replace(/\\u003d/g, "=");
      return { picture: pic, email, name: names ? names[1] : "" };
    } catch (e) {
      return null;
    }
  }

  function send(found) {
    const key = found.email + "|" + found.picture;
    if (key === sent) return;
    sent = key;
    try {
      browser.runtime.sendNativeMessage("account", found).catch(() => {});
    } catch (e) {}
  }

  async function check() {
    let found = fromPage();
    if (!found || !found.picture) {
      const listedOne = await fromList();
      if (listedOne && (!found || !found.email || found.email === listedOne.email)) {
        found = { picture: listedOne.picture || (found && found.picture) || "", email: listedOne.email, name: (found && found.name) || listedOne.name };
      }
    }
    if (found) send(found);
  }

  // Google draws its header late: look a few times.
  check();
  let n = 0;
  const t = setInterval(() => {
    check();
    if (++n >= 8) clearInterval(t);
  }, 1500);
})();
