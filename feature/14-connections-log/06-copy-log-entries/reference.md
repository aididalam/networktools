# Copy Log Entries

## Feature: How will it work?

The user can copy the currently available log entries from the Connections Log screen to the system clipboard. The action is enabled only when content exists and confirms completion without changing the result.

## Google Play Policy Requirements

- Logging must be explicitly enabled by the user, visibly indicated, and limited to connectivity metadata observable by NetworkTools; do not use VpnService, packet capture, package usage, or other-app monitoring.
- Store entries only in app-private local storage, provide clear/delete/export controls, and never send logs to analytics, advertising, or remote storage.
- Do not run hidden persistent surveillance; stop callbacks when logging is disabled and accurately disclose any user-visible foreground execution. Follow the [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).

## Test Cases

### 1. UI/UX Testing

- [ ] Verify the Copy Log Entries action uses a recognizable icon or clear command label, has an appropriate enabled or disabled state, and gives immediate feedback.
- [ ] Trigger success, unavailable, and failure states; verify messages are concise, anchored to the action, and do not obscure existing results.

### 2. Functional Testing

- [ ] With a known Connections Log result, invoke Copy Log Entries and verify the clipboard text exactly matches the visible eligible fields and formatting.
- [ ] Verify the action is disabled or explains the empty state when no result exists, and that repeated copies do not mutate the source data.

- [ ] Verify opening the visible Connections Log screen establishes a fresh baseline, closing it unregisters monitoring, and no event is inferred or recorded while the screen is closed.

### 3. Compatibility Testing

- [ ] Verify Copy Log Entries on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on Wi-Fi, mobile data, VPN, IPv4-only, IPv6-only, dual-stack, offline, and platform-restricted configurations where the feature applies.

### 4. Performance Testing

- [ ] Measure opening and using Copy Log Entries; verify work stays off the main thread where needed and UI frames remain responsive within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Copy Log Entries validates untrusted input and persisted values, rejects oversized or malformed data, and fails without exposing internal exceptions.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Inspect permissions, services, packets, logs, analytics, and stored rows; verify there is no VpnService, packet content, app inventory, hidden background service, or remote log transmission.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Copy Log Entries controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] While Copy Log Entries is loading or running, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify no duplicate jobs, crashes, or corrupted results.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
