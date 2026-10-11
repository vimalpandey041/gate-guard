/**
 * GATE Guard v3.2 — Content Script — DIRECT LINK & APPROVED CHANNEL GATE
 * - YouTube Home / Dashboard is 100% BLOCKED.
 * - YouTube Search (/results) is 100% BLOCKED.
 * - Non-approved channels are 100% BLOCKED.
 * - Search bar, comments, live chat, and related videos are hidden.
 * - ONLY direct video links of approved GATE channels can play.
 * - DevTools proof overlay guard. SPA-safe.
 */

let allowedChannels = [
  "pw-solutions",
  "GOClassesforGATECS",
  "GATEWallahbyPW",
  "gatewallah_cse_da",
  "GATEWallahCSE&DA",
  "GATE Wallah CSE & DA",
  "Gatecsit-dsai",
  "UnacademyComputerScience",
  "GfG_GATE",
  "AmitKhuranaSir",
  "DreamMaths",
  "KNOWLEDGEGATE_kg",
  "Knowledge Gate",
  "GateSmashers",
  "Gate Smashers",
  "GATEWallah",
  "GATE Wallah",
  "UnacademyGATECSIT&DA",
  "Unacademy GATE CS IT & DA"
];

// Fetch dynamically from storage
chrome.storage.local.get(["allowedChannels"], (res) => {
  if (res.allowedChannels && Array.isArray(res.allowedChannels) && res.allowedChannels.length > 0) {
    allowedChannels = res.allowedChannels;
  }
});

chrome.storage.onChanged.addListener((changes, namespace) => {
  if (namespace === "local" && changes.allowedChannels) {
    if (Array.isArray(changes.allowedChannels.newValue) && changes.allowedChannels.newValue.length > 0) {
      allowedChannels = changes.allowedChannels.newValue;
      handlePage();
    }
  }
});

let lastUrl = "";
let overlayEl = null;
let isCurrentPageBlocked = false;
let guardInterval = null;
let currentBlockMessage = "";

// ── Strict CSS Injection ──────────────────────────────────────────

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

    /* Hide Live Chat & Chat Replay */
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

function normalizeChannel(str) {
  if (!str) return "";
  return str.toLowerCase().replace(/[^a-z0-9]/g, "");
}

function isChannelAllowed(identifier) {
  if (!identifier) return false;
  const norm = normalizeChannel(identifier);
  if (!norm) return false;
  return allowedChannels.some(ch => {
    if (!ch) return false;
    const chNorm = normalizeChannel(ch);
    return ch.toLowerCase() === identifier.toLowerCase() || chNorm === norm;
  });
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

function removeDistractions() {
  try {
    const comments = document.querySelector("#comments, ytd-comments");
    if (comments) comments.style.setProperty("display", "none", "important");

    const chat = document.querySelector("#chat, #chat-container, ytd-live-chat-frame, iframe#chatframe");
    if (chat) chat.style.setProperty("display", "none", "important");

    document.querySelectorAll("ytd-engagement-panel-section-list-renderer[target-id*=\"chat\"], ytd-engagement-panel-section-list-renderer[target-id*=\"Chat\"], [target-id*=\"live-chat\"]").forEach(el => {
      el.style.setProperty("display", "none", "important");
    });

    document.querySelectorAll("ytd-item-section-renderer, ytd-engagement-panel-section-list-renderer").forEach(el => {
      const text = el.innerText || "";
      if ((text.includes("Live chat replay") || text.includes("Top chat replay") || text.includes("Live chat")) && !el.querySelector("ytd-watch-metadata")) {
        el.style.setProperty("display", "none", "important");
      }
    });
  } catch (e) {}
}

// ── Block Overlay ─────────────────────────────────────────────────

function showBlockOverlay(message) {
  currentBlockMessage = message;
  isCurrentPageBlocked = true;
  pauseAllVideos();

  // Hide underlying page content so nothing can show through
  try {
    const pageManager = document.querySelector("ytd-page-manager");
    if (pageManager) pageManager.style.setProperty("display", "none", "important");
  } catch (e) {}

  if (overlayEl && document.contains(overlayEl)) {
    const msgEl = overlayEl.querySelector(".gate-guard-msg");
    if (msgEl) msgEl.innerHTML = message;
    return;
  }

  overlayEl = document.createElement("div");
  overlayEl.id = "gate-guard-block";
  overlayEl.style.cssText = `
    position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important;
    background: #0a0a0f !important; z-index: 2147483647 !important;
    display: flex !important; flex-direction: column !important;
    align-items: center !important; justify-content: center !important;
    font-family: Inter, -apple-system, sans-serif !important;
    color: #fff !important; text-align: center !important; padding: 24px !important; box-sizing: border-box !important;
  `;
  overlayEl.innerHTML = `
    <div style="width:72px;height:72px;background:linear-gradient(135deg,#ef4444,#dc2626);border-radius:20px;display:flex;align-items:center;justify-content:center;margin-bottom:20px;box-shadow:0 8px 32px rgba(239,68,68,0.35);">
      <svg width="36" height="36" viewBox="0 0 24 24" fill="white"><path d="M12 2L3 7v5c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V7l-9-5zm-1 14.59l-3.29-3.3 1.41-1.41L11 13.77l4.88-4.88 1.41 1.41L11 16.59z"/></svg>
    </div>
    <div style="font-size:1.6rem;font-weight:800;margin-bottom:12px;background:linear-gradient(135deg,#f87171,#fbbf24);-webkit-background-clip:text;-webkit-text-fill-color:transparent;">GATE Guard — Focus Mode</div>
    <div class="gate-guard-msg" style="color:#d4d4d8;font-size:1rem;margin-bottom:24px;line-height:1.5;max-width:520px;">${message}</div>
    <div style="padding:8px 18px;background:rgba(239,68,68,0.12);border:1px solid rgba(239,68,68,0.3);border-radius:12px;color:#fca5a5;font-size:0.85rem;font-weight:600;letter-spacing:0.5px;">
      🔒 GATE Guard Focus Lock Active
    </div>
  `;

  if (document.body) {
    document.body.appendChild(overlayEl);
  } else {
    document.documentElement.appendChild(overlayEl);
  }
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
    } else if (document.body && overlay.parentNode !== document.body) {
      document.body.appendChild(overlay);
    }
  }, 200);
}

