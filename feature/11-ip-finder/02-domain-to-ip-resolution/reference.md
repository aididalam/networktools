# Domain To IP Resolution

## Feature: How will it work?

The IP Finder validates the domain and resolves its A and AAAA records using Android's active DNS configuration or a user-selected DNS server. Resolution uses standard DNS directly and requires no API account or key.

## Google Play Policy Requirements

- Use only keyless public-IP echo, standard DNS, IANA bootstrap, and authoritative RDAP/DNS-to-ASN lookups; do not add paid/keyed enrichment or hidden location/privacy profiling.
- Run lookups only after an explicit user action, apply conservative rate limits, and do not provide bulk IP or domain enumeration. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Do not store or send targets and results to analytics, advertising, or unrelated services; disclose the minimum keyless endpoint processing in the privacy policy. Follow the [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).

## Test Cases

### 1. UI/UX Testing

- [ ] Verify Domain To IP Resolution presents a clear input or source state, progress indication, result area, empty state, and retry path.
- [ ] Verify long hostnames, IPv6 addresses, multiple records, timeouts, and partial results do not cause clipping or layout shifts.

### 2. Functional Testing

- [ ] Use a controlled resolver or response fixture with known Domain To IP Resolution data and verify query construction, parsing, normalization, and displayed output.
- [ ] Test malformed input, no-record responses, referral or multiple-answer responses, timeout, truncated data, and server failure; verify distinct outcomes.

- [ ] Attempt bulk, wildcard, multi-line, rapid-repeat, and geolocation/privacy-enrichment requests; verify only one bounded keyless lookup runs and removed intelligence fields are never requested or shown.

### 3. Compatibility Testing

- [ ] Verify Domain To IP Resolution on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure Domain To IP Resolution with small, typical, and maximum supported workloads; verify progress remains responsive and completion respects the configured timeout or product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Domain To IP Resolution validates and bounds all host, address, port, subnet, resolver, URI, and provider data before network use or intent launch; malformed data must not cause injection or unsafe routing.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Capture all network calls and inspect configuration; verify only documented keyless DNS, IANA, RDAP, ASN, and public-IP endpoints are contacted and no secret or runtime API key exists.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Domain To IP Resolution controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] While Domain To IP Resolution is loading or running, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify no duplicate jobs, crashes, or corrupted results.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
