/* Real browser coverage for small-screen layout, keypad actions, and portable/offline use. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright');

const base = process.env.POCKET_DEX_URL || 'http://127.0.0.1:8765/';
const root = path.resolve(__dirname, '..');
const output = path.join(root, 'test-results');
fs.mkdirSync(output, { recursive: true });

(async () => {
  const browser = await chromium.launch({
    headless: true,
    ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {})
  });
  const context = await browser.newContext({ viewport: { width: 240, height: 320 } });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('response', response => { if (response.status() >= 400) errors.push(`${response.status()} ${response.url()}`); });
  const checks = [];
  async function view(expected) {
    await page.waitForFunction(value => document.getElementById('instrument').dataset.view === value, expected);
  }
  async function selected(id, container = 'list-items') {
    assert.equal(await page.locator(`#${container} [aria-selected="true"]`).getAttribute('data-id'), String(id));
  }
  async function find(query, name) {
    await page.keyboard.press('F1');
    await view('search');
    await page.locator('#search-input').fill(query);
    await page.keyboard.press('Enter');
    await view('detail');
    assert.equal(await page.locator('#detail-name').textContent(), name);
    await page.locator('#detail-sprite').evaluate(image => image.decode());
  }
  async function backToList() {
    for (let n = 0; n < 3; n++) {
      if (await page.locator('#instrument').getAttribute('data-view') === 'list') break;
      await page.keyboard.press('Escape');
    }
    await view('list');
  }
  async function noOverflow() {
    const result = await page.evaluate(() => {
      const screen = document.getElementById('screen').getBoundingClientRect();
      const footer = document.querySelector('.softkeys').getBoundingClientRect();
      const active = document.querySelector('.view:not([hidden])');
      const bounds = active.getBoundingClientRect();
      return {
        horizontal: document.documentElement.scrollWidth > innerWidth,
        footerVisible: footer.bottom <= innerHeight && footer.top >= 0,
        screenVisible: bounds.bottom <= screen.bottom + 1,
        detailFits: active.id !== 'detail-view' || active.scrollHeight <= active.clientHeight + 1,
        spriteHeight: active.id === 'detail-view' ? document.getElementById('detail-sprite').getBoundingClientRect().height : null,
        rows: document.querySelectorAll('.view:not([hidden]) .dex-row').length
      };
    });
    assert.equal(result.horizontal, false, JSON.stringify(result));
    assert.equal(result.footerVisible, true, JSON.stringify(result));
    assert.equal(result.screenVisible, true, JSON.stringify(result));
    assert.equal(result.detailFits, true, JSON.stringify(result));
    if (result.spriteHeight !== null) assert.ok(result.spriteHeight >= 32, JSON.stringify(result));
    assert.ok(result.rows <= 9, 'Only the visible slice of the catalogue should be rendered');
  }

  try {
    await page.goto(base);
    await view('list');
    assert.equal(await page.locator('#list-count').textContent(), '1,025');
    await selected(1);
    await noOverflow();
    await page.screenshot({ path: path.join(output, 'index-240x320.png') });
    await page.keyboard.press('ArrowDown');
    await selected(2);
    await page.keyboard.press('ArrowRight');
    const afterPage = Number(await page.locator('#list-items [aria-selected="true"]').getAttribute('data-id'));
    assert.ok(afterPage > 2 && afterPage <= 8);
    await page.keyboard.press('ArrowLeft');
    await selected(2);
    checks.push('D-pad row and page navigation');

    await find('#0025', 'Pikachu');
    assert.equal(await page.locator('#detail-types').textContent(), 'electric');
    assert.deepEqual(await page.locator('.stat strong').allTextContents(), ['112', '96', '111']);
    await noOverflow();
    await page.screenshot({ path: path.join(output, 'pikachu-240x320.png') });
    await page.keyboard.press('F1');
    assert.equal(await page.locator('#detail-star').textContent(), '★');
    await page.keyboard.press('Enter');
    await view('info');
    assert.ok((await page.locator('#info-date').textContent()).includes('2026-06-18'));
    await page.keyboard.press('ArrowDown');
    assert.ok(await page.locator('#info-scroll').evaluate(el => el.scrollTop) > 0);
    await page.keyboard.press('F2');
    await view('detail');
    await backToList();
    await page.keyboard.press('F2');
    assert.equal(await page.locator('#list-title').textContent(), 'SAVED FAVORITES');
    await selected(25);
    await page.keyboard.press('Enter');
    await view('detail');
    await page.reload();
    await view('list');
    await selected(25);
    await page.keyboard.press('F2');
    await selected(25);
    checks.push('Favorites survive reload; entry scroll and Back use the keypad');

    await page.keyboard.press('F2');
    for (const [query, name] of [["farfetchd", "Farfetch'd"], ['FLABEBE', 'Flabébé'], ['nidoran female', 'Nidoran♀'], ['type null', 'Type: Null'], ['1025', 'Pecharunt']]) {
      await find(query, name);
      await backToList();
    }
    checks.push('Search handles accents, punctuation, gender names, leading zeros, and the last species');

    await page.keyboard.press('F1');
    await page.locator('#search-input').fill('not-a-pokemon');
    assert.equal(await page.locator('#search-count').textContent(), '0 MATCHES');
    await page.keyboard.press('Enter');
    await view('search');
    await page.keyboard.press('F1');
    assert.equal(await page.locator('#search-input').inputValue(), '');
    await page.locator('#search-input').fill('pikachux');
    await page.keyboard.press('Backspace');
    assert.equal(await page.locator('#search-input').inputValue(), 'pikachu');
    await page.keyboard.press('Enter');
    await view('detail');
    checks.push('Empty search and native text deletion do not navigate away');

    for (const height of [280, 240]) {
      await page.setViewportSize({ width: 240, height });
      await noOverflow();
      await page.screenshot({ path: path.join(output, `pikachu-240x${height}.png`) });
    }
    await page.setViewportSize({ width: 240, height: 320 });
    await backToList();
    await find('1', 'Bulbasaur');
    await page.keyboard.press('ArrowLeft');
    assert.equal(await page.locator('#detail-name').textContent(), 'Pecharunt');
    await page.keyboard.press('ArrowRight');
    assert.equal(await page.locator('#detail-name').textContent(), 'Bulbasaur');
    checks.push('Small browser viewport and 1–1025 portrait wrap');

    await page.evaluate(() => document.dispatchEvent(new KeyboardEvent('keydown', { key: 'SoftRight', bubbles: true, cancelable: true })));
    await view('search');
    await page.evaluate(() => document.dispatchEvent(new KeyboardEvent('keydown', { key: 'SoftRight', bubbles: true, cancelable: true })));
    await view('list');
    await page.evaluate(() => document.dispatchEvent(new KeyboardEvent('keydown', { key: 'SoftLeft', bubbles: true, cancelable: true })));
    await view('search');
    await page.locator('#search-input').fill('eevee');
    await page.keyboard.press('ArrowDown');
    await selected(133, 'search-items');
    await page.keyboard.press('Enter');
    await view('detail');
    assert.equal(await page.locator('#detail-name').textContent(), 'Eevee');
    checks.push('KaiOS SoftLeft/SoftRight key names and Down-to-results navigation');

    // A single downloaded file must never depend on a remote font, API, or sprite.
    const offline = await context.newPage();
    const network = [];
    offline.on('request', request => { if (/^https?:/.test(request.url())) network.push(request.url()); });
    offline.on('pageerror', error => errors.push(error.message));
    await offline.goto(pathToFileURL(path.join(root, 'dist/overdex-pocket.html')).href);
    await offline.keyboard.press('F1');
    await offline.locator('#search-input').fill('1025');
    await offline.keyboard.press('Enter');
    assert.equal(await offline.locator('#detail-name').textContent(), 'Pecharunt');
    await offline.locator('#detail-sprite').evaluate(image => image.decode());
    assert.ok((await offline.locator('#detail-sprite').getAttribute('src')).startsWith('data:image/png;'));
    assert.deepEqual(network, []);
    checks.push('Portable HTML opens locally with zero network requests, including the last sprite');

    // Private/restricted storage should only affect persistence, never usability.
    const restricted = await context.newPage();
    await restricted.addInitScript(() => {
      Storage.prototype.getItem = function () { throw new Error('Storage blocked'); };
      Storage.prototype.setItem = function () { throw new Error('Storage blocked'); };
    });
    await restricted.goto(base);
    await restricted.keyboard.press('Enter');
    await restricted.keyboard.press('F1');
    assert.ok((await restricted.locator('#notice').textContent()).includes('this visit'));
    assert.equal(await restricted.locator('#detail-star').textContent(), '★');
    checks.push('Blocked browser storage retains usable session-only favorites');

    assert.deepEqual(errors, []);
    fs.writeFileSync(path.join(output, 'checks.json'), JSON.stringify({ checks, errors }, null, 2) + '\n');
    console.log(JSON.stringify({ passed: checks.length, checks }, null, 2));
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
