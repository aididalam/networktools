# Stop Active Ping

## Feature: How will it work?

The stop action cancels the active Ping operation, prevents additional results from being appended, and retains the results already received. The screen then returns to a state where the user can start a new operation.

## Google Play Policy Requirements

- Start probes only after an explicit user action against the displayed target and show an authorized-use notice. Follow the [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).
- Enforce conservative packet-count, packet-size, timeout, interval, and concurrency limits; do not implement flood, stealth, evasion, or background probing.
- Cancel sockets and jobs when stopped or when the active workflow ends, and never expand a hostname into scans of neighboring addresses.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify the Stop Active Ping action uses a recognizable icon or clear command label, has an appropriate enabled or disabled state, and gives immediate feedback.
- [ ] Trigger success, unavailable, and failure states; verify messages are concise, anchored to the action, and do not obscure existing results.

### 2. Functional Testing

- [ ] Start a multi-step Ping operation, invoke Stop Active Ping, and verify active jobs and sockets are cancelled and no later callback appends results.
- [ ] Invoke stop before start, during startup, and after completion; verify idempotent behavior and immediate ability to run again.

- [ ] Try zero, negative, excessive, rapid, repeated, and concurrent probe settings; verify validation and hard limits prevent flooding and no probe starts without pressing the visible start action.

### 3. Compatibility Testing

- [ ] Verify Stop Active Ping on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure opening and using Stop Active Ping; verify work stays off the main thread where needed and UI frames remain responsive within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Stop Active Ping validates untrusted input and persisted values, rejects oversized or malformed data, and fails without exposing internal exceptions.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Use packet capture and lifecycle tests to verify traffic is limited to the displayed target and configured bounded method, stops on cancellation, and never continues as a hidden background task.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Stop Active Ping controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Stop Active Ping, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
