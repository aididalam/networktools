# Automatic Server Discovery

## Feature: How will it work?

Local / nearest is the default. NetworkTools makes a bounded HTTPS probe on the active Android network, reads the returned edge code and client-country code, and tests the provider's automatically routed nearest edge. On a Bangladesh route this currently resolves to the Dhaka (`DAC`) or Chittagong (`CGP`) edge when available. The UI displays the detected edge instead of promising a city before routing is known. The optional global mode still discovers and latency-ranks up to four research-network candidates.

## Backend, Privacy, And Release Requirements

- The default path follows Cloudflare's official MIT-licensed [speedtest engine](https://github.com/cloudflare/speedtest) and its documented keyless `speed.cloudflare.com` endpoints. Cloudflare's [global network directory](https://www.cloudflare.com/network/) lists Dhaka and Chittagong. Recheck endpoint terms before every release; do not add an API token, paid API, result-logging call, or unofficial server scrape.
- Optional remote-country tests use M-Lab NDT7 through [Locate API v2](https://www.measurementlab.net/develop/locate-v2/) and remain release-gated separately.
- M-Lab's current [developer guidance](https://www.measurementlab.net/develop/) permits commercial and closed-source integrations without an API key. Its [Acceptable Use Policy](https://www.measurementlab.net/aup/) requires informed consent and a published privacy policy, limits interactive users to 40 tests per day, and publishes measurement data including the client public IP. Release remains blocked until NetworkTools has a live privacy-policy URL.
- Country requests must follow M-Lab's documented ISO country query and signed URLs. Try `strict=true` first; when no in-country node exists, retry through Locate without strict mode and clearly identify the returned regional server's actual city and country. Never scrape server maps or bypass Locate load balancing.
- Tests must be foreground, explicitly initiated, rate-limited, cancellable, synthetic-data only, and accurately disclosed under Google Play's [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311) and [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Before each release, recheck provider terms. If an API key, payment, commercial contribution, account contract, or incompatible data practice becomes mandatory, keep the feature disabled rather than silently changing providers.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify Automatic Server Discovery has clear idle, active, success, stopped, unavailable, and error states with stable layout and no overlapping controls.
- [ ] Verify portrait/landscape, light/dark theme, 200% font/display scaling, and long translated server/error labels remain readable.

### 2. Functional Testing

- [ ] Verify one explicit Start action resolves one nearest edge, displays its bounded edge/country metadata, and sends measurement traffic only to the allowlisted HTTPS host.
- [ ] From Bangladesh networks, verify normal routing reports `DAC` or `CGP` when those edges are available; if routing selects another edge, display the actual code without claiming a Bangladesh server.
- [ ] Verify equal-latency and failed probes retain Locate order, all probes are bounded/cancellable, and unavailable probes remain fallback candidates.
- [ ] Return empty, truncated, malformed, non-HTTPS, expired-token, and multiple-candidate responses; verify strict parsing, ordered fallback, and a recoverable unavailable state.

### 3. Compatibility Testing

- [ ] Verify Automatic Server Discovery on API 24, target API 36, and the latest available Android API on representative Pixel and non-Pixel devices.
- [ ] Verify Wi-Fi, mobile, Ethernet, VPN, IPv4-only, IPv6-only, dual-stack, metered, captive-portal, and offline behavior where applicable.

### 4. Performance Testing

- [ ] Measure UI frame time, CPU, memory, allocations, battery, and socket count during Automatic Server Discovery; keep protocol and parsing work off the main thread.
- [ ] Repeat controlled tests and cancellation 20 times; verify callbacks, sockets, buffers, and coroutines return to baseline and local/provider rate limits are enforced.

### 5. Security Testing

- [ ] Verify all provider JSON, URLs, WebSocket frames, numeric values, labels, and persisted rows are bounded and validated before use or display.
- [ ] Inspect APK, logs, storage, clipboard/share payloads, and traffic; verify no API key, reusable access token, public IP, device identifier, raw frame, dynamic code, or cleartext endpoint leaks.

### 6. Accessibility Testing

- [ ] With TalkBack, verify Automatic Server Discovery values, units, phases, progress, errors, buttons, dialogs, and server selection have meaningful labels and logical traversal.
- [ ] Verify switch/keyboard access, 48 dp targets, visible focus, contrast, reduced motion, and that color or meter position is never the only status signal.

### 7. Interruption Testing

- [ ] During Automatic Server Discovery, rotate, resize, background/foreground, lock/unlock, trigger process recreation, and press Back; verify no duplicate test or stale success result.
- [ ] Interrupt with network loss/change, provider rejection, timeout, airplane mode, and app force-stop; verify prompt closure, accurate terminal state, and explicit retry.
