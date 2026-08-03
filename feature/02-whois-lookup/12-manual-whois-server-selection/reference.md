# Manual WHOIS Server Selection

## Feature: How will it work?

The WHOIS Lookup screen uses manual WHOIS server selection to determine which WHOIS service receives the validated domain or IP query. The selected server is shown before execution, and an unreachable or unsupported server produces a recoverable error.

## Google Play Policy Requirements

- Run only an explicit, user-initiated lookup for one validated domain or IP target at a time; do not provide bulk harvesting or automated enumeration. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Respect authoritative RDAP/WHOIS rate limits, referrals, terms, redaction, and retry guidance; do not bypass registry controls or claim unavailable registrant data.
- Treat registrant and contact fields as sensitive: do not send them to analytics, advertising, or remote storage, and expose copy/share only as explicit user actions. Follow the [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).

## Test Cases

### 1. UI/UX Testing

- [ ] Given the WHOIS Lookup screen is open, verify the Manual WHOIS Server Selection control has a clear label, suitable input control or keyboard, and visible current value.
- [ ] Enter empty, malformed, minimum, maximum, and over-limit values; verify inline errors do not resize, overlap, or hide the action controls.

### 2. Functional Testing

- [ ] Apply valid boundary and representative values for Manual WHOIS Server Selection; verify the next WHOIS Lookup operation uses the exact normalized setting.
- [ ] Submit empty, malformed, out-of-range, duplicate, and unsupported values; verify execution is blocked with a field-specific error.

- [ ] Attempt rapid, repeated, wildcard, and bulk-style submissions; verify the app validates a single target, applies client-side rate limits, and never starts an automatic enumeration job.

### 3. Compatibility Testing

- [ ] Verify Manual WHOIS Server Selection on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure opening and using Manual WHOIS Server Selection; verify work stays off the main thread where needed and UI frames remain responsive within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Manual WHOIS Server Selection validates and bounds all host, address, port, subnet, resolver, URI, and provider data before network use or intent launch; malformed data must not cause injection or unsafe routing.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Inspect logs, analytics, cache, clipboard, and network calls; verify registrant data is retained only for the visible result or explicit user action and no registry-control bypass is attempted.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Manual WHOIS Server Selection controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Manual WHOIS Server Selection, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
