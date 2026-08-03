# DNS Propagation Checking

## Feature: How will it work?

The DNS Lookup Compare view sends the same user-entered DNS question once to the active system resolver and the fixed Cloudflare, Google, and Quad9 public resolvers. It displays each structured answer, TTL, latency, and response state, then reports whether the successful answer sets match at that moment. It does not claim exhaustive worldwide propagation coverage.

## Google Play Policy Requirements

- Execute only user-initiated, bounded DNS questions for the displayed name and selected record type. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Validate names, record types, resolver addresses, message sizes, retry counts, and timeouts; do not implement amplification, spoofing, reflection, bulk enumeration, background querying, or scheduled monitoring.
- Clearly identify comparison as a current consistency sample across four resolvers rather than a guarantee of global propagation.
- Build and parse bounded DNS messages on-device with the embedded BSD-licensed dnsjava library; do not use a paid API, API key, hosted lookup backend, analytics provider, proxy list, or remotely supplied resolver list.
- Limit each user action to one displayed DNS name, the fixed four-resolver comparison, a 1-3 second timeout, a 1,232-byte EDNS UDP payload, one TCP retry only after truncation, and at most 100 displayed records per response.
- For a private-LAN system resolver, request Nearby Devices only immediately before an Android 16 query when local-network protection applies. When targeting Android 17 or later, migrate to `ACCESS_LOCAL_NETWORK` and preserve denial and revocation behavior.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify DNS Propagation Checking displays the queried name and type, progress for up to four resolvers, per-resolver state, answer values, TTLs, latency, and a clear consistency summary.
- [ ] Verify long names, multiple records, different TTLs, partial failures, and differing answers wrap correctly without clipping or layout shifts.

### 2. Functional Testing

- [ ] Query a controlled name with matching answers across all resolvers and verify the result is marked matching even when TTL values differ.
- [ ] Test differing answers, NXDOMAIN, no-record, refusal, timeout, truncation with TCP retry, cancellation, and partial resolver failure; verify each result and final consistency state remain accurate.
- [ ] Trigger rapid repeated actions and malformed or oversized input; verify only one active comparison, four fixed resolvers, and no automatic enumeration or scheduled rechecking.

### 3. Compatibility Testing

- [ ] Verify DNS Propagation Checking on the minimum supported Android API, the target API, and latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, Private DNS, offline, and networks that block external UDP or TCP DNS.
- [ ] On Android 16, verify a private system resolver requests Nearby Devices only after the user starts comparison and handles denial or revocation safely; on Android 17 or later, repeat with `ACCESS_LOCAL_NETWORK`.

### 4. Performance Testing

- [ ] Measure comparison with small, typical, and maximum supported answer sets; verify the UI remains responsive and all work ends within the selected per-resolver timeout budget.
- [ ] Repeat comparison at least 20 times and monitor memory, CPU, battery, sockets, DNS NIO tasks, and coroutine counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify names and fixed resolver addresses are validated before network use and malformed messages cannot cause injection, unsafe routing, oversized allocation, or record-count bypass.
- [ ] Verify least-privilege permissions, transient app-private state, and no DNS names, resolver addresses, or results in production logs, analytics, backups, or crash messages.
- [ ] Inspect the packaged app and captured traffic; verify there is no hosted propagation service, paid or keyed API, analytics upload, background query, automatic enumeration, or resolver download.
- [ ] Capture traffic for success, timeout, TCP fallback, cancellation, navigation, and backgrounding; verify only one question reaches each displayed fixed resolver and all active work stops promptly.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify resolver names, response states, answer values, TTLs, latency, consistency state, errors, and progress have meaningful labels and logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During comparison, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify no duplicate queries, crashes, or corrupted partial results.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery; verify cancellation, final per-resolver status, and retry behavior remain accurate.
