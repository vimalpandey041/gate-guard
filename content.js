/**
 * GATE Guard v3.2 — Content Script — DIRECT LINK ONLY MODE
 * - YouTube Home / Dashboard is 100% BLOCKED.
 * - YouTube Search (/results) is 100% BLOCKED.
 * - Search bar and related videos are hidden.
 * - ONLY direct video links (/watch, /playlist, /embed, /live) are allowed.
 * - Allowed GATE channels are verified on watch pages.
 * - DevTools proof overlay guard. SPA-safe.
 */

let allowedChannels = [
  "pw-solutions",
  "GOClassesforGATECS",
  "GATEWallahbyPW",
  "gatewallah_cse_da",
  "Gatecsit-dsai",
  "UnacademyComputerScience",
  "GfG_GATE",
  "AmitKhuranaSir",
  "DreamMaths"
];

// Fetch dynamically from storage
chrome.storage.local.get(["allowedChannels"], (res) => {
  if (res.allowedChannels) allowedChannels = res.allowedChannels;
});
chrome.storage.onChanged.addListener((changes, namespace) => {
  if (namespace === "local" && changes.allowedChannels) {
    allowedChannels = changes.allowedChannels.newValue;
    handlePage(); // re-filter immediately
  }
});

let lastUrl = "";
let overlayEl = null;
let isCurrentPageBlocked = false;
let guardInterval = null;
let currentBlockMessage = "";

// ── Strict CSS Injection: Hide Search, Recommendations & Miniplayer ─

function injectCSS() {
  if (document.getElementById("gate-guard-css")) return;
  const style = document.createElement("style");
  style.id = "gate-guard-css";
  style.textContent = `
    /* Hide YouTube Search Box everywhere */
    ytd-searchbox,
    #search,
    #search-input,
    #search-button,
    #voice-search-button,
    button#search-icon-legacy,
    ytd-search-box-renderer,
    ytd-masthead #center {
      display: none !important;
      visibility: hidden !important;
      pointer-events: none !important;
    }

    /* Hide Home Dashboard Feeds */
    ytd-browse[page-subtype="home"],
    ytd-rich-grid-renderer {
      display: none !important;
    }

    /* Hide Sidebar Recommendations on Watch page */
    #related,
    ytd-watch-next-secondary-results-renderer,
    #secondary-inner #related {
      display: none !important;
    }

    /* Hide Shorts everywhere */
    ytd-reel-shelf-renderer,
    ytd-rich-shelf-renderer[is-shorts],
    ytd-guide-entry-renderer:has(a[title="Shorts"]),
    ytd-mini-guide-entry-renderer[aria-label="Shorts"] {
      display: none !important;
    }

        /* Hide Comments completely */
    #comments,
    ytd-comments,
    #comment-teaser,
    ytd-item-section-renderer[section-identifier="comment-item-section"],
    ytd-engagement-panel-section-list-renderer[target-id="engagement-panel-comments-section"],
    ytd-engagement-panel-section-list-renderer[target-id="comments"] {
      display: none !important;
      visibility: hidden !important;
      pointer-events: none !important;
    }

    /* Hide Live Chat, Chat Replay & Chat Teaser Panels */
    #chat,
    #chat-container,
    #chatframe,
    ytd-live-chat-frame,
    iframe#chatframe,
    #chat-messages,
    ytd-item-section-renderer:has(#chat),
    ytd-item-section-renderer:has(ytd-live-chat-frame),
    ytd-item-section-renderer:has(iframe#chatframe),
    ytd-engagement-panel-section-list-renderer[target-id*="chat"],
    ytd-engagement-panel-section-list-renderer[target-id*="Chat"],
    ytd-engagement-panel-section-list-renderer[target-id*="live-chat"],
    [target-id*="chat"],
    [target-id*="Chat"],
    ytd-button-renderer:has(button[aria-label*="chat" i]),
    ytd-button-renderer:has(button[aria-label*="Chat" i]) {
      display: none !important;
      visibility: hidden !important;
      pointer-events: none !important;
    }

    /* Hide Miniplayer */
    .ytp-miniplayer-button,
    button[aria-label="Miniplayer"],
    .ytp-pip-button {
      display: none !important;
      pointer-events: none !important;
    }
  `;
  (document.head || document.documentElement).appendChild(style);
}
injectCSS();

// ── Helpers ───────────────────────────────────────────────────────

function isChannelAllowed(handle) {
  if (!handle) return false;
  return allowedChannels.some(ch => ch.toLowerCase() === handle.toLowerCase());
}

function isDirectVideoPath(path) {
  return (
    path.startsWith("/watch") ||
    path.startsWith("/playlist") ||
    path.startsWith("/embed") ||
    path.startsWith("/live")
  );
}

function pauseAllVideos() {
  try {
    document.querySelectorAll("video").forEach(v => {
      v.pause();
      v.currentTime = 0;
    });
  } catch (e) {}
}

// ── Block Overlay ─────────────────────────────────────────────────

