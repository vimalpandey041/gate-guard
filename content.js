/**
 * GATE Guard v3.1 — Content Script — CLEAN BUILD
 * Filters YouTube to only allowed GATE channels.
 * DevTools proof. No video killing. SPA-safe.
 */

const HARDCODED_CHANNELS = [
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
  return HARDCODED_CHANNELS.some(ch => ch.toLowerCase() === handle.toLowerCase());
}

// ── Block Overlay ─────────────────────────────────────────────────

function showBlockOverlay(message) {
  if (overlayEl && document.contains(overlayEl)) return;
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
    <div style="color:#a1a1aa;font-size:0.95rem;margin-bottom:24px;">${message}</div>
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
  const selectors = [
    'ytd-video-owner-renderer a.yt-simple-endpoint[href*="/@"]',
    '#owner a[href*="/@"]',
    '#channel-name a[href*="/@"]',
    'a.ytd-channel-name[href*="/@"]',
    '#top-row ytd-channel-name a[href*="/@"]',
    'ytd-watch-metadata a[href*="/@"]',
    'span[itemprop="author"] link[itemprop="url"]',
  ];
  for (const sel of selectors) {
    const el = document.querySelector(sel);
    if (el) {
      const href = el.getAttribute('href');
      const match = href.match(/\/@([^\/?#]+)/);
      if (match) return match[1];
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

function checkWatchPage() {
  if (watchCheckTimer) { clearInterval(watchCheckTimer); watchCheckTimer = null; }
  
  const expectedUrl = window.location.href;
  showBlockOverlay('Verifying channel...');

  // Wait 1.5s for SPA DOM to update, then start checking
  setTimeout(() => {
    let attempts = 0;
    let allowedCount = 0;

    watchCheckTimer = setInterval(() => {
      // If URL changed, abort
      if (window.location.href !== expectedUrl) {
        clearInterval(watchCheckTimer);
        watchCheckTimer = null;
        return;
      }

      attempts++;
      const handle = getWatchPageChannel();

      if (handle !== null) {
        if (isChannelAllowed(handle)) {
          allowedCount++;
          if (allowedCount >= 2) {
            removeBlockOverlay();
            clearInterval(watchCheckTimer);
            watchCheckTimer = null;
          }
        } else {
          allowedCount = 0;
          clearInterval(watchCheckTimer);
          watchCheckTimer = null;
          removeBlockOverlay();
          showBlockOverlay('This video is not from an allowed GATE channel');
          return;
        }
      }

      if (attempts >= 10) {
        clearInterval(watchCheckTimer);
        watchCheckTimer = null;
        if (allowedCount < 2) {
          removeBlockOverlay();
          showBlockOverlay('Could not verify channel — blocked for safety');
        }
      }
    }, 500);
  }, 1500);
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
