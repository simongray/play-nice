# Development

Install the tools for the tests once, then run each suite:

```bash
npm install                            # once, for the tests
npx playwright install firefox webkit  # once, for the browsers
clojure -M:cljs compile test           # the tests in Node
clojure -X:test                        # the .cljc tests on the JVM
clojure -M:cljs release browser-test   # the tests for a browser
node test/browsers.mjs                 # in Chrome, Firefox and WebKit
node test/simulator.mjs                # in Safari on an iOS simulator
```

The browser tests play a real media element, and record each call on a
stand-in session, which can throw as Safari does. They run in your own
Chrome, since Playwright's Chromium can't play AAC or H.264, and in
Playwright's Firefox and WebKit. Like CI, the commands compile them with
`release`, where a missing `^js` hint breaks a call that a development
build would let through. With Xcode, the same build also runs in Safari
on an iPhone simulator, which CI doesn't do.
