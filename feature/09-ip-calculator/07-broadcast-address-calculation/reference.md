# Broadcast Address Calculation

## Feature: How will it work?

The IP Calculator derives broadcast address calculation locally from validated IPv4 or IPv6 input and prefix information. Recalculation is deterministic, uses address-family-correct arithmetic, and shows inline errors for invalid or incompatible values.

## Google Play Policy Requirements

- Perform all calculations locally and deterministically without internet access, device identifiers, location, account data, or sensitive runtime permissions.
- Do not transmit calculator inputs or results to analytics, advertising, crash messages, or remote storage. Follow the [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).
- Use only packaged calculation code and data; do not download executable logic or silently change calculation rules outside a Play-distributed update.

## Test Cases

### 1. UI/UX Testing

- [ ] Verify inputs, address family, prefix controls, calculated Broadcast Address Calculation output, and validation messages are visually grouped in reading order.
- [ ] Use long IPv6 values and large results; verify monospace-sensitive values remain readable, copyable, and unclipped.

### 2. Functional Testing

- [ ] Run published IPv4 and IPv6 test vectors for Broadcast Address Calculation and compare every result against independently calculated expected values.
- [ ] Test prefix boundaries, zero and maximum addresses, malformed notation, mixed address families, and overflow conditions; verify deterministic validation.

- [ ] Disable connectivity and deny all optional permissions; verify every supported calculation, validation path, copy action, and history decision still works locally.

### 3. Compatibility Testing

- [ ] Verify Broadcast Address Calculation on the minimum supported Android API, the target API, and the latest available API across representative Pixel and non-Pixel devices.
- [ ] Verify behavior on phone and tablet window sizes, portrait and landscape, light and dark themes, and with supported locale changes.

### 4. Performance Testing

- [ ] Measure opening and using Broadcast Address Calculation; verify work stays off the main thread where needed and UI frames remain responsive within the product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Verify Broadcast Address Calculation validates untrusted input and persisted values, rejects oversized or malformed data, and fails without exposing internal exceptions.
- [ ] Verify least-privilege permissions, encrypted HTTPS for remote providers, app-private storage where applicable, and no sensitive network identifiers or results in production logs, analytics, or crash messages.

- [ ] Inspect manifest permissions, network traffic, logs, analytics, and storage while calculating; verify inputs and results never leave the documented on-device workflow.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Broadcast Address Calculation controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Broadcast Address Calculation, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify committed state is restored and transient actions are not repeated.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
