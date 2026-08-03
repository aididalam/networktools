# Remote Host Scanning

## Feature: How will it work?

The Port Scanner applies remote host scanning to a validated target and bounded port set using timed TCP connection attempts. It reports confirmed results only, distinguishes closed ports from timeouts when possible, and supports cancellation.

## Google Play Policy Requirements

- Scan only a single displayed host supplied or confirmed by the user, require an authorized-use acknowledgement, and start only from an explicit action. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Bound the port count, range, custom list, timeout, retries, and concurrency; reject duplicate, malformed, oversized, or automation-style requests.
- Detect connection state and common services only; do not implement exploitation, banner attacks, authentication attempts, evasion, stealth, or background scanning.
- Implement scanning directly with bounded TCP socket connections; do not use a third-party scanning service, paid API, API key, telemetry provider, or remotely supplied proxy list.
- For private-LAN targets, request Nearby Devices only immediately before an Android 16 operation when local-network protection applies. When targeting Android 17 or later, migrate to `ACCESS_LOCAL_NETWORK` and preserve denial and revocation behavior.
- Keep targets, results, and history in app-private on-device storage, exclude them from device backup, and never send them to analytics, advertising, or crash-reporting services.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify Remote Host Scanning presents a clear input or source state, progress indication, result area, empty state, and retry path.
- [ ] Verify long hostnames, IPv6 addresses, multiple records, timeouts, and partial results do not cause clipping or layout shifts.

### 2. Functional Testing

- [ ] Run Remote Host Scanning against controlled reachable, closed, and unreachable targets and verify emitted results match packet captures or the test server.
- [ ] Test cancellation, timeout, duplicate responses, out-of-order responses, partial discovery, and permission denial; verify final state and counts remain correct.

- [ ] Test excessive ranges, duplicate lists, invalid ports, rapid restart, missing authorization acknowledgement, and concurrent scans; verify hard limits and explicit start requirements.

### 3. Compatibility Testing

- [ ] Verify Remote Host Scanning on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.
- [ ] On Android 16, verify a private-LAN scan requests Nearby Devices only when the user starts it and handles denial or revocation safely; on Android 17 or later, repeat with `ACCESS_LOCAL_NETWORK`.

### 4. Performance Testing

- [ ] Measure Remote Host Scanning with small, typical, and maximum supported workloads; verify progress remains responsive and completion respects the configured timeout or product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Remote Host Scanning validates and bounds all host, address, port, subnet, resolver, URI, and provider data before network use or intent launch; malformed data must not cause injection or unsafe routing.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.
- [ ] Inspect the build, captured traffic, and backup output; verify there is no paid or keyed scanning provider, no analytics upload, and no backed-up target, result, or history data.

- [ ] Capture traffic for success, timeout, cancel, navigation, and backgrounding; verify connections go only to the displayed host and ports, contain no exploit or credential payloads, and terminate promptly.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Remote Host Scanning controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] While Remote Host Scanning is loading or running, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify no duplicate jobs, crashes, or corrupted results.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
