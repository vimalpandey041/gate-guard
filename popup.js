document.addEventListener('DOMContentLoaded', () => {
  const blockedListEl = document.getElementById('blockedList');
  const addInput      = document.getElementById('addInput');
  const addBtn        = document.getElementById('addBtn');
  const toastEl       = document.getElementById('toast');

  const API_BASE = 'https://gateguard.vimalpandey.in';
  let blockedSites = [];

  // ── Load blocked sites ──────────────────────────────────────────
  function loadData() {
    chrome.storage.local.get(['blockedSites'], (result) => {
      blockedSites = result.blockedSites || [];
      renderList();
    });

    // Also fetch latest config from MongoDB API to ensure real-time sync
    fetch(`${API_BASE}/api/blocked`)
      .then(res => res.json())
      .then(data => {
        if (data && Array.isArray(data.sites)) {
          blockedSites = data.sites;
          chrome.storage.local.set({ blockedSites });
          renderList();
        }
      })
      .catch(err => console.log('Offline / using local cache', err));
  }

  // ── Render blocked list (NO remove buttons) ─────────────────────
  function renderList() {
    blockedListEl.innerHTML = '';
    blockedSites.forEach(site => {
      const item = document.createElement('div');
      item.className = 'list-item';
      item.innerHTML = `
        <span class="item-dot"></span>
        <span class="item-name">${escapeHtml(site)}</span>
        <span class="item-tag">Blocked</span>
      `;
      blockedListEl.appendChild(item);
    });
  }

  // ── Add new blocked site ────────────────────────────────────────
  addBtn.addEventListener('click', addSite);
  addInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') addSite(); });

  async function addSite() {
    let domain = addInput.value.trim().toLowerCase();
    if (!domain) return;

    // Clean up input
    domain = domain.replace(/^https?:\/\//, '').replace(/^www\./, '').replace(/\/.*$/, '');

    if (!domain || !domain.includes('.')) {
      showToast('Enter a valid domain (e.g. tiktok.com)', true);
      return;
    }

    if (blockedSites.some(s => s.toLowerCase() === domain)) {
      showToast('Already blocked', true);
      return;
    }

    addBtn.disabled = true;
    addBtn.textContent = '...';

    // 1. Send to MongoDB API
    try {
      const res = await fetch(`${API_BASE}/api/blocked/add`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ site: domain })
      });
      const data = await res.json();
      if (data && Array.isArray(data.sites)) {
        blockedSites = data.sites;
      } else {
        blockedSites.push(domain);
      }
      chrome.storage.local.set({ blockedSites });
      addInput.value = '';
      renderList();
      showToast('🚫 ' + domain + ' blocked & saved in DB');
    } catch (err) {
      console.warn('API sync failed, saving locally:', err);
      blockedSites.push(domain);
      chrome.storage.local.set({ blockedSites });
      addInput.value = '';
      renderList();
      showToast(domain + ' blocked locally (Offline)', true);
    } finally {
      addBtn.disabled = false;
      addBtn.textContent = '+ Block';
    }
  }

  // ── Toast ───────────────────────────────────────────────────────
  let toastTimer;
  function showToast(msg, isError = false) {
    toastEl.textContent = msg;
    toastEl.className = 'toast show' + (isError ? ' error' : '');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { toastEl.className = 'toast'; }, 2000);
  }

  function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }

  loadData();
});
