(function () {
  "use strict";

  const STYLE_ID = "khobor-live-dark-style";
  const STORAGE_KEY = "khoborDarkEnabled";
  const OVERLAY_CSS = `
html, body {
  background: #080808 !important;
  color: #F5F2EC !important;
  color-scheme: dark !important;
}
body, main, article, section, header, footer, nav, aside,
div, p, span, li, ul, ol, table, tbody, thead, tfoot, tr, td, th,
form, label, figure, figcaption, blockquote {
  border-color: #2A2A2A !important;
  color: inherit !important;
}
main, article, section, header, footer, nav, aside,
div, table, tbody, thead, tfoot, tr, td, th, form {
  background-color: transparent !important;
}
h1, h2, h3, h4, h5, h6, p, span, li, blockquote, figcaption,
strong, b, em, small, label, time {
  color: #F5F2EC !important;
}
a, a span {
  color: #F5F2EC !important;
}
input, textarea, select, button {
  background: #151515 !important;
  color: #F5F2EC !important;
  border-color: #333333 !important;
}
*[style*="background:#fff"],
*[style*="background: #fff"],
*[style*="background-color:#fff"],
*[style*="background-color: #fff"],
*[style*="background:white"],
*[style*="background: white"],
*[style*="background-color:white"],
*[style*="background-color: white"] {
  background-color: #151515 !important;
}
img, picture, video, svg, canvas, iframe {
  filter: none !important;
  opacity: 1 !important;
}
`;

  function styleParent() {
    return document.head || document.documentElement;
  }

  function applyDarkOverlay(enabled) {
    const existing = document.getElementById(STYLE_ID);
    if (!enabled) {
      if (existing) existing.remove();
      return;
    }
    const parent = styleParent();
    if (!parent) return;
    const style = existing || document.createElement("style");
    style.id = STYLE_ID;
    style.textContent = OVERLAY_CSS;
    if (!existing) parent.appendChild(style);
  }

  function setEnabled(enabled) {
    applyDarkOverlay(enabled);
    if (typeof browser !== "undefined" && browser.storage && browser.storage.local) {
      browser.storage.local.set({ [STORAGE_KEY]: enabled }).catch(function () {});
    }
  }

  function syncFromNative() {
    if (typeof browser === "undefined" || !browser.runtime || !browser.runtime.sendNativeMessage) {
      return;
    }
    browser.runtime.sendNativeMessage("khobor_dark", { type: "GET_KHOBOR_DARK" }).then(function (message) {
      if (message && message.type === "SET_KHOBOR_DARK") {
        setEnabled(message.enabled === true);
      }
    }).catch(function () {});
  }

  if (typeof browser !== "undefined" && browser.storage && browser.storage.local) {
    browser.storage.local.get({ [STORAGE_KEY]: false }).then(function (value) {
      applyDarkOverlay(value[STORAGE_KEY] === true);
      syncFromNative();
    }).catch(function () {
      syncFromNative();
    });
  } else {
    syncFromNative();
  }

  if (typeof browser !== "undefined" && browser.runtime) {
    browser.runtime.onMessage.addListener(function (message) {
      if (message && message.type === "SET_KHOBOR_DARK") {
        setEnabled(message.enabled === true);
      }
    });
  }
})();
