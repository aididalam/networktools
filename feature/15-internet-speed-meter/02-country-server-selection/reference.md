# Country Server Selection

## Feature: How will it work?

The UI has separate Country / region and Server selectors. The searchable country sheet starts with Local / nearest and shows the detected local country, then offers global Automatic plus every ISO country supplied by the Android runtime. Selecting a country asynchronously requests live candidates from M-Lab Locate instead of using app-maintained server records. Server defaults to Automatic (lowest latency), while a user may explicitly select a candidate by city and anonymous sequence number. When a country has no current M-Lab node, the selector clearly marks a regional fallback and displays every candidate's actual city and country. Provider hostnames and signed URLs are never displayed.

## Backend, Privacy, And Release Requirements

- The planned provider is M-Lab NDT7 through [Locate API v2](https://www.measurementlab.net/develop/locate-v2/). M-Lab's [developer guidance](https://www.measurementlab.net/develop/) currently says NDT needs no developer API key, but NetworkTools must not ship a paid key, paid API, hardcoded server, token bypass, or unapproved public test endpoint.
- M-Lab's current [developer guidance](https://www.measurementlab.net/develop/) permits commercial and closed-source integrations without an API key. Its [Acceptable Use Policy](https://www.measurementlab.net/aup/) requires informed consent and a published privacy policy, limits interactive users to 40 tests per day, and publishes measurement data including the client public IP. Release remains blocked until NetworkTools has a live privacy-policy URL.
- Country requests must follow M-Lab's documented ISO country query and signed URLs. Try `strict=true` first; when no in-country node exists, retry through Locate without strict mode and clearly identify the returned regional server's actual city and country. Never scrape server maps or bypass Locate load balancing.
- Tests must be foreground, explicitly initiated, rate-limited, cancellable, synthetic-data only, and accurately disclosed under Google Play's [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311) and [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Before each release, recheck provider terms. If an API key, payment, commercial contribution, account contract, or incompatible data practice becomes mandatory, keep the feature disabled rather than silently changing providers.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify Country Server Selection has clear idle, active, success, stopped, unavailable, and error states with stable layout and no overlapping controls.
- [ ] Verify the bottom sheet opens without a popup delay, the fixed search field filters case-insensitively by localized country name and ISO code, and clear/close/select actions dismiss the keyboard predictably.
- [ ] Verify portrait/landscape, light/dark theme, 200% font/display scaling, and long translated server/error labels remain readable.

### 2. Functional Testing

- [ ] Verify Local / nearest is first and default, global Automatic is second, every runtime ISO country appears once, and country selections first produce the exact encoded `country=XX&strict=true` Locate request.
- [ ] Verify searching `Bangladesh`, `BD`, `Dhaka`, `DAC`, `Chittagong`, or `CGP` returns the local row when the detected route provides that metadata.
- [ ] Select countries with zero, one, and multiple current candidates; verify the independent Server selector resets to Automatic, shows bounded loading/error states, deduplicates machines, and lists candidates without provider hostnames.
- [ ] Test unsupported, unavailable, mismatched, and empty strict-country results; verify the app retries through Locate, labels the result as regional, and shows the actual returned city and country before testing.

### 3. Compatibility Testing

- [ ] Verify Country Server Selection on API 24, target API 36, and the latest available Android API on representative Pixel and non-Pixel devices.
- [ ] Verify Wi-Fi, mobile, Ethernet, VPN, IPv4-only, IPv6-only, dual-stack, metered, captive-portal, and offline behavior where applicable.

### 4. Performance Testing

- [ ] Measure UI frame time, CPU, memory, allocations, battery, and socket count during Country Server Selection; keep protocol and parsing work off the main thread.
- [ ] Repeat controlled tests and cancellation 20 times; verify callbacks, sockets, buffers, and coroutines return to baseline and local/provider rate limits are enforced.

### 5. Security Testing

- [ ] Verify all provider JSON, URLs, WebSocket frames, numeric values, labels, and persisted rows are bounded and validated before use or display.
- [ ] Inspect APK, logs, storage, clipboard/share payloads, and traffic; verify no API key, reusable access token, public IP, device identifier, raw frame, dynamic code, or cleartext endpoint leaks.

### 6. Accessibility Testing

- [ ] With TalkBack, verify Country Server Selection values, units, phases, progress, errors, buttons, dialogs, and server selection have meaningful labels and logical traversal.
- [ ] Verify switch/keyboard access, 48 dp targets, visible focus, contrast, reduced motion, and that color or meter position is never the only status signal.

### 7. Interruption Testing

- [ ] During Country Server Selection, rotate, resize, background/foreground, lock/unlock, trigger process recreation, and press Back; verify no duplicate test or stale success result.
- [ ] Interrupt with network loss/change, provider rejection, timeout, airplane mode, and app force-stop; verify prompt closure, accurate terminal state, and explicit retry.
