# Total Address Calculation

## Feature: How will it work?

The IP Calculator derives total address calculation locally from validated IPv4 or IPv6 input and prefix information. Recalculation is deterministic, uses address-family-correct arithmetic, and shows inline errors for invalid or incompatible values.

## Google Play Policy Requirements

- Perform all calculations locally and deterministically without internet access, device identifiers, location, account data, or sensitive runtime permissions.
- Do not transmit calculator inputs or results to analytics, advertising, crash messages, or remote storage. Follow the [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).
- Use only packaged calculation code and data; do not download executable logic or silently change calculation rules outside a Play-distributed update.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify inputs, address family, prefix controls, calculated Total Address Calculation output, and validation messages are visually grouped in reading order.
- [ ] Use long IPv6 values and large results; verify monospace-sensitive values remain readable, copyable, and unclipped.

### 2. Functional Testing

- [ ] Run published IPv4 and IPv6 test vectors for Total Address Calculation and compare every result against independently calculated expected values.
- [ ] Test prefix boundaries, zero and maximum addresses, malformed notation, mixed address families, and overflow conditions; verify deterministic validation.

- [ ] Disable connectivity and deny all optional permissions; verify every supported calculation, validation path, copy action, and history decision still works locally.

### 3. Compatibility Testing

- [ ] Verify Total Address Calculation on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on phone and tablet window sizes, portrait and landscape, light and dark themes, and with supported locale changes.

### 4. Performance Testing

- [ ] Measure opening and using Total Address Calculation with IPv4 /0 and IPv6 /0 values; verify 128-bit arithmetic completes promptly without blocking UI frames.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Total Address Calculation validates untrusted input and persisted values, rejects oversized or malformed data, and fails without exposing internal exceptions.
- [ ] Verify the calculator requests no runtime or network permission, opens no socket, persists no input or result, and writes no address data to logs, analytics, backups, or crash messages.

- [ ] Inspect manifest permissions, network traffic, logs, analytics, and storage while calculating; verify inputs and results never leave the documented on-device workflow.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Total Address Calculation controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Total Address Calculation, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Switch Wi-Fi, mobile data, VPN, and airplane mode while calculating; verify the result is unchanged because calculation is local and does not depend on connectivity.
