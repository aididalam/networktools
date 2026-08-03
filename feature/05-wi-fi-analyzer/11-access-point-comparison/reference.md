# Access Point Comparison

## Feature: How will it work?

The Wi-Fi Analyzer uses Android's permitted scan results to calculate and present access point comparison. Scans run asynchronously, clearly distinguish stale and current data, and explain when location, nearby-device, Wi-Fi, or platform restrictions prevent access.

## Google Play Policy Requirements

- Perform Wi-Fi scans only in a visible, user-initiated workflow, comply with platform scan throttling, and do not request background location. Follow the [Permissions and Sensitive APIs policy](https://support.google.com/googleplay/android-developer/answer/16909972).
- Present a just-in-time permission explanation, support denial and revocation, and never block unrelated tools because scan permission is unavailable.
- Keep SSIDs, BSSIDs, saved-network details, and scan results on-device; do not upload them, derive precise location, or send them to analytics or advertising. Follow the [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).

## Test Cases

### 1. UI/UX Testing

- [ ] Verify users can select access points for comparison and see aligned SSID, BSSID, signal, channel, frequency, band, and security fields.
- [ ] Verify missing fields, long identifiers, equal values, weak signals, and cross-band comparisons remain readable and do not imply unsupported rankings.

### 2. Functional Testing

- [ ] Compare controlled scan fixtures and verify each displayed metric is associated with the correct BSSID and retains its original unit and value.
- [ ] Test fewer than two networks, duplicate selections, disappearing access points, refreshed values, hidden SSIDs, and different bands; verify selection and comparison state remain valid.

- [ ] Verify no scan or permission prompt occurs before the user enters Wi-Fi Analyzer and starts scanning; test denial, revocation, throttling, Wi-Fi disabled, and leaving the screen.

### 3. Compatibility Testing

- [ ] Verify Access Point Comparison on Android versions before and after Wi-Fi scan permission changes, including representative Pixel and non-Pixel devices.
- [ ] Verify Wi-Fi enabled and disabled, location services enabled and disabled, permission granted and denied, scan throttling, and supported 2.4 GHz, 5 GHz, and 6 GHz networks.

### 4. Performance Testing

- [ ] Measure Access Point Comparison with small, typical, and maximum supported workloads; verify progress remains responsive and completion respects the configured timeout or product performance budget.
- [ ] Repeat the workflow at least 20 times and monitor memory, CPU, battery, sockets, and coroutine or callback counts; verify resources return to baseline after completion or cancellation.

### 5. Security Testing

- [ ] Feed malformed, oversized, duplicated, and rapidly changing scan-result fields; verify parsing is bounded and cannot crash or corrupt the network list.
- [ ] Verify least-privilege Wi-Fi and location permissions and confirm SSIDs, BSSIDs, saved-network details, and scan results are not written to production logs, analytics, or remote services.

- [ ] Inspect manifest permissions, network calls, logs, analytics, and storage; verify there is no background-location request or upload of SSIDs, BSSIDs, saved-network details, or scan results.

### 6. Accessibility Testing

- [ ] With TalkBack enabled, verify Access Point Comparison controls, values, units, states, errors, and progress have meaningful labels and a logical traversal order.
- [ ] Verify keyboard or switch access, minimum touch targets, visible focus, color contrast, dark and light themes, and 200% font or display scaling without clipped content.

### 7. Interruption Testing

- [ ] During Access Point Comparison, rotate, resize, background and foreground the app, lock and unlock the device, and trigger process recreation; verify selections and the latest comparison are restored without mismatching BSSIDs.
- [ ] Interrupt with Wi-Fi or mobile switching, VPN changes, airplane mode, permission revocation, and network loss or recovery where applicable; verify cancellation, retry, and final status remain accurate.
