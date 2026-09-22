/**
 * GATE Guard v3.1 — Content Script — CLEAN BUILD
 * Filters YouTube to only allowed GATE channels.
 * DevTools proof. No video killing. SPA-safe.
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
chrome.storage.local.get(['allowedChannels'], (res) => {
  if (res.allowedChannels) allowedChannels = res.allowedChannels;
});
chrome.storage.onChanged.addListener((changes, namespace) => {
  if (namespace === 'local' && changes.allowedChannels) {
    allowedChannels = changes.allowedChannels.newValue;
    handlePage(); // re-filter immediately
  }
});


let lastUrl = '';
let watchCheckTimer = null;
let overlayEl = null;
let isCurrentPageBlocked = false;
let guardInterval = null;

// ── Miniplayer Button Hider (CSS only, no JS DOM manipulation) ────

function injectCSS() {
  if (document.getElementById('gate-guard-css')) return;
  const style = document.createElement('style');
  style.id = 'gate-guard-css';
  style.textContent = `
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

// ── Block Overlay ─────────────────────────────────────────────────

function showBlockOverlay(message) {
  if (overlayEl && document.contains(overlayEl)) {
    const msgEl = overlayEl.querySelector('.gate-guard-msg');
    if (msgEl) msgEl.innerHTML = message;
    return;
  }
  isCurrentPageBlocked = true;
  overlayEl = document.createElement('div');
  overlayEl.id = 'gate-guard-block';
  overlayEl.style.cssText = `
    position: fixed; top: 0; left: 0; width: 100vw; height: 100vh;
    background: #0a0a0f; z-index: 2147483647;
    display: flex; flex-direction: column;
    align-items: center; justify-content: center;
    font-family: Inter, -apple-system, sans-serif;
  `;
  overlayEl.innerHTML = `
    <div style="width:70px;height:70px;background:linear-gradient(135deg,#ef4444,#dc2626);border-radius:18px;display:flex;align-items:center;justify-content:center;margin-bottom:20px;box-shadow:0 8px 32px rgba(239,68,68,0.3);">
      <svg width="36" height="36" viewBox="0 0 24 24" fill="white"><path d="M12 2L3 7v5c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V7l-9-5zm-1 14.59l-3.29-3.3 1.41-1.41L11 13.77l4.88-4.88 1.41 1.41L11 16.59z"/></svg>
    </div>
    <div style="font-size:1.5rem;font-weight:800;margin-bottom:8px;background:linear-gradient(135deg,#f87171,#fbbf24);-webkit-background-clip:text;-webkit-text-fill-color:transparent;">Blocked by GATE Guard</div>
    <div class="gate-guard-msg" style="color:#a1a1aa;font-size:0.95rem;margin-bottom:24px;"></div>
    <a href="https://www.youtube.com/" style="padding:10px 20px;background:rgba(139,92,246,0.15);border:1px solid rgba(139,92,246,0.3);border-radius:10px;color:#a78bfa;text-decoration:none;font-size:0.85rem;font-weight:600;">Go to Home</a>
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
    const overlay = document.getElementById('gate-guard-block');
    if (!overlay || !document.contains(overlay)) {
      clearInterval(guardInterval);
      guardInterval = null;
      window.location.replace('https://www.youtube.com/');
    }
  }, 200);
}

function removeBlockOverlay() {
  isCurrentPageBlocked = false;
  if (guardInterval) { clearInterval(guardInterval); guardInterval = null; }
  if (overlayEl) { overlayEl.remove(); overlayEl = null; }
}

// ── Channel Extraction ────────────────────────────────────────────

function extractHandleFromCard(card) {
  const links = card.querySelectorAll('a[href*="/@"]');
  for (const link of links) {
    const match = link.getAttribute('href').match(/\/@([^\/?#]+)/);
    if (match) return match[1];
  }
  return null;
}

function getWatchPageChannel() {
  // 1. Try to get from meta tags (instant)
  const authorNameLink = document.querySelector('span[itemprop="author"] link[itemprop="name"]');
  if (authorNameLink) {
    const name = authorNameLink.getAttribute('content');
    if (name) return name.trim().replace(/\s+/g, '');
  }

  const authorUrlLink = document.querySelector('span[itemprop="author"] link[itemprop="url"]');
  if (authorUrlLink) {
    const href = authorUrlLink.getAttribute('href') || '';
    const match = href.match(/\/@([^\/?#]+)/);
    if (match) return match[1];
    const matchFallback = href.match(/\/(channel|c|user)\/([^\/?#]+)/);
    if (matchFallback) return matchFallback[2];
  }

  // 2. Try to get from DOM elements
  const selectors = [
    'ytd-video-owner-renderer a.yt-simple-endpoint',
    '#owner a.yt-simple-endpoint',
    'ytd-channel-name a.yt-simple-endpoint'
  ];
  for (const sel of selectors) {
    const el = document.querySelector(sel);
    if (el) {
      const href = el.getAttribute('href') || el.getAttribute('content') || '';
      const match = href.match(/\/@([^\/?#]+)/);
      if (match) return match[1];
      const matchFallback = href.match(/\/(channel|c|user)\/([^\/?#]+)/);
      if (matchFallback) return matchFallback[2];
    }
  }

  // 3. Fallback: text content
  const textEl = document.querySelector('ytd-video-owner-renderer ytd-channel-name yt-formatted-string, #owner ytd-channel-name yt-formatted-string, #channel-name .yt-formatted-string');
  if (textEl && textEl.innerText) {
    return textEl.innerText.trim().replace(/\s+/g, '');
  }

  // 4. Page title fallback
  const title = document.title;
  if (title && title.includes(' - ')) {
    const parts = title.split(' - ');
    if (parts.length >= 2) {
      const possibleChannel = parts[parts.length - 2];
      if (possibleChannel !== 'YouTube') {
         return possibleChannel.trim().replace(/\s+/g, '');
      }
    }
  }

  return null;
}

// ── Filter Video Cards ────────────────────────────────────────────

function filterVideoCards() {
  const cards = document.querySelectorAll(
    'ytd-rich-item-renderer, ytd-video-renderer, ytd-grid-video-renderer, ytd-compact-video-renderer, ytd-reel-item-renderer'
  );
  cards.forEach(card => {
    if (card.dataset.gateFiltered === lastUrl) return;
    const handle = extractHandleFromCard(card);
    if (handle !== null) {
      card.dataset.gateFiltered = lastUrl;
      card.style.display = isChannelAllowed(handle) ? '' : 'none';
    }
  });

  document.querySelectorAll('ytd-reel-shelf-renderer, ytd-rich-shelf-renderer[is-shorts]').forEach(s => s.style.display = 'none');
  document.querySelectorAll('ytd-rich-section-renderer').forEach(section => {
    const title = section.querySelector('#title-text, .title');
    if (title && title.textContent.toLowerCase().includes('shorts')) section.style.display = 'none';
  });
}

// ── Watch Page Check (SPA-safe) ───────────────────────────────────

let allowedCount = 0;
function checkWatchPage() {
  const channel = getWatchPageChannel();
  if (channel) {
    if (isChannelAllowed(channel)) {
      removeBlockOverlay();
    } else {
      showBlockOverlay('Blocked by GATE Guard<br><span style="font-size:16px;color:#888;">Channel <b>' + channel + '</b> is not in allowed list</span>');
    }
  } else {
    allowedCount++;
    if (allowedCount < 20) {
      setTimeout(checkWatchPage, 500);
    } else {
      showBlockOverlay('Blocked by GATE Guard<br><span style="font-size:16px;color:#888;">Could not verify channel — blocked for safety</span>');
    }
  }
}

// ── Main Handler ──────────────────────────────────────────────────

function handlePage() {
  if (window.location.hostname === 'music.youtube.com') return;

  const path = window.location.pathname;
  const currentUrl = window.location.href;

  if (currentUrl !== lastUrl) {
    lastUrl = currentUrl;
    removeBlockOverlay();

    if (path.startsWith('/shorts')) {
      showBlockOverlay('Shorts are blocked — stay focused on GATE prep');
      return;
    }

    if (path.startsWith('/@')) {
      const handle = decodeURIComponent(path.split('/')[1].substring(1).split('/')[0]);
      if (!isChannelAllowed(handle)) {
        showBlockOverlay('This channel is not in your allowed list');
        return;
      }
    }

    if (path.startsWith('/watch')) {
      allowedCount = 0;
      showBlockOverlay('Verifying channel...');
      checkWatchPage();
    }
  }

  filterVideoCards();
}

// ── Observer & Events ─────────────────────────────────────────────

function setupObserver() {
  new MutationObserver(() => {
    if (window.location.hostname === 'music.youtube.com') return;
    handlePage();
  }).observe(document.documentElement, {
    childList: true, subtree: true,
  });
}

window.addEventListener('yt-navigate-finish', () => handlePage());
window.addEventListener('popstate', () => handlePage());

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => { handlePage(); setupObserver(); });
} else {
  handlePage(); setupObserver();
}
