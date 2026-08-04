# Latency And Jitter

## Feature: How will it work?

The app will derive latency from bounded NDT7 WebSocket round-trip control samples taken during the active test and compute jitter as the variation between valid consecutive samples. The UI will label these as test-path estimates, exclude timeouts from averages, and show Unavailable when the sample set is insufficient.

## Backend, Privacy, And Release Requirements

- The planned provider is M-Lab NDT7 through [Locate API v2](https://www.measurementlab.net/develop/locate-v2/). M-Lab's [developer guidance](https://www.measurementlab.net/develop/) currently says NDT needs no developer API key, but NetworkTools must not ship a paid key, paid API, hardcoded server, token bypass, or unapproved public test endpoint.
- M-Lab's current [Acceptable Use Policy](https://www.measurementlab.net/aup/) requires informed consent and a published privacy policy, limits interactive users to 40 tests per day, publishes measurement data including the client public IP, and may require commercial integrations to contribute or later register/pay. Release is blocked until NetworkTools has a live privacy-policy URL and the individual developer confirms written zero-cost eligibility for the intended monetization model.
- Country requests must follow M-Lab's documented ISO country query and signed URLs. If strict country service is unavailable, show Unavailable; do not route to an undisclosed country, scrape server maps, or bypass Locate load balancing.
- Tests must be foreground, explicitly initiated, rate-limited, cancellable, synthetic-data only, and accurately disclosed under Google Play's [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311) and [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Before each release, recheck provider terms. If an API key, payment, commercial contribution, account contract, or incompatible data practice becomes mandatory, keep the feature disabled rather than silently changing providers.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify Latency And Jitter has clear idle, active, success, stopped, unavailable, and error states with stable layout and no overlapping controls.
- [ ] Verify portrait/landscape, light/dark theme, 200% font/display scaling, and long translated server/error labels remain readable.

### 2. Functional Testing

- [ ] Use fixed latency samples to verify minimum, average, and jitter calculations, units, rounding, timeout exclusion, and insufficient-sample handling.
- [ ] Inject clock changes and delayed callbacks; verify monotonic timing prevents negative or implausible results.

### 3. Compatibility Testing

- [ ] Verify Latency And Jitter on API 24, target API 36, and the latest available Android API on representative Pixel and non-Pixel devices.
- [ ] Verify Wi-Fi, mobile, Ethernet, VPN, IPv4-only, IPv6-only, dual-stack, metered, captive-portal, and offline behavior where applicable.

### 4. Performance Testing

- [ ] Measure UI frame time, CPU, memory, allocations, battery, and socket count during Latency And Jitter; keep protocol and parsing work off the main thread.
- [ ] Repeat controlled tests and cancellation 20 times; verify callbacks, sockets, buffers, and coroutines return to baseline and local/provider rate limits are enforced.

### 5. Security Testing

- [ ] Verify all provider JSON, URLs, WebSocket frames, numeric values, labels, and persisted rows are bounded and validated before use or display.
- [ ] Inspect APK, logs, storage, clipboard/share payloads, and traffic; verify no API key, reusable access token, public IP, device identifier, raw frame, dynamic code, or cleartext endpoint leaks.

### 6. Accessibility Testing

- [ ] With TalkBack, verify Latency And Jitter values, units, phases, progress, errors, buttons, dialogs, and server selection have meaningful labels and logical traversal.
- [ ] Verify switch/keyboard access, 48 dp targets, visible focus, contrast, reduced motion, and that color or meter position is never the only status signal.

### 7. Interruption Testing

- [ ] During Latency And Jitter, rotate, resize, background/foreground, lock/unlock, trigger process recreation, and press Back; verify no duplicate test or stale success result.
- [ ] Interrupt with network loss/change, provider rejection, timeout, airplane mode, and app force-stop; verify prompt closure, accurate terminal state, and explicit retry.
