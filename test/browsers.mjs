// Run the browser tests of target/browser-test in Chrome, Firefox and
// WebKit, print each report, and exit with 1 unless all of them pass.
//
//     clojure -M:cljs release browser-test
//     node test/browsers.mjs
//
// Chrome is the installed one, since Playwright's own Chromium can't play
// AAC or H.264, which the tests of what the browser can play rely on.

import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';
import { chromium, firefox, webkit } from 'playwright';

const root = 'target/browser-test';

const types = {
  '.html': 'text/html',
  '.js': 'text/javascript',
  '.map': 'application/json',
};

const browsers = [
  ['Chrome', chromium, { channel: 'chrome' }],
  ['Firefox', firefox, {}],
  ['WebKit', webkit, {}],
];

const server = createServer(async (request, response) => {
  const path = new URL(request.url, 'http://localhost').pathname;
  const file = join(root, normalize(path === '/' ? '/index.html' : path));
  try {
    if (!file.startsWith(root)) throw new Error('outside the tests');
    const body = await readFile(file);
    response.writeHead(200, { 'content-type': types[extname(file)] ?? 'application/octet-stream' });
    response.end(body);
  } catch {
    response.writeHead(404).end();
  }
});

await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const url = `http://127.0.0.1:${server.address().port}/`;

// The runner writes the summary into the title once every test has ended.
async function run([name, type, options]) {
  const browser = await type.launch(options);
  try {
    const page = await browser.newPage();
    page.on('pageerror', error => console.log(`${name}: ${error}`));
    await page.goto(url);
    await page.waitForFunction(() => document.title.includes('failures'),
                               null, { timeout: 60_000 });
    console.log(`--- ${name} ${browser.version()}`);
    console.log((await page.locator('pre').innerText()).trim(), '\n');
    return (await page.title()) === '0 failures, 0 errors';
  } catch (error) {
    console.log(`--- ${name}: ${error.message}\n`);
    return false;
  } finally {
    await browser.close();
  }
}

const passed = [];
for (const browser of browsers) passed.push(await run(browser));
server.close();
process.exit(passed.every(Boolean) ? 0 : 1);
