// The browser's own colours on DuckDuckGo: light, dark, or pure black in private tabs.
(() => {
  const dark = matchMedia("(prefers-color-scheme: dark)").matches;
  let incognito = false;
  try { incognito = browser.extension.inIncognitoContext; } catch (e) {}
  const p = incognito
    ? { bg: "#000000", card: "#111113", field: "#1C1C1E", line: "#2C2C2E", text: "#E8EAED", mute: "#BDC1C6", faint: "#9AA0A6", accent: "#C58AF9", visited: "#D7AEFB" }
    : dark
      ? { bg: "#202124", card: "#292A2D", field: "#303134", line: "#3C4043", text: "#E8EAED", mute: "#BDC1C6", faint: "#9AA0A6", accent: "#8AB4F8", visited: "#C58AF9" }
      : { bg: "#FFFFFF", card: "#F8F9FA", field: "#F1F3F4", line: "#DADCE0", text: "#202124", mute: "#5F6368", faint: "#80868B", accent: "#1A73E8", visited: "#681DA8" };

  const root = document.documentElement;
  for (const [k, v] of Object.entries(p)) root.style.setProperty("--sw-" + k, v);
  root.classList.add("sw-look");

  // DuckDuckGo's own settings (kept as its cookies, from the next page on): no ads, matching theme.
  const set = (k, v) => { document.cookie = k + "=" + v + "; path=/; max-age=31536000; secure; samesite=lax"; };
  set("1", "-1");
  set("ae", dark || incognito ? "d" : "-1");

  // Inter, like the rest of the browser.
  const face = new FontFace("SonderInter", "url(" + browser.runtime.getURL("fonts/inter_regular.ttf") + ")", { weight: "400" });
  const bold = new FontFace("SonderInter", "url(" + browser.runtime.getURL("fonts/inter_semibold.ttf") + ")", { weight: "600" });
  Promise.all([face.load(), bold.load()]).then((f) => f.forEach((x) => document.fonts.add(x))).catch(() => {});
})();
