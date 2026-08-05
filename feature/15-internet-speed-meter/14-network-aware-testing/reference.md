# Network Aware Testing

## Feature: How will it work?

The screen will show the current Android connection type and warn before testing on metered or mobile networks. A network change during a session will stop the test as interrupted instead of combining two paths; no background or scheduled tests will run.

## Backend, Privacy, And Release Requirements

- The planned provider is M-Lab NDT7 through [Locate API v2](https://www.measurementlab.net/develop/locate-v2/). M-Lab's [developer guidance](https://www.measurementlab.net/develop/) currently says NDT needs no developer API key, but NetworkTools must not ship a paid key, paid API, hardcoded server, token bypass, or unapproved public test endpoint.
- M-Lab's current [developer guidance](https://www.measurementlab.net/develop/) permits commercial and closed-source integrations without an API key. Its [Acceptable Use Policy](https://www.measurementlab.net/aup/) requires informed consent and a published privacy policy, limits interactive users to 40 tests per day, and publishes measurement data including the client public IP. Release remains blocked until NetworkTools has a live privacy-policy URL.
- Country requests must follow M-Lab's documented ISO country query and signed URLs. Try `strict=true` first; when no in-country node exists, retry through Locate without strict mode and clearly identify the returned regional server's actual city and country. Never scrape server maps or bypass Locate load balancing.
- Tests must be foreground, explicitly initiated, rate-limited, cancellable, synthetic-data only, and accurately disclosed under Google Play's [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311) and [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Before each release, recheck provider terms. If an API key, payment, commercial contribution, account contract, or incompatible data practice becomes mandatory, keep the feature disabled rather than silently changing providers.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify Network Aware Testing has clear idle, active, success, stopped, unavailable, and error states with stable layout and no overlapping controls.
- [ ] Verify portrait/landscape, light/dark theme, 200% font/display scaling, and long translated server/error labels remain readable.

### 2. Functional Testing

- [ ] Start on Wi-Fi, mobile, Ethernet, VPN, metered, unmetered, and offline states; verify warnings and state labels use Android capabilities without inferring unavailable data.
- [ ] Switch networks during discovery, download, and upload; verify cancellation, no mixed result, no automatic restart, and no work after the screen closes.

### 3. Compatibility Testing

- [ ] Verify Network Aware Testing on API 24, target API 36, and the latest available Android API on representative Pixel and non-Pixel devices.
- [ ] Verify Wi-Fi, mobile, Ethernet, VPN, IPv4-only, IPv6-only, dual-stack, metered, captive-portal, and offline behavior where applicable.

### 4. Performance Testing

- [ ] Measure UI frame time, CPU, memory, allocations, battery, and socket count during Network Aware Testing; keep protocol and parsing work off the main thread.
- [ ] Repeat controlled tests and cancellation 20 times; verify callbacks, sockets, buffers, and coroutines return to baseline and local/provider rate limits are enforced.

### 5. Security Testing

- [ ] Verify all provider JSON, URLs, WebSocket frames, numeric values, labels, and persisted rows are bounded and validated before use or display.
- [ ] Inspect APK, logs, storage, clipboard/share payloads, and traffic; verify no API key, reusable access token, public IP, device identifier, raw frame, dynamic code, or cleartext endpoint leaks.

### 6. Accessibility Testing

- [ ] With TalkBack, verify Network Aware Testing values, units, phases, progress, errors, buttons, dialogs, and server selection have meaningful labels and logical traversal.
- [ ] Verify switch/keyboard access, 48 dp targets, visible focus, contrast, reduced motion, and that color or meter position is never the only status signal.

### 7. Interruption Testing

- [ ] During Network Aware Testing, rotate, resize, background/foreground, lock/unlock, trigger process recreation, and press Back; verify no duplicate test or stale success result.
- [ ] Interrupt with network loss/change, provider rejection, timeout, airplane mode, and app force-stop; verify prompt closure, accurate terminal state, and explicit retry.
