# Custom DNS Server Selection

## Feature: How will it work?

The IP and Host Converter performs custom DNS server selection after validating the hostname or address. DNS work runs off the main thread, preserves multiple valid answers, and distinguishes no-record, malformed-input, and resolver-failure states.

## Google Play Policy Requirements

- Run hostname and reverse-DNS operations only after an explicit user action for one validated target; do not provide bulk enumeration. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Bound answer count, resolver requests, retries, timeout, input length, and custom resolver behavior; stop all work when the active conversion ends.
- Do not persist or transmit entered hosts, addresses, or DNS answers to analytics, advertising, or unrelated services. Follow the [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).

## Test Cases

### 1. UI/UX Testing

- [ ] Given the IP And Host Converter screen is open, verify the Custom DNS Server Selection control has a clear label, suitable input control or keyboard, and visible current value.
- [ ] Enter empty, malformed, minimum, maximum, and over-limit values; verify inline errors do not resize, overlap, or hide the action controls.

### 2. Functional Testing

- [ ] Apply valid boundary and representative values for Custom DNS Server Selection; verify the next IP And Host Converter operation uses the exact normalized setting.
- [ ] Submit empty, malformed, out-of-range, duplicate, and unsupported values; verify execution is blocked with a field-specific error.

- [ ] Try wildcard, multi-line, oversized, rapid-repeat, and bulk-style inputs; verify one bounded target is processed per explicit action and work stops after cancellation.

### 3. Compatibility Testing

- [ ] Verify Custom DNS Server Selection on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure opening and using Custom DNS Server Selection; verify work stays off the main thread where needed and UI frames remain responsive within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Custom DNS Server Selection validates untrusted input and persisted values, rejects oversized or malformed data, and fails without exposing internal exceptions.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Inspect DNS traffic, logs, analytics, cache, and lifecycle behavior; verify only the displayed target reaches the selected resolver and no lookup continues in the background.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Custom DNS Server Selection controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Custom DNS Server Selection, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
