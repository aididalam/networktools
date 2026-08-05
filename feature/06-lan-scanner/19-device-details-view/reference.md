# Device Details View

## Feature: How will it work?

The expanded device view shows normalized identity evidence, discovery protocols, advertised services, and open common TCP ports found by a Deep scan. Each port is paired with a conservative likely-service label based on the port number; supported web ports offer an explicit external-browser action.

## Google Play Policy Requirements

- Default scanning to the active private LAN only, require explicit user start and authorization acknowledgement, and reject public internet ranges or oversized subnets. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Enforce conservative host, protocol, timeout, retry, and concurrency limits; stop immediately on cancellation or when the active scan ends.
- Limit behavior to discovery and identification; do not add exploitation, credential guessing, vulnerability bypass, stealth, persistence, or hidden background scanning.
- Request Nearby Devices only just before an Android 16 local-network operation when local-network protection applies; when targeting Android 17 or higher, migrate this gate to `ACCESS_LOCAL_NETWORK` and preserve denial and revocation handling.
- Treat IP addresses, hostnames, MAC addresses, and service advertisements as sensitive local data: keep them on-device, exclude history from backup and device transfer, and do not send them to analytics or remote providers.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify Device Details View is clearly labeled, aligned with related LAN Scanner values, and distinguishes loading, available, unavailable, and error states.
- [ ] Verify long values, multiple values, and localized labels wrap or truncate intentionally without hiding copy, refresh, or navigation actions.

### 2. Functional Testing

- [ ] Provide controlled platform or provider data for Device Details View and verify the LAN Scanner screen displays the exact normalized expected value.
- [ ] Verify unavailable permissions, invalid or absent data, timeout, and provider or platform errors produce the documented fallback without stale or fabricated device details view data.

- [ ] Test public ranges, oversized CIDRs, rapid restart, excessive concurrency, and missing authorization acknowledgement; verify scanning is blocked until a valid private subnet and explicit start are present.

### 3. Compatibility Testing

- [ ] Verify Device Details View on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure Device Details View with small, typical, and maximum supported workloads; verify progress remains responsive and completion respects the configured timeout or product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Device Details View validates untrusted input and persisted values, rejects oversized or malformed data, and fails without exposing internal exceptions.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Capture scan traffic and lifecycle state; verify packets stay inside the displayed private subnet, remain rate-limited, contain no exploit or credential attempts, and stop after cancellation.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Device Details View controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Device Details View, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
