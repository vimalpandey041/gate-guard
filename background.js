/**
 * GATE Guard v2.0 — LOCKED DOWN Background Service Worker
 *
 * MILITARY MODE:
 * - Extension is ALWAYS ON (no toggle)
 * - chrome://extensions is BLOCKED (prevents uninstall/disable)
 * - Blocked sites cannot be removed, only added
 * - Allowed YouTube channels are HARDCODED and cannot be changed
 * - Other blocked sites are fully blocked with redirect
 * - YouTube is filtered by content script
 */

const DEFAULT_BLOCKED_SITES = [
  "youtube.com",
  "reddit.com",
  "x.com",
  "twitter.com",
  "instagram.com",
  "cricbuzz.com",
  "jiohotstar.com",
  "hotstar.com",
  "flipkart.com",
  "amazon.in",
  "amazon.com"
];

const HARDCODED_ALLOWED_CHANNELS = [
  "pw-solutions",
  "GOClassesforGATECS",
  "GATEWallahbyPW",
  "gatewallah_cse_da",
  "Gatecsit-dsai",
  "UnacademyComputerScience",
  "GfG_GATE"
];

let blockedSites = [];

// ── Initialize ─────────────────────────────────────────────────────

function initFromStorage() {
  chrome.storage.local.get(['blockedSites'], (result) => {
    if (result.blockedSites === undefined) {
      blockedSites = [...DEFAULT_BLOCKED_SITES];
      chrome.storage.local.set({
        blockedSites,
        allowedChannels: HARDCODED_ALLOWED_CHANNELS
      });
    } else {
      blockedSites = result.blockedSites;
      // Always overwrite allowed channels with hardcoded list (can't be changed)
      chrome.storage.local.set({ allowedChannels: HARDCODED_ALLOWED_CHANNELS });
    }
    console.log("GATE Guard v2.0 LOCKED. Blocked sites:", blockedSites);
  });
}

initFromStorage();

chrome.storage.onChanged.addListener((changes, namespace) => {
  if (namespace !== 'local') return;
  if (changes.blockedSites) blockedSites = changes.blockedSites.newValue;
  
  // SECURITY: Force allowed channels back to hardcoded if someone tries to change them
  if (changes.allowedChannels) {
    const newVal = changes.allowedChannels.newValue;
    const isValid = JSON.stringify(newVal.sort()) === JSON.stringify([...HARDCODED_ALLOWED_CHANNELS].sort());
    if (!isValid) {
      chrome.storage.local.set({ allowedChannels: HARDCODED_ALLOWED_CHANNELS });
    }
  }
});

// ── MILITARY SECURITY: Block chrome://extensions & settings ────────

chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  // Check the tab URL (requires 'tabs' permission)
  const url = (changeInfo.url || tab.url || '').toLowerCase();
  
  if (url.startsWith('chrome://extensions') ||
      url.startsWith('chrome://settings') ||
      url.startsWith('chrome://flags') ||
      url.startsWith('edge://extensions') ||
      url.startsWith('about:addons')) {
    
    chrome.tabs.update(tabId, {
      url: chrome.runtime.getURL('blocked.html') + '?site=chrome-settings'
    });
  }
});

// Also catch when new tabs are created directly to chrome://extensions
chrome.tabs.onCreated.addListener((tab) => {
  setTimeout(() => {
    chrome.tabs.get(tab.id, (updatedTab) => {
      if (chrome.runtime.lastError) return;
      const url = (updatedTab.url || updatedTab.pendingUrl || '').toLowerCase();
      if (url.startsWith('chrome://extensions') ||
          url.startsWith('chrome://settings') ||
          url.startsWith('chrome://flags')) {
        chrome.tabs.update(tab.id, {
          url: chrome.runtime.getURL('blocked.html') + '?site=chrome-settings'
        });
      }
    });
  }, 100);
});

// ── URL Blocking (non-YouTube sites) ───────────────────────────────

function getBlockedDomain(url) {
  let parsedUrl;
  try { parsedUrl = new URL(url); } catch (e) { return null; }
  if (parsedUrl.protocol !== 'http:' && parsedUrl.protocol !== 'https:') return null;

  const hostname = parsedUrl.hostname.toLowerCase();

  // music.youtube.com always allowed
  if (hostname === 'music.youtube.com' || hostname === 'www.music.youtube.com') return null;

  // YouTube handled by content script
  if (hostname === 'youtube.com' || hostname === 'www.youtube.com' || hostname === 'm.youtube.com') return null;

  // Check blocked sites
  for (const site of blockedSites) {
    const domain = site.toLowerCase();
    if (domain === 'youtube.com') continue;
    if (hostname === domain || hostname.endsWith('.' + domain)) return site;
  }

  return null;
}

function redirectToBlocked(tabId, site) {
  chrome.tabs.update(tabId, {
    url: chrome.runtime.getURL('blocked.html') + '?site=' + encodeURIComponent(site || '')
  });
}

chrome.webNavigation.onBeforeNavigate.addListener((details) => {
  if (details.frameId !== 0) return;
  const blockedDomain = getBlockedDomain(details.url);
  if (blockedDomain) redirectToBlocked(details.tabId, blockedDomain);
});
