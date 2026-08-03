# IP Address Tracing

## Feature: How will it work?

The Traceroute screen collects IP address tracing while probing successive hop limits toward the validated target. Results are emitted in hop order, and missing responses remain visible as timed-out hops so the route is not misleading.

## Google Play Policy Requirements

- Run tracing only after an explicit user action against the displayed, validated target and show an authorized-use notice. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Bound maximum hops, attempts per hop, timeout, concurrency, and retry behavior; do not add stealth, evasion, exploitation, or continuous background tracing.
- Show timeouts and partial routes honestly and stop all network work when the user cancels or leaves the active trace.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify IP Address Tracing presents a clear input or source state, progress indication, result area, empty state, and retry path.
- [ ] Verify long hostnames, IPv6 addresses, multiple records, timeouts, and partial results do not cause clipping or layout shifts.

### 2. Functional Testing

- [ ] Run IP Address Tracing against controlled reachable, closed, and unreachable targets and verify emitted results match packet captures or the test server.
- [ ] Test cancellation, timeout, duplicate responses, out-of-order responses, partial discovery, and permission denial; verify final state and counts remain correct.

- [ ] Submit excessive hop, retry, timeout, and rapid-repeat requests; verify hard limits apply and no trace begins without an explicit visible start action.

### 3. Compatibility Testing

- [ ] Verify IP Address Tracing on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure IP Address Tracing with small, typical, and maximum supported workloads; verify progress remains responsive and completion respects the configured timeout or product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify IP Address Tracing validates and bounds all host, address, port, subnet, resolver, URI, and provider data before network use or intent launch; malformed data must not cause injection or unsafe routing.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Capture traffic during start, cancel, navigation, and process backgrounding; verify packets target only the displayed destination, remain within limits, and stop when the active trace ends.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify IP Address Tracing controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] While IP Address Tracing is loading or running, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify no duplicate jobs, crashes, or corrupted results.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
