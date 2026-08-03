# Public IP Lookup

## Feature: How will it work?

The IP Finder requests the device's internet-facing IPv4 or IPv6 address from a keyless HTTPS IP-echo endpoint and validates the response before display. It provides explicit offline, timeout, HTTP-error, and invalid-response states.

## Google Play Policy Requirements

- Use only keyless public-IP echo, standard DNS, IANA bootstrap, and authoritative RDAP/DNS-to-ASN lookups; do not add paid/keyed enrichment or hidden location/privacy profiling.
- Run lookups only after an explicit user action, apply conservative rate limits, and do not provide bulk IP or domain enumeration. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Do not store or send targets and results to analytics, advertising, or unrelated services; disclose the minimum keyless endpoint processing in the privacy policy. Follow the [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).

## Test Cases

### 1. UI/UX Testing

- [ ] Verify Public IP Lookup presents a clear input or source state, progress indication, result area, empty state, and retry path.
- [ ] Verify long hostnames, IPv6 addresses, multiple records, timeouts, and partial results do not cause clipping or layout shifts.

### 2. Functional Testing

- [ ] Return controlled IPv4 and IPv6 endpoint responses and verify the displayed address exactly matches the normalized response.
- [ ] Test timeout, offline, HTTP errors, redirects, oversized bodies, HTML, private addresses, and malformed responses; verify distinct error outcomes.

- [ ] Attempt bulk, wildcard, multi-line, rapid-repeat, and geolocation/privacy-enrichment requests; verify only one bounded keyless lookup runs and removed intelligence fields are never requested or shown.

### 3. Compatibility Testing

- [ ] Verify Public IP Lookup on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure Public IP Lookup with small, typical, and maximum supported workloads; verify progress remains responsive and completion respects the configured timeout or product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Public IP Lookup validates and bounds all host, address, port, subnet, resolver, URI, and provider data before network use or intent launch; malformed data must not cause injection or unsafe routing.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Capture all network calls and inspect configuration; verify only documented keyless DNS, IANA, RDAP, ASN, and public-IP endpoints are contacted and no secret or runtime API key exists.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Public IP Lookup controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] While Public IP Lookup is loading or running, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify no duplicate jobs, crashes, or corrupted results.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
