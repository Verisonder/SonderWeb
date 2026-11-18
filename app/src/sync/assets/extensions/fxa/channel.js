/*
 * Mozilla's sign-in page talks to the browser through "WebChannel" events: it asks what the
 * browser supports and, once you've signed in, hands it the result. They are passed to SonderWeb
 * and its answers back to the page.
 */
"use strict";

const port = browser.runtime.connectNative("fxa");

port.onMessage.addListener(message => {
  window.dispatchEvent(
    new CustomEvent("WebChannelMessageToContent", { detail: JSON.stringify(message) })
  );
});

window.addEventListener("WebChannelMessageToChrome", event => {
  try {
    port.postMessage(JSON.parse(event.detail));
  } catch (e) {
    // Not a message for the browser.
  }
});
