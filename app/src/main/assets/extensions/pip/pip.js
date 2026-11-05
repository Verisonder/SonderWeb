// Picture-in-picture: tells the browser while a video is playing (and its shape), and while
// the page is in the small window, makes that video fill it on black.
(() => {
  let port = null;
  let video = null;
  let filled = null;

  function connect() {
    if (port) return port;
    try {
      port = browser.runtime.connectNative("pip");
      port.onMessage.addListener(onMessage);
      port.onDisconnect.addListener(() => { port = null; });
    } catch (e) {
      port = null;
    }
    return port;
  }

  // The biggest video on the page that is playing.
  function playing() {
    let pick = null;
    let area = 0;
    for (const v of document.querySelectorAll("video")) {
      if (v.paused || v.ended || v.readyState < 2) continue;
      const r = v.getBoundingClientRect();
      const a = r.width * r.height;
      if (a > area) { area = a; pick = v; }
    }
    return pick;
  }

  function report() {
    const v = playing();
    if (v) video = v;
    // Nothing to say until a video has played here.
    if (!v && !port) return;
    const p = connect();
    if (!p) return;
    const ref = v || video;
    try {
      p.postMessage({
        playing: !!v,
        w: (ref && ref.videoWidth) || 16,
        h: (ref && ref.videoHeight) || 9,
      });
    } catch (e) {}
  }

  for (const t of ["play", "playing", "pause", "ended", "emptied", "loadedmetadata"]) {
    document.addEventListener(t, report, true);
  }

  const FILL = [
    ["position", "fixed"], ["inset", "0"], ["top", "0"], ["left", "0"],
    ["width", "100vw"], ["height", "100vh"], ["max-width", "none"], ["max-height", "none"],
    ["margin", "0"], ["transform", "none"], ["object-fit", "contain"],
    ["background", "#000"], ["z-index", "2147483647"], ["visibility", "visible"], ["opacity", "1"],
  ];
  // Ancestors that would otherwise keep a fixed video boxed in, or under other things.
  const FREE = [
    ["transform", "none"], ["filter", "none"], ["perspective", "none"], ["contain", "none"],
    ["will-change", "auto"], ["overflow", "visible"], ["z-index", "2147483647"],
    ["visibility", "visible"], ["opacity", "1"],
  ];

  function fill() {
    const v = (video && video.isConnected) ? video : playing();
    if (!v || filled) return;
    // Already filling the screen on its own (the site's fullscreen): nothing to do.
    if (document.fullscreenElement && document.fullscreenElement.contains(v)) return;
    const saved = [];
    const set = (el, rules) => {
      saved.push([el, el.getAttribute("style")]);
      for (const [k, val] of rules) el.style.setProperty(k, val, "important");
    };
    set(v, FILL);
    for (let el = v.parentElement; el && el !== document.documentElement; el = el.parentElement) set(el, FREE);
    set(document.documentElement, [["overflow", "hidden"], ["background", "#000"]]);
    filled = saved;
  }

  function unfill() {
    if (!filled) return;
    for (const [el, style] of filled.reverse()) {
      if (style === null) el.removeAttribute("style"); else el.setAttribute("style", style);
    }
    filled = null;
  }

  function onMessage(m) {
    if (m.pip === true) fill();
    if (m.pip === false) unfill();
    if (m.toggle && video) {
      if (video.paused) video.play().catch(() => {}); else video.pause();
    }
  }
})();
