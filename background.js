/**
 * GATE Guard v3.0 — DYNAMIC CONFIG via MongoDB API
 *
 * MILITARY MODE:
 * - Extension is ALWAYS ON
 * - chrome://extensions is BLOCKED
 * - Config is fetched dynamically from API every 30 minutes
 */

const API_URL = "https://gateguard.vimalpandey.in/api/config"; // EC2 IP will go here

const DEFAULT_BLOCKED_SITES = [
  "youtube.com", "reddit.com", "x.com", "twitter.com",
  "instagram.com", "cricbuzz.com", "jiohotstar.com",
  "hotstar.com", "nextdns.io"
];

const DEFAULT_CHANNELS = [
  "pw-solutions", "GOClassesforGATECS", "GATEWallahbyPW",
  "gatewallah_cse_da", "Gatecsit-dsai", "UnacademyComputerScience",
  "GfG_GATE", "AmitKhuranaSir", "DreamMaths"
];

let blockedSites = [];
let blockedMessage = "Blocked by GATE Guard";

// ── Fetch Config from API ──────────────────────────────────────────

async function fetchConfig() {
  try {
    const res = await fetch(API_URL);
    if (!res.ok) throw new Error("API not ok");
    const data = await res.json();
    
    // Cache in local storage
    chrome.storage.local.set({
      allowedChannels: data.allowedChannels || DEFAULT_CHANNELS,
      blockedSites: data.blockedSites || DEFAULT_BLOCKED_SITES,
      blockedMessage: data.blockedMessage || "Blocked by GATE Guard",
      lastFetch: Date.now()
    });
    console.log("✅ Config fetched and cached from API", data);
  } catch (err) {
    console.error("❌ API fetch failed, using cache/defaults", err);
  }
}

// ── Initialize ─────────────────────────────────────────────────────

function initFromStorage() {
  chrome.storage.local.get(['blockedSites', 'blockedMessage', 'allowedChannels'], (result) => {
    blockedSites = result.blockedSites || DEFAULT_BLOCKED_SITES;
    blockedMessage = result.blockedMessage || "Blocked by GATE Guard";
    
    // Seed initial defaults if completely empty
    if (!result.blockedSites) {
      chrome.storage.local.set({
        blockedSites: DEFAULT_BLOCKED_SITES,
        allowedChannels: DEFAULT_CHANNELS,
        blockedMessage: "Blocked by GATE Guard"
      });
    }
  });
}

initFromStorage();
fetchConfig();

// Fetch every 30 minutes
chrome.alarms.create("fetchConfig", { periodInMinutes: 30 });
chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === "fetchConfig") fetchConfig();
});

// Refresh on startup or install
chrome.runtime.onStartup.addListener(fetchConfig);
chrome.runtime.onInstalled.addListener(fetchConfig);

chrome.storage.onChanged.addListener((changes, namespace) => {
  if (namespace !== 'local') return;
  if (changes.blockedSites) blockedSites = changes.blockedSites.newValue;
  if (changes.blockedMessage) blockedMessage = changes.blockedMessage.newValue;
});

// ── MILITARY SECURITY: Block chrome://extensions & settings ────────

chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (changeInfo.url) {
    const url = changeInfo.url.toLowerCase();
    
    if (url.startsWith('chrome://extensions') || 
        url.startsWith('chrome://settings') ||
        url.startsWith('edge://extensions') ||
        url.startsWith('edge://settings') ||
        url.startsWith('brave://extensions') ||
        url.startsWith('brave://settings')) {
      
      const blockedUrl = chrome.runtime.getURL(`blocked.html?site=chrome-settings`);
      chrome.tabs.update(tabId, { url: blockedUrl });
    }
  }
});

chrome.tabs.onCreated.addListener((tab) => {
  const url = (tab.pendingUrl || tab.url || '').toLowerCase();
  if (url.startsWith('chrome://extensions') ||
      url.startsWith('chrome://settings') ||
      url.startsWith('chrome://flags')) {
    
    chrome.tabs.update(tab.id, {
      url: chrome.runtime.getURL('blocked.html') + '?site=chrome-settings'
    });
  }
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
