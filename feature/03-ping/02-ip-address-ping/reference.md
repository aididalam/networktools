# IP Address Ping

## Feature: How will it work?

During a Ping run, the app derives IP address ping from the selected ICMP or TCP probe and updates the result summary as responses arrive. Timeouts and unsupported probe methods are reported explicitly without fabricating response data.

## Google Play Policy Requirements

- Start probes only after an explicit user action against the displayed target and show an authorized-use notice. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Enforce conservative packet-count, packet-size, timeout, interval, and concurrency limits; do not implement flood, stealth, evasion, or background probing.
- Cancel sockets and jobs when stopped or when the active workflow ends, and never expand a hostname into scans of neighboring addresses.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify IP Address Ping presents a clear input or source state, progress indication, result area, empty state, and retry path.
- [ ] Verify long hostnames, IPv6 addresses, multiple records, timeouts, and partial results do not cause clipping or layout shifts.

### 2. Functional Testing

- [ ] Run IP Address Ping against controlled reachable, closed, and unreachable targets and verify emitted results match packet captures or the test server.
- [ ] Test cancellation, timeout, duplicate responses, out-of-order responses, partial discovery, and permission denial; verify final state and counts remain correct.

- [ ] Try zero, negative, excessive, rapid, repeated, and concurrent probe settings; verify validation and hard limits prevent flooding and no probe starts without pressing the visible start action.

### 3. Compatibility Testing

- [ ] Verify IP Address Ping on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure IP Address Ping with small, typical, and maximum supported workloads; verify progress remains responsive and completion respects the configured timeout or product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify IP Address Ping validates and bounds all host, address, port, subnet, resolver, URI, and provider data before network use or intent launch; malformed data must not cause injection or unsafe routing.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Use packet capture and lifecycle tests to verify traffic is limited to the displayed target and configured bounded method, stops on cancellation, and never continues as a hidden background task.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify IP Address Ping controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] While IP Address Ping is loading or running, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify no duplicate jobs, crashes, or corrupted results.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
