// Picture-in-picture: tells the browser while a video is playing (and its shape), and while
// the page is in the small window, makes that video fill it on black.
(() => {
  // Leaving the app, turning the screen off or the picture-in-picture window: the page isn't
  // told it's hidden, so sites that pause then (YouTube) keep playing. The browser itself
  // still pauses pages that aren't playing anything, to save battery.
  try {
    const page = window.wrappedJSObject;
    const proto = page.Document.prototype;
    const visible = (name, value) => Object.defineProperty(proto, name, {
      get: exportFunction(() => value, window),
      configurable: true,
    });
    visible("hidden", false);
    visible("webkitHidden", false);
    visible("visibilityState", "visible");
    visible("webkitVisibilityState", "visible");
  } catch (e) {}
  for (const t of ["visibilitychange", "webkitvisibilitychange"]) {
    window.addEventListener(t, (e) => e.stopImmediatePropagation(), true);
  }
  // Losing focus (the small window never has it) and the page being frozen or hidden are
  // also kept from the page. Only the window's own events: a text field's blur still goes through.
  for (const t of ["blur", "pagehide", "freeze"]) {
    window.addEventListener(t, (e) => {
      if (e.target === window || e.target === document) e.stopImmediatePropagation();
    }, true);
  }
  try {
    exportFunction(() => true, window.wrappedJSObject.Document.prototype, { defineAs: "hasFocus" });
  } catch (e) {}

  // TEMPORARY: what stops the video. A pause() call from the page is recorded with where it
  // came from; a pause with no call before it was the browser. Shown on the phone as a message.
  let pauseCall = null;
  try {
    const mproto = window.wrappedJSObject.HTMLMediaElement.prototype;
    const realPause = mproto.pause;
    exportFunction(function () {
      let where = "";
      try {
        const lines = String(new Error().stack).split("\n").filter((l) => l && !l.includes("pip.js"));
        where = lines.slice(0, 2).map((l) => l.replace(/https?:\/\/[^ ]*\//, "").slice(0, 60)).join(" < ");
      } catch (e) {}
      pauseCall = { at: Date.now(), where };
      return realPause.call(this);
    }, mproto, { defineAs: "pause" });
  } catch (e) {}
  document.addEventListener("pause", (e) => {
    if (!(e.target instanceof HTMLVideoElement)) return;
    const byPage = pauseCall && Date.now() - pauseCall.at < 300;
    const why = byPage ? "page called pause(): " + (pauseCall.where || "?") : "browser paused it";
    const state = "vis " + document.visibilityState + ", focus " + document.hasFocus();
    const p = connect();
    if (p) try { p.postMessage({ debug: why + " · " + state }); } catch (err) {}
    pauseCall = null;
  }, true);

  let port = null;
  let video = null;
  let filled = null;
  let scrolled = null;

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

  // Going in or out of fullscreen, the screen changes size in steps (the browser's bar and the
  // phone's bars go away a moment later). Players like YouTube's only place the video for the
  // size they saw first, which left it stuck at the top. Telling the page the size changed again
  // once things settle puts the video back in the middle.
  // Safety net: back at full size, the video goes back into its page even if the browser's
  // "small window closed" message never came.
  window.addEventListener("resize", () => {
    if (filled && (window.innerWidth > 480 || window.innerHeight > 480)) unfill();
  });

  document.addEventListener("fullscreenchange", () => {
    for (const ms of [120, 350, 800]) {
      setTimeout(() => {
        try { window.dispatchEvent(new Event("resize")); } catch (e) {}
      }, ms);
    }
  }, true);

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
    // Players pause a video whose own box has scrolled out of sight (YouTube does): its box is
    // brought into view first, and the page goes back to where it was afterwards.
    scrolled = [window.scrollX, window.scrollY];
    try { v.scrollIntoView({ block: "center", inline: "center" }); } catch (e) {}
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
    if (scrolled) {
      try { window.scrollTo(scrolled[0], scrolled[1]); } catch (e) {}
      scrolled = null;
    }
  }

  function onMessage(m) {
    if (m.pip === true) fill();
    if (m.pip === false) unfill();
    if (m.pause && video) video.pause();
    if (m.toggle && video) {
      if (video.paused) video.play().catch(() => {}); else video.pause();
    }
  }
})();
