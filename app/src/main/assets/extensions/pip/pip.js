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

  // While the video is in a small window or playing with the app in the background, the page
  // may not stop it: YouTube calls pause() itself when it notices. The play/pause button, the
  // media controls and the × still work - they don't go through the page.
  let hold = false;
  // The page's last pause that nobody asked for. YouTube often gets one in just before the
  // browser has said the video went small; it is undone as soon as the browser does.
  let strayPause = null;
  function resumeStray() {
    const s = strayPause;
    strayPause = null;
    // The swipe home is itself a touch on the page, so it can't count as "you paused it":
    // the browser only holds a video that was playing when you left.
    if (!s || Date.now() - s.at > 4000) return;
    try {
      if (s.el.paused && !s.el.ended) s.el.play().catch(() => {});
    } catch (err) {}
  }
  try {
    const mproto = window.wrappedJSObject.HTMLMediaElement.prototype;
    const realPause = mproto.pause;
    exportFunction(function () {
      // Held: nothing on the page can pause it. In the small windows the page can't be touched;
      // the window's own buttons and the media controls don't come through here.
      if (hold) {
        // The player now thinks it paused: told to play again in a moment.
        setTimeout(keepGoing, 300);
        return undefined;
      }
      strayPause = { at: Date.now(), el: this };
      return realPause.call(this);
    }, mproto, { defineAs: "pause" });
  } catch (e) {}

  let port = null;
  let video = null;
  let filled = null;
  let scrolled = null;

  // On while the video is in a small window or the app is in the background; off again at full size.
  function setHold(on) {
    // A fresh hold (you just left with it playing): an old pause of yours no longer counts.
    if (on && !hold) {
      userPaused = false;
      lastTime = -1;
      stuckSince = 0;
      loadedEnd = 0;
      loadedSince = Date.now();
    }
    hold = on;
    if (on) {
      resumeStray();
      if (!keeper) keeper = setInterval(keepGoing, 250);
    } else if (keeper) {
      clearInterval(keeper);
      keeper = null;
    }
  }

  // Blocking YouTube's pause() keeps the picture moving, but YouTube's player then thinks it is
  // paused and stops loading more of the video: it ran for about 30 seconds and stopped. While
  // held, the player itself is told to play again, so it keeps loading. Your own pause (the
  // window's buttons, the media controls) is remembered and left alone.
  let keeper = null;
  let userPaused = false;
  function youtube() {
    try {
      const mp = document.getElementById("movie_player");
      const p = mp && mp.wrappedJSObject;
      return p && typeof p.getPlayerState === "function" ? p : null;
    } catch (e) {
      return null;
    }
  }
  // How far ahead the video has loaded from where it's playing, in seconds.
  function ahead(v) {
    try {
      const b = v.buffered;
      for (let i = 0; i < b.length; i++) {
        if (b.start(i) <= v.currentTime + 0.25 && b.end(i) >= v.currentTime) return b.end(i) - v.currentTime;
      }
    } catch (e) {}
    return 0;
  }

  // Watching for the video running dry. Even told to play, YouTube sometimes stops loading the
  // rest while it's small or in the background: it plays what it has (about 30 seconds) and
  // stops. A seek to where it already is makes it load from there again. It's done early, while
  // there's still some loaded (no visible stop), and at once if it does stop.
  let lastTime = -1;
  let stuckSince = 0;
  let loadedEnd = 0;
  let loadedSince = 0;
  let nudgedAt = 0;
  function watchLoading(v, p, waiting) {
    const now = Date.now();
    const t = v.currentTime;
    if (!v.paused && Math.abs(t - lastTime) < 0.05) {
      if (!stuckSince) stuckSince = now;
    } else {
      stuckSince = 0;
    }
    lastTime = t;
    const left = ahead(v);
    const end = t + left;
    if (end > loadedEnd + 0.5) { loadedEnd = end; loadedSince = now; }
    if (end < loadedEnd - 5) { loadedEnd = end; loadedSince = now; } // moved back (a seek, the next video)
    if (v.ended) return;
    // Stopped: the video says it's waiting for data, or hasn't moved for a moment.
    const dry = (waiting && left < 0.5) || (stuckSince && now - stuckSince >= 300);
    // Nothing new loaded for a while and not much left: loading stopped, so it's restarted
    // before the video runs out.
    const stalling = left < 12 && now - loadedSince > 4000;
    if (!dry && !stalling) return;
    if (now - nudgedAt < (dry ? 1200 : 5000)) return;
    nudgedAt = now;
    loadedSince = now;
    stuckSince = 0;
    try {
      if (p && typeof p.seekTo === "function") {
        p.seekTo(p.getCurrentTime(), true);
        p.playVideo();
      } else {
        v.currentTime = t;
        if (v.paused) v.play().catch(() => {});
      }
    } catch (e) {}
  }

  function keepGoing(waiting) {
    if (!hold || userPaused) return;
    const v = (filledVideo && filledVideo.isConnected) ? filledVideo : video;
    if (!v || v.ended) return;
    let p = null;
    try {
      p = youtube();
      // 2 is YouTube's "paused"; 0 is "ended", which is left alone.
      if (p && p.getPlayerState() === 2) p.playVideo();
      if (v.paused) v.play().catch(() => {});
    } catch (e) {}
    watchLoading(v, p, waiting === true);
    // YouTube also stops after a long time with no touches ("Continue watching?").
    try { window.wrappedJSObject._lact = Date.now(); } catch (e) {}
  }

  // The moment the video runs out of data, not at the next check (timers slow down in the background).
  for (const t of ["waiting", "stalled"]) {
    document.addEventListener(t, (e) => {
      if (hold && e.target instanceof HTMLMediaElement) keepGoing(true);
    }, true);
  }

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
  // "small window closed" message never came. Only once it has stayed big for a moment (the
  // window passes through other sizes while it shrinks), and it stays held: whether the page
  // may pause it again is the browser's call (it says so when you're back in the app).
  let bigSince = 0;
  window.addEventListener("resize", () => {
    if (!filled) return;
    const big = window.innerWidth > 480 || window.innerHeight > 480;
    if (!big) { bigSince = 0; return; }
    if (!bigSince) bigSince = Date.now();
    const since = bigSince;
    setTimeout(() => {
      if (filled && bigSince === since && (window.innerWidth > 480 || window.innerHeight > 480)) {
        if (watch) { clearInterval(watch); watch = null; }
        restore();
        if (scrolled) {
          try { window.scrollTo(scrolled[0], scrolled[1]); } catch (e) {}
          scrolled = null;
        }
      }
    }, 1000);
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
    if (!scrolled) scrolled = [window.scrollX, window.scrollY];
    try { v.scrollIntoView({ block: "center", inline: "center" }); } catch (e) {}
    const set = (el, rules) => {
      saved.push([el, el.getAttribute("style")]);
      for (const [k, val] of rules) el.style.setProperty(k, val, "important");
    };
    set(v, FILL);
    for (let el = v.parentElement; el && el !== document.documentElement; el = el.parentElement) set(el, FREE);
    set(document.documentElement, [["overflow", "hidden"], ["background", "#000"]]);
    filled = saved;
    filledVideo = v;
    video = v;
    setHold(true);
    if (!watch) watch = setInterval(check, 1000);
  }

  // While the video fills a small window: the next video, an ad in its own player, or the site
  // redrawing its player can leave the window showing the page's header over black. Checked
  // every second, and the right video is put back in the window.
  let filledVideo = null;
  let watch = null;
  function check() {
    if (!filled) return;
    const kept = filledVideo && filledVideo.isConnected;
    const cur = playing() || (kept ? filledVideo : document.querySelector("video"));
    const lost = !kept || filledVideo.style.getPropertyValue("position") !== "fixed";
    if (cur && (cur !== filledVideo || lost)) {
      restore();
      video = cur;
      fill();
    }
  }

  function restore() {
    if (!filled) return;
    for (const [el, style] of filled.reverse()) {
      if (style === null) el.removeAttribute("style"); else el.setAttribute("style", style);
    }
    filled = null;
    filledVideo = null;
  }

  function unfill() {
    setHold(false);
    if (watch) { clearInterval(watch); watch = null; }
    if (!filled) return;
    restore();
    if (scrolled) {
      try { window.scrollTo(scrolled[0], scrolled[1]); } catch (e) {}
      scrolled = null;
    }
  }

  function onMessage(m) {
    if (m.pip === true) fill();
    if (m.pip === false) unfill();
    if (m.hold === true) setHold(true);
    if (m.hold === false && !filled) setHold(false);
    if (m.pause && video) {
      userPaused = true;
      video.pause();
    }
    if (m.userPaused === true) userPaused = true;
    if (m.userPaused === false) userPaused = false;
    if (m.toggle && video) {
      if (video.paused) {
        userPaused = false;
        const p = youtube();
        if (p) try { p.playVideo(); } catch (e) {}
        video.play().catch(() => {});
      } else {
        userPaused = true;
        video.pause();
      }
    }
  }
})();
