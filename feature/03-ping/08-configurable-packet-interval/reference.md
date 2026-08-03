# Configurable Packet Interval

## Feature: How will it work?

The Ping screen exposes configurable packet interval as a validated setting applied to the next probe run. Unsafe or unsupported values are rejected inline and the chosen value remains visible while the run is active.

## Google Play Policy Requirements

- Start probes only after an explicit user action against the displayed target and show an authorized-use notice. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Enforce conservative packet-count, packet-size, timeout, interval, and concurrency limits; do not implement flood, stealth, evasion, or background probing.
- Cancel sockets and jobs when stopped or when the active workflow ends, and never expand a hostname into scans of neighboring addresses.

## Test Cases

### 1. UI/UX Testing

- [ ] Given the Ping screen is open, verify the Configurable Packet Interval control has a clear label, suitable input control or keyboard, and visible current value.
- [ ] Enter empty, malformed, minimum, maximum, and over-limit values; verify inline errors do not resize, overlap, or hide the action controls.

### 2. Functional Testing

- [ ] Apply valid boundary and representative values for Configurable Packet Interval; verify the next Ping operation uses the exact normalized setting.
- [ ] Submit empty, malformed, out-of-range, duplicate, and unsupported values; verify execution is blocked with a field-specific error.

- [ ] Try zero, negative, excessive, rapid, repeated, and concurrent probe settings; verify validation and hard limits prevent flooding and no probe starts without pressing the visible start action.

### 3. Compatibility Testing

- [ ] Verify Configurable Packet Interval on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure opening and using Configurable Packet Interval; verify work stays off the main thread where needed and UI frames remain responsive within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Configurable Packet Interval validates untrusted input and persisted values, rejects oversized or malformed data, and fails without exposing internal exceptions.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Use packet capture and lifecycle tests to verify traffic is limited to the displayed target and configured bounded method, stops on cancellation, and never continues as a hidden background task.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Configurable Packet Interval controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Configurable Packet Interval, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
