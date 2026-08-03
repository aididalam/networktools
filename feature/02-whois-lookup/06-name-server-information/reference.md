# Name Server Information

## Feature: How will it work?

After a validated domain or IP query, the WHOIS client follows the selected server strategy, parses the response, and exposes name server information as a labeled result. Missing or redacted WHOIS fields are shown as unavailable rather than guessed.

## Google Play Policy Requirements

- Run only an explicit, user-initiated lookup for one validated domain or IP target at a time; do not provide bulk harvesting or automated enumeration. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Respect authoritative RDAP/WHOIS rate limits, referrals, terms, redaction, and retry guidance; do not bypass registry controls or claim unavailable registrant data.
- Treat registrant and contact fields as sensitive: do not send them to analytics, advertising, or remote storage, and expose copy/share only as explicit user actions. Follow the [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).

## Test Cases

### 1. UI/UX Testing

- [ ] Verify Name Server Information presents a clear input or source state, progress indication, result area, empty state, and retry path.
- [ ] Verify long hostnames, IPv6 addresses, multiple records, timeouts, and partial results do not cause clipping or layout shifts.

### 2. Functional Testing

- [ ] Use a controlled resolver or response fixture with known Name Server Information data and verify query construction, parsing, normalization, and displayed output.
- [ ] Test malformed input, no-record responses, referral or multiple-answer responses, timeout, truncated data, and server failure; verify distinct outcomes.

- [ ] Attempt rapid, repeated, wildcard, and bulk-style submissions; verify the app validates a single target, applies client-side rate limits, and never starts an automatic enumeration job.

### 3. Compatibility Testing

- [ ] Verify Name Server Information on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure Name Server Information with small, typical, and maximum supported workloads; verify progress remains responsive and completion respects the configured timeout or product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Name Server Information validates and bounds all host, address, port, subnet, resolver, URI, and provider data before network use or intent launch; malformed data must not cause injection or unsafe routing.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Inspect logs, analytics, cache, clipboard, and network calls; verify registrant data is retained only for the visible result or explicit user action and no registry-control bypass is attempted.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Name Server Information controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] While Name Server Information is loading or running, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify no duplicate jobs, crashes, or corrupted results.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