function removeBlockOverlay() {
  isCurrentPageBlocked = false;
  currentBlockMessage = "";
  if (guardInterval) { clearInterval(guardInterval); guardInterval = null; }
  if (overlayEl) { overlayEl.remove(); overlayEl = null; }
  try {
    const pageManager = document.querySelector("ytd-page-manager");
    if (pageManager) pageManager.style.removeProperty("display");
  } catch (e) {}
}

// ── Channel Extraction & Multi-Identifier Verification ───────────

function getWatchPageChannels() {
  const found = new Set();

  // 1. Meta tag author url (exact handle e.g. /@gatewallah_cse_da)
  const authorUrlLink = document.querySelector('span[itemprop="author"] link[itemprop="url"]');
  if (authorUrlLink) {
    const href = authorUrlLink.getAttribute("href") || "";
    const matchHandle = href.match(/\/@([^\/?#]+)/);
    if (matchHandle) found.add(matchHandle[1]);
    const matchId = href.match(/\/(channel|c|user)\/([^\/?#]+)/);
    if (matchId) found.add(matchId[2]);
  }

  // 2. Meta tag author name (display name e.g. GATE Wallah CSE & DA)
  const authorNameLink = document.querySelector('span[itemprop="author"] link[itemprop="name"]');
  if (authorNameLink) {
    const name = authorNameLink.getAttribute("content");
    if (name) {
      found.add(name);
      found.add(name.trim().replace(/\s+/g, ""));
    }
  }

  // 3. DOM owner / channel links
  const ownerLinks = document.querySelectorAll(
    "#owner a[href*=\"/@\"], ytd-video-owner-renderer a[href*=\"/@\"], ytd-channel-name a[href*=\"/@\"]"
  );
  ownerLinks.forEach(link => {
    const href = link.getAttribute("href") || "";
    const match = href.match(/\/@([^\/?#]+)/);
    if (match) found.add(match[1]);
  });

  // 4. DOM channel name text
  const textEl = document.querySelector(
    "ytd-video-owner-renderer ytd-channel-name yt-formatted-string, #owner ytd-channel-name yt-formatted-string, #channel-name .yt-formatted-string"
  );
  if (textEl && textEl.innerText) {
    const t = textEl.innerText.trim();
    found.add(t);
    found.add(t.replace(/\s+/g, ""));
  }

  // 5. Document title fallback
  const title = document.title;
  if (title && title.includes(" - ")) {
    const parts = title.split(" - ");
    if (parts.length >= 2) {
      const possible = parts[parts.length - 2].trim();
      if (possible !== "YouTube") {
        found.add(possible);
        found.add(possible.replace(/\s+/g, ""));
      }
    }
  }

  return Array.from(found);
}

// ── Watch Page Check (SPA-safe) ───────────────────────────────────

let allowedCount = 0;
function checkWatchPage() {
  const channels = getWatchPageChannels();
  if (channels.length > 0) {
    const allowedMatch = channels.find(ch => isChannelAllowed(ch));
    if (allowedMatch) {
      removeBlockOverlay();
    } else {
      const displayChannel = channels[0];
      showBlockOverlay(
        "Blocked by GATE Guard<br>" +
        "<span style=\"font-size:15px;color:#a1a1aa;margin-top:6px;display:block;\">" +
        "Channel <b>" + displayChannel + "</b> is not in your approved GATE list.</span>"
      );
    }
  } else {
    allowedCount++;
    if (allowedCount < 25) {
      setTimeout(checkWatchPage, 300);
    } else {
      showBlockOverlay(
        "Blocked by GATE Guard<br>" +
        "<span style=\"font-size:15px;color:#a1a1aa;margin-top:6px;display:block;\">" +
        "Could not verify channel — blocked for safety.</span>"
      );
    }
  }
}

// ── Main Page Handler ─────────────────────────────────────────────

function handlePage() {
  if (window.location.hostname === "music.youtube.com") return;

  const path = window.location.pathname;
  const currentUrl = window.location.href;

  injectCSS();
  removeDistractions();

  // 1. LAYER 1: STRICT NON-VIDEO BLOCKING (Dashboard, Search, Feeds, Channels)
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

    // Check Channel page (/@handle)
    if (path.startsWith("/@")) {
      const handlePart = path.split("/")[1];
      const handle = decodeURIComponent(handlePart.substring(1));
      if (!isChannelAllowed(handle)) {
        showBlockOverlay(
          "Channel Blocked<br>" +
          "<span style=\"font-size:15px;color:#a1a1aa;margin-top:8px;display:block;\">" +
          "Channel <b>@" + handle + "</b> is not in your approved GATE list.<br>" +
          "<b>Access denied.</b></span>"
        );
        return;
      }
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
