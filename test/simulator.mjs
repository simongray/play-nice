// Run the browser tests in target/browser-test in Safari on an iOS
// simulator, print the report, and exit with 1 unless they all pass.
//
//     clojure -M:cljs release browser-test
//     node test/simulator.mjs                # on an iPhone 17
//     node test/simulator.mjs "iPhone Air"
//
// Nothing can read the page of a simulator as Playwright reads a browser's,
// so the page posts its report here, at the end of a URL that asks for it.
// The simulator needs Xcode, and reaches this machine at 127.0.0.1.

import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
import { extname, join, normalize } from 'node:path';

const root = 'target/browser-test';
const device = process.argv[2] ?? 'iPhone 17';

const types = {
  '.html': 'text/html',
  '.js': 'text/javascript',
  '.map': 'application/json',
};

let reported;
const report = new Promise(resolve => { reported = resolve; });

const server = createServer(async (request, response) => {
  const path = new URL(request.url, 'http://localhost').pathname;
  if (request.method === 'POST' && path === '/report') {
    let body = '';
    for await (const chunk of request) body += chunk;
    response.writeHead(204).end();
    return reported(body);
  }
  const file = join(root, normalize(path === '/' ? '/index.html' : path));
  try {
    if (!file.startsWith(root)) throw new Error('outside the tests');
    const body = await readFile(file);
    const type = types[extname(file)] ?? 'application/octet-stream';
    // a new build at every run, rather than Safari's cached copy
    response.writeHead(200, { 'content-type': type, 'cache-control': 'no-store' });
    response.end(body);
  } catch {
    response.writeHead(404).end();
  }
});

await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const url = `http://127.0.0.1:${server.address().port}/?report`;

const simctl = (...args) => execFileSync('xcrun', ['simctl', ...args], { encoding: 'utf8' });

const { devices } = JSON.parse(simctl('list', 'devices', 'available', '-j'));
const udid = Object.values(devices).flat().find(d => d.name === device)?.udid;
if (!udid) {
  console.log(`--- no simulator named ${device}`);
  process.exit(1);
}

// through Simulator.app, since a simulator that simctl boots on its own
// may get no display, and its home screen crashes
execFileSync('open', ['-a', 'Simulator', '--args', '-CurrentDeviceUDID', udid]);
simctl('bootstatus', udid, '-b');
simctl('openurl', udid, url);

const timeout = new Promise(resolve => setTimeout(() => resolve(null), 120_000));
const body = await Promise.race([report, timeout]);
server.close();

if (body === null) {
  console.log(`--- Safari on ${device}: no report in 120 s`);
  process.exit(1);
}
console.log(`--- Safari on ${device}`);
console.log(body.trim(), '\n');
process.exit(body.trim().endsWith('0 failures, 0 errors') ? 0 : 1);
