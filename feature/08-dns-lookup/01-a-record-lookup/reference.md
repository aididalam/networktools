# A Record Lookup

## Feature: How will it work?

The DNS Lookup screen performs a record lookup for the validated name through the selected resolver. The response is parsed into structured records with resolver and TTL context, while NXDOMAIN, timeout, and malformed responses receive distinct error states.

## Google Play Policy Requirements

- Execute only user-initiated, bounded DNS questions for the displayed name and selected resolver. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Validate names, record types, resolver addresses, message sizes, retry counts, and timeouts; do not implement amplification, spoofing, reflection, bulk enumeration, or background querying.
- Respect active Private DNS behavior and clearly identify when a custom resolver changes the normal protected resolution path.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify A Record Lookup presents a clear input or source state, progress indication, result area, empty state, and retry path.
- [ ] Verify long hostnames, IPv6 addresses, multiple records, timeouts, and partial results do not cause clipping or layout shifts.

### 2. Functional Testing

- [ ] Use a controlled resolver or response fixture with known A Record Lookup data and verify query construction, parsing, normalization, and displayed output.
- [ ] Test malformed input, no-record responses, referral or multiple-answer responses, timeout, truncated data, and server failure; verify distinct outcomes.

- [ ] Submit oversized names, excessive retries, rapid repeated queries, invalid custom resolvers, and bulk-style input; verify bounded single-target behavior and no automatic enumeration.

### 3. Compatibility Testing

- [ ] Verify A Record Lookup on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure A Record Lookup with small, typical, and maximum supported workloads; verify progress remains responsive and completion respects the configured timeout or product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify A Record Lookup validates and bounds all host, address, port, subnet, resolver, URI, and provider data before network use or intent launch; malformed data must not cause injection or unsafe routing.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Capture DNS traffic and malformed responses; verify requests use the displayed resolver and record type, reject amplification-oriented payloads, avoid leaking unrelated names, and stop after the active lookup.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify A Record Lookup controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] While A Record Lookup is loading or running, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify no duplicate jobs, crashes, or corrupted results.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
