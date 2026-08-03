# ARP Discovery

## Feature: How will it work?

The LAN Scanner applies ARP discovery within the validated local subnet and merges matching responses into a single device record. Discovery is bounded by the selected scan mode, can be cancelled, and labels values as unknown when a protocol does not supply them.

## Google Play Policy Requirements

- Default scanning to the active private LAN only, require explicit user start and authorization acknowledgement, and reject public internet ranges or oversized subnets. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Enforce conservative host, protocol, timeout, retry, and concurrency limits; stop immediately on cancellation or when the active scan ends.
- Limit behavior to discovery and identification; do not add exploitation, credential guessing, vulnerability bypass, stealth, persistence, or hidden background scanning.
- Request Nearby Devices only just before an Android 16 local-network operation when local-network protection applies; when targeting Android 17 or higher, migrate this gate to `ACCESS_LOCAL_NETWORK` and preserve denial and revocation handling.
- Treat IP addresses, hostnames, MAC addresses, and service advertisements as sensitive local data: keep them on-device, exclude history from backup and device transfer, and do not send them to analytics or remote providers.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify ARP Discovery presents a clear input or source state, progress indication, result area, empty state, and retry path.
- [ ] Verify long hostnames, IPv6 addresses, multiple records, timeouts, and partial results do not cause clipping or layout shifts.

### 2. Functional Testing

- [ ] Run ARP Discovery against controlled reachable, closed, and unreachable targets and verify emitted results match packet captures or the test server.
- [ ] Test cancellation, timeout, duplicate responses, out-of-order responses, partial discovery, and permission denial; verify final state and counts remain correct.

- [ ] Test public ranges, oversized CIDRs, rapid restart, excessive concurrency, and missing authorization acknowledgement; verify scanning is blocked until a valid private subnet and explicit start are present.

### 3. Compatibility Testing

- [ ] Verify ARP Discovery on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure ARP Discovery with small, typical, and maximum supported workloads; verify progress remains responsive and completion respects the configured timeout or product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify ARP Discovery validates and bounds all host, address, port, subnet, resolver, URI, and provider data before network use or intent launch; malformed data must not cause injection or unsafe routing.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Capture scan traffic and lifecycle state; verify packets stay inside the displayed private subnet, remain rate-limited, contain no exploit or credential attempts, and stop after cancellation.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify ARP Discovery controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] While ARP Discovery is loading or running, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify no duplicate jobs, crashes, or corrupted results.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