function showBlockOverlay(message) {
  currentBlockMessage = message;
  isCurrentPageBlocked = true;
  pauseAllVideos();

  if (overlayEl && document.contains(overlayEl)) {
    const msgEl = overlayEl.querySelector(".gate-guard-msg");
    if (msgEl) msgEl.innerHTML = message;
    return;
  }

  overlayEl = document.createElement("div");
  overlayEl.id = "gate-guard-block";
  overlayEl.style.cssText = `
    position: fixed; top: 0; left: 0; width: 100vw; height: 100vh;
    background: #0a0a0f; z-index: 2147483647;
    display: flex; flex-direction: column;
    align-items: center; justify-content: center;
    font-family: Inter, -apple-system, sans-serif;
    color: #fff; text-align: center; padding: 24px; box-sizing: border-box;
  `;
  overlayEl.innerHTML = `
    <div style="width:72px;height:72px;background:linear-gradient(135deg,#ef4444,#dc2626);border-radius:20px;display:flex;align-items:center;justify-content:center;margin-bottom:20px;box-shadow:0 8px 32px rgba(239,68,68,0.35);">
      <svg width="36" height="36" viewBox="0 0 24 24" fill="white"><path d="M12 2L3 7v5c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V7l-9-5zm-1 14.59l-3.29-3.3 1.41-1.41L11 13.77l4.88-4.88 1.41 1.41L11 16.59z"/></svg>
    </div>
    <div style="font-size:1.6rem;font-weight:800;margin-bottom:12px;background:linear-gradient(135deg,#f87171,#fbbf24);-webkit-background-clip:text;-webkit-text-fill-color:transparent;">GATE Guard — Focus Mode</div>
    <div class="gate-guard-msg" style="color:#d4d4d8;font-size:1rem;margin-bottom:24px;line-height:1.5;max-width:520px;">${message}</div>
    <div style="padding:8px 18px;background:rgba(239,68,68,0.12);border:1px solid rgba(239,68,68,0.3);border-radius:12px;color:#fca5a5;font-size:0.85rem;font-weight:600;letter-spacing:0.5px;">
      🔒 Direct Video Link Layer Active
    </div>
  `;
  (document.documentElement || document.body).appendChild(overlayEl);
  startOverlayGuard();
}

function startOverlayGuard() {
  if (guardInterval) clearInterval(guardInterval);
  guardInterval = setInterval(() => {
    if (!isCurrentPageBlocked) {
      clearInterval(guardInterval);
      guardInterval = null;
      return;
    }
    pauseAllVideos();
    const overlay = document.getElementById("gate-guard-block");
    if (!overlay || !document.contains(overlay)) {
      overlayEl = null;
      showBlockOverlay(currentBlockMessage);
    }
  }, 200);
}

function removeBlockOverlay() {
  isCurrentPageBlocked = false;
  currentBlockMessage = "";
  if (guardInterval) { clearInterval(guardInterval); guardInterval = null; }
  if (overlayEl) { overlayEl.remove(); overlayEl = null; }
}

// ── Channel Extraction ────────────────────────────────────────────

