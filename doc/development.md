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
stand-in session, which can throw as Safari does. CI runs them in your
Chrome's channel and in Playwright's Firefox and WebKit, compiled with
`release`, where a missing `^js` hint would show. With Xcode, the same
build also runs in Safari on an iPhone simulator, which CI doesn't do.
