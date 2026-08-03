# Reverse DNS Lookup

## Feature: How will it work?

The IP and Host Converter performs reverse DNS lookup after validating the hostname or address. DNS work runs off the main thread, preserves multiple valid answers, and distinguishes no-record, malformed-input, and resolver-failure states.

## Google Play Policy Requirements

- Run hostname and reverse-DNS operations only after an explicit user action for one validated target; do not provide bulk enumeration. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Bound answer count to 32, resolver requests to two, timeout to 1-3 seconds, input to 253 characters, and custom resolvers to numeric unicast addresses; stop all work when the active conversion ends.
- Use direct DNS only; no paid API, API key, account, backend, advertising SDK, or remote history service is required.
- Do not persist or transmit entered hosts, addresses, or DNS answers to analytics, advertising, or unrelated services. Follow the [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).

## Test Cases

### 1. UI/UX Testing

- [ ] Verify Reverse DNS Lookup presents a clear input or source state, progress indication, result area, empty state, and retry path.
- [ ] Verify long hostnames, IPv6 addresses, multiple records, timeouts, and partial results do not cause clipping or layout shifts.

### 2. Functional Testing

- [ ] Use a controlled resolver or response fixture with known Reverse DNS Lookup data and verify query construction, parsing, normalization, and displayed output.
- [ ] Test malformed input, no-record responses, referral or multiple-answer responses, timeout, truncated data, and server failure; verify distinct outcomes.

- [ ] Try wildcard, multi-line, oversized, rapid-repeat, and bulk-style inputs; verify one bounded target is processed per explicit action and work stops after cancellation.

### 3. Compatibility Testing

- [ ] Verify Reverse DNS Lookup on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure Reverse DNS Lookup with A, AAAA, and PTR fixtures containing zero, one, and 32 answers; verify progress remains responsive and completion respects the 1-3 second configured timeout.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Reverse DNS Lookup validates and bounds all host, address, port, subnet, resolver, URI, and provider data before network use or intent launch; malformed data must not cause injection or unsafe routing.
- [ ] Verify direct DNS traffic goes only to the displayed resolver; request local-network permission only when Android requires it for a local resolver, and write no inputs or answers to storage, logs, analytics, backups, or crash messages.

- [ ] Inspect DNS traffic, logs, analytics, cache, and lifecycle behavior; verify only the displayed target reaches the selected resolver and no lookup continues in the background.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Reverse DNS Lookup controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] While Reverse DNS Lookup is loading or running, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify no duplicate jobs, crashes, or corrupted results.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