function getWatchPageChannel() {
  // 1. Try to get from meta tags (instant)
  const authorNameLink = document.querySelector('span[itemprop="author"] link[itemprop="name"]');
  if (authorNameLink) {
    const name = authorNameLink.getAttribute("content");
    if (name) return name.trim().replace(/\s+/g, "");
  }

  const authorUrlLink = document.querySelector('span[itemprop="author"] link[itemprop="url"]');
  if (authorUrlLink) {
    const href = authorUrlLink.getAttribute("href") || "";
    const match = href.match(/\/@([^\/?#]+)/);
    if (match) return match[1];
    const matchFallback = href.match(/\/(channel|c|user)\/([^\/?#]+)/);
    if (matchFallback) return matchFallback[2];
  }

  // 2. Try to get from DOM elements
  const selectors = [
    "ytd-video-owner-renderer a.yt-simple-endpoint",
    "#owner a.yt-simple-endpoint",
    "ytd-channel-name a.yt-simple-endpoint"
  ];
  for (const sel of selectors) {
    const el = document.querySelector(sel);
    if (el) {
      const href = el.getAttribute("href") || el.getAttribute("content") || "";
      const match = href.match(/\/@([^\/?#]+)/);
      if (match) return match[1];
      const matchFallback = href.match(/\/(channel|c|user)\/([^\/?#]+)/);
      if (matchFallback) return matchFallback[2];
    }
  }

  // 3. Fallback: text content
  const textEl = document.querySelector("ytd-video-owner-renderer ytd-channel-name yt-formatted-string, #owner ytd-channel-name yt-formatted-string, #channel-name .yt-formatted-string");
  if (textEl && textEl.innerText) {
    return textEl.innerText.trim().replace(/\s+/g, "");
  }

  // 4. Page title fallback
  const title = document.title;
  if (title && title.includes(" - ")) {
    const parts = title.split(" - ");
    if (parts.length >= 2) {
      const possibleChannel = parts[parts.length - 2];
      if (possibleChannel !== "YouTube") {
         return possibleChannel.trim().replace(/\s+/g, "");
      }
    }
  }

  return null;
}

// ── Watch Page Check (SPA-safe) ───────────────────────────────────

let allowedCount = 0;
function checkWatchPage() {
  const channel = getWatchPageChannel();
  if (channel) {
    if (isChannelAllowed(channel)) {
      removeBlockOverlay();
    } else {
      showBlockOverlay("Blocked by GATE Guard<br><span style=\"font-size:15px;color:#a1a1aa;margin-top:6px;display:block;\">Channel <b>" + channel + "</b> is not in your approved GATE list.</span>");
    }
  } else {
    allowedCount++;
    if (allowedCount < 20) {
      setTimeout(checkWatchPage, 400);
    } else {
      showBlockOverlay("Blocked by GATE Guard<br><span style=\"font-size:15px;color:#a1a1aa;margin-top:6px;display:block;\">Could not verify channel — blocked for safety.</span>");
    }
  }
}


function removeDistractions() {
  // Hide comments
  const comments = document.querySelector("#comments, ytd-comments");
  if (comments) comments.style.setProperty("display", "none", "important");

  // Hide live chat
  const chat = document.querySelector("#chat, #chat-container, ytd-live-chat-frame, iframe#chatframe");
  if (chat) chat.style.setProperty("display", "none", "important");

  // Hide chat teaser / panels
  document.querySelectorAll("ytd-engagement-panel-section-list-renderer[target-id*=\"chat\"], ytd-engagement-panel-section-list-renderer[target-id*=\"Chat\"], [target-id*=\"live-chat\"]").forEach(el => {
    el.style.setProperty("display", "none", "important");
  });

  // Find any teaser card that contains Live chat text
  document.querySelectorAll("ytd-item-section-renderer, ytd-engagement-panel-section-list-renderer").forEach(el => {
    const text = el.innerText || "";
    if ((text.includes("Live chat replay") || text.includes("Top chat replay") || text.includes("Live chat")) && !el.querySelector("ytd-watch-metadata")) {
      el.style.setProperty("display", "none", "important");
    }
  });
}

// ── Main Page Handler ─────────────────────────────────────────────

function handlePage() {
  if (window.location.hostname === "music.youtube.com") return;

  const path = window.location.pathname;
  const currentUrl = window.location.href;

  injectCSS();
  removeDistractions();

  // 1. LAYER 1: STRICT NON-VIDEO BLOCKING (Dashboard, Search, Feeds)
  if (!isDirectVideoPath(path)) {
    lastUrl = currentUrl;
    pauseAllVideos();

    if (path === "/" || path === "") {
      showBlockOverlay(
        "YouTube Dashboard is Disabled<br>" +
        "<span style=\"font-size:15px;color:#a1a1aa;margin-top:8px;display:block;\">" +
        "Browsing the home feed is blocked.<br>" +
        "<b>Sirf direct lecture link hone par hi video chalegi.</b></span>"
      );
      return;
    }

    if (path.startsWith("/results")) {
      showBlockOverlay(
        "YouTube Search is Blocked<br>" +
        "<span style=\"font-size:15px;color:#a1a1aa;margin-top:8px;display:block;\">" +
        "Searching YouTube is completely disabled.<br>" +
        "<b>Direct video link use karo.</b></span>"
      );
      return;
    }

    if (path.startsWith("/shorts")) {
      showBlockOverlay(
        "Shorts are Blocked<br>" +
        "<span style=\"font-size:15px;color:#a1a1aa;margin-top:8px;display:block;\">" +
        "Stay focused on GATE preparation.</span>"
      );
      return;
    }

    showBlockOverlay(
      "Browsing is Blocked<br>" +
      "<span style=\"font-size:15px;color:#a1a1aa;margin-top:8px;display:block;\">" +
      "Only direct video links are allowed.<br>" +
      "<b>No browsing or feeds permitted.</b></span>"
    );
    return;
  }

  // 2. LAYER 2: DIRECT VIDEO LINK (/watch, /playlist, /embed, /live)
  if (currentUrl !== lastUrl) {
    lastUrl = currentUrl;
    removeBlockOverlay();

    if (path.startsWith("/watch")) {
      allowedCount = 0;
      showBlockOverlay("Verifying channel...");
      checkWatchPage();
    }
  }
}

// ── Observer & Events ─────────────────────────────────────────────

function setupObserver() {
  new MutationObserver(() => {
    if (window.location.hostname === "music.youtube.com") return;
    injectCSS();
    removeDistractions();
    const path = window.location.pathname;
    if (!isDirectVideoPath(path) && !isCurrentPageBlocked) {
      handlePage();
    }
  }).observe(document.documentElement, {
    childList: true, subtree: true,
  });
}

window.addEventListener("yt-navigate-finish", () => handlePage());
window.addEventListener("popstate", () => handlePage());

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", () => { handlePage(); setupObserver(); });
} else {
  handlePage(); setupObserver();
}
