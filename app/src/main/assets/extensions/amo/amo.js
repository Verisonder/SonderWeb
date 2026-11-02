// The add-ons site's install buttons say "Add to Firefox" (in every language):
// in SonderWeb they say SonderWeb. The page builds them itself, so keep watching.
(() => {
  const BUTTONS = ".AMInstallButton, .AMInstallButton-button, .InstallButtonWrapper, .GetFirefoxButton";

  const fix = (root) => {
    const scopes = root.matches && root.matches(BUTTONS) ? [root] : [];
    if (root.querySelectorAll) scopes.push(...root.querySelectorAll(BUTTONS));
    for (const scope of scopes) {
      const walker = document.createTreeWalker(scope, NodeFilter.SHOW_TEXT);
      for (let n = walker.nextNode(); n; n = walker.nextNode()) {
        if (n.nodeValue.includes("Firefox")) n.nodeValue = n.nodeValue.replace(/Firefox/g, "SonderWeb");
      }
      for (const el of [scope, ...scope.querySelectorAll("[aria-label],[title]")]) {
        for (const attr of ["aria-label", "title"]) {
          const v = el.getAttribute && el.getAttribute(attr);
          if (v && v.includes("Firefox")) el.setAttribute(attr, v.replace(/Firefox/g, "SonderWeb"));
        }
      }
    }
  };

  new MutationObserver((changes) => {
    for (const c of changes) {
      if (c.type === "characterData") {
        const el = c.target.parentElement;
        if (el && el.closest(BUTTONS)) fix(el.closest(BUTTONS));
      } else {
        for (const node of c.addedNodes) {
          if (node.nodeType === 1) {
            fix(node);
            const host = node.closest && node.closest(BUTTONS);
            if (host) fix(host);
          } else if (node.nodeType === 3 && node.parentElement && node.parentElement.closest(BUTTONS)) {
            fix(node.parentElement.closest(BUTTONS));
          }
        }
      }
    }
  }).observe(document, { childList: true, subtree: true, characterData: true });

  document.addEventListener("DOMContentLoaded", () => fix(document.documentElement));
})();
