# Custom Port List Scanning

## Feature: How will it work?

The Port Scanner applies custom port list scanning to a validated target and bounded port set using timed TCP connection attempts. It reports confirmed results only, distinguishes closed ports from timeouts when possible, and supports cancellation.

## Google Play Policy Requirements

- Scan only a single displayed host supplied or confirmed by the user, require an authorized-use acknowledgement, and start only from an explicit action. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Bound the port count, range, custom list, timeout, retries, and concurrency; reject duplicate, malformed, oversized, or automation-style requests.
- Detect connection state and common services only; do not implement exploitation, banner attacks, authentication attempts, evasion, stealth, or background scanning.

## Test Cases

### 1. UI/UX Testing

- [ ] Given the Port Scanner screen is open, verify the Custom Port List Scanning control has a clear label, suitable input control or keyboard, and visible current value.
- [ ] Enter empty, malformed, minimum, maximum, and over-limit values; verify inline errors do not resize, overlap, or hide the action controls.

### 2. Functional Testing

- [ ] Apply valid boundary and representative values for Custom Port List Scanning; verify the next Port Scanner operation uses the exact normalized setting.
- [ ] Submit empty, malformed, out-of-range, duplicate, and unsupported values; verify execution is blocked with a field-specific error.

- [ ] Test excessive ranges, duplicate lists, invalid ports, rapid restart, missing authorization acknowledgement, and concurrent scans; verify hard limits and explicit start requirements.

### 3. Compatibility Testing

- [ ] Verify Custom Port List Scanning on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure Custom Port List Scanning with small, typical, and maximum supported workloads; verify progress remains responsive and completion respects the configured timeout or product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Custom Port List Scanning validates untrusted input and persisted values, rejects oversized or malformed data, and fails without exposing internal exceptions.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Capture traffic for success, timeout, cancel, navigation, and backgrounding; verify connections go only to the displayed host and ports, contain no exploit or credential payloads, and terminate promptly.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Custom Port List Scanning controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Custom Port List Scanning, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
