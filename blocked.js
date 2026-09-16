// Show which site was blocked
const params = new URLSearchParams(window.location.search);
const site = params.get('site');
const siteNameEl = document.getElementById('blockedSiteName');

if (site === 'chrome-settings') {
  siteNameEl.textContent = 'Nice try. Settings are locked.';
} else if (site) {
  siteNameEl.textContent = site + ' is blocked';
} else {
  siteNameEl.textContent = 'This site is blocked';
}
