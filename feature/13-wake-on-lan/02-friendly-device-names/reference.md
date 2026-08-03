# Friendly Device Names

## Feature: How will it work?

Every profile requires a trimmed friendly name of 1-48 non-control characters. Names identify saved cards and confirmation/result states only; they are not placed in the binary magic packet, sent to DNS, or uploaded.

## Google Play Policy Requirements

- Send a magic packet only after an explicit user action to a displayed, user-saved or user-entered device that the user is authorized to manage. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Validate MAC, destination, broadcast scope, and UDP port; rate-limit repeated sends and never run scheduled, hidden, relayed, or background Wake-on-LAN jobs.
- Keep saved device identifiers in app-private local storage and transmit only the standard magic packet to the confirmed destination.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify Friendly Device Names is clearly labeled, aligned with related Wake-on-LAN values, and distinguishes loading, available, unavailable, and error states.
- [ ] Verify long values, multiple values, and localized labels wrap or truncate intentionally without hiding copy, refresh, or navigation actions.

### 2. Functional Testing

- [ ] Provide controlled platform or provider data for Friendly Device Names and verify the Wake-on-LAN screen displays the exact normalized expected value.
- [ ] Verify unavailable permissions, invalid or absent data, timeout, and provider or platform errors produce the documented fallback without stale or fabricated friendly device names data.

- [ ] Test missing confirmation, malformed targets, public destinations, rapid repeated taps, app backgrounding, and cancellation; verify validation and rate limits prevent unintended packet sends.

### 3. Compatibility Testing

- [ ] Verify Friendly Device Names on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure opening and using Friendly Device Names; verify work stays off the main thread where needed and UI frames remain responsive within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Friendly Device Names validates untrusted input and persisted values, rejects oversized or malformed data, and fails without exposing internal exceptions.
- [ ] Verify friendly names remain app-private, are excluded from backup/device transfer, are absent from packet bytes and DNS questions, and are not logged or sent to analytics or remote services.

- [ ] Capture traffic, logs, analytics, and storage; verify only a standard magic packet reaches the displayed destination and saved MAC/IP data is not uploaded or exposed.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Friendly Device Names controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Friendly Device Names, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
